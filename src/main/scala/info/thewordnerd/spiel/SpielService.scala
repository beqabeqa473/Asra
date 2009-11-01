package info.thewordnerd.spiel

import android.accessibilityservice._
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

import presenters._
import scripting.Scripter

class SpielService extends AccessibilityService {

  override def onCreate {
    SpielService.instance = this
    TTS(this)
  }

  override def onDestroy {
    SpielService.instance = null
  }

  override protected def onServiceConnected {
    val info = new AccessibilityServiceInfo()
    import AccessibilityServiceInfo._
    info.feedbackType = FEEDBACK_SPOKEN
    import AccessibilityEvent._
    info.eventTypes = TYPES_ALL_MASK
    setServiceInfo(info)
    Scripter()
    Log.d(this.toString, "Spiel is configured.")
  }

  override def onInterrupt {
  }

  override def onAccessibilityEvent(event:AccessibilityEvent) = {
    import AccessibilityEvent._
    Log.d(this.toString, "Got AccessibilityEvent: "+event.toString)
    event.getEventType match {
      case TYPE_NOTIFICATION_STATE_CHANGED => NotificationStateChanged(event)
      case TYPE_VIEW_CLICKED => ViewClicked(event)
      case TYPE_VIEW_FOCUSED => ViewFocused(event)
      case TYPE_VIEW_SELECTED => ViewSelected(event)
      case TYPE_VIEW_TEXT_CHANGED => ViewTextChanged(event)
      case TYPE_WINDOW_STATE_CHANGED => WindowStateChanged(event)
      case _ => Log.d(this.toString, "Unhandled event")
    }
  }

}

private object SpielService {
  var instance:SpielService = null

  def apply() = instance
}
