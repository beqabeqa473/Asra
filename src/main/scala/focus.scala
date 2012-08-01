package info.spielproject.spiel
package focus

import android.graphics.Rect
import android.os.Build.VERSION
import android.util.Log
import android.view.accessibility._
import AccessibilityNodeInfo._

case class RichAccessibilityNode(node:AccessibilityNodeInfo) {

  lazy val root = {
    def iterate(v:AccessibilityNodeInfo):AccessibilityNodeInfo = v.getParent match {
      case null => v
      case v2 => iterate(v2)
    }
    if(VERSION.SDK_INT >= 16)
      SpielService.rootInActiveWindow.getOrElse(iterate(node))
    else
      iterate(node)
  }

  lazy val parent = node.getParent

  lazy val children =
    (for(i <- 0 to node.getChildCount-1) yield(node.getChild(i))).toList.filterNot(_ == null)

  lazy val siblings = parent.children

  lazy val descendants:List[AccessibilityNodeInfo] = children++children.map { c =>
    c.descendants
  }.flatten

  lazy val interactive_? =
    node.isCheckable || node.isClickable || node.isLongClickable || node.isFocusable

  lazy val rect = {
    val r = new Rect()
    node.getBoundsInScreen(r)
    r
  }

  lazy val row = {
    val origin = new Rect(0, rect.top, Int.MaxValue, rect.bottom)
    root.descendants.filter(_.rect.intersect(origin))
  }

  lazy val label = {
    row.find((v) => v.getClassName == "android.widget.TextView" && v.getText != null && v.getText.length > 0)
    .orElse {
      root.descendants.filter(_.rect.bottom <= rect.top)
      .filter((v) => v.getClassName == "android.widget.TextView" && v.getText != null && v.getText.length > 0)
      .sortBy(_.rect.bottom)
      .reverse.headOption
    }
  }

  protected def interestedInAccessibilityFocus = {
    val nodeClass = utils.classForName(node.getClassName.toString, node.getPackageName.toString)
    val ancestors = nodeClass.map(utils.ancestors(_).map(_.getName)).getOrElse(Nil)
    Log.d("spielcheck", "Evaluating "+node+": "+(node.children == Nil)+", "+ancestors)
    Log.d("spielcheck", "Row: "+node.row)
    val text = Option(node.getText).map(_.toString).getOrElse("")+(Option(node.getContentDescription).map(": "+_).getOrElse(""))
    def isLeafNonHtmlViewGroup =
      node.children == Nil &&
      !(ancestors.contains("android.view.ViewGroup") && text.isEmpty && (node.getActions&ACTION_NEXT_HTML_ELEMENT) == 0)
    isLeafNonHtmlViewGroup
  }

  private def findAccessibilityFocus(nodes:List[AccessibilityNodeInfo], from:Int, wrapped:Boolean = false):Option[AccessibilityNodeInfo] =
    nodes.drop(from).find { n =>
      n.interestedInAccessibilityFocus
    }.orElse {
      if(wrapped)
        None
      else
        findAccessibilityFocus(nodes, 0, true)
    }

  lazy val nextAccessibilityFocus = {
    val nodes = root.descendants.filter(_.isVisibleToUser)
    .sortBy(_.rect.top)
    nodes.indexOf(node) match {
      case -1 => findAccessibilityFocus(nodes, 0, true)
      case v => findAccessibilityFocus(nodes, v+1)
    }
  }

  lazy val prevAccessibilityFocus = {
    val nodes = root.descendants.filter(_.isVisibleToUser)
    .sortBy(_.rect.top).reverse
    nodes.indexOf(node) match {
      case -1 => findAccessibilityFocus(nodes, 0, true)
      case v => findAccessibilityFocus(nodes, v+1)
    }
  }


}
