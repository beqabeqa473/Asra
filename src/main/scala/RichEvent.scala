package info.spielproject.spiel

import collection.JavaConversions._

import android.os.Build.VERSION
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import AccessibilityEvent._

object ContentChangeType extends Enumeration {
  val ContentDescription = Value(CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION)
  val Subtree = Value(CONTENT_CHANGE_TYPE_SUBTREE)
  val Text = Value(CONTENT_CHANGE_TYPE_TEXT)
  val Undefined = Value(CONTENT_CHANGE_TYPE_UNDEFINED)
}

class RichEvent(e:AccessibilityEvent) {

  lazy val records =
    (for(r <- 0 to e.getRecordCount-1)
      yield(e.getRecord(r))
    ).toList

  lazy val text = {
    val txt = Option(e.getText).map(_.toList).getOrElse(Nil)
    .filterNot(_ == null).map(_.toString)
    txt match {
      case Nil => source.flatMap(_.text).map(List(_)).getOrElse(Nil)
      case v => v
    }
  }

  lazy val contentDescription =
    Option(e.getContentDescription).map(_.toString).orElse(source.flatMap(_.contentDescription))

  lazy val source = Option(e.getSource)

  def utterances(addBlank:Boolean = true, stripBlanks:Boolean = false, guessLabelIfTextMissing:Boolean = false, guessLabelIfContentDescriptionMissing:Boolean = false, guessLabelIfTextShorterThan:Option[Int] = None, providedText:Option[String] = None):List[String] = {
    var rv = List[String]()
    val t = text.filterNot(_ == null).mkString("\n") match {
      case "" => List()
      case v => v.split("\n").toList
    }
    val txt:List[String] = if(stripBlanks)
      t.filterNot(_.trim.length == 0)
    else t
    var blankAdded = false
    providedText.map(rv ::= _).getOrElse {
      if(txt.size == 0 && e.getContentDescription == null && addBlank) {
        blankAdded = true
        rv ::= ""
      }
    }
    rv :::= txt
    contentDescription.foreach { c =>
      if(c != "" && rv.distinct != List(c))
        rv ::= c
    }
    def removeBlank() = if(blankAdded) rv = rv.tail
    if(guessLabelIfTextMissing && e.getText.length == 0)
      rv :::= source.flatMap(_.label).map(_.getText.toString).map { v =>
        removeBlank()
        List(v)
      }.getOrElse(Nil)
    else if(guessLabelIfContentDescriptionMissing && e.getContentDescription == null)
      rv :::= source.flatMap(_.label).map(_.getText.toString).map { v =>
        removeBlank()
        List(v)
      }.getOrElse(Nil)
    else guessLabelIfTextShorterThan.foreach { v =>
      if(VERSION.SDK_INT >= 16 || text.length < v)
        rv :::= source.flatMap(_.label).map(_.getText.toString).map { v =>
          removeBlank()
          List(v)
        }.getOrElse(Nil)
    }
    rv
  }

  def utterances:List[String] = utterances()

  def contentChangeTypes =
    ContentChangeType.values.map(_.id)
    .filter(v => (e.getContentChangeTypes & v) != 0)
    .map(ContentChangeType(_))

}
