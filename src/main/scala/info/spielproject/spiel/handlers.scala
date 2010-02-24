package info.spielproject.spiel.handlers

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import scala.collection.mutable.Map

import org.mozilla.javascript.{Context, Function}

import scripting.Scripter

abstract class Callback {
  def apply(e:AccessibilityEvent):Boolean
}

class NativeCallback(f:AccessibilityEvent => Boolean) extends Callback{
  def apply(e:AccessibilityEvent) = f(e)
}

class RhinoCallback(f:Function) extends Callback {
  def apply(e:AccessibilityEvent):Boolean = {
    var args = new Array[Object](1)
    args(0) = e
    Context.toBoolean(f.call(Scripter.context, Scripter.scope, Scripter.scope, args))
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
    Tab,
    Default
  )

  private var myNextShouldNotInterrupt = false
  private var nextShouldNotInterruptCalled = false

  def nextShouldNotInterrupt = {
    Log.d(this.getClass.toString, "Next accessibility event should not interrupt speech.")
    nextShouldNotInterruptCalled = true
    myNextShouldNotInterrupt = true
  }

  private var service:SpielService = null
  def apply(s:SpielService) {
    service = s
  }

  def handle(e:AccessibilityEvent) {
    nextShouldNotInterruptCalled = false
    var continue = true 
    var alreadyCalled = List[Handler]()

    def dispatchTo(pkg:String, cls:String):Boolean = handlers.get(pkg -> cls) match {
      case Some(h) =>
        if(alreadyCalled.contains(h))
          true
        else {
          alreadyCalled ::= h
          !h(e)
        }
      case None => true
    }

    if(continue)
      continue = dispatchTo(e.getPackageName.toString, e.getClassName.toString)

    if(continue)
      continue = dispatchTo("", e.getClassName.toString)

    try {
      if(continue) {
        val testClass = service.getClassLoader.loadClass(e.getClassName.toString)
        handlers.foreach { v =>
          if(v._1._2 != "" && continue) {
            try {
              val testClass2 = service.getClassLoader.loadClass(v._1._2)
              //Log.d(this.getClass.toString, testClass2.toString+".isAssignableFrom("+testClass+"): "+testClass2.isAssignableFrom(testClass))
              if(testClass2.isAssignableFrom(testClass))
                continue = continue && dispatchTo(v._1._1, v._1._2)
            } catch {
              case _ =>
            }
          }
        }
      }
    } catch {
      case exception => Log.d(this.getClass.toString, exception.toString)
    }

    if(continue) dispatchTo("", "")
    if(!nextShouldNotInterruptCalled)
      myNextShouldNotInterrupt = false
  }

  def speak(text:String, interrupt:Boolean):Unit = TTS.speak(text, interrupt)
  def speak(text:String):Unit = speak(text, !myNextShouldNotInterrupt)

}

class Handler(pkg:String, cls:String) {

  def this() = this("", "")
  def this(c:String) = this("", c)

  import AccessibilityEvent._
  import Handler._

  handlers(pkg -> cls) = this

  def init {
  }

  def speak(text:String, interrupt:Boolean):Unit = Handler.speak(text, interrupt)
  def speak(text:String):Unit = Handler.speak(text)

  def nextShouldNotInterrupt = Handler.nextShouldNotInterrupt

  implicit def toNativeCallback(f:AccessibilityEvent => Boolean):NativeCallback = new NativeCallback(f)

  implicit def toRhinoCallback(f:Function):RhinoCallback = new RhinoCallback(f)

  private var notificationStateChanged:Callback = null
  protected def onNotificationStateChanged(c:Callback) = notificationStateChanged = c

  private var viewClicked:Callback = null
  protected def onViewClicked(c:Callback) = viewClicked = c

  private var viewFocused:Callback = null
  protected def onViewFocused(c:Callback) = viewFocused = c

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
      str += Util.toFlatString(e.getText)
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
    speak("Alert! "+Util.toFlatString(e.getText), true)
    nextShouldNotInterrupt
    true
  }
}

trait GenericButtonHandler extends Handler {
  onViewFocused { e:AccessibilityEvent =>
    Log.d(this.getClass.toString, "Button: "+e)
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
  onWindowStateChanged { e:AccessibilityEvent =>
    speak(Util.toFlatString(e.getText), true)
    nextShouldNotInterrupt
    true
  }
}

object EditText extends Handler("android.widget.EditText") {

  onViewFocused { e:AccessibilityEvent =>
    Log.d(this.getClass.toString, "Edit text: "+e)
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
    Log.d(this.getClass.toString, "change: "+e)
    if(e.getAddedCount > 0 || e.getRemovedCount > 0) {
      if(e.isPassword)
        speak("*", true)
      else
        if(e.getAddedCount > 0)
          speak(Util.toFlatString(e.getText).substring(e.getFromIndex, e.getFromIndex+e.getAddedCount), true)
        else if(e.getRemovedCount > 0)
          speak(e.getBeforeText.toString.substring(e.getFromIndex, e.getFromIndex+e.getRemovedCount), true)
      else
        speak(Util.toFlatString(e.getText), false)
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

object Tab extends Handler("android.widget.RelativeLayout") {
  onViewFocused { e:AccessibilityEvent =>
    Log.d("tab", e.toString)
    speak(textFor(e)+": tab", true)
    true
  }
}

object Default extends Handler {

  onNotificationStateChanged { e:AccessibilityEvent =>
    Log.d(this.getClass.toString, "onNotificationStateChanged")
    false
  }

  onViewClicked { e:AccessibilityEvent =>
    Log.d(this.getClass.toString, "onViewClicked")
    true
  }

  onViewFocused { e:AccessibilityEvent =>
    Log.d(this.getClass.toString, "onViewFocused")
    if(e.isFullScreen || (e.getItemCount == 0 && e.getCurrentItemIndex == -1))
      true
    else
      false
  }

  onViewSelected { e:AccessibilityEvent =>
    Log.d(this.getClass.toString, "onViewSelected")
    false
  }

  onViewTextChanged { e:AccessibilityEvent =>
    Log.d(this.getClass.toString, "onViewTextChanged")
    false
  }

  onWindowStateChanged { e:AccessibilityEvent =>
    Log.d(this.getClass.toString, "onWindowStateChanged")
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
    Log.d(this.getClass.toString, "Unhandled event: "+e.toString)
    speak(textFor(e))
    true
  }

}
