package info.spielproject.spiel
package gestures

import android.accessibilityservice.AccessibilityService._
import android.content._
import android.os.{Bundle, PowerManager}
import android.util.Log
import android.view.accessibility._
import AccessibilityEvent._

import events._
import routing._

object Gesture extends Enumeration {
  val Up = Value
  val Down = Value
  val Left = Value
  val Right = Value
  val UpLeft = Value
  val UpRight = Value
  val DownLeft = Value
  val DownRight = Value
  val LeftDown = Value
  val LeftUp = Value
  val RightDown = Value
  val RightUp = Value
  val UpDown = Value
  val DownUp = Value
  val LeftRight = Value
  val RightLeft = Value
}

case class GesturePayload(gesture:Gesture.Value, source:Option[AccessibilityNodeInfo])

class Listener(directive:Option[Directive] = None) extends Handler[GesturePayload](GestureDispatcher, directive) {

  private type Callback = (Option[AccessibilityNodeInfo]) => Boolean

  private var left:Option[Callback] = None
  def onLeft(c:Callback) = left = Some(c)

  private var right:Option[Callback] = None
  def onRight(c:Callback) = right = Some(c)

  private var up:Option[Callback] = None
  def onUp(c:Callback) = up = Some(c)

  private var down:Option[Callback] = None
  def onDown(c:Callback) = down = Some(c)

  private var upLeft:Option[Callback] = None
  def onUpLeft(c:Callback) = upLeft = Some(c)

  private var upRight:Option[Callback] = None
  def onUpRight(c:Callback) = upRight = Some(c)

  private var downLeft:Option[Callback] = None
  def onDownLeft(c:Callback) = downLeft = Some(c)

  private var downRight:Option[Callback] = None
  def onDownRight(c:Callback) = downRight = Some(c)

  private var leftUp:Option[Callback] = None
  def onLeftUp(c:Callback) = leftUp = Some(c)

  private var rightUp:Option[Callback] = None
  def onRightUp(c:Callback) = rightUp = Some(c)

  private var leftDown:Option[Callback] = None
  def onLeftDown(c:Callback) = leftDown = Some(c)

  private var rightDown:Option[Callback] = None
  def onRightDown(c:Callback) = rightDown = Some(c)

  private var rightLeft:Option[Callback] = None
  def onRightLeft(c:Callback) = rightLeft = Some(c)

  private var leftRight:Option[Callback] = None
  def onLeftRight(c:Callback) = leftRight = Some(c)

  private var upDown:Option[Callback] = None
  def onUpDown(c:Callback) = upDown = Some(c)

  private var downUp:Option[Callback] = None
  def onDownUp(c:Callback) = downUp = Some(c)

  GestureDispatcher.register(this)

  import Gesture._

  def apply(payload:GesturePayload) = payload.gesture match {
    case Left => left.exists(_(payload.source))
    case Right => right.exists(_(payload.source))
    case Up => up.exists(_(payload.source))
    case Down => down.exists(_(payload.source))
    case UpLeft => upLeft.exists(_(payload.source))
    case UpRight => upRight.exists(_(payload.source))
    case DownLeft => downLeft.exists(_(payload.source))
    case DownRight => downRight.exists(_(payload.source))
    case LeftUp => leftUp.exists(_(payload.source))
    case RightUp => rightUp.exists(_(payload.source))
    case LeftDown => leftDown.exists(_(payload.source))
    case RightDown => rightDown.exists(_(payload.source))
    case LeftRight => leftRight.exists(_(payload.source))
    case RightLeft => rightLeft.exists(_(payload.source))
    case UpDown => upDown.exists(_(payload.source))
    case DownUp => downUp.exists(_(payload.source))
  }

}

object GestureDispatcher extends Router[GesturePayload] {

  def apply() {
    utils.instantiateAllMembers(classOf[Gestures])
  }

}

class Gestures {

  class Default extends Listener(Some(Directive(Value(""), Value("")))) with Commands {

    onLeft { source => navigate(NavigationDirection.Prev) }

    onRight { source => navigate(NavigationDirection.Next) }

    onUp { source => changeGranularity(GranularityDirection.Decrease) }

    onDown { source => changeGranularity(GranularityDirection.Increase) }

    onUpLeft { source => SpielService.performGlobalAction(GLOBAL_ACTION_HOME) }

    onUpRight { source => SpielService.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }

    onDownLeft { source => SpielService.performGlobalAction(GLOBAL_ACTION_BACK) }

    onDownRight { source => SpielService.performGlobalAction(GLOBAL_ACTION_RECENTS) }

    onLeftUp { source => true }

    onRightUp { source => true }

    onLeftDown { source => true }

    onRightDown { source => true }

    onLeftRight { source => continuousRead() }

    onRightLeft { source => disableSpiel(); true }

    onUpDown { source => true }

    onDownUp { source => true }

  }

}
