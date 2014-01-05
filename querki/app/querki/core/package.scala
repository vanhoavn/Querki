package querki

import models.Property

import models.system.QLText
import models.system.OIDs.sysId

import querki.ecology._

import modules.ModuleIds

/**
 * Querki's "core" objects
 * 
 * This is conceptually the center of the onion, at least in terms of static declarations of
 * actual Things. querki.core contains the Things that are *frequently* used elsewhere. As such,
 * it can be depended upon by all other Modules, and doesn't depend on any of them.
 */
package object core {
  object MOIDs extends ModuleIds(16) {
    val UrPropOID = sysId(5)
  }
  
  trait Core extends EcologyInterface {
    def UrProp:Property[QLText, String]
  }
}