package info.spielproject

import android.view.accessibility._

package object spiel {
  implicit def accessibilityNodeInfo2RichAccessibilityNode(n:AccessibilityNodeInfo) =
    RichAccessibilityNode(n)
}
