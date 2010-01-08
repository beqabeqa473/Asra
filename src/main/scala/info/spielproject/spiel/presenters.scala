package info.spielproject.spiel.presenters

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
        Log.d(this.toString, "No script found for event.")
        false
    }
  }

  protected def accessibleTextFor(e:AccessibilityEvent) = {
    var str = ""
    if(e.getContentDescription != null) {
      str += e.getContentDescription
      if(e.getText.size > 0)
        str += ": "
    }
    if(e.getText.size > 0)
      str += Util.toFlatString(e.getText)
    str
  }
}

// TODO: Ugly hack.
object Presenter {
  var lastProcessed:AccessibilityEvent = null
}

object NotificationStateChanged extends Presenter {
  override def apply(e:AccessibilityEvent):Boolean = {
    if(super.apply(e)) return true
    val at = accessibleTextFor(e)
    if(at.length > 0)
      tts.speak(at, true)
    Presenter.lastProcessed = e
    true
  }
}

object ViewClicked extends Presenter {
  override def apply(e:AccessibilityEvent):Boolean = {
    if(super.apply(e)) return true
    val at = accessibleTextFor(e)
    if(at.length > 0)
      tts.speak(at, true)
    Presenter.lastProcessed = e
    true
  }
}

object ViewFocused extends Presenter {
  override def apply(e:AccessibilityEvent):Boolean = {
    if(super.apply(e)) return true
    if(e.getClassName.toString.contains("TabWidget")) return true
    // Ugly hack to not interrupt dialog box speech when a control is focused.
    if(Presenter.lastProcessed != null && Presenter.lastProcessed.getClassName != null && !(Presenter.lastProcessed.getClassName.toString.contains("View") && !e.getClassName.toString.contains("View")))
      tts.stop
    val at = accessibleTextFor(e)
    if(e.getClassName.toString.contains("RadioButton")) {
      if(at.length > 0)
        tts.speak(at, false)
      tts.speak(SpielService().getString(R.string.radioButton), false)
    } else if(e.getClassName.toString.contains("Button")) {
      if(at.length > 0)
        tts.speak(at, false)
      tts.speak(SpielService().getString(R.string.button), false)
    } else if(e.getClassName.toString.contains("Search")) {
      tts.speak(at, false)
      tts.speak(SpielService().getString(R.string.search), false)
    } else if(e.getClassName.toString.contains("EditText")) {
      if(e.isPassword)
        tts.speak(SpielService().getString(R.string.password), false)
      else
        tts.speak(at, false)
      tts.speak(SpielService().getString(R.string.editText), false)
    } else
      tts.speak(at, false)
    Presenter.lastProcessed = e
    true
  }
}

object ViewSelected extends Presenter {
  override def apply(e:AccessibilityEvent):Boolean = {
    if(super.apply(e)) return true
    val at = accessibleTextFor(e)
    if(at.length > 0)
      tts.speak(at, true)
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
    else
      tts.speak(accessibleTextFor(e), false)
    Presenter.lastProcessed = e
    true
  }
}
