package querki.pages

import org.scalajs.dom
import org.scalajs.jquery._
import scalatags.JsDom.all._
import scalatags.JsDom.tags2.section
import autowire._
import rx._

import querki.globals._

import querki.api._
import querki.data.ThingInfo
import querki.display.{Gadget, RawDiv}
import querki.display.input.{InputGadget, LargeTextInputGadget}
import querki.display.rx.RxTextFrag

class SharingPage(implicit e:Ecology) extends Page(e) with EcologyMember {
  
  lazy val Client = interface[querki.client.Client]
  lazy val Editing = interface[querki.editing.Editing]
  
  lazy val space = DataAccess.space.get
  
  case class RoleInfo(map:Map[TID, ThingInfo], roles:Seq[ThingInfo]) {
    def default = roles.head
  }
  def makeRoleMap(roles:Seq[ThingInfo]) = RoleInfo(Map(roles.map(role => (role.oid -> role)):_*), roles)
  
    
  class RoleDisplay(initialRole:ThingInfo, tid:TID, roleInfo:RoleInfo, std:StandardThings) extends InputGadget[dom.HTMLSpanElement](ecology) {
    val role = Var(initialRole)
    val roleName = Rx(role().displayName)
      
    override lazy val thingId = tid
    override def path = Editing.propPath(std.security.personRolesProp.oid, Some(thingId))
    def values = List(role().oid.underlying)
      
    def roleChosen(roleId:String) = {
      role() = roleInfo.map(TID(roleId))
      $(selector).detachReplaceWith(elem)
      save()
    }
      
    def hook() = {
      $(elem).click({ evt:JQueryEventObject =>
        $(elem).detachReplaceWith(selector)
      })
    }
	        
    def doRender() =
      span(cls:="_chooseRole label label-info",
        data("personid"):=thingId.underlying,
        new RxTextFrag(roleName)
      )
        
    lazy val selector = (new RoleSelector).render
        
    class RoleSelector extends Gadget[dom.HTMLSelectElement] {
      override def onCreate(e:dom.HTMLSelectElement) = {
        $(elem).value(role().oid.underlying)
          
        $(elem).change({ evt:JQueryEventObject =>
          val chosen = $(elem).find(":selected").valueString
          roleChosen(chosen)
        })
      }
        
      def doRender() =
        select(
          for (r <- roleInfo.roles)
            yield option(value:=r.oid.underlying,
              r.displayName)
        )
    }
  }
  
  
  class PersonDisplay(showCls:String, person:PersonInfo, roleInfo:RoleInfo, std:StandardThings) extends Gadget[dom.HTMLTableRowElement] {
    val initPersonRole = person.roles.headOption.map(roleInfo.map(_)).getOrElse(roleInfo.default)
    
    def doRender() =
      tr(cls:=showCls,
	    td({
	      MSeq(
	        person.person.displayName, 
	        " -- ",
	        new RoleDisplay(initPersonRole, person.person.oid, roleInfo, std)
	      )
	    })
	  )
  }
  
  def pageContent = for {
    std <- DataAccess.standardThings
    securityInfo <- Client[SecurityFunctions].getSecurityInfo().call()
    roles <- Client[SecurityFunctions].getRoles().call()
    inviteEditInfo <- Client[EditFunctions].getOnePropertyEditor(DataAccess.space.get.oid, std.security.inviteTextProp).call()
    roleMap = makeRoleMap(roles)
    (members, invitees) <- Client[SecurityFunctions].getMembers().call()
    guts =
      div(
        section(id:="sendInvitations",
          h2("Send Invitations to Join this Space"),
          
          p("""Use this form to invite people to become Members of this Space. The Invitation Message may contain the usual Querki Text formatting,
          |including [[]]-style expressions; however, links may not yet work quite the way you expect.""".stripMargin),
          
          p("""Specify invitees by email address. Note that your current invitations are listed below. While it is acceptable to retry once or
          |twice, doing so excessively is frowned upon, as is sending unwelcome invitations. Either of these is considered spam, and is
          |grounds for removal from Querki.""".stripMargin),
          
          p(s"""Invitations will come from "${securityInfo.fromEmail}". If your invitations are getting spam-filtered, tell your invitees
          |to add that address to their Contacts.""".stripMargin),
          
          p("""Invitations will have your email address included as the Reply-To, so if the invitees write back, it will go directly to you.
          |(Eventually, we may have replies come to you via Querki, but for now, keep in mind that your invitees will see your email address.)""".stripMargin),
          
          new RawDiv(inviteEditInfo.editor),
        
          div(cls:="control-group",
            div(cls:="controls",
              "These people should be invited as ",
              new RoleDisplay(roleMap.map(securityInfo.defaultRole), DataAccess.space.get.oid, roleMap, std)
            )
          )
        
        ),
        
        section(id:="invitees",
          h2("Outstanding Invitations"),
          p("The following invitations have been sent but not yet accepted."),
          
          table(cls:="table table-hover",
            tbody(
              for (member <- invitees) 
                yield new PersonDisplay("warning", member, roleMap, std)
            )
          )          
        ),
        
        section(id:="members",
          h2("Members"),
          p("The following people are members of this Space. Click on a member's Role in order to change it."),
          
          table(cls:="table table-hover",
            tbody(
              for (member <- members) 
                yield new PersonDisplay("info", member, roleMap, std)
            )
          )
        )
      )
  }
    yield PageContents(s"Manage Sharing for ${space.displayName}", guts)
}
