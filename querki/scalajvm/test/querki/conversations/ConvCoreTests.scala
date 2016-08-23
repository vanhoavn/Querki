package querki.conversations

import querki.conversations.messages._
import querki.globals._
import querki.identity.User
import querki.persistence._
import querki.spaces._
import querki.spaces.messages._
import querki.test._
import querki.types.ModelTypeDefiner
import querki.values.SpaceState

class TestConvCore(initState:SpaceState, thingId:OID, val initHistory:List[HistoryRecord] = List.empty)(implicit e:Ecology) 
  extends ThingConversationsCore(initState, thingId) with ModelTypeDefiner with PersistentCoreTestBase 
{
}

/**
 * This pretends to be the SpaceConversations router; it plugs into SpaceCoreSpaceBase.
 */
trait TestConversations extends EcologyMember { self:TestSpace =>
  def state:SpaceState
  def !(msg:AnyRef):Option[AnyRef]  
  
  var thingConvs:Map[OID, TestConvCore] = Map.empty
  
  /**
   * Shorthand for ordinary ConversationRequests, coming from the Space's owner.
   */
  def convReq(msg:ConversationMessage) = this ! ConversationRequest(owner, state.id, msg)
  
  /**
   * Passes a ConversationRequest on to the appropriate TestConvCore. Returns the response to that
   * message, if there is one. This is generally invoked indirectly, by calling ! on SpaceCoreSpaceBase.
   */
  def routeToConv(rawMsg:AnyRef):Option[AnyRef] = {
    rawMsg match {
      case msg @ CurrentState(current) => {
        thingConvs.values.foreach { _ ! msg }
        None
      }
      
      case msg:ConversationRequest => {
        val thingId = msg.payload match {
          case GetConversations(thingId) => thingId
          case NewComment(comment) => comment.thingId
          case DeleteComment(thingId, _) => thingId
        }
        
        val conv = thingConvs.get(thingId) match {
          case Some(c) => c
          case _ => {
            val c = new TestConvCore(state, thingId)
            thingConvs = thingConvs + (thingId -> c)
            c
          }
        }
        
        conv ! msg
      }
    }
  }
}

class SimpleCoreSpace(implicit e:Ecology) extends SpaceCoreSpace()(e) with ModelTypeDefiner {
  // Boot the Space up
  this ! InitialState(owner, sc.id, "Test Space", owner.mainIdentity.id)
}

/**
 * Tests for ThingConversationsCore.
 */
class ConvCoreTests extends QuerkiTests {
  
  lazy val Conversations = interface[Conversations]
  
  lazy val CommentText = Conversations.CommentText
  
  /**
   * Provide a simple structural matcher. Errors aren't as nice as ScalaTest's, but this helps trim boilerplate.
   */
  implicit class ShouldMatchExt[T](test:T) {
    def shouldMatch(pf:PartialFunction[T,Unit]):Unit = {
      if (!(pf isDefinedAt test))
        fail(s"$test does not match expected pattern $pf")
    }
  }
  
  def addCommentOn(
    thingId:OID, 
    text:String, 
    primary:Boolean = false, 
    responseTo:Option[CommentId] = None,
    expectedParentIdOpt:Option[CommentId] = None)(implicit s:SpaceCoreSpaceBase):ConversationNode = 
  {
    val comment = Comment(
      s.state.id,
      0,
      thingId,
      s.owner.mainIdentity.id,
      None,
      Map(CommentText(text)),
      responseTo,
      primary
    )
    
    s.convReq(NewComment(comment)) match {
      case Some(AddedNode(parentIdOpt, node)) => {
        // Check whether we got the expected parent:
        responseTo match {
          case Some(exp) => {
            // This is a bit subtle: in rare circumstances (when there has been a primary-response race condition),
            // we expect the server to reparent the comment, so it *won't* be responseTo:
            val expectedParentId = expectedParentIdOpt.getOrElse(exp)
            assert(parentIdOpt.isDefined && (parentIdOpt.get == expectedParentId))
          }
          case _ => // There wasn't a parent specified
        }
        node
      }
      case other => fail(s"Attempt to add comment $comment resulted in $other")
    }
  }
  
  implicit class EnhancedConversationNode(node:ConversationNode) {
    lazy val comment = node.comment
    
    def reply(text:String, primary:Boolean, expectedParentOpt:Option[ConversationNode] = None)(implicit s:SpaceCoreSpaceBase):ConversationNode = {
      addCommentOn(comment.thingId, text, primary, Some(comment.id), expectedParentOpt.map(_.comment.id))
    }
    
    def getText:String = {
      CommentText.first(comment.props).text
    }
    def assertText(text:String) = assert(getText == text)
    
    def isPrimary:Boolean = comment.primaryResponse
  }

  
  "A Thing's Conversations" should {
    "initially be empty" in {
      implicit val s = new SimpleCoreSpace
      
      val fooId = s.addSimpleThing("Foo")
      s.convReq(GetConversations(fooId)) shouldMatch { case Some(ThingConversations(convs)) if (convs.isEmpty) => }
    }
    
    "be able to handle normal interactions" in {
      implicit val s = new SimpleCoreSpace
      
      val fooId = s.addSimpleThing("Foo")
      val comment1 = addCommentOn(fooId, "This is the first comment")
      val comment1_1 = comment1.reply("This is the first reply", true)
      val comment1_2 = comment1.reply("This is the second reply", false)
      
      val comment2 = addCommentOn(fooId, "Second comment")
      val comment2_1 = comment2.reply("First reply", true)
      val comment2_1_1 = comment2_1.reply("First sub-reply", true)
      // Note that we expect this one to get reparented down the chain, since we have a primary-comment race condition:
      val comment2_2 = comment2.reply("Second attempt at a primary", true, Some(comment2_1_1))
      
      val convs = s.convReq(GetConversations(fooId))
      convs match {
        case Some(ThingConversations(convs)) if (convs.size == 2) => {
          convs(0).responses match {
            case Seq(reply1, reply2) => {
              assert(reply1.isPrimary)
              reply1.assertText("This is the first reply")
              
              assert(!reply2.isPrimary)
              reply2.assertText("This is the second reply")
            }
          }
          
          convs(1).responses match {
            case Seq(reply1) => {
              reply1.responses match {
                case Seq(reply1_1) => {
                  reply1_1.assertText("First sub-reply")
                  
                  // The original comment2_2 has been reparented down to here:
                  reply1_1.responses match {
                    case Seq(reply1_1_1) => {
                      assert(reply1_1_1.isPrimary)
                      reply1_1_1.assertText("Second attempt at a primary")
                    }
                  }
                }
                case other => fail(s"reply1's responses were $other")
              }
            }
          }
        }
        case _ => fail(s"Conversation structure doesn't match expected: $convs")
      }
    }
  }
}
