package info.spielproject.spiel

import android.accessibilityservice._
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nullwire.trace.ExceptionHandler

import handlers.Handler
import scripting.Scripter

class SpielService extends AccessibilityService {

  // TODO: Why is this needed?
  private var connected = false

  override def onCreate {
    ExceptionHandler.register(this, "http://stacktrace.spielproject.info/index.php")
  }

  override def onDestroy {
    Log.d("spiel", "onDestroy")
    Handler.onDestroy
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
    StateObserver(this)
    // TODO: Why is this needed?
    connected = true
    Log.d(this.toString, "Spiel is configured.")
  }

  override def onInterrupt = TTS.stop

  override def onAccessibilityEvent(event:AccessibilityEvent) {

    def clone = {
      val e = AccessibilityEvent.obtain
      e.setAddedCount(event.getAddedCount())
      e.setBeforeText(event.getBeforeText())
      e.setChecked(event.isChecked())
      e.setClassName(event.getClassName())
      e.setContentDescription(event.getContentDescription())
      e.setCurrentItemIndex(event.getCurrentItemIndex())
      e.setEventTime(event.getEventTime())
      e.setEventType(event.getEventType())
      e.setFromIndex(event.getFromIndex())
      e.setFullScreen(event.isFullScreen())
      e.setItemCount(event.getItemCount())
      e.setPackageName(event.getPackageName())
      e.setParcelableData(event.getParcelableData())
      e.setPassword(event.isPassword())
      e.setRemovedCount(event.getRemovedCount())
      e.getText().clear()
      e.getText().addAll(event.getText());
      e
    }

    Handler.handle(clone)
  }

}
