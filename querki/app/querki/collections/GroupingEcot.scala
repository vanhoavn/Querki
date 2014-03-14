package querki.collections

import models.ThingState

import querki.ecology._
import querki.types.{ModelTypeDefiner, SimplePropertyBundle}

object GroupingMOIDs extends EcotIds(36) {
  val GroupModelOID = moid(1)
  val GroupKeyPropOID = moid(2)
  val GroupMembersPropOID = moid(3)
  val GroupTypeOID = moid(4)
  val GroupByFunctionOID = moid(5)  
}
import GroupingMOIDs._

/**
 * This rather specialized Ecot defines the _groupBy Function, and all the attendant bits. It was
 * originally in the CollectionsModule, but turned out to be big enough to be worth an Ecot to itself.
 * Some of its ideas might yet wind up getting refactored or exposed elsewhere, but for now it is
 * nicely self-contained.
 */
class GroupingEcot(e:Ecology) extends QuerkiEcot(e) with querki.core.MethodDefs with ModelTypeDefiner {
  
  val Types = initRequires[querki.types.Types]
  
  /***********************************************
   * PROPERTIES
   ***********************************************/
  
  lazy val groupKeyProperty = new SystemProperty(GroupKeyPropOID, Types.WrappedValueType, Optional,
      toProps(
        setName("_groupKey"),
        setInternal,
        Summary("The key of a single grouping that comes from _groupBy")))
  
  lazy val groupMembersProperty = new SystemProperty(GroupMembersPropOID, Core.LinkType, QList,
      toProps(
        setName("_groupMembers"),
        setInternal,
        Summary("The Things in a single grouping from _groupBy")))
  
  /***********************************************
   * TYPES
   ***********************************************/
  
  lazy val groupModel = ThingState(GroupModelOID, systemOID, RootOID,
    toProps(
      setName("_groupModel"),
      setInternal,
      Summary("The Model of the values you get from _groupBy"),
      groupKeyProperty(Core.QNone),
      groupMembersProperty()))
      
  override lazy val things = Seq(
    groupModel
  )
  
  lazy val groupType = new ModelType(GroupTypeOID, GroupModelOID,
    toProps(
      setName("_groupType"),
      setInternal,
      Summary("The Type of the values you get from _groupBy")))
  
  override lazy val types = Seq(
    groupType
  )
  
  /***********************************************
   * FUNCTIONS
   ***********************************************/
  
  lazy val groupByFunction = new InternalMethod(GroupByFunctionOID,
      toProps(
        setName("_groupBy"),
        Summary("Groups the received Things by the specified Property or Expression"),
        Details("""    LIST OF THING -> _groupBy(PROP or EXPR) -> GROUPS
            |This Function takes a list of Things, and collects them into groups based on the given Expression.
            |The Expression may in principle be anything, but is most often a Property on the Things.
            |
            |The result of this is a List of _groupModel values. _groupModel has two Properties:
            |* _groupKey -- the key that identifies everything in this group
            |* _groupMembers -- the list of Things in this group
            |
            |For example, say that My Model has a Property named Score, which is a number from 1-5.
            |I can separate out all of the Instances of My Model based on Score, and print each group,
            |by saying:
            |    \[[My Model._instances -> _groupBy(Score) -> 
            |        ""**Score:** [[_groupKey]]   **Members:** [[_groupMembers -> _sort -> _commas]]""\]]
            |
            |This Function is still pretty delicate. If the parameter doesn't evaluate properly on
            |all of the Things, you will likely get an error.""".stripMargin)))
  {
    override def qlApply(inv:Invocation):QValue = {
      val keyThingsWrapped = for {
        // TODO: this should really be contextAllBundles, but we need to figure out what Type
        // groupMembersProperty should be. I don't think we yet have a Type that represents a
        // Bundle.
        elemContext <- inv.contextElements
        thing <- inv.opt(elemContext.value.firstAs(LinkType))
        key <- inv.processParam(0, elemContext)
      }
        yield (key, thing)
        
      val keyThingPairs = keyThingsWrapped.get.toSeq
      
      def sortFunc(left:(QValue, OID), right:(QValue, OID)):Boolean = {
        val pt = left._1.pType
        val sortResult =
	      for {
	        leftVal <- left._1.firstOpt
	        rightVal <- right._1.firstOpt
	      }
            yield pt.comp(inv.context)(leftVal, rightVal)

        sortResult.getOrElse(false)
	  }
      
      val sortedPairs = keyThingPairs.sortWith(sortFunc)
      
      val rawGroupings = (Seq.empty[(QValue, Seq[OID])] /: sortedPairs) { (seqs, pair) =>
        val (key, id) = pair
        if (seqs.isEmpty)
          Seq((key, Seq(id)))
        else {
          val current = seqs.last
          val (curKey, curIds) = current
          if (curKey.pType.matches(curKey.first, key.first))
            seqs.dropRight(1) :+ (curKey, curIds :+ id)
          else
            seqs :+ (key, Seq(id))
        }
      }
      
      val groupings = rawGroupings.map { raw =>
        val (key, ids) = raw
        groupType(SimplePropertyBundle(
            groupKeyProperty(key),
            groupMembersProperty(ids:_*)))
      }
      
      QList.makePropValue(groupings, groupType)
    }
  }

  override lazy val props = Seq(
    groupKeyProperty,
    groupMembersProperty,
    groupByFunction
  )

}