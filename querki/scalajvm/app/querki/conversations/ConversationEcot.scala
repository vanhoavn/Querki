package querki.conversations

import akka.actor.{ActorRef, Props}

import querki.ecology._
import querki.globals._
import querki.identity.User
import querki.spaces.SpacePersistenceFactory
import querki.values.SpaceState

import PersistentEvents._

object MOIDs extends EcotIds(35) {
  val CommentTextOID = moid(1)
  val CanCommentPermOID = moid(2)
  val CanReadCommentsPermOID = moid(3)
}
import MOIDs._

class ConversationEcot(e:Ecology) extends QuerkiEcot(e) with Conversations {
    
  val AccessControl = initRequires[querki.security.AccessControl]
  lazy val ApiRegistry = interface[querki.api.ApiRegistry]
  lazy val SpaceOps = interface[querki.spaces.SpaceOps]
  
  override def postInit() = {
    // Some entry points are legal without login:
    ApiRegistry.registerApiImplFor[ConversationFunctions, ConversationFunctionsImpl](SpaceOps.spaceRegion, false)
  }
  
  override def persistentMessages = persist(35,
    (classOf[DHComment] -> 100),
    (classOf[DHNode] -> 101),
    (classOf[DHConvs] -> 102),
    (classOf[DHAddComment] -> 103),
    (classOf[DHDeleteComment] -> 104)
  )
  
  lazy val traceConv = Config.getBoolean("querki.test.traceConversations", false)
  def convTrace(msg: => String):Unit = {
    if (traceConv)
      QLog.spew(msg)
  }
  
  // TODO: the following Props signature is now deprecated, and should be replaced (in Akka 2.2)
  // with "Props(classOf(Space), ...)". See:
  //   http://doc.akka.io/docs/akka/2.2.3/scala/actors.html
  def conversationActorProps(persistenceFactory:SpacePersistenceFactory, spaceId:OID, space:ActorRef):Props = 
    Props(new SpaceConversationsActor(ecology, persistenceFactory, spaceId, space))
    
  def conversationsManagerProps(router:ActorRef):Props = Props(classOf[SpaceConversationsManager], ecology, router)
  
  // TODO: the following Props signature is now deprecated, and should be replaced (in Akka 2.2)
  // with "Props(classOf(Space), ...)". See:
  //   http://doc.akka.io/docs/akka/2.2.3/scala/actors.html
  def conversationPersisterProps(spaceId:OID):Props = 
    Props(new ConversationPersister(spaceId, ecology))
      
  def canReadComments(req:User, thingId:OID, state:SpaceState) = {
    AccessControl.hasPermission(CanReadComments, state, req, thingId)
  }
  
  def canWriteComments(identity:OID, thingId:OID, state:SpaceState) = {
    AccessControl.hasPermission(CanComment, state, identity, thingId)
  }
  
  /***********************************************
   * PROPERTIES
   ***********************************************/
  
  /**
   * TODO: this shouldn't really be PlainText -- it should be a Type that is explicitly Wikitext for now.
   * Model this on QL.ParsedTextType, but make it realer.
   */
  lazy val CommentText = new SystemProperty(CommentTextOID, TextType, Optional,
      toProps(
        setName("Comment Text"),
        setInternal))
  
  lazy val CanComment = AccessControl.definePermission(CanCommentPermOID, "Who Can Comment", "Who can comment on this Thing (or generally in this Space)",
      Seq(AccessControl.OwnerTag, AccessControl.MembersTag),
      Seq(AccessControl.AppliesToSpace, AccessControl.AppliesToModels, AccessControl.AppliesToInstances),
      true, false)
      
  lazy val CanReadComments = AccessControl.definePermission(CanReadCommentsPermOID, "Who Can Read Comments", "Who can read the comments on this Thing?",
      Seq(AccessControl.OwnerTag, AccessControl.PublicTag), 
      Seq(AccessControl.AppliesToSpace, AccessControl.AppliesToModels, AccessControl.AppliesToInstances),
      true, true)
  
  override lazy val props = Seq(
    CommentText,
    CanComment,
    CanReadComments
  )
}