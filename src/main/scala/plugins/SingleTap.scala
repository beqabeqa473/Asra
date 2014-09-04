package info.spielproject.spiel
package plugins

import android.util.Log
import android.view.accessibility._
import AccessibilityEvent._

import events._

class SingleTap extends Plugin {

  private var lastTouchStart = 0l

  val accessibilityEventHandler = { e:AccessibilityEvent =>
    if(e.getEventType == TYPE_TOUCH_EXPLORATION_GESTURE_START)
      lastTouchStart = System.currentTimeMillis
    else if(e.getEventType == TYPE_TOUCH_EXPLORATION_GESTURE_END) {
      if(System.currentTimeMillis-lastTouchStart <= 150)
        for(
          r <- SpielService.rootInActiveWindow;
          f <- r.find(Focus.Accessibility).orElse(r.find(Focus.Input))
        ) { f.perform(Action.Click) }
      lastTouchStart = 0
    }
  }

  def start() {
    AccessibilityEventReceived += accessibilityEventHandler
  }

  def stop() {
    AccessibilityEventReceived -= accessibilityEventHandler
  }

}