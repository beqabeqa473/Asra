package info.spielproject.spiel

import android.accessibilityservice._
import android.content.Intent
import android.os.Debug
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nullwire.trace.ExceptionHandler

import handlers.Handler
import scripting.Scripter

class SpielService extends AccessibilityService {

  override def onCreate() {
    super.onCreate()
    Log.d("spiel", "onCreate")
    //Debug.startMethodTracing("spiel")
    ExceptionHandler.register(this, "http://stacktrace.spielproject.info/index.php")
    Preferences(this)
    TTS(this)
    Handler(this)
    Scripter(this)
    StateObserver(this)
    StateReactor(this)
    telephony.TelephonyListener(this)
    SpielService.initialized = true
  }

  override def onDestroy() {
    super.onDestroy
    Log.d("spiel", "onDestroy")
    Handler.onDestroy
    TTS.shutdown
    Scripter.onDestroy
    //Debug.stopMethodTracing
    SpielService.initialized = false
  }

  override protected def onServiceConnected {
    Log.d("spiel", "onServiceConnected")
    val info = new AccessibilityServiceInfo()
    import AccessibilityServiceInfo._
    info.feedbackType = FEEDBACK_SPOKEN
    import AccessibilityEvent._
    info.eventTypes = TYPES_ALL_MASK
    setServiceInfo(info)
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

object SpielService {
  private[spiel] var initialized = false
}
