package querki.spaces

import org.joda.time.DateTime

import models.{OID}
import models.Thing.PropMap

import querki.values.SpaceState

/**
 * This object exists mainly for purposes of access and import control. The contained
 * case classes are what matter.
 */
private [spaces] object PersistMessages {
  /**
   * Request from the Space to the Persister, send when the Space is booted. Persister should
   * respond with Loaded.
   */
  case object Load
  
  /**
   * Response sent when the Persister successfully has loaded the Space.
   */
  case class Loaded(state:SpaceState)

  /**
   * Command to delete the Thing with the specified OID. Fire-and-forget, with no response.
   */
  case class Delete(thingId:OID)
  
  case class SpaceChange(newName:String, newDisplay:String)
  /**
   * Command to alter the specified Thing. spaceChange should be given iff the Thing is the Space
   * itself. Should response with Changed().
   */
  case class Change(state:SpaceState, thingId:OID, modelId:OID, props:PropMap, spaceChange:Option[SpaceChange])
  
  /**
   * Response from a Change() or Create().
   */
  case class Changed(timestamp:DateTime)
  
  /**
   * The general error response when things go wrong. This probably needs to become more
   * complex as we go along.
   */
  case object PersistError
}