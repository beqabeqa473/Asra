package info.spielproject.spiel
package gestures

import android.accessibilityservice.AccessibilityService._
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import AccessibilityNodeInfo._

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

class Listener(directive:Option[HandlerDirective] = None) extends Handler[GesturePayload](directive) {

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

  class Default extends Listener(Some(HandlerDirective(Value(""), Value("")))) {

    private def setInitialFocus() =
      SpielService.rootInActiveWindow.map { root =>
        Log.d("spielcheck", "Setting initial focus")
        val filtered = root.descendants.filter(_.isVisibleToUser)
        Option(root.findFocus(FOCUS_INPUT)).map(_.performAction(FOCUS_ACCESSIBILITY)).getOrElse(false) ||
        filtered.exists(_.performAction(FOCUS_ACCESSIBILITY)) ||
        filtered.exists(_.performAction(FOCUS_INPUT))
      }.getOrElse(false)

    private def prev(source:Option[AccessibilityNodeInfo]):Boolean = 
      source.flatMap { s =>
        granularity.flatMap { g =>
          if((s.getActions&ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) != 0) {
            val b = new Bundle()
            b.putInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, g)
            Some(s.performAction(ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, b))
          } else None
        }.orElse {
          var rv = false
          if((s.getActions&ACTION_PREVIOUS_HTML_ELEMENT) != 0)
            rv = s.performAction(ACTION_PREVIOUS_HTML_ELEMENT)
          if(!rv) {
            var n = s.prevAccessibilityFocus
            while(!rv) {
              rv = n.map(_.performAction(ACTION_ACCESSIBILITY_FOCUS)).getOrElse(false)
              if(!rv)
                n = n.flatMap(_.prevAccessibilityFocus)
            }
          }
          Some(rv)
        }
      }.getOrElse(setInitialFocus())

    private def next(source:Option[AccessibilityNodeInfo]):Boolean = 
      source.flatMap { s =>
        granularity.flatMap { g =>
          if((s.getActions&ACTION_NEXT_AT_MOVEMENT_GRANULARITY) != 0) {
            val b = new Bundle()
            b.putInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, g)
            Some(s.performAction(ACTION_NEXT_AT_MOVEMENT_GRANULARITY, b))
          } else None
        }.orElse {
          var rv = false
          if((s.getActions&ACTION_NEXT_HTML_ELEMENT) != 0)
            rv = s.performAction(ACTION_NEXT_HTML_ELEMENT)
          if(!rv) {
            var n = s.nextAccessibilityFocus
            while(!rv) {
              rv = n.map(_.performAction(ACTION_ACCESSIBILITY_FOCUS)).getOrElse(false)
              if(!rv)
                n = n.flatMap(_.nextAccessibilityFocus)
            }
          }
          Some(rv)
        }
      }.getOrElse(setInitialFocus())

    onLeft { source => prev(source) }

    onRight { source => next(source) }

    onUp { source => prev(source) }

    onDown { source => next(source) }

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

    private val granularities = List(MOVEMENT_GRANULARITY_CHARACTER, MOVEMENT_GRANULARITY_WORD, MOVEMENT_GRANULARITY_LINE, MOVEMENT_GRANULARITY_PARAGRAPH, MOVEMENT_GRANULARITY_PAGE)

    private var granularity:Option[Int] = None

    private def describeGranularity() {
      val id = granularity.map { g =>
        g match {
          case MOVEMENT_GRANULARITY_CHARACTER => R.string.character
          case MOVEMENT_GRANULARITY_WORD => R.string.word
          case MOVEMENT_GRANULARITY_LINE => R.string.line
          case MOVEMENT_GRANULARITY_PARAGRAPH => R.string.paragraph
          case MOVEMENT_GRANULARITY_PAGE => R.string.page
        }
      }.getOrElse(R.string.none)
      TTS.speak(getString(id), true)
    }

    onUpDown { source =>
      source.map { s =>
        val grans = s.getMovementGranularities
        val candidates = granularities.filter(v => (grans & v) != 0)
        granularity.map { g =>
          candidates.indexOf(g) match {
            case -1 => granularity = candidates.reverse.headOption
            case 0 =>
              granularity = None
              granularity.size
            case v => granularity = Some(candidates(v-1))
          }
        }.getOrElse {
          granularity = candidates.reverse.headOption
        }
        describeGranularity()
      }
      true
    }

    onDownUp { source =>
      source.map { s =>
        val grans = s.getMovementGranularities
        val candidates = granularities.filter(v => (grans & v) != 0)
        granularity.map { g =>
          candidates.indexOf(g) match {
            case -1 => granularity = candidates.headOption
            case v if(v == candidates.size-1) =>
              granularity = None
              candidates.size
            case v => granularity = Some(candidates(v+1))
          }
        }.getOrElse {
          granularity = candidates.headOption
        }
        describeGranularity()
      }

      true
    }

  }

}
