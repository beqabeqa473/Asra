package info.spielproject.spiel

import android.accessibilityservice._
import android.app.{Notification, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.os.Debug
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nullwire.trace.ExceptionHandler

import handlers.Handler
import scripting._
import triggers.Triggers

/**
 * <code>AccessibilityService</code> implementation that serves as main entry point.
*/

class SpielService extends AccessibilityService {

  private var notificationManager:NotificationManager = null

  private var notification:Notification = null

  override def onCreate() {
    super.onCreate()
    Log.d("spiel", "onCreate")
    //Debug.startMethodTracing("spiel")
    Preferences(this)
    if(Preferences.sendBacktraces)
      ExceptionHandler.register(this, "http://stacktrace.spielproject.info/")
    try {
      TTS(this)
    } catch {
      case e:VerifyError => // We've almost certainly handled this, so ignore.
    }
    Handler(this)
    Scripter(this)
    BazaarProvider(this)
    StateObserver(this)
    StateReactor(this)
    Triggers(this)
    TelephonyListener(this)
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    notification = new Notification(R.drawable.empty, getString(R.string.appName), 0)
    notification.setLatestEventInfo(this, getString(R.string.appName), null, PendingIntent.getActivity(this, 0, new Intent(this, classOf[activities.Spiel]), 0))
    startForeground(1, notification)
    SpielService.initialized = true
    SpielService.enabled = true
  }

  override def onDestroy() {
    super.onDestroy
    Log.d("spiel", "onDestroy")
    Handler.onDestroy
    TTS.shutdown
    Scripter.onDestroy
    //Debug.stopMethodTracing
    notificationManager.cancelAll
    SpielService.initialized = false
    SpielService.enabled = false
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

    if(!SpielService.enabled) return

    // Clone an AccessibilityEvent since the system recycles them 
    // aggressively, sometimes before we're done with them.
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

  var initialized = false

  var enabled = false

}
