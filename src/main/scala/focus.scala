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

  lazy val ancestors = {
    val nodeClass = utils.classForName(node.getClassName.toString, node.getPackageName.toString)
    nodeClass.map(utils.ancestors(_).map(_.getName)).getOrElse(Nil)
  }

  protected def isA_?(cls:String) =
    node.getClassName == cls || ancestors.contains(cls)

  lazy val label = {
    def isTextView(n:AccessibilityNodeInfo) =
      n.isA_?("android.widget.TextView") && !n.isA_?("android.widget.EditText")
    if(
      List("android.widget.CheckBox", "android.widget.EditText", "android.widget.ProgressBar", "android.widget.RadioButton", "android.widget.RatingBar")
      .exists(isA_?(_))
    )
      row.find((v) => isTextView(v) && v.getText != null && v.getText.length > 0)
      .orElse {
        root.descendants.filter(_.rect.bottom <= rect.top)
        .sortBy(_.rect.bottom)
        .reverse.headOption.filter { c =>
          isTextView(c) && c.getText != null && c.getText.length > 0
        }
      }
    else
      None
  }

  protected def interestedInAccessibilityFocus = {
    Log.d("spielcheck", "Evaluating "+node)
    val text = Option(node.getText).map(_.toString).getOrElse("")+(Option(node.getContentDescription).map(": "+_).getOrElse(""))
    Log.d("spielcheck", "Text: "+text)
    def isNotAdapterView = !node.isA_?("android.widget.AdapterView")
    Log.d("spielcheck", "isNotAdapterView: "+isNotAdapterView)
    def isNonLabel =
      !node.root.descendants.map(_.label).contains(Some(node))
    Log.d("spielcheck", "isNonLabel: "+isNonLabel)
    def isLeafOrTextualNonHtmlViewGroup =
      if(isA_?("android.view.ViewGroup"))
        children == Nil &&
        (!text.isEmpty || (node.getActions&ACTION_NEXT_HTML_ELEMENT) == 0)
      else
        node.children == Nil
    Log.d("spielcheck", "Leaf: "+isLeafOrTextualNonHtmlViewGroup)
    isNotAdapterView &&
    isNonLabel &&
    isLeafOrTextualNonHtmlViewGroup
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
