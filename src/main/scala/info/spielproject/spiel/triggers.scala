package info.spielproject.spiel
package triggers

/**
 * Defines a named action to be carried out.
*/

abstract class Action(val name:String, val function:() => Unit)

/**
 * Stop speech.
*/

object StopSpeech extends Action("Stop speech", () => TTS.stop)

/**
 * Ties a physical action to a function's execution.
*/

abstract class Trigger {

  private var _action:Option[Action] = None

  def action = _action

  def apply(a:Option[Action]) {
    a.map((act) => install()).getOrElse {
      action.foreach((a) => uninstall(a.function))
    }
    _action = a
  }

  def install()

  def uninstall(f:() => Unit)

}

/**
 * Triggered when the proximity sensor registers something nearby.
*/

object ProximityNear extends Trigger {

  def install() = action.foreach((a) => StateObserver.onProximityNear(a.function))

  def uninstall(f:() => Unit) = StateObserver.removeProximityNear(f)

}

/**
 * Triggers when device is shaken.
*/

object ShakingStarted extends Trigger {

  def install() = action.foreach((a) => StateObserver.onShakingStarted(a.function))

  def uninstall(f:() => Unit) = StateObserver.removeShakingStarted(f)

}
