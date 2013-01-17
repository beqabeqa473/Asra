package info.spielproject.spiel
package events

import collection.mutable.ListBuffer

import android.content._
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

}

object AccessibilityEventReceived extends Event[AccessibilityEvent]

object ApplicationAdded extends Event[Intent]

object ApplicationRemoved extends Event[Intent]

object BluetoothSCOHeadsetConnected extends Event[Unit]

object BluetoothSCOHeadsetDisconnected extends Event[Unit]

object CallAnswered extends Event[Unit]

object CallIdle extends Event[Unit]

object CallRinging extends Event[String]

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

object RingerModeChanged extends Event[String]

object ScreenOff extends Event[Unit]

object ScreenOn extends Event[Unit]

object ShakingStarted extends Event[Unit]

object ShakingStopped extends Event[Unit]

object TTSEngineChanged extends Event[Unit]

object Unlocked extends Event[Unit]

object UtteranceStarted extends Event[Option[String]]

object UtteranceEnded extends Event[Option[String]]

object UtteranceError extends Event[Option[String]]
