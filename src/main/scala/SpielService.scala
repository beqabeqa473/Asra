package info.spielproject.spiel

import android.accessibilityservice._
import AccessibilityService._
import android.app.{Notification, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.content.res.Configuration
import android.os.Debug
import android.os.Build.VERSION
import android.util.Log
import android.view._
import KeyEvent._
import accessibility.{AccessibilityEvent, AccessibilityNodeInfo}
import AccessibilityEvent._
import AccessibilityNodeInfo._

import events._
import gestures.{Gesture, GestureDispatcher, GesturePayload}
import keys._
import routing._
import scripting._

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
    try {
      TTS(this)
    } catch {
      case e:VerifyError => // We've almost certainly handled this, so ignore.
    }
    presenters.Presenter()
    GestureDispatcher()
    KeyDispatcher()
    Scripter(this)
    Sensors(this)
    Device()
    Bluetooth()
    Telephony(this)
    plugins.PluginManager(this)
    Initialized(this)
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
    Destroyed(this)
  }

  override protected def onServiceConnected {
    setServiceInfo(SpielService.info)
  }

  override def onInterrupt = TTS.stop

  override def onAccessibilityEvent(event:AccessibilityEvent) {
    if(!SpielService.enabled) return
    AccessibilityEventReceived(AccessibilityEvent.obtain(event))
  }

  override protected def onKeyEvent(event:KeyEvent) = {
    val root = Option(getRootInActiveWindow)
    val source = root.flatMap(_.find(Focus.Accessibility)).orElse(root.flatMap(_.find(Focus.Input)))
    val directive = source.map { s =>
      new Directive(s.getPackageName.toString, s.getClassName.toString)
    }.getOrElse(new Directive("", ""))
    KeyEventReceived(event)
    if(event.getAction == ACTION_DOWN && event.getKeyCode != KEYCODE_VOLUME_DOWN && event.getKeyCode != KEYCODE_VOLUME_UP)
      TTS.stop()
    KeyDispatcher.dispatch(KeyPayload(event, source), directive)
  }

  override protected def onGesture(id:Int) = {
    val source = Option(getRootInActiveWindow).flatMap(_.find(Focus.Accessibility))
    val directive = source.map { s =>
      new Directive(s.getPackageName.toString, s.getClassName.toString)
    }.getOrElse(new Directive("", ""))
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
    TTS.stop()
    try {
      GestureDispatcher.dispatch(GesturePayload(gesture, source), directive)
    } catch {
      case e:Throwable =>
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
    if(VERSION.SDK_INT >= 18)
      info.flags = info.flags|FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY|FLAG_REQUEST_FILTER_KEY_EVENTS
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
