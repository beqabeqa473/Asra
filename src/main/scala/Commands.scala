package info.spielproject.spiel

import android.content._
import android.os._
import android.view.accessibility._
import AccessibilityEvent._
import AccessibilityNodeInfo._

import events._

trait Commands {

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
    TTS.speak(SpielService.context.getString(id), true)
  }

  def decreaseGranularity() = {
    SpielService.rootInActiveWindow.flatMap(_.find(Focus.Accessibility)).map { s =>
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

  def increaseGranularity() = {
    SpielService.rootInActiveWindow.flatMap(_.find(Focus.Accessibility)).map { s =>
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

  private def setInitialFocus() =
    SpielService.rootInActiveWindow.exists { root =>
      val filtered = root.descendants.filter(_.isVisibleToUser)
      root.find(Focus.Input).exists(_.perform(Action.AccessibilityFocus)) ||
      filtered.exists(_.perform(Action.AccessibilityFocus)) ||
      filtered.exists(_.perform(Action.Focus))
    }

  object NavigationDirection extends Enumeration {
    val Prev = Value
    val Next = Value
  }

  protected def navigate(direction:NavigationDirection.Value, source:Option[AccessibilityNodeInfo] = None, wrap:Boolean = true):Boolean =
    source.orElse(SpielService.rootInActiveWindow.flatMap(_.find(Focus.Accessibility))).flatMap { s =>
      granularity.flatMap { g =>
        val action = if(direction == NavigationDirection.Next) Action.NextAtMovementGranularity else Action.PreviousAtMovementGranularity
        if(s.supports_?(action)) {
          val b = new Bundle()
          b.putInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, g)
          Some(s.perform(action, b))
        } else None
      }.orElse {
        var rv = false
        val htmlAction = if(direction == NavigationDirection.Next) Action.NextHtmlElement else Action.PreviousHtmlElement
        if(s.supports_?(htmlAction))
          rv = s.perform(htmlAction)
        if(!rv) {
          def getNew(s:AccessibilityNodeInfo):Option[AccessibilityNodeInfo] =
            if(direction == NavigationDirection.Next)
              s.nextAccessibilityFocus(wrap)
            else
              s.prevAccessibilityFocus(wrap)
          val scrollAction = if(direction == NavigationDirection.Next) Action.ScrollForward else Action.ScrollBackward
          var n = getNew(s)
          val scrollableContainer = s.ancestors.find(v => v.supports_?(scrollAction))
          scrollableContainer.foreach { sc =>
            if(!n.map(_.ancestors.contains(sc)).getOrElse(true)) {
              sc.perform(scrollAction)
              if(sc.descendants.contains(s))
                n = getNew(s)
              else
                n = scrollableContainer
            }
          }
          val sameSourceDest = n.map { v =>
            if(v == s) true else false
          }.getOrElse(!wrap)
          n.foreach { n2 =>
            if(sameSourceDest && !n2.supports_?(htmlAction))
              n.map(_.perform(Action.ClearAccessibilityFocus))
          }
          while(!rv) {
            rv = n.exists(_.perform(Action.AccessibilityFocus))
            if(sameSourceDest) rv = true
            if(!sameSourceDest)
              if(rv)
                if(n.exists(_.supports_?(htmlAction)))
                  navigate(direction, n)
              else
                n = n.flatMap(s => getNew(s))
          }
        }
        Some(rv)
      }
    }.getOrElse(setInitialFocus())

  protected def continuousRead() {
    val oldGranularity = granularity
    val pm = SpielService.context.getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
    val wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "spiel")
    wl.setReferenceCounted(false)
    val continue = { (Unit):Any =>
      granularity = None
      wl.acquire()
      SpielService.rootInActiveWindow.foreach { root =>
        TTS.noFlush = true
        navigate(NavigationDirection.Next, wrap = false)
      }
    }
    def clear() {
      granularity = oldGranularity
      wl.release()
      TTS.noFlush = false
      SpeechQueueEmpty -= continue
    }
    val stop = { (e:AccessibilityEvent) =>
      if(List(TYPE_TOUCH_EXPLORATION_GESTURE_START, TYPE_TOUCH_INTERACTION_START, TYPE_WINDOW_STATE_CHANGED).contains(e.getEventType)) {
        clear()
        AccessibilityEventReceived -= this
      }
    }
    SpeechQueueEmpty += continue
    AccessibilityEventReceived += stop
    CallAnswered += { () =>
      clear()
      CallAnswered -= this
    }
    CallRinging += { () =>
      clear()
      CallRinging -= this
    }
    continue()
  }

}
