package info.spielproject.spiel
package events

import collection.mutable.ListBuffer

import android.content._
import android.util.Log
import android.view.accessibility._

class Event[T] {

  private val handlers = ListBuffer[(T) => Any]()

  def add(h:(T) => Any) =
    handlers += h

  def +=(h:(T) => Any) =
    add(h)

  def add(h: => Any) =
    handlers += { (Unit) => h }

  def +=(h: => Any) =
    add(h)

  def remove(h:(T) => Any) =
    handlers -= h

  def -=(h:(T) => Any) =
    remove(h)

  def remove(h: => Any) =
    handlers -= { (Unit) => h }

  def -=(h: => Any) =
    remove(h)

  def apply(arg:T) = handlers.foreach(_(arg))

  def on(intents:List[String], arg:T, dataScheme:Option[String] = None):Any = {
    val f = new IntentFilter
    intents.foreach(f.addAction(_))
    dataScheme.foreach(f.addDataScheme(_))
    SpielService.context.registerReceiver(
      { (c:Context, i:Intent) =>
        Event.this(Option(arg).getOrElse(i.asInstanceOf[T]))
      },
      f
    )
  }

  def on(intents:List[String]):Any = on(intents, null.asInstanceOf[T])

  def on(intent:String, arg:T):Any = on(intent :: Nil, arg)

  def on(intent:String):Any = on(intent :: Nil)

  def on(intent:String, dataScheme:Option[String]):Any = on(intent :: Nil, null.asInstanceOf[T], dataScheme = dataScheme)


}

object AccessibilityEventReceived extends Event[AccessibilityEvent]

object ApplicationAdded extends Event[Intent]

object ApplicationRemoved extends Event[Intent]

object BatteryChanged extends Event[Intent]

object BatteryLevelChanged extends Event[Int]

object BluetoothConnected extends Event[Intent]

object BluetoothDisconnected extends Event[Intent]

object BluetoothSCOHeadsetConnected extends Event[Unit]

object BluetoothSCOHeadsetDisconnected extends Event[Unit]

object CallAnswered extends Event[Unit]

object CallIdle extends Event[Unit]

object CallRinging extends Event[String]

object Destroyed extends Event[Context]

object Initialized extends Event[Context]

object MessageNoLongerWaiting extends Event[Unit]

object MessageWaiting extends Event[Unit]

object OrientationLandscape extends Event[Unit]

object OrientationPortrait extends Event[Unit]

object PitchChanged extends Event[Unit]

object PowerConnected extends Event[Unit]

object PowerDisconnected extends Event[Unit]

object ProximityFar extends Event[Unit]

object ProximityNear extends Event[Unit]

object RateChanged extends Event[Unit]

object RingerMode extends Enumeration {
  val Normal = Value
  val Silent = Value
  val Vibrate = Value
}

object RingerModeChanged extends Event[RingerMode.Value]

object RingerModeChangedIntent extends Event[Intent]

object ScreenOff extends Event[Unit]

object ScreenOn extends Event[Unit]

object ShakingStarted extends Event[Unit]

object ShakingStopped extends Event[Unit]

object TTSEngineChanged extends Event[Unit]

object UnhandledException extends Event[Exception]

object Unlocked extends Event[Unit]

object UtteranceStarted extends Event[Option[String]]

object UtteranceEnded extends Event[Option[String]]

object UtteranceError extends Event[Option[String]]
