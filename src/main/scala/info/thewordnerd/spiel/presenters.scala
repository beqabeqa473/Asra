package info.thewordnerd.spiel.presenters

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import scala.collection.mutable.Map

import org.mozilla.javascript.{Context, Function}

import scripting.Scripter

protected abstract class Presenter {

  val tts = TTS

  val handlers = Map[Tuple2[String, String], Function]()

  def registerHandler(pkg:String, cls:String, f:Function) {
    Log.d(this.toString, "Registering handler for "+(pkg -> cls))
    handlers(pkg -> cls) = f
  }

  def apply(e:AccessibilityEvent):Boolean = {
    Log.d(this.toString, e.toString)
    val pkg = e.getPackageName.toString
    val cls = e.getClassName.toString
    handlers.get(pkg -> cls) match {
      case Some(fn) =>
        var args = new Array[Object](1)
        args(0) = e
        Context.toBoolean(fn.call(Scripter.context, Scripter.scope, Scripter.scope, args))
      case None =>
        Log.d(this.toString, "No match found for event.")
        false
    }
  }
}

// TODO: Ugly hack.
private object Presenter {
  var lastProcessed:AccessibilityEvent = null
}

object NotificationStateChanged extends Presenter {
  override def apply(e:AccessibilityEvent):Boolean = {
    if(super.apply(e)) return true
    if(e.getText.size > 0)
      tts.speak(e.getText, true)
    Presenter.lastProcessed = e
    true
  }
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
