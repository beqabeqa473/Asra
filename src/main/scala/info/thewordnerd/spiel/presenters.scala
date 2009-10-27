package info.thewordnerd.spiel.presenters

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.HashMap
import scala.collection.mutable.Map

import scripting.AccessibilityEventHandler

protected abstract class Presenter {

  val tts = TTS

  val handlers = new HashMap[Array[String], AccessibilityEventHandler]

  def apply(e:AccessibilityEvent):Boolean = {
    val k = new Array[String](2)
    k(0) = e.getClassName.toString
    k(1) = e.getPackageName.toString
    if(handlers.containsKey(k)) 
      handlers.get(k).run(e)
    else
      false
  }
}

// TODO: Ugly hack.
private object Presenter {
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
    if(Presenter.lastProcessed != null && Presenter.lastProcessed.getText.size == 1)
      tts.stop
    if(e.getClassName.toString.contains("Button")) {
      if(e.getText.size > 0)
        tts.speak(e.getText, false)
      tts.speak(SpielService().getString(R.string.button), false)
    } else if(e.getClassName.toString.contains("Search"))
      tts.speak(SpielService().getString(R.string.search), false)
    else if(e.getClassName.toString.contains("EditText")) {
      if(e.isPassword)
        tts.speak(SpielService().getString(R.string.password), false)
      tts.speak(SpielService().getString(R.string.editText), false)
    } else
      tts.speak(e.getText, false)
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
      tts.speak(SpielService().getString(R.string.menu), false)
    else if(!e.isFullScreen)
      tts.speak(e.getText, false)
    Presenter.lastProcessed = e
    true
  }
}
