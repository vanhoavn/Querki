package models

import language.existentials

import scala.concurrent.Future

import querki.core.MOIDs._
import querki.ecology._
import querki.globals._
import querki.ql.Invocation
import querki.time.DateTime
import querki.types.Types

import querki.util.QLog
import querki.values._

/**
 * A Property is a field that may exist on a Thing. It is, itself, a Thing with a
 * specific Type.
 */
case class Property[VT, RT](
    i:OID, 
    s:OID, 
    m:OID, 
    val pType:PType[VT] with PTypeBuilder[VT, RT], 
    val cType:Collection, 
    pf:PropMap,
    mt:DateTime)(implicit val ecology:Ecology) 
  extends Thing(i, s, m, Kind.Property, pf, mt)
{
  def Core = ecology.api[querki.core.Core]
    
  def default(implicit state:SpaceState) = {
    // Note that we deliberately do this completely raw, to avoid Ecology dependency.
    // Fortunately, that's what we want anyway:
    val explicitDefault = props.get(querki.types.MOIDs.DefaultValuePropOID)
    explicitDefault.getOrElse(cType.default(pType))
  }
  def defaultPair(implicit state:SpaceState):PropAndVal[VT] = PropAndVal(this, default)
  def pair(v:QValue) = PropAndVal(this, v)

  lazy val rawProps = propMap
  override lazy val props:PropMap = rawProps + 
		  (CollectionPropOID -> Core.ExactlyOne(Core.LinkType(cType))) +
		  (TypePropOID -> Core.ExactlyOne(Core.LinkType(pType)))

  /**
   * This little method is a type-math workaround. We're often dealing with properties in contexts
   * where we know the PType of the property (and thus, the VT) for external reasons, but the
   * Scala compiler isn't smart enough to figure that out. So this provides a consistent way to
   * do the cast safely, at runtime. 
   * 
   * DEPRECATED: this is fundamentally inappropriate in the face of possible coercion, and nowadays
   * there are other ways to accomplish the same effect. In particular, instead of trying to force
   * the Type of the Property, do so with *values*, which is better-defined.
   */
  def confirmType[PVT](pt:PType[PVT]):Option[Property[PVT,_]] = {    
    if (pt == pType)
      Some(this.asInstanceOf[Property[PVT,_]])
    else
      None
  }
  
  def from(m:PropMap):QValue = m(this)
  def fromOpt(m:PropMap):Option[QValue] = m.get(this.id)
  
  /**
   * Convenience method to fetch the value of this property in this map.
   */
  def first(m:PropMap):VT = pType.get(cType.first(from(m)))
  def firstOpt(m:PropMap):Option[VT] = fromOpt(m) flatMap cType.firstOpt map pType.get

  def apply(raws:RT*) = (this.id, QValue.make(cType, pType, raws:_*))
  def apply(qv:QValue) = (this.id, qv)
  
  def validate(str:String, state:SpaceState) = pType.validate(str, this, state)
  
  def validatingQValue[R](v:QValue)(f: => R):R = {
    // NameProp is a conspicuous exception to this usual sanity-check:
    if ((v.cType != cType) && (id != querki.core.MOIDs.NameOID))
      QLog.error(s"Property $displayName Validation Failed: expected collection ${cType.displayName}, but got ${v.cType.displayName}")
    if (v.pType.realType != pType.realType)
      QLog.error(s"Property $displayName Validation Failed: expected type ${pType.displayName}, but got ${v.pType.displayName}")
      
    f
  }
  
  // TODO: this clearly isn't correct. How are we actually going to handle more complex types?
  def toUser(v:QValue)(implicit state:SpaceState):String = {
    if (cType.isEmpty(v))
      ""
    else
      pType.toUser(cType.first(v))
  }
  
  /**
   * This is a deliberately hardcoded and SpaceState-less lookup of whether this Property is NotInherited. It is
   * designed to be as fast as possible, because getPropOpt depends on it. (Which is why it doesn't use a SpaceState;
   * that way, it can be a lazy val.)
   * 
   * TBD: this explicitly assumes that we don't have Not Inherited Properties descending from each other. That's
   * a potentially questionable assumption in principle, but until we have mechanisms for Property inheritance, it'll do.
   */
  lazy val notInherited:Boolean = {
    if (props.contains(querki.core.MOIDs.NotInheritedOID)) {
      // EEEEVIL! Do not imitate this code, which makes all sorts of horrible assumptions that do not
      // generalize! This is designed to be fast, not correct.
      props(querki.core.MOIDs.NotInheritedOID).first.elem.asInstanceOf[Boolean]
    } else
      false
  }
  
  def serialize(v:QValue)(implicit state:SpaceState):String = validatingQValue(v){ v.serialize(pType) }
  def deserialize(str:String)(implicit state:SpaceState):QValue = cType.deserialize(str, pType)
  
  def applyToIncomingProps(inv:Invocation)(action:(PropertyBundle, QLContext) => QValue):QFut = {
    for {
      (bundle, elemContext) <- inv.bundlesAndContextsForProp(this)
    }
      yield action(bundle, elemContext)
  }
  
  override def thingOps(ecology:Ecology):ThingOps = new PropertyThingOps(this)(ecology)
}

class PropertyThingOps[VT,RT](prop:Property[VT,RT])(implicit e:Ecology) extends ThingOps(prop) {
  
  def pType = prop.pType

  /**
   * This renders the Property itself, if it has no DisplayText defined.
   */
  override def renderDefault(implicit request:RequestContext, state:SpaceState):Future[Wikitext] = {
    val fromType = pType.renderProperty(prop)
    fromType.getOrElse(renderProps)
  }
  
  /**
   * By default, qlApply on a Property expects the input context to be a single Link. It returns the value
   * of this Property on that Link.
   * 
   * TODO: if this Property isn't defined on the target Thing or its ancestors, this should return None.
   * So technically, this should be returning Optional. Note that PType.qlApply() already does this.
   */
  override def qlApply(inv:Invocation):QFut = {
    // Give the Type first dibs at handling the call; otherwise, return the value of this property
    // on the incoming thing.
    (pType.qlApplyFromProp(inv, prop).getOrElse(
      prop.applyToIncomingProps(inv) { (t, innerContext) =>
        t.getPropVal(prop)(innerContext.state)
      }))
  }  
}