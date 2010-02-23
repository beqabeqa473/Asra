package info.spielproject.spiel

import android.accessibilityservice._
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

import handlers.Handler
import scripting.Scripter

class SpielService extends AccessibilityService {

  override def onCreate {
    SpielService.instance = this
    TTS(this)
  }

  override def onDestroy = SpielService.instance = null

  override protected def onServiceConnected {
    val info = new AccessibilityServiceInfo()
    import AccessibilityServiceInfo._
    info.feedbackType = FEEDBACK_SPOKEN
    import AccessibilityEvent._
    info.eventTypes = TYPES_ALL_MASK
    setServiceInfo(info)
    Scripter()
    Handler.registered.foreach { h => h.init }
    Log.d(this.toString, "Spiel is configured.")
  }

  override def onInterrupt = TTS.stop

  override def onAccessibilityEvent(event:AccessibilityEvent) = Handler(event)

}

private object SpielService {
  var instance:SpielService = null

  def apply() = instance
}
