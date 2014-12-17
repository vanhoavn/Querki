package querki.pages

import querki.globals._

import querki.data.SpaceInfo
import querki.search.SearchResultsPage

class PagesEcot(e:Ecology) extends ClientEcot(e) with Pages {
  
  def implements = Set(classOf[Pages])
  
  lazy val PageManager = interface[querki.display.PageManager]

  // Factories for some pages with no obvious homes:
  lazy val exploreFactory = registerStandardFactory("_explore", { (params) => new ExplorePage(params) })
  lazy val viewFactory = registerStandardFactory("_view", { (params) => new ViewPage(params) })
  lazy val createAndEditFactory = registerStandardFactory("_createAndEdit", { (params) => new CreateAndEditPage(params) })
  
  override def postInit() = {
    exploreFactory
    viewFactory
    createAndEditFactory
  }
  
  private var factories = Seq.empty[PageFactory]
  
  def registerFactory(factory:PageFactory):PageFactory = {
    factories :+= factory
    factory
  }
  
  def registerStandardFactory(registeredName:String, const:ParamMap => Page):PageFactory = {
    registerFactory(new PageFactory {
      def constructPageOpt(pageName:String, params:ParamMap):Option[Page] = {
        if (pageName == registeredName)
          Some(const(params))
        else
          None
      }
      
      def pageUrl(params:(String, String)*) = PageManager.pageUrl(registeredName, Map(params:_*))
    })    
  }
  
  // TODO: this doesn't yet work correctly to navigate cross-Spaces:
  def showSpacePage(space:SpaceInfo) = PageManager.showPage(s"${space.urlName}", Map.empty)
  
  var flash:Option[(Boolean, String)] = None
  def flashMessage(error:Boolean, msg:String) = flash = Some((error, msg))
  def getFlash:Option[(Boolean, String)] = {
    val ret = flash
    flash = None
    ret
  }
  
  /**
   * The big hardcoded factory for Pages.
   * 
   * This is a bit inelegant and coupled, but there is no great answer, and it doesn't
   * actually violate DRY.
   * 
   * TODO: a better way for this to work would be for each Page to have a factory, and
   * have its corresponding Ecot register that factory in postInit(). That probably implies
   * moving each Page to the relevant package, but that seems more and more correct anyway.
   */
  def constructPage(name:String, params:ParamMap):Page = {
    val pageOpt = (Option.empty[Page] /: factories) { (opt, factory) =>
      opt match {
        case Some(page) => opt
        case None => factory.constructPageOpt(name, params)
      }
    }
    
    // Fall back to ThingPage if nothing else claims ownership:
    pageOpt.getOrElse(new ThingPage(TID(name), params))
  }
  
}
