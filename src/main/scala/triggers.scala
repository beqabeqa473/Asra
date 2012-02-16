package info.spielproject.spiel
package triggers

import android.content.{BroadcastReceiver, Context, Intent}
import android.util.Log

import handlers.EventReviewQueue
import scripting.Scripter

/**
 * Defines a named action to be carried out by a <code>Trigger</code>.
*/

abstract class Action(val key:String, val name:String, val function:() => Unit) {
  Triggers._actions(key) = this
}

/**
 * Holds all defined actions. By creating this class and using the powers of 
 * reflection, we need only define a new <code>Action</code> within and it 
 * will be automatically registered.
*/

class Actions {

  /**
   * Create a script template based on the last <code>AccessibilityEvent</code>.
  */

  class CreateScriptTemplate extends Action("createScriptTemplate", Triggers.service.getString(R.string.createScriptTemplateAction), () =>
    EventReviewQueue.reverse.headOption.foreach { event =>
      val filename = Scripter.createTemplateFor(event)
      TTS.speak(Triggers.service.getString(R.string.templateCreated, filename), true)
    }
  )

  /**
   * Stop speech.
  */

  class StopSpeech extends Action("stopSpeech", Triggers.service.getString(R.string.stopSpeech), () => TTS.stop)

  /**
   * Toggle whether or not Spiel is enabled.
  */

  class ToggleSpiel extends Action("toggleSpiel", Triggers.service.getString(R.string.toggleSpiel), { () =>
    if(SpielService.enabled) {
      TTS.speak(Triggers.service.getString(R.string.spielOff), true)
      SpielService.enabled = false
    } else {
      SpielService.enabled = true
      TTS.speak(Triggers.service.getString(R.string.spielOn), true)
    }
  })

}

/**
 * Ties a physical action to a function's execution.
*/

abstract class Trigger {

  private var _action:Option[Action] = None

  def action = _action

  /**
   * Binds this <code>Trigger</code> to a specified <code>Action</code>. 
   * Pass in <code>None</code> to unbind.
  */

  def apply(a:Option[Action]) {
    _action.foreach((act) => uninstall(act.function))
    _action = a
    a.foreach { (act) => install() }
  }

  /**
   * Called when a new <code>Action</code> is installed for this <code>Trigger</code>.
  */

  def install()

  /**
   * Called with the function that is to be installed from this <code>Trigger</code>.
  */

  def uninstall(f:() => Unit)

}

/**
 * Convenience singleton for setting up triggers.
*/

object Triggers {

  private[triggers] var _actions:collection.mutable.Map[String, Action] = collection.mutable.Map.empty

  /**
   * Mapping of action names to triggers.
  */

  def actions = _actions

  private[triggers] var service:SpielService = null

  /**
   * Initialize triggers using the specified <code>SpielService</code>.
  */

  def apply(svc:SpielService) {
    service = svc
    // Here's where we iterate through the above <code>Actions</code> class, 
    // registering all <code>Action</code> classes it contains. Doing this 
    // obviates the need to manually register new actions on creation.
    val a = new Actions
    a.getClass.getDeclaredClasses.foreach { cls =>
      try {
        val cons = cls.getConstructor(classOf[Actions])
        if(cons != null)
          cons.newInstance(a)
      } catch { case _ => }
    }
    // Set triggers to the <code>Action</code> specified in <code>Preferences</code>.
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

  override def apply(a:Option[Action]) {
    super.apply(a)
    StateObserver.onScreenOff { () => a.foreach(v => uninstall(v.function)) }
    StateObserver.onScreenOn { () => install() }
  }

  private var installed = false

  def install() {
    if(!installed) {
      action.foreach((a) => StateObserver.onShakingStarted(a.function))
      installed = true
    }
  }

  def uninstall(f:() => Unit) {
    if(installed) {
      StateObserver.removeShakingStarted(f)
      installed = false
    }
  }

}
