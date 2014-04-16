package querki.session

import akka.actor._
import akka.event.LoggingReceive

import models.OID

import querki.ecology._
import querki.session.messages._
import querki.spaces.messages.{SessionRequest, CurrentState}
import querki.util._
import querki.values.SpaceState

private [session] class UserSessions(val ecology:Ecology, val spaceId:OID)
  extends Actor with EcologyMember with RoutingParent[OID]
{
  var state:Option[SpaceState] = None
  
  def createChild(key:OID):ActorRef = {
    // Sessions need a special dispatcher so they can use Stash. (Seriously? Unfortunate leakage in the Akka API.)
    context.actorOf(UserSession.actorProps(ecology, spaceId).withDispatcher("session-dispatcher"), key.toString)
  }
  
  override def initChild(child:ActorRef) = state.map(child ! CurrentState(_))
  
  def receive = LoggingReceive {
    /**
     * The Space has sent an updated State, so tell everyone about it.
     */
    case msg @ CurrentState(s) => {
      state = Some(s)
      children.foreach(session => session.forward(msg))
    }
    
    case GetActiveSessions => sender ! ActiveSessions(children.size)

    /**
     * Message to forward to a UserSession. Create the session, if needed.
     */
    case SessionRequest(requester, _, _, payload) => routeToChild(requester.id, payload)
  }

}

object UserSessions {
  // TODO: the following Props signature is now deprecated, and should be replaced (in Akka 2.2)
  // with "Props(classOf(Space), ...)". See:
  //   http://doc.akka.io/docs/akka/2.2.3/scala/actors.html
  def actorProps(ecology:Ecology, spaceId:OID):Props = Props(new UserSessions(ecology, spaceId))
}