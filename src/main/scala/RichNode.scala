package info.spielproject.spiel

import android.graphics.Rect
import android.os.Build.VERSION
import android.util.Log
import android.view.accessibility._
import AccessibilityNodeInfo._

case class RichNode(node:AccessibilityNodeInfo) {

  def text = Option(node.getText).map(_.toString)

  def nonEmptyText = text.filterNot(_.isEmpty)

  def nonEmptyText_? = nonEmptyText != None

  def contentDescription = Option(node.getContentDescription).map(_.toString)

  def nonEmptyContentDescription = contentDescription.filterNot(_.isEmpty)

  lazy val parent = node.getParent

  lazy val ancestors:List[AccessibilityNodeInfo] = Option(parent).map { p =>
    parent :: parent.ancestors
  }.getOrElse(Nil)

  lazy val root = {
    lazy val r = ancestors match {
      case Nil => node
      case v => v.reverse.head
    }
    if(VERSION.SDK_INT >= 16)
      SpielService.rootInActiveWindow.getOrElse(r)
    else
      r
  }

  def children =
    (for(i <- 0 to node.getChildCount-1) yield(node.getChild(i))).toList.filterNot(_ == null)

  def visibleChildren = children.filter(_.isVisibleToUser)

  def siblings = Option(parent).map(_.children).getOrElse(Nil)

  def visibleSiblings = siblings.filter(_.isVisibleToUser)

  def nextVisibleSibling = {
    val vs = visibleSiblings
    vs.indexOf(node) match {
      case -1 => None
      case v if(v == vs.length-1) => None
      case v => Some(vs(v+1))
    }
  }

  def prevVisibleSibling = {
    val vs = visibleSiblings
    vs.indexOf(node) match {
      case v if(v <= 0) => None
      case v => Some(vs(v-1))
    }
  }

  def firstVisibleLeaf:AccessibilityNodeInfo = visibleChildren match {
    case Nil => node
    case hd :: tl => hd.firstVisibleLeaf
  }

  def lastVisibleLeaf:AccessibilityNodeInfo = visibleChildren match {
    case Nil => node
    case hd :: Nil => hd.lastVisibleLeaf
    case hd :: tl => tl.last.lastVisibleLeaf
  }

  def descendants:List[AccessibilityNodeInfo] =children++children.map { c =>
    c.descendants
  }.flatten

  lazy val interactive_? =
    node.isCheckable || node.isClickable || node.isLongClickable || node.isFocusable

  lazy val rect = {
    val r = new Rect()
    node.getBoundsInScreen(r)
    r
  }

  def row = {
    val origin = new Rect(0, rect.top, Int.MaxValue, rect.bottom)
    val descendants = if(VERSION.SDK_INT >= 16)
      root.descendants.filter(_.isVisibleToUser)
    else root.descendants
    descendants.filter(_.rect.intersect(origin)).sortBy(_.rect.left)
  }

  lazy val classAncestors = {
    val nodeClass = utils.classForName(node.getClassName.toString, node.getPackageName.toString)
    nodeClass.map(utils.ancestors(_).map(_.getName)).getOrElse(Nil)
  }

  protected def isA_?(cls:String) =
    node.getClassName == cls || classAncestors.contains(cls)

  lazy val label = {
    def isTextView(n:AccessibilityNodeInfo) =
      n.isA_?("android.widget.TextView") && !n.isA_?("android.widget.EditText") && !n.isA_?("android.widget.Button")
    if(
      List("android.widget.CheckBox", "android.widget.EditText", "android.widget.ProgressBar", "android.widget.RadioButton", "android.widget.RatingBar")
      .exists(isA_?(_))
    ) {
      row.find(v => isTextView(v) && v.nonEmptyText_?)
      } .orElse {
        val descendants = if(VERSION.SDK_INT >= 16)
          root.descendants.filter(_.isVisibleToUser)
        else root.descendants
        descendants.filter(_.rect.bottom <= rect.top)
        .sortBy(_.rect.bottom)
        .reverse.headOption.filter { c =>
          isTextView(c) && !c.interactive_? && c.nonEmptyText_?
        }
      }
    else
      None
  }

  def supports_?(action:Int) =
    (node.getActions&action) != 0

  def nextAccessibilityFocus:Option[AccessibilityNodeInfo] = {
    nextVisibleSibling.map(_.firstVisibleLeaf)
    .orElse(parent.nextAccessibilityFocus)
    .orElse(Some(root.firstVisibleLeaf))
    .orElse(None)
  }

  def prevAccessibilityFocus:Option[AccessibilityNodeInfo] = {
    prevVisibleSibling.map(_.lastVisibleLeaf)
    .orElse(parent.prevAccessibilityFocus)
    .orElse(Some(root.lastVisibleLeaf))
    .orElse(None)
  }

}
