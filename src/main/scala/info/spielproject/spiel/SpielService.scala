package info.spielproject.spiel

import android.accessibilityservice._
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

import handlers.Handler
import scripting.Scripter
import tts.TTS

class SpielService extends AccessibilityService {

  // TODO: Why is this needed?
  private var connected = false

  override def onCreate {
    Log.d("spiel", "onCreate")
  }

  override def onDestroy {
    Log.d("spiel", "onDestroy")
  }

  override protected def onServiceConnected {
    // TODO: Why is this needed?
    if(connected) return
    Log.d("spiel", "onServiceConnected")
    val info = new AccessibilityServiceInfo()
    import AccessibilityServiceInfo._
    info.feedbackType = FEEDBACK_SPOKEN
    import AccessibilityEvent._
    info.eventTypes = TYPES_ALL_MASK
    setServiceInfo(info)
    TTS(this)
    Scripter(this)
    Handler(this)
    telephony.TelephonyListener(this)
    // TODO: Why is this needed?
    connected = true
    Log.d(this.toString, "Spiel is configured.")
  }

  override def onInterrupt = TTS.stop

  override def onAccessibilityEvent(event:AccessibilityEvent) = Handler.handle(event)

}
