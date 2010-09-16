package info.spielproject.spiel
package triggers

import android.util.Log

/**
 * Defines a named action to be carried out.
*/

abstract class Action(val key:String, val name:String, val function:() => Unit) {
  Triggers._actions(key) = this
}

class Actions {

  /**
   * Stop speech.
  */

  class StopSpeech extends Action("stopSpeech", "Stop speech", () => TTS.stop)

}

/**
 * Ties a physical action to a function's execution.
*/

abstract class Trigger {

  private var _action:Option[Action] = None

  def action = _action

  def apply(a:Option[Action]) {
    _action.foreach((act) => uninstall(act.function))
    _action = a
    a.foreach { (act) => 
      install()
    }
  }

  def install()

  def uninstall(f:() => Unit)

}

object Triggers {

  private[triggers] var _actions:collection.mutable.Map[String, Action] = collection.mutable.Map.empty

  def actions = _actions

  def apply(service:SpielService) {
    val a = new Actions
    a.getClass.getDeclaredClasses.foreach { cls =>
      try {
        val cons = cls.getConstructor(classOf[Actions])
        if(cons != null)
          cons.newInstance(a)
      } catch { case _ => }
    }
    ProximityNear(Preferences.onProximityNear)
    ShakingStarted(Preferences.onShakingStarted)
  }

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
