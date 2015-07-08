package querki

import scala.concurrent.Future
import scala.reflect.ClassTag

import akka.actor.ActorRef

import querki.ecology._
import querki.identity.{Identity, PublicIdentity, User}

package object session {
  
  /**
   * A basic cache that AutowireApiImpls can use if they need it.
   */
  type ImplCache = Map[String, Any]

  /**
   * Provides access to the UserSessions.
   */
  trait Session extends EcologyInterface {
    /**
     * The root of the UserSession hierarchy. Other Actors may use this directly to send messages to UserSessions.
     */
    def sessionManager:ActorRef
    
    /**
     * Asynchronously gets the current info to show this user.
     */
    def getSessionInfo(user:User):Future[UserSessionInfo]
    
    /**
     * Asynchronously fetch all of this Identity's Collaborators -- people they share Spaces with --  who fit the given search term.
     */
    def getCollaborators(user:User, identity:Identity, term:String):Future[UserSessionMessages.Collaborators]
  }
  
  /**
   * Post-init-time interface for registering handlers for Session APIs.
   */
  trait SessionHandlerRegistry extends EcologyInterface {
    /**
     * Registers the given IMPL as the implementation for the specified API. Should be called during postInit()!
     */
    def registerUserSessionImplFor[API, IMPL <: API with AutowireApiImpl](implicit apiTag:ClassTag[API], implTag:ClassTag[IMPL])
  }
  
}