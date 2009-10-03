package info.thewordnerd.spiel.presenters

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.{List => JList}
import scala.collection.mutable.Map

import utils.TTS

abstract class Presenter {

  val tts = TTS

  val handlers = Map[Tuple2[CharSequence, CharSequence], ((AccessibilityEvent) => Boolean)]()

  def apply(e:AccessibilityEvent):Boolean = if(handlers.contains(e.getPackageName -> e.getClassName)) {
    val rv = handlers(e.getPackageName -> e.getClassName)(e)
    if(rv) Presenter.lastProcessed = e
    rv
  } else
    false

}

// TODO: Ugly hack.
object Presenter {
  var lastProcessed:AccessibilityEvent = null
}

object ViewClicked extends Presenter {
  override def apply(e:AccessibilityEvent):Boolean = {
    if(super.apply(e)) return true
    if(e.getText.size > 0)
      tts.speak(e.getText, true)
    Presenter.lastProcessed = e
    true
  }
}

object ViewFocused extends Presenter {
  override def apply(e:AccessibilityEvent):Boolean = {
    if(super.apply(e)) return true
    // TODO: Ugly hack. More than 1 line in prev event = dialog, don't stop.
    val flush = if(Presenter.lastProcessed != null && Presenter.lastProcessed.getText.size == 1) true else false
    tts.speak(e.getText, flush)
    if(e.getClassName.toString.contains("Button"))
      tts.speak("Button", false)
    else if(e.getClassName.toString.contains("Search"))
      tts.speak("Search", false)
    else if(e.getClassName.toString.contains("EditText")) {
      if(e.isPassword)
        tts.speak("Password", false)
      tts.speak("Edit text", false)
    }
    Presenter.lastProcessed = e
    true
  }
}

object ViewSelected extends Presenter {
  override def apply(e:AccessibilityEvent):Boolean = {
    if(super.apply(e)) return true
    if(e.getText.size > 0)
      tts.speak(e.getText, true)
    Presenter.lastProcessed = e
    true
  }
}

object ViewTextChanged extends Presenter {
  override def apply(e:AccessibilityEvent):Boolean = {
    if(super.apply(e)) return true
    if(e.getAddedCount > 0 || e.getRemovedCount > 0) {
      if(e.isPassword)
        tts.speak("*", true)
      else {
        val str1 = e.getText.toString
        val str2 = str1.substring(1, str1.size-1)
        if(e.getAddedCount > 0)
          tts.speak(str2.substring(e.getFromIndex, e.getFromIndex+e.getAddedCount), true)
        else if(e.getRemovedCount > 0)
          if(e.getFromIndex-e.getRemovedCount >= 0)
            tts.speak(str2.substring(e.getFromIndex-e.getRemovedCount, e.getFromIndex), true)
        }
      } else
        tts.speak(e.getText, false)
    Presenter.lastProcessed = e
    true
  }
}

object WindowStateChanged extends Presenter {
  override def apply(e:AccessibilityEvent):Boolean = {
    if(super.apply(e)) return true
    val cn = e.getClassName.toString
    if(cn.contains("menu"))
      tts.speak("Menu", false)
    else if(!e.isFullScreen)
      tts.speak(e.getText, false)
    Presenter.lastProcessed = e
    true
  }
}
