package info.thewordnerd.spiel.services

import android.accessibilityservice._
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

import info.thewordnerd.spiel.presenters._
import info.thewordnerd.spiel.scripting.Scripter
import info.thewordnerd.spiel.utils.TTS

class Spiel extends AccessibilityService {

  override def onCreate {
    TTS(this)
  }

  override def onDestroy {
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
      case TYPE_VIEW_CLICKED => ViewClicked(event)
      case TYPE_VIEW_FOCUSED => ViewFocused(event)
      case TYPE_VIEW_SELECTED => ViewSelected(event)
      case TYPE_VIEW_TEXT_CHANGED => ViewTextChanged(event)
      case TYPE_WINDOW_STATE_CHANGED => WindowStateChanged(event)
      case _ => Log.d(this.toString, "Unhandled event")
    }
  }

}
