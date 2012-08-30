package info.spielproject.spiel

import collection.JavaConversions._

import android.os.Build.VERSION
import android.view.accessibility.AccessibilityEvent

class RichEvent(e:AccessibilityEvent) {

  def utterances(addBlank:Boolean = true, stripBlanks:Boolean = false, guessLabelIfTextMissing:Boolean = false, guessLabelIfContentDescriptionMissing:Boolean = false, guessLabelIfTextShorterThan:Option[Int] = None, providedText:Option[String] = None):List[String] = {
    var rv = List[String]()
    val txt = Option(e.getText).map(_.toList).getOrElse(Nil).filterNot(_ == null).map(_.toString).mkString("\n") match {
      case "" => List()
      case v => v.split("\n").toList
    }
    val text = if(stripBlanks)
      txt.filterNot(_.trim.length == 0)
    else txt
    var blankAdded = false
    providedText.map(rv ::= _).getOrElse {
      if(text.size == 0 && e.getContentDescription == null && addBlank) {
        blankAdded = true
        rv ::= ""
      }
    }
    rv :::= text
    if(e.getContentDescription != null && e.getContentDescription != "")
      rv match {
        case hd :: Nil if(hd.toLowerCase.trim == e.getContentDescription.toString.toLowerCase.trim) =>
        case _ =>
          rv ::= e.getContentDescription.toString
      }
    def removeBlank() = if(blankAdded) rv = rv.tail
    if(VERSION.SDK_INT >= 14) {
      if(guessLabelIfTextMissing && e.getText.length == 0)
        rv :::= Option(e.getSource).flatMap(_.label).map(_.getText.toString).map { v =>
          removeBlank()
          List(v)
        }.getOrElse(Nil)
      else if(guessLabelIfContentDescriptionMissing && e.getContentDescription == null)
        rv :::= Option(e.getSource).flatMap(_.label).map(_.getText.toString).map { v =>
          removeBlank()
          List(v)
        }.getOrElse(Nil)
      else guessLabelIfTextShorterThan.foreach { v =>
        if(VERSION.SDK_INT >= 16 || text.length < v)
          rv :::= Option(e.getSource).flatMap(_.label).map(_.getText.toString).map { v =>
            removeBlank()
            List(v)
          }.getOrElse(Nil)
      }
    }
    rv
  }

  def utterances:List[String] = utterances()

}
