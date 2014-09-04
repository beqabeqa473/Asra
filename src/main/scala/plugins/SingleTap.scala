package info.spielproject.spiel
package plugins

import android.util.Log
import android.view.accessibility._
import AccessibilityEvent._

import events._

class SingleTap extends Plugin {

  val name = "Single-tap"

  val description = "Activate items with a single tap"

  val key = "singleTap"

  private var lastTouchStart = 0l

  private var lastTouchedNode:Option[AccessibilityNodeInfo] = None

  val accessibilityEventHandler = { e:AccessibilityEvent =>
    if(e.getEventType == TYPE_TOUCH_EXPLORATION_GESTURE_START)
      lastTouchStart = System.currentTimeMillis
    else if(e.getEventType == TYPE_TOUCH_EXPLORATION_GESTURE_END) {
      if(System.currentTimeMillis-lastTouchStart <= 150)
        lastTouchedNode.foreach(_.perform(Action.Click))
      lastTouchStart = 0
      lastTouchedNode = None
    } else if(e.getEventType == TYPE_VIEW_HOVER_ENTER && e.source != None)
      lastTouchedNode = e.source
  }

  def start() {
    AccessibilityEventReceived += accessibilityEventHandler
  }

  def stop() {
    AccessibilityEventReceived -= accessibilityEventHandler
  }

}