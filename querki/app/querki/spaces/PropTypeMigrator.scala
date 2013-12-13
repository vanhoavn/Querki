package querki.spaces

import models.{Attachment, Property, PType, PTypeBuilder, Thing, ThingState}
import models.Thing.PropMap

import models.system.{CollectionProp, IsTextType, LinkType, TypeProp}

import querki.util._
import querki.values._

case class TypeChangeInfo(typeChanged:Boolean, newType:PType[Any] with PTypeBuilder[Any, Any], serializedValues:Map[Thing, String],
    prop:Property[_,_])
{
  def finish(newProp:Property[_,_], stateIn:SpaceState, updateState:SpaceState => Unit):Unit = {
    if (typeChanged) {
      // Now, run through all of the previously-serialized values, and rebuild them with the new Type:
      val newState = (stateIn /: serializedValues) { (state, oldPair) =>
        val (usingThing, serialized) = oldPair
        val usingThingId = usingThing.id
        val newV = newProp.deserialize(serialized)
        val usingProps = usingThing.props + (prop.id -> newV)
        usingThing match {
          case t:Attachment => state.copy(things = state.things + (usingThingId -> t.copy(pf = () => usingProps)))
          case t:ThingState => state.copy(things = state.things + (usingThingId -> t.copy(pf = () => usingProps)))
          case p:Property[_,_] => state.copy(spaceProps = state.spaceProps + (usingThingId -> p.copy(pf = () => usingProps)))
          case s:SpaceState => state.copy(pf = () => usingProps)
        }
      }
      
      updateState(newState)
    }    
  } 
}

/**
 * This utility deals with the important edge case of trying to change the Type of a
 * Property. This is a sufficient big and complex problem as to demand its own class.
 */
object PropTypeMigrator {
  
  private def isLegalTypeChange(oldType:PType[_], newType:PType[_]):Boolean = {
    oldType.isInstanceOf[IsTextType] && newType.isInstanceOf[IsTextType]
  }
  
  /**
   * This will throw a PublicException iff the change is *not* legal.
   */
  def checkLegalChange(state:SpaceState, oldThing:Thing, newProps:PropMap):Unit = {
    implicit val s = state
    oldThing match {
      case prop:Property[_,_] => {
        for (
          oldTypeVal <- oldThing.getPropOpt(TypeProp);
          oldTypeId <- oldTypeVal.firstOpt;
          newPropVal <- newProps.get(TypeProp);
          newTypeId <- newPropVal.firstTyped(LinkType);
          if (oldTypeId != newTypeId);
          oldType <- state.typ(oldTypeId.toThingId);
          newType <- state.typ(newTypeId.toThingId);
          if (!isLegalTypeChange(oldType, newType))
            )
          throw new PublicException("Space.modifyThing.illegalTypeChange", oldType.displayName, newType.displayName)
        
        for (
          oldCollVal <- oldThing.getPropOpt(CollectionProp);
          oldCollId <- oldCollVal.firstOpt;
          newCollVal <- newProps.get(CollectionProp);
          newCollId <- newCollVal.firstTyped(LinkType);
          if (oldCollId != newCollId)
            )
          throw new PublicException("Space.modifyThing.illegalCollectionChange")
      }
      case _ =>
    }
  }
  
  def prepChange(state:SpaceState, prop:Property[_,_], newProps:PropMap):TypeChangeInfo = {
    val propId = prop.id
    val newTypeOpt =
      for (
        newPropVal <- newProps.get(TypeProp);
        newTypeId <- newPropVal.firstTyped(LinkType);
        newType <- state.typ(newTypeId.toThingId)
          )
        yield newType
    // TBD: same ugly cast as we have in SpacePersister. Is there a way around this?
    val newType = newTypeOpt.getOrElse(prop.pType).asInstanceOf[PType[Any] with PTypeBuilder[Any, Any]]
    val changedTypes:Boolean = (newType.id != prop.pType.id)
        
    val serializedValues:Map[Thing, String] = {
      if (changedTypes) {
        // Save off all of the existing property values in serialized form:
        for (
          (usingThingId, usingThing) <- state.things;
          if (usingThing.props.contains(propId));
          oldPropAndVal <- usingThing.getPropOpt(propId)(state);
          oldVal = oldPropAndVal.v
            )
          yield (usingThing -> prop.serialize(oldVal))
      } else
        Map.empty
    }
    
    if (changedTypes)
      TypeChangeInfo(true, newType, serializedValues, prop)
    else
      TypeChangeInfo(false, prop.pType.asInstanceOf[PType[Any] with PTypeBuilder[Any, Any]], Map.empty, prop)
  }
}