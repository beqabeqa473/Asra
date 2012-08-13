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
    root.descendants.filter(_.rect.intersect(origin)).sortBy(_.rect.left)
  }

  lazy val ancestors = {
    val nodeClass = utils.classForName(node.getClassName.toString, node.getPackageName.toString)
    nodeClass.map(utils.ancestors(_).map(_.getName)).getOrElse(Nil)
  }

  protected def isA_?(cls:String) =
    node.getClassName == cls || ancestors.contains(cls)

  lazy val label = {
    def isTextView(n:AccessibilityNodeInfo) =
      n.isA_?("android.widget.TextView") && !n.isA_?("android.widget.EditText") && !n.isA_?("android.widget.Button")
    if(
      List("android.widget.CheckBox", "android.widget.EditText", "android.widget.ProgressBar", "android.widget.RadioButton", "android.widget.RatingBar")
      .exists(isA_?(_))
    ) {
      Log.d("spielcheck", "Row: "+row)
      row.find((v) => isTextView(v) && !v.interactive_? && v.getText != null && v.getText.length > 0)
      } .orElse {
        root.descendants.filter(_.rect.bottom <= rect.top)
        .sortBy(_.rect.bottom)
        .reverse.headOption.filter { c =>
          isTextView(c) && !c.interactive_? && c.getText != null && c.getText.length > 0
        }
      }
    else
      None
  }

  protected def interestedInAccessibilityFocus = {
    Log.d("spielcheck", "Evaluating "+node)
    val text = Option(node.getText).map(_.toString).getOrElse("")+(Option(node.getContentDescription).map(": "+_).getOrElse(""))
    Log.d("spielcheck", "Text: "+text)
    // TODO: These predicates are ugly, should clean this logic up later.
    lazy val isNotDisabledWithNoText =
      if(node.isEnabled)
        true
      else
        !text.isEmpty
    lazy val isNotAdapterView = !node.isA_?("android.widget.AdapterView")
    Log.d("spielcheck", "isNotAdapterView: "+isNotAdapterView)
    lazy val isNonLabel =
      if(!isA_?("android.widget.TextView") || isA_?("android.widget.EditText") || isA_?("android.widget.Button")) {
        true
      }else {
        val all = root.descendants
        val index = all.indexOf(node)
        var before:List[Option[AccessibilityNodeInfo]] = all.take(index).reverse.map(Some(_))
        var after:List[Option[AccessibilityNodeInfo]] = all.drop(index+1).map(Some(_))
        if(before.length < after.length)
          before ++= (before.length to after.length-1).map(v => None)
        if(after.length < before.length)
          after ++= (after.length to before.length-1).map(v => None)
        val pattern = before.zip(after).map(v => List(v._1, v._2)).flatten
        !pattern.exists(_.map(_.label == Some(node)).getOrElse(false))
      }
    Log.d("spielcheck", "isNonLabel: "+isNonLabel)
    lazy val isLeafOrTextualNonHtmlViewGroup =
      if(isA_?("android.view.ViewGroup"))
        children == Nil &&
        (!text.isEmpty || (node.getActions&ACTION_NEXT_HTML_ELEMENT) == 0)
      else
        node.children == Nil
    Log.d("spielcheck", "Leaf: "+isLeafOrTextualNonHtmlViewGroup)
    isNotDisabledWithNoText &&
    isNotAdapterView &&
    isNonLabel &&
    isLeafOrTextualNonHtmlViewGroup
  }

  private def findAccessibilityFocus(nodes:List[AccessibilityNodeInfo], from:Int, wrapped:Boolean = false):Option[AccessibilityNodeInfo] = {
    nodes.drop(from).find { n =>
      n.interestedInAccessibilityFocus
    }.orElse {
      if(wrapped)
        None
      else
        findAccessibilityFocus(nodes, 0, true)
    }
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
