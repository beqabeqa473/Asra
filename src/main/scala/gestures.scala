package info.spielproject.spiel
package gestures

import android.view.accessibility.AccessibilityNodeInfo

import routing._
import android.util.Log

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

case class GesturePayload(gesture:Gesture.Value, source:AccessibilityNodeInfo)

class Listener(directive:Option[HandlerDirective] = None) extends Handler[GesturePayload](directive) {

  private type Callback = (AccessibilityNodeInfo) => Boolean

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
  def onRightDown(c:Callback) = Some(c)

  private var rightLeft:Option[Callback] = None
  def onRightLeft(c:Callback) = Some(c)

  private var leftRight:Option[Callback] = None
  def onLeftRight(c:Callback) = leftRight = Some(c)

  private var upDown:Option[Callback] = None
  def onUpDown(c:Callback) = upDown = Some(c)

  private var downUp:Option[Callback] = None
  def onDownUp(c:Callback) = downUp = Some(c)

  GestureDispatcher.register(this)

  import Gesture._

  def apply(payload:GesturePayload) = payload.gesture match {
    case Left => left.map(_(payload.source)).getOrElse(false)
    case Right => right.map(_(payload.source)).getOrElse(false)
    case Up => up.map(_(payload.source)).getOrElse(false)
    case Down => down.map(_(payload.source)).getOrElse(false)
    case UpLeft => upLeft.map(_(payload.source)).getOrElse(false)
    case UpRight => upRight.map(_(payload.source)).getOrElse(false)
    case DownLeft => downLeft.map(_(payload.source)).getOrElse(false)
    case DownRight => downRight.map(_(payload.source)).getOrElse(false)
    case LeftUp => leftUp.map(_(payload.source)).getOrElse(false)
    case RightUp => rightUp.map(_(payload.source)).getOrElse(false)
    case LeftDown => leftDown.map(_(payload.source)).getOrElse(false)
    case RightDown => rightDown.map(_(payload.source)).getOrElse(false)
    case LeftRight => leftRight.map(_(payload.source)).getOrElse(false)
    case RightLeft => rightLeft.map(_(payload.source)).getOrElse(false)
    case UpDown => upDown.map(_(payload.source)).getOrElse(false)
    case DownUp => downUp.map(_(payload.source)).getOrElse(false)
  }

}

object GestureDispatcher extends Router[GesturePayload] {

  utils.instantiateAllMembers(classOf[Gestures])

}

class Gestures {

  class Default extends Listener(Some(HandlerDirective(All, All))) {

    import android.accessibilityservice.AccessibilityService._

    onLeft { source => true }

    onRight { source => true }

    onUp { source => true }

    onDown { source => true }

    onUpLeft { source => SpielService.performGlobalAction(GLOBAL_ACTION_HOME) }

    onUpRight { source => SpielService.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }

    onDownLeft { source => SpielService.performGlobalAction(GLOBAL_ACTION_BACK) }

    onDownRight { source => SpielService.performGlobalAction(GLOBAL_ACTION_RECENTS) }

    onLeftUp { source => true }

    onRightUp { source => true }

    onLeftDown { source => true }

    onRightDown { source => true }

    onLeftRight { source => true }

    onRightLeft { source => true }

    onUpDown { source => true }

    onDownUp { source => true }

  }

}
