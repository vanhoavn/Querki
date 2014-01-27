package querki.html

import scala.xml.XML

import querki.test._

class UITests extends QuerkiTests {
  // === _class ===
  "_class method" should {
    "throw an Exception if handed a Number" in {
      class TSpace extends CommonSpace {
        val intProp = new TestProperty(Core.IntType, ExactlyOne, "Int Prop")
        
        val withInt = new SimpleTestThing("Int Thing", intProp(42))
      }
      val space = new TSpace
      
      implicit val requester = commonSpace.owner
      
      processQText(thingAsContext[TSpace](space, _.withInt), """[[Int Prop -> _class(""myClass"")]]""") should 
        equal (expectedWarning("UI.transform.htmlRequired"))            
    }
    
    "throw an Exception if no param is given" in {
      implicit val requester = commonSpace.owner
      
      processQText(commonThingAsContext(_.instance), """[[My Optional Text._edit -> _class]]""") should 
        equal (expectedWarning("UI.transform.classRequired"))                  
    }
    
    "add classes to an _edit" in {
      implicit val requester = commonSpace.owner
      
      val html = processQText(commonThingAsContext(_.instance), """[[My Optional Text._edit -> _class(""myClass otherClass"")]]""")
      val xml = XML.loadString(html)
      val classesOpt = xml.attribute("class")
      assert(classesOpt.isDefined)
      val classes = classesOpt.get
      // Make sure it hasn't lost the original class from _edit:
      assert(classes.toString.contains("propEditor"))
      assert(classes.toString.contains("myClass"))
      assert(classes.toString.contains("otherClass"))
    }
    
    "add classes to a text" in {
      processQText(commonThingAsContext(_.instance), """[[""hello world"" -> _class(""myClass otherClass"")]]""") should
        equal ("""<span class="myClass otherClass">hello world</span>""")
    }
    
    "add classes to a bullet list" in {
      processQText(commonThingAsContext(_.instance), """[[""* hello
          |* world"" -> _class(""myClass otherClass"")]]""".stripMargin) should
        equal ("<ul class=\"myClass otherClass\">\n<li>hello</li>\n<li>world</li>\n</ul>")
    }
    
    // TODO: this is validating a bug, but at least it's a known bug. Note that, currently, we only return the *last*
    // paragraph of the input block:
    "add classes incorrectly to a multiparagraph text" in {
      processQText(commonThingAsContext(_.instance), """[[""hello
          |
          |world"" -> _class(""myClass otherClass"")]]""".stripMargin) should
        equal ("""<span class="myClass otherClass">world</span>""")
    }
  }
  
  // === _data ===
  "_data method" should {
    "add a simple data attribute to a span" in {
      processQText(commonThingAsContext(_.instance), """[[""hello world"" -> _data(""foo"",""I am some data"")]]""") should
        equal ("""<span data-foo="I am some data">hello world</span>""")            
    }
    
    "add a simple data attribute to a div" in {
      processQText(commonThingAsContext(_.instance), """[[""{{myClass:
          |hello world
          |}}"" -> _data(""foo"",""I am some data"")]]""".stripMargin) should
        equal ("""<div data-foo="I am some data" class="myClass">
            |<span>hello world</span></div>""".stripReturns)
    }
  }
  
  // === _linkButton ===
  "_linkButton" should {
    "work with a Link to Thing" in {
      processQText(commonThingAsContext(_.sandbox), """[[_linkButton(""hello"")]]""") should 
        equal ("""<a class="btn btn-primary" href="Sandbox">hello</a>""")
    }
    "work with an external URL" in {
      processQText(commonThingAsContext(_.withUrl), """[[My Optional URL -> _linkButton(""hello"")]]""") should
        equal ("""<a class="btn btn-primary" href="http://www.google.com/">hello</a>""")
    }
    "quietly ignore an empty context" in {
      processQText(commonThingAsContext(_.withoutUrl), """[[My Optional URL -> _linkButton(""hello"")]]""") should
        equal ("")      
    }
  }
  
  // === _showLink ===
  "_showLink" should {
    "work with a Link to Thing" in {
      processQText(commonThingAsContext(_.sandbox), """[[_showLink(""hello"")]]""") should 
        equal ("""[hello](Sandbox)""")
    }
    "work with an external URL" in {
      processQText(commonThingAsContext(_.withUrl), """[[My Optional URL -> _showLink(""hello"")]]""") should
        equal ("""[hello](http://www.google.com/)""")
    }
    "quietly ignore an empty context" in {
      processQText(commonThingAsContext(_.withoutUrl), """[[My Optional URL -> _showLink(""hello"")]]""") should
        equal ("")      
    }
    "work with a list of external URLs" in {
      // Note that a List result will have newlines in the QText, intentionally:
      processQText(commonThingAsContext(_.withUrl), """[[My List of URLs -> _showLink(""hello"")]]""") should
        equal ("\n[hello](http://www.google.com/)\n[hello](http://www.querki.net/)")      
    }
    "work with a list of Links" in {
      class testSpace extends CommonSpace {
        val withLinks = new SimpleTestThing("With Links", listLinksProp(sandbox.id, withUrlOID))
      }
      
      processQText(thingAsContext[testSpace](new testSpace, _.withLinks), """[[My List of Links -> _showLink(Name)]]""") should
        equal ("\n[Sandbox](Sandbox)\n[With URL](With-URL)")
    }
  }
  
  // === _tooltip ===
  "_tooltip method" should {
    "add a tooltip to a text block" in {
      processQText(commonThingAsContext(_.instance), """[[""hello world"" -> _tooltip(""I am a tooltip"")]]""") should
        equal ("""<span title="I am a tooltip" class="_withTooltip">hello world</span>""")      
    }
  }
}