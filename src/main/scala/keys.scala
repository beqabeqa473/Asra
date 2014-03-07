package info.spielproject.spiel
package keys

import android.accessibilityservice.AccessibilityService._
import android.content._
import android.util.Log
import android.view._
import KeyEvent._
import accessibility._

import routing._

case class KeyPayload(event:KeyEvent, source:Option[AccessibilityNodeInfo])

class Listener(directive:Option[Directive] = None) extends Handler[KeyPayload](KeyDispatcher, directive) {

  private type Callback = (KeyEvent, Option[AccessibilityNodeInfo]) => Boolean

  private var down:Option[Callback] = None
  def onDown(c:Callback) = down = Some(c)

  private var up:Option[Callback] = None
  def onUp(c:Callback) = up = Some(c)

  KeyDispatcher.register(this)

  def apply(payload:KeyPayload) = payload.event.getAction match {
    case ACTION_DOWN => down.exists(_(payload.event, payload.source))
    case ACTION_UP => up.exists(_(payload.event, payload.source))
  }

}

object KeyDispatcher extends Router[KeyPayload] {

  def apply() {
    utils.instantiateAllMembers(classOf[Keys])
  }

}

class Keys {

  class Default extends Listener(Some(Directive(Value(""), Value("")))) with Commands {

    private var spielKeyDown = false

    onDown { (event, source) =>
      event.getKeyCode match {
        case KEYCODE_CAPS_LOCK =>
          spielKeyDown = true
          true
        case KEYCODE_DPAD_RIGHT if spielKeyDown =>
          if(event.isCtrlPressed)
            source.flatMap { s =>
              (s :: s.ancestors).find(_.supports_?(Action.ScrollForward))
              .map(_.perform(Action.ScrollForward))
            }.getOrElse(true)
          else
            navigate(NavigationDirection.Next)
        case KEYCODE_DPAD_LEFT if spielKeyDown => 
          if(event.isCtrlPressed)
            source.flatMap { s =>
              (s :: s.ancestors).find(_.supports_?(Action.ScrollBackward))
              .map(_.perform(Action.ScrollBackward))
            }.getOrElse(true)
          else
            navigate(NavigationDirection.Prev)
        case KEYCODE_DPAD_UP if spielKeyDown =>
          if(event.isCtrlPressed)
            SpielService.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
          else
            changeGranularity(GranularityDirection.Decrease)
        case KEYCODE_DPAD_DOWN if spielKeyDown =>
          if(event.isCtrlPressed)
            continuousRead()
          else
            changeGranularity(GranularityDirection.Increase)
        case KEYCODE_ESCAPE =>
          if(spielKeyDown)
            SpielService.performGlobalAction(GLOBAL_ACTION_HOME)
          else
            SpielService.performGlobalAction(GLOBAL_ACTION_BACK)
        case KEYCODE_B if spielKeyDown =>
          speakBatteryPercentage()
          true
        case KEYCODE_T if spielKeyDown => speakTime()
        case _ =>
          TTS.stop()
          false
      }
    }

    onUp { (event, source) =>
      event.getKeyCode match {
        case KEYCODE_CAPS_LOCK =>
          spielKeyDown = false
          true
        case _ => false
      }
    }

  }

}
