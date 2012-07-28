package info.spielproject.spiel
package focus

import android.view.accessibility._

case class RichAccessibilityNode(node:AccessibilityNodeInfo) {

  def root = {
    def iterate(v:AccessibilityNodeInfo):AccessibilityNodeInfo = v.getParent match {
      case null => v
      case v2 => iterate(v2)
    }
    iterate(node)
  }

  def parent = node.getParent

  def children =
    (for(i <- 0 to node.getChildCount-1) yield(node.getChild(i))).toList.filterNot(_ == null)

  def siblings = node.parent.children

  def descendants:List[AccessibilityNodeInfo] = children.map { c =>
    List(c)++c.descendants
  }.flatten

}
