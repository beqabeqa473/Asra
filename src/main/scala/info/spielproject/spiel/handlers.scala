package info.spielproject.spiel.handlers

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import collection.JavaConversions._
import collection.mutable.Map

import org.mozilla.javascript.{Context, Function}

import info.spielproject.spiel._
import scripting.Scripter
import tts.TTS

abstract class Callback {
  def apply(e:AccessibilityEvent):Boolean
}

class NativeCallback(f:AccessibilityEvent => Boolean) extends Callback{
  def apply(e:AccessibilityEvent) = f(e)
}

class RhinoCallback(f:Function) extends Callback {
  def apply(e:AccessibilityEvent):Boolean = {
    Context.enter
    var args = new Array[Object](1)
    args(0) = e
    try {
      Context.toBoolean(f.call(Scripter.context, Scripter.scope, Scripter.scope, args))
    } catch {
      case e => Log.e("spiel", "Error running script: "+e.getMessage)
      false
    }
  }

}

object Handler {
  private var handlers = Map[(String, String), Handler]()

  var registered = List(
    AlertDialog,
    Button,
    CheckBox,
    Dialog,
    EditText,
    ImageButton,
    Menu,
    RadioButton,
    SearchBox,
    Tab,
    Default
  )

  private var myNextShouldNotInterrupt = false
  private var nextShouldNotInterruptCalled = false

  def nextShouldNotInterrupt = {
    Log.d("spiel", "Next accessibility event should not interrupt speech.")
    nextShouldNotInterruptCalled = true
    myNextShouldNotInterrupt = true
  }

  private var service:SpielService = null
  def apply(s:SpielService) {
    service = s
    handlers.foreach { v => v._2.init }
  }

  def handle(e:AccessibilityEvent) {
    Log.d("spiel", "Event "+e.toString)
    nextShouldNotInterruptCalled = false
    var continue = true 
    var alreadyCalled = List[Handler]()

    def dispatchTo(pkg:String, cls:String):Boolean = handlers.get(pkg -> cls) match {
      case Some(h) =>
        if(alreadyCalled.contains(h)) {
          Log.d("spiel", "Already called this handler, skipping.")
          true
        } else {
          Log.d("spiel", "Dispatching to "+pkg+":"+cls)
          alreadyCalled ::= h
          !h(e)
        }
      case None =>
        Log.d("spiel", "No exact match for "+pkg+":"+cls+". Continuing.")
        true
    }

    if(continue) {
      Log.d("spiel", "Performing exact-match dispatch check.")
      continue = dispatchTo(e.getPackageName.toString, e.getClassName.toString)
    }

    if(continue) {
      Log.d("spiel", "Performing class-only-match dispatch check.")
      continue = dispatchTo("", e.getClassName.toString)
    }

    try {
      if(continue) {
        Log.d("spiel", "Performing subclass-match dispatch.")
        val testClass = service.getClassLoader.loadClass(e.getClassName.toString)
        handlers.foreach { v =>
          if(v._1._2 != "" && continue) {
            try {
              val testClass2 = service.getClassLoader.loadClass(v._1._2)
              //Log.d(this.getClass.toString, testClass2.toString+".isAssignableFrom("+testClass+"): "+testClass2.isAssignableFrom(testClass))
              if(
                testClass2.isAssignableFrom(testClass) && 
                (v._1._1 == "" || e.getPackageName == v._1._1)
              )
                continue = continue && dispatchTo(v._1._1, v._1._2)
            } catch {
              case _ =>
            }
          }
        }
      }
    } catch {
      case exception => Log.d("spiel", exception.toString)
    }

    if(continue) dispatchTo("", "")
    if(!nextShouldNotInterruptCalled)
      myNextShouldNotInterrupt = false
  }

  def speak(text:String, interrupt:Boolean):Unit = TTS.speak(text, interrupt)
  def speak(text:String):Unit = speak(text, !myNextShouldNotInterrupt)
  def speak(list:java.util.List[CharSequence], interrupt:Boolean):Unit = TTS.speak(list.map(_.toString), interrupt)
  def speak(list:java.util.List[CharSequence]):Unit = speak(list, !myNextShouldNotInterrupt)

}

class Handler(pkg:String, cls:String) {

  def this() = this("", "")
  def this(c:String) = this("", c)

  import AccessibilityEvent._
  import Handler._

  handlers(pkg -> cls) = this

  def init {
    Log.d("spiel", "Initializing handler for "+pkg+":"+cls)
  }

  def speak(text:String, interrupt:Boolean):Unit = Handler.speak(text, interrupt)
  def speak(text:String):Unit = Handler.speak(text, !myNextShouldNotInterrupt)
  def speak(list:java.util.List[CharSequence], interrupt:Boolean):Unit = Handler.speak(list, interrupt)
  def speak(list:java.util.List[CharSequence]):Unit = Handler.speak(list)

  def nextShouldNotInterrupt = Handler.nextShouldNotInterrupt

  implicit def toNativeCallback(f:AccessibilityEvent => Boolean):NativeCallback = new NativeCallback(f)

  implicit def toRhinoCallback(f:Function):RhinoCallback = new RhinoCallback(f)

  private var notificationStateChanged:Callback = null
  protected def onNotificationStateChanged(c:Callback) = notificationStateChanged = c

  private var viewClicked:Callback = null
  protected def onViewClicked(c:Callback) = viewClicked = c

  private var viewFocused:Callback = null
  protected def onViewFocused(c:Callback) = viewFocused = c

  private var viewLongClicked:Callback = null
  protected def onViewLongClicked(c:Callback) = viewLongClicked = c

  private var viewSelected:Callback = null
  protected def onViewSelected(c:Callback) = viewSelected = c

  private var viewTextChanged:Callback = null
  protected def onViewTextChanged(c:Callback) = viewTextChanged = c

  private var windowStateChanged:Callback = null
  protected def onWindowStateChanged(c:Callback) = windowStateChanged = c

  private var default:Callback = null
  protected def byDefault(c:Callback) = default = c

  protected def textFor(e:AccessibilityEvent) = {
    var str = ""
    if(e.getContentDescription != null) {
      str += e.getContentDescription
      if(e.getText.size > 0)
        str += ": "
    }
    if(e.getText.size > 0)
      str += ("" /: e.getText) (_ + _)
    str
  }

  def apply(e:AccessibilityEvent):Boolean = {

    def dispatchTo(callback:Callback):Boolean = {
      if(callback != null) callback(e) else false
    }

    val fallback = e.getEventType match {
      case TYPE_NOTIFICATION_STATE_CHANGED => dispatchTo(notificationStateChanged)
      case TYPE_VIEW_CLICKED => dispatchTo(viewClicked)
      case TYPE_VIEW_FOCUSED => dispatchTo(viewFocused)
      case TYPE_VIEW_LONG_CLICKED => dispatchTo(viewLongClicked)
      case TYPE_VIEW_SELECTED => dispatchTo(viewSelected)
      case TYPE_VIEW_TEXT_CHANGED => dispatchTo(viewTextChanged)
      case TYPE_WINDOW_STATE_CHANGED => dispatchTo(windowStateChanged)
      case _ => false
    }

    if(!fallback)
      dispatchTo(default)
    else fallback
  }

}

object AlertDialog extends Handler("android.app.AlertDialog") {
  onWindowStateChanged { e:AccessibilityEvent =>
    speak("Alert!" +: e.getText, true)
    nextShouldNotInterrupt
    true
  }
}

trait GenericButtonHandler extends Handler {
  onViewFocused { e:AccessibilityEvent =>
    speak(textFor(e)+": button")
    true
  }
}

object Button extends Handler("android.widget.Button") with GenericButtonHandler

object CheckBox extends Handler("android.widget.CheckBox") {
  onViewClicked { e:AccessibilityEvent =>
    if(e.isChecked)
      speak("checked")
    else
      speak("not checked")
    true
  }

  onViewFocused { e:AccessibilityEvent =>
    speak(textFor(e)+" checkbox")
    true
  }

}

object Dialog extends Handler("android.app.Dialog") {
  import collection.JavaConversions._
  onWindowStateChanged { e:AccessibilityEvent =>
    speak(e.getText, true)
    nextShouldNotInterrupt
    true
  }
}

object EditText extends Handler("android.widget.EditText") {

  onViewFocused { e:AccessibilityEvent =>
    if(e.getCurrentItemIndex != -1) {
      if(!e.isPassword) {
        speak(textFor(e), false)
        speak("edit text", false)
      } else
        speak(textFor(e)+": password")
    }
    true
  }

  onViewTextChanged { e:AccessibilityEvent =>
    if(e.getAddedCount > 0 || e.getRemovedCount > 0) {
      if(e.isPassword)
        speak("*", true)
      else
        if(e.getAddedCount > 0)
          speak(("" /: e.getText) (_ + _).substring(e.getFromIndex, e.getFromIndex+e.getAddedCount), true)
        else if(e.getRemovedCount > 0)
          speak(e.getBeforeText.toString.substring(e.getFromIndex, e.getFromIndex+e.getRemovedCount), true)
      else
        speak(e.getText, false)
    }
    true
  }

}

object ImageButton extends Handler("android.widget.ImageButton") with GenericButtonHandler

object Menu extends Handler("com.android.internal.view.menu.MenuView") {

  onViewSelected { e:AccessibilityEvent =>
    Log.d("spiel", "onViewSelected for menu")
    speak(textFor(e))
    true
  }

  onWindowStateChanged { e:AccessibilityEvent =>
    if(e.getCurrentItemIndex == -1) {
      speak("menu")
      nextShouldNotInterrupt
    }
    true
  }

}

object RadioButton extends Handler("android.widget.RadioButton") {

  onViewClicked { e:AccessibilityEvent =>
    if(e.isChecked)
      speak("checked")
    else
      speak("not checked")
    true
  }

  onViewFocused { e:AccessibilityEvent =>
    speak(textFor(e)+": radio button")
    true
  }

}

object SearchBox extends Handler("android.app.SearchDialog$SearchAutoComplete") {

  onViewFocused { e:AccessibilityEvent =>
    speak(textFor(e))
    speak("search text", false)
    true
  }

}

object Tab extends Handler("android.widget.RelativeLayout") {
  onViewFocused { e:AccessibilityEvent =>
    speak(textFor(e)+": tab", true)
    true
  }
}

object Default extends Handler {

  onNotificationStateChanged { e:AccessibilityEvent =>
    Log.d("spiel", "onNotificationStateChanged")
    false
  }

  onViewClicked { e:AccessibilityEvent =>
    Log.d("spiel", "onViewClicked")
    true
  }

  onViewFocused { e:AccessibilityEvent =>
    Log.d("spiel", "onViewFocused")
    if(e.isFullScreen || (e.getItemCount == 0 && e.getCurrentItemIndex == -1))
      true
    else
      false
  }

  onViewLongClicked { e:AccessibilityEvent =>
    Log.d("spiel", "onViewLongClicked")
    true
  }

  onViewSelected { e:AccessibilityEvent =>
    Log.d("spiel", "onViewSelected")
    false
  }

  onViewTextChanged { e:AccessibilityEvent =>
    Log.d("spiel", "onViewTextChanged")
    false
  }

  onWindowStateChanged { e:AccessibilityEvent =>
    Log.d("spiel", "onWindowStateChanged")
    // Needed because menus send their contents as a non-fullscreen 
    // onWindowStateChanged event and we don't want to read an entire menu 
    // when it focuses.
    if(!e.isFullScreen)
      true
    else {
      nextShouldNotInterrupt
      false
    }
  }

  byDefault { e:AccessibilityEvent =>
    Log.d("spiel", "Unhandled event: "+e.toString)
    speak(textFor(e))
    true
  }

}
