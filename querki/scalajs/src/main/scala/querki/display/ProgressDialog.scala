package querki.display

import scala.scalajs.js.timers._
import org.scalajs.dom

import scalatags.JsDom.all._
import autowire._

import querki.api._
import querki.display.rx.GadgetRef
import querki.globals._

/**
 * @author jducoeur
 */
class ProgressDialogEcot(e:Ecology) extends ClientEcot(e) with ProgressDialog {
  
  def implements = Set(classOf[ProgressDialog])
  
  lazy val Client = interface[querki.client.Client]
  
  def showDialog(processName:String, operation:OperationHandle, onSuccess: => Unit, onFailure: => Unit) = {
    val progressBar = GadgetRef.of[dom.html.Div]
    val progressMsg = GadgetRef.of[dom.html.Paragraph]
    
    val dialog:Dialog = 
      new Dialog(s"Progress on $processName", 200, 350,
        div(
          div(cls:="progress",
            progressBar <= div(cls:="progress-bar progress-bar-striped active", role:="progressbar", 
              aria.valuenow:="0", aria.valuemin:="0", aria.valuemax:="100", style:="width: 0%"
            )
          ),
          progressMsg <= p("")
        )
      )
    
    dialog.show()
    
    // Once a second, ping the uploader and see how it's coming:
    var progressTimer:SetIntervalHandle = null 
    progressTimer = setInterval(1000) {
      Client[CommonFunctions].getProgress(operation).call() foreach { progress =>
        if (progress.complete) {
          // We're done -- fire-and-forget call to the server to ack the completion:
          Client[CommonFunctions].acknowledgeComplete(operation).call()
          clearInterval(progressTimer)
          
          if (progress.failed) {
            onFailure
          } else {
            onSuccess
          }
        } else {
          // Normal progress:
          progressMsg.jq.text(progress.msg)
          progressBar.jq.width(s"${progress.percent}%")
          progressBar.jq.text(s"${progress.percent}%")            
        }
      }
    }
  }
}