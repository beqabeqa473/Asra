package info.spielproject.spiel

import android.accessibilityservice._
import AccessibilityService._
import android.app.{Notification, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.content.res.Configuration
import android.os.Debug
import android.os.Build.VERSION
import android.util.Log
import android.view.accessibility.{AccessibilityEvent, AccessibilityNodeInfo}
import AccessibilityEvent._
import AccessibilityNodeInfo._
import com.nullwire.trace.ExceptionHandler

import events._
import gestures.{Gesture, GestureDispatcher, GesturePayload}
import presenters.Presenter
import routing._
import scripting._
import triggers.Triggers

/**
 * <code>AccessibilityService</code> implementation that serves as main entry point.
*/

class SpielService extends AccessibilityService {

  override def onCreate() {
    super.onCreate()
    SpielService.service = this
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
    plugins.PluginManager(this)
    val nb = new Notification.Builder(this)
    .setSmallIcon(R.drawable.empty)
    .setTicker(getString(R.string.appName))
    .setContentTitle(getString(R.string.appName))
    .setOngoing(true)
    .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, classOf[ui.Spiel]), 0))
    if(VERSION.SDK_INT >= 17)
      nb.setShowWhen(false)
    startForeground(1, nb.getNotification)
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
    setServiceInfo(SpielService.info)
  }

  override def onInterrupt = TTS.stop

  private var accessibilityFocusCandidate:Option[AccessibilityNodeInfo] = None

  override def onAccessibilityEvent(event:AccessibilityEvent) {
    if(!SpielService.enabled) return
    if(List(TYPE_VIEW_FOCUSED, TYPE_VIEW_HOVER_ENTER).contains(event.getEventType))
      Option(event.getSource).foreach { n =>
        if(VERSION.SDK_INT >= 16)
          if(n.children == Nil && n.performAction(ACTION_ACCESSIBILITY_FOCUS))
            return
      }
    Presenter.process(event)
  }

  override protected def onGesture(id:Int) = {
    val source = Option(getRootInActiveWindow).flatMap(v => Option(v.findFocus(FOCUS_ACCESSIBILITY)))
    val directive = source.map { s =>
      new PayloadDirective(s.getPackageName.toString, s.getClassName.toString)
    }.getOrElse(new PayloadDirective("", ""))
    val gesture = id match {
      case GESTURE_SWIPE_UP => Gesture.Up
      case GESTURE_SWIPE_DOWN => Gesture.Down
      case GESTURE_SWIPE_LEFT => Gesture.Left
      case GESTURE_SWIPE_RIGHT => Gesture.Right
      case GESTURE_SWIPE_UP_AND_LEFT => Gesture.UpLeft
      case GESTURE_SWIPE_UP_AND_RIGHT => Gesture.UpRight
      case GESTURE_SWIPE_DOWN_AND_LEFT => Gesture.DownLeft
      case GESTURE_SWIPE_DOWN_AND_RIGHT => Gesture.DownRight
      case GESTURE_SWIPE_LEFT_AND_UP => Gesture.LeftUp
      case GESTURE_SWIPE_RIGHT_AND_UP => Gesture.RightUp
      case GESTURE_SWIPE_LEFT_AND_DOWN => Gesture.LeftDown
      case GESTURE_SWIPE_RIGHT_AND_DOWN => Gesture.RightDown
      case GESTURE_SWIPE_UP_AND_DOWN => Gesture.UpDown
      case GESTURE_SWIPE_DOWN_AND_UP => Gesture.DownUp
      case GESTURE_SWIPE_RIGHT_AND_LEFT => Gesture.RightLeft
      case GESTURE_SWIPE_LEFT_AND_RIGHT => Gesture.LeftRight
    }
    try {
      GestureDispatcher.dispatch(GesturePayload(gesture, source), directive)
    } catch {
      case e =>
        Log.e("spiel", "Error in gesture dispatch", e)
        false
    }
  }

  private var portrait = true

  override def onConfigurationChanged(c:Configuration) {
    super.onConfigurationChanged(c)
    if(portrait && c.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      OrientationLandscape()
      portrait = false
    } else if(!portrait && c.orientation == Configuration.ORIENTATION_PORTRAIT){
      OrientationPortrait()
      portrait = true
    }
  }

}

object SpielService {

  private var service:SpielService = null

  def context = service.asInstanceOf[Context]

  def performGlobalAction(action:Int) = service.performGlobalAction(action)

  var initialized = false

  private var _enabled = false
  def enabled = _enabled

  lazy val info = {
    val info = new AccessibilityServiceInfo()
    import AccessibilityServiceInfo._
    info.feedbackType = FEEDBACK_SPOKEN
    // Should not need this but when using install -r to reinstall, explore by touch may not get enabled otherwise
    if(VERSION.SDK_INT >= 16)
      info.flags = FLAG_REQUEST_TOUCH_EXPLORATION_MODE | DEFAULT
    info.eventTypes = TYPES_ALL_MASK
    info
  }

  def enabled_=(e:Boolean) = {
    if(e)
      service.setServiceInfo(info)
    else
      service.setServiceInfo(new AccessibilityServiceInfo)
    _enabled = e
  }

  def rootInActiveWindow = Option(service.getRootInActiveWindow)

}
