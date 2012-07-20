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

import gestures.{Gesture, GestureDispatcher}
import presenters.Presenter
import routing.PayloadDirective
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
    GestureDispatcher(this)
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

  private var lastEvent:Option[AccessibilityEvent] = None

  override def onAccessibilityEvent(event:AccessibilityEvent) {
    if(!SpielService.enabled) return
    lastEvent = Some(event)
    Presenter.process(event)
  }

  override protected def onGesture(id:Int) = {
    import AccessibilityService._
    lastEvent.map { e =>
      val directive = new PayloadDirective(e.getPackageName.toString, e.getClassName.toString)
      id match {
        case GESTURE_SWIPE_UP => GestureDispatcher.dispatch(Gesture.Up, directive)
        case GESTURE_SWIPE_DOWN => GestureDispatcher.dispatch(Gesture.Down, directive)
        case GESTURE_SWIPE_LEFT => GestureDispatcher.dispatch(Gesture.Left, directive)
        case GESTURE_SWIPE_RIGHT => GestureDispatcher.dispatch(Gesture.Right, directive)
        case GESTURE_SWIPE_UP_AND_LEFT => GestureDispatcher.dispatch(Gesture.UpLeft, directive)
        case GESTURE_SWIPE_UP_AND_RIGHT => GestureDispatcher.dispatch(Gesture.UpRight, directive)
        case GESTURE_SWIPE_DOWN_AND_LEFT => GestureDispatcher.dispatch(Gesture.DownLeft, directive)
        case GESTURE_SWIPE_DOWN_AND_RIGHT => GestureDispatcher.dispatch(Gesture.DownRight, directive)
        case GESTURE_SWIPE_LEFT_AND_UP => GestureDispatcher.dispatch(Gesture.LeftUp, directive)
        case GESTURE_SWIPE_RIGHT_AND_UP => GestureDispatcher.dispatch(Gesture.RightUp, directive)
        case GESTURE_SWIPE_LEFT_AND_DOWN => GestureDispatcher.dispatch(Gesture.LeftDown, directive)
        case GESTURE_SWIPE_RIGHT_AND_DOWN => GestureDispatcher.dispatch(Gesture.RightDown, directive)
        case GESTURE_SWIPE_UP_AND_DOWN => GestureDispatcher.dispatch(Gesture.UpDown, directive)
        case GESTURE_SWIPE_DOWN_AND_UP => GestureDispatcher.dispatch(Gesture.DownUp, directive)
        case GESTURE_SWIPE_RIGHT_AND_LEFT => GestureDispatcher.dispatch(Gesture.RightLeft, directive)
        case GESTURE_SWIPE_LEFT_AND_RIGHT => GestureDispatcher.dispatch(Gesture.LeftRight, directive)
      }
    }.getOrElse(false)
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
