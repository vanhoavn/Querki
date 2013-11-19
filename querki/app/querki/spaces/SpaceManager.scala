package querki.spaces

import language.postfixOps
import scala.util._

import models.{AsName, AsOID, OID}
import models.{Kind, Thing}
import models.system.{DisplayNameProp, NameProp, NameType}
import models.system.OIDs._
import messages._
import SpaceError._

import querki.db.ShardKind
import ShardKind._

import scala.concurrent.duration._
import scala.concurrent.Future

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import anorm.{Success=>AnormSuccess,_}
import play.api._
import play.api.db._
import play.api.libs.concurrent._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current
import play.Configuration

import querki.identity.User

import querki.spaces._

import querki.util._
import querki.util.SqlHelpers._

class SpaceManager extends Actor {
  import models.system.SystemSpace
  import SystemSpace._
  import Space._
  
  // TEMP: this should be passed into SpaceManager:
  val persistenceFactory = new DBSpacePersistenceFactory
  
  /**
   * This Actor deals with all DB-style operations for the SpaceManager.
   */
  lazy val persister = persistenceFactory.getSpaceManagerPersister
  
  def getSpace(spaceId:OID):ActorRef = {
    val sid = Space.sid(spaceId)
 	// TODO: this *should* be using context.child(), but that doesn't exist in Akka
    // 2.0.2, so we have to wait until we have access to 2.1.0:
    //val childOpt = context.child(sid)
    val childOpt = context.children find (_.path.name == sid)
    childOpt match {
      case Some(child) => child
      case None => {
        context.actorOf(Space.actorProps(persistenceFactory), sid)
      }
    }
  }
  
  def receive = {
    // This is entirely a DB operation, so just have the Persister deal with it:
    case req @ ListMySpaces(owner) => persister.forward(req)

    case req @ CreateSpace(requester, display) => {
      checkLegalSpaceCreation(requester,display) match {
        case Failure(error:PublicException) => sender ! ThingError(error)
        case Failure(ex) => { QLog.error("Error during CreateSpace", ex); sender ! ThingError(UnexpectedPublicException) }
        case Success(u) => {
          val userMaxSpaces = {
            if (requester.isAdmin || requester.level == querki.identity.UserLevel.PermanentUser)
              Int.MaxValue
            else
              maxSpaces
          }
          createSpace(requester.mainIdentity.id, userMaxSpaces, NameType.canonicalize(display), display) match {
            case Failure(error:PublicException) => sender ! ThingError(error)
            case Failure(ex) => { QLog.error("Error during CreateSpace", ex); sender ! ThingError(UnexpectedPublicException) }
            // Now, let the Space Actor finish the process once it is ready:
            case Success(spaceId) => getSpace(spaceId).forward(req)
          }       
        }
      }
    }

    // TODO: CRITICAL: we need a pseudo-Space for System!
    // This clause is a pure forwarder for messages to a particular Space.
    // Is there a better way to do this?
    case req:SpaceMessage => {
      Logger.info("SpaceMgr got " + req)
      // TODO: cope with messages in name style instead
      req match {
        case SpaceMessage(_, _, AsOID(spaceId)) => getSpace(spaceId).forward(req)
        case SpaceMessage(_, ownerId, AsName(spaceName)) => {
          val spaceOpt = getSpaceByName(ownerId, spaceName)
          // TODO: the error clause below potentially leaks information about whether a
          // give space exists for an owner. Examine the various security paths holistically.
          spaceOpt match {
            case Some(spaceId) => getSpace(spaceId).forward(req)
            case None => sender ! ThingFailed(UnknownPath, "Not a legal path")
          }
        }
      }
    }
  }
  
  // TODO: this should be cached!!!!
  private def getSpaceByName(ownerId:OID, name:String):Option[OID] = {
    DB.withTransaction(dbName(System)) { implicit conn =>
      val rowOption = SQL("""
          SELECT id from Spaces WHERE owner = {ownerId} AND name = {name}
          """).on("ownerId" -> ownerId.raw, "name" -> NameType.canonicalize(name))().headOption
      rowOption.map(row => OID(row.get[Long]("id").get))
    }
  }
  
  // Any checks we can make without needing to go to the DB should go here. Note that we
  // intentionally don't do any legality checking on the name yet -- since it is a display name,
  // we're pretty liberal about what's allowed.
  private def checkLegalSpaceCreation(owner:User, display:String):Try[Unit] = Try {
    if (!owner.canOwnSpaces)
      throw new PublicException("Space.create.pendingUser")
  }
  
  lazy val maxSpaces = Config.getInt("querki.public.maxSpaces", 5)
  
  private def createSpace(owner:OID, userMaxSpaces:Int, name:String, display:String):Try[OID] = Try {
    
    DB.withTransaction(dbName(System)) { implicit conn =>
      val numWithName = SQL("""
          SELECT COUNT(*) AS c from Spaces 
           WHERE owner = {owner} AND name = {name}
          """).on("owner" -> owner.raw, "name" -> name).apply().headOption.get.get[Long]("c").get
      if (numWithName > 0) {
        throw new PublicException("Space.create.alreadyExists", name)
      }
      
      if (userMaxSpaces < Int.MaxValue) {
        val sql = SQL("""
                SELECT COUNT(*) AS count FROM Spaces
                WHERE owner = {owner}
                """).on("owner" -> owner.id.raw)
        val row = sql().headOption
        val numOwned = row.map(_.long("count")).getOrElse(throw new InternalException("Didn't get any rows back in canCreateSpaces!"))
        if (numOwned >= maxSpaces)
          throw new PublicException("Space.create.maxSpaces", userMaxSpaces)
      }
    }
    
    val spaceId = OID.next(ShardKind.User)
    
    // NOTE: we have to do this as two separate Transactions, because part goes into the User DB and
    // part into System. That's unfortunate, but kind of a consequence of the architecture.
    DB.withTransaction(dbName(ShardKind.User)) { implicit conn =>
      SpacePersister.SpaceSQL(spaceId, """
          CREATE TABLE {tname} (
            id bigint NOT NULL,
            model bigint NOT NULL,
            kind int NOT NULL,
            props MEDIUMTEXT NOT NULL,
            PRIMARY KEY (id))
          """).executeUpdate()
      SpacePersister.AttachSQL(spaceId, """
          CREATE TABLE {tname} (
            id bigint NOT NULL,
            mime varchar(127) NOT NULL,
            size int NOT NULL,
            content mediumblob NOT NULL,
            PRIMARY KEY (id))
          """).executeUpdate()
      val initProps = Thing.toProps(Thing.setName(name), DisplayNameProp(display))()
      SpacePersister.createThingInSql(spaceId, spaceId, systemOID, Kind.Space, initProps, State)
    }
    DB.withTransaction(dbName(System)) { implicit conn =>
      SQL("""
          INSERT INTO Spaces
          (id, shard, name, display, owner, size) VALUES
          ({sid}, {shard}, {name}, {display}, {ownerId}, 0)
          """).on("sid" -> spaceId.raw, "shard" -> 1.toString, "name" -> name,
                  "display" -> display, "ownerId" -> owner.raw).executeUpdate()
    }
    spaceId
  }
}

object SpaceManager {
  // I don't love having to hold a static reference like this, but Play's statelessness
  // probably requires that. Should we instead be holding a Path, and looking it up
  // each time?
  lazy val ref = Akka.system.actorOf(Props[SpaceManager], name="SpaceManager")
  
  // This is probably over-broad -- we're going to need the timeout to push through to
  // the ultimate callers.
  implicit val timeout = Timeout(5 seconds)
  
  // Send a message to the SpaceManager, expecting a return of type A to be
  // passed into the callback. This wraps up the messy logic to go from a
  // non-actor-based Play environment to the SpaceManager. We'll likely
  // generalize it further eventually.
  //
  // Type A is the response we expect to get back from the message, which will
  // be sent to the given callback.
  //
  // Type B is the type of the callback. I'm a little surprised that this isn't
  // inferred -- I suspect I'm doing something wrong syntactically.
  def ask[A,B](msg:SpaceMgrMsg)(cb: A => B)(implicit m:Manifest[A]):Future[B] = {
    // Why isn't this compiling properly? We should be getting an implicit import of ?
//    (ref ? msg).mapTo[A].map(cb)
    akka.pattern.ask(ref, msg).mapTo[A].map(cb)
  }
}
