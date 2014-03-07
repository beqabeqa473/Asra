package info.spielproject.spiel
package events

import collection.mutable.Set

import android.content._
import android.util.Log
import android.view._
import accessibility._

class Event[T] {

  private val handlers = Set[(T) => Unit]()

  def add(h:(T) => Unit) =
    handlers += h

  def +=(h:(T) => Unit) =
    add(h)

  def add(h: => Unit) =
    handlers += { (Unit) => h }

  def +=(h: => Unit) =
    add(h)

  def remove(h:(T) => Unit) =
    handlers -= h

  def -=(h:(T) => Unit) =
    remove(h)

  def remove(h: => Unit) =
    handlers -= { (Unit) => h }

  def -=(h: => Unit) =
    remove(h)

  def apply(arg:T) {
    Log.d("spiel", "Firing "+this.getClass.getName)
    handlers.foreach { h =>
      def fail:PartialFunction[Throwable, Unit] = {
        case t:Throwable =>
          Log.e("spiel", "Error handling event "+this+", "+arg, t)
          UnhandledException(t)
      }
      try {
        h(arg)
      } catch {
        fail
      }
    }
  }

  def apply() {
    apply(null.asInstanceOf[T])
  }

  def on(intents:List[String], arg:T, dataScheme:Option[String] = None):Unit = {
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

  def on(intents:List[String]):Unit = on(intents, null.asInstanceOf[T])

  def on(intent:String, arg:T):Unit = on(intent :: Nil, arg)

  def on(intent:String):Unit = on(intent :: Nil)

  def on(intent:String, dataScheme:Option[String]):Unit = on(intent :: Nil, null.asInstanceOf[T], dataScheme = dataScheme)

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

object KeyEventReceived extends Event[KeyEvent]

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

object SpeechQueueEmpty extends Event[Unit]

object SpeechStopped extends Event[Unit]

object TTSEngineChanged extends Event[Unit]

object UnhandledException extends Event[Throwable]

object Unlocked extends Event[Unit]

object UtteranceEnded extends Event[String]

object UtteranceError extends Event[String]

object UtteranceStarted extends Event[String]
