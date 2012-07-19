package info.spielproject.spiel

import android.accessibilityservice._
import android.app.{Notification, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.os.Debug
import android.os.Build.VERSION
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.support.v4.app.NotificationCompat
import com.nullwire.trace.ExceptionHandler

import presenters.Presenter
import scripting._
import triggers.Triggers

/**
 * <code>AccessibilityService</code> implementation that serves as main entry point.
*/

class SpielService extends AccessibilityService {


  override def onCreate() {
    super.onCreate()
    SpielService._context = this
    Preferences(this)
    if(Preferences.profiling)
      Debug.startMethodTracing("spiel")
    if(Preferences.sendBacktraces)
      ExceptionHandler.register(this, "http://stacktrace.thewordnerd.info/stacktrace")
    try {
      TTS(this)
    } catch {
      case e:VerifyError => // We've almost certainly handled this, so ignore.
    }
    Presenter(this)
    Scripter(this)
    BazaarProvider(this)
    StateObserver(this)
    StateReactor(this)
    Triggers(this)
    TelephonyListener(this)
    val notification = new NotificationCompat.Builder(this)
    .setSmallIcon(R.drawable.empty)
    .setTicker(getString(R.string.appName))
    .setContentTitle(getString(R.string.appName))
    .setOngoing(true)
    .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, classOf[activities.Spiel]), 0))
    .setWhen(null.asInstanceOf[Long])
    .getNotification
    startForeground(1, notification)
    SpielService.initialized = true
    SpielService.enabled = true
  }

  override def onDestroy() {
    super.onDestroy
    TTS.shutdown
    Scripter.shutdown()
    if(Preferences.profiling)
      Debug.stopMethodTracing()
    Option(getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]).foreach(_.cancelAll())
    SpielService.initialized = false
    SpielService.enabled = false
  }

  override protected def onServiceConnected {
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
    Presenter.process(event)
  }

  override protected def onGesture(id:Int) = id match {
    case _ => true
  }

}

object SpielService {

  private var _context:Context = null
  def context = _context

  var initialized = false

  private var _enabled = false
  def enabled = _enabled
  def enabled_=(e:Boolean) = _enabled = e

}
