package info.spielproject.spiel
package focus

import android.util.Log
import android.view.accessibility._

case class RichAccessibilityNode(node:AccessibilityNodeInfo) {

  lazy val root = {
    def iterate(v:AccessibilityNodeInfo):AccessibilityNodeInfo = v.getParent match {
      case null => v
      case v2 => iterate(v2)
    }
    iterate(node)
  }

  lazy val parent = node.getParent

  lazy val children =
    (for(i <- 0 to node.getChildCount-1) yield(node.getChild(i))).toList.filterNot(_ == null)

  lazy val siblings = parent.children

  lazy val descendants:List[AccessibilityNodeInfo] = children.map { c =>
    List(c)++c.descendants
  }.flatten

  lazy val interactive_? =
    node.isCheckable || node.isClickable || node.isLongClickable || node.isFocusable

  private def findAccessibilityFocus(nodes:List[AccessibilityNodeInfo], from:Int):Option[AccessibilityNodeInfo] =
    nodes.drop(from).find { n =>
      Log.d("spielcheck", "Evaluating "+nodes.indexOf(n)+" with "+children.size+" children")
      n.children == Nil
    }.orElse(findAccessibilityFocus(nodes, 0))

  lazy val nextAccessibilityFocus = {
    val nodes = root.descendants
    nodes.indexOf(node) match {
      case -1 => None
      case v => findAccessibilityFocus(nodes, v+1)
    }
  }

  lazy val prevAccessibilityFocus = {
    val nodes = root.descendants.reverse
    nodes.indexOf(node) match {
      case -1 => None
      case v => findAccessibilityFocus(nodes, v+1)
    }
  }


}
