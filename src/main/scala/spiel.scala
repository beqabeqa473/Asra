package info.spielproject

import android.view.accessibility._

package object spiel {

  implicit def accessibilityEvent2RichEvent(e:AccessibilityEvent) =
    new RichEvent(e)

  implicit def accessibilityNodeInfo2RichNode(n:AccessibilityNodeInfo) =
    RichNode(n)

}
