package info.spielproject.spiel.handlers

import actors.Actor
import Actor._

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import collection.JavaConversions._
import collection.mutable.Map

import info.spielproject.spiel._

abstract class Callback {
  def apply(e:AccessibilityEvent):Boolean
}

class NativeCallback(f:AccessibilityEvent => Boolean) extends Callback{
  def apply(e:AccessibilityEvent) = f(e)
}

object Handler extends Actor {
  private var handlers = Map[(String, String), Handler]()

  private var myNextShouldNotInterrupt = false
  def shouldNextInterrupt = !myNextShouldNotInterrupt

  private var nextShouldNotInterruptCalled = false

  def nextShouldNotInterrupt = {
    Log.d("spiel", "Next accessibility event should not interrupt speech.")
    nextShouldNotInterruptCalled = true
    myNextShouldNotInterrupt = true
  }

  private var service:SpielService = null

  def apply(s:SpielService) {
    start
    service = s
    val h = new Handlers
    h.getClass.getDeclaredClasses.foreach { cls =>
      try {
        val cons = cls.getConstructor(classOf[Handlers])
        if(cons != null)
          cons.newInstance(h)
      } catch {
        case e => Log.e("spiel", e.getMessage+" while initializing handler for "+cls.getName)
      }
    }
  }

  def onDestroy {
    exit
  }

  private val timeout = 200

  case class Item(event:AccessibilityEvent, presentationTime:Long)

  def handle(event:AccessibilityEvent) = {
    this ! Item(event, System.currentTimeMillis+timeout)
  }

  def act = react {
    case i:Item =>
      val delay = i.presentationTime-System.currentTimeMillis
      if(delay > 0) Thread.sleep(delay)
      actWithLookahead(i)
  }

  import AccessibilityEvent._

  private def actWithLookahead(i:Item):Unit = reactWithin(0) {
    case actors.TIMEOUT =>
      process(i.event)
      act
    case i2:Item =>
      if(i.event.getEventType != TYPE_NOTIFICATION_STATE_CHANGED && i2.event.getEventType == i.event.getEventType)  {
        TTS.stop
        actWithLookahead(i2)
      } else {
        process(i.event)
        actWithLookahead(i2)
      }
  }

  private def process(e:AccessibilityEvent) {
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
      case _ =>
    }

    if(continue) dispatchTo("", "")
    if(!nextShouldNotInterruptCalled)
      myNextShouldNotInterrupt = false

  }

  val dispatchers = Map(
    TYPE_NOTIFICATION_STATE_CHANGED -> "notificationStateChanged",
    TYPE_VIEW_CLICKED -> "viewClicked",
    TYPE_VIEW_FOCUSED -> "viewFocused",
    TYPE_VIEW_LONG_CLICKED -> "viewLongClicked",
    TYPE_VIEW_SELECTED -> "viewSelected",
    TYPE_VIEW_TEXT_CHANGED -> "viewTextChanged",
    TYPE_WINDOW_STATE_CHANGED -> "windowStateChanged"
  )

}

class Handler(pkg:String, cls:String) {

  def this() = this("", "")
  def this(c:String) = this("", c)

  import Handler._

  handlers(pkg -> cls) = this

  def speak(text:String, interrupt:Boolean):Unit = TTS.speak(text, interrupt)
  def speak(text:String):Unit = TTS.speak(text, !myNextShouldNotInterrupt)
  def speak(list:java.util.List[CharSequence], interrupt:Boolean):Unit = TTS.speak(list.map(_.toString).toList, interrupt)
  def speak(list:java.util.List[CharSequence]):Unit = TTS.speak(list.map(_.toString).toList, !myNextShouldNotInterrupt)
  def speak(list:List[String], interrupt:Boolean):Unit = TTS.speak(list, interrupt)
  def speak(list:List[String]):Unit = TTS.speak(list, !myNextShouldNotInterrupt)

  def nextShouldNotInterrupt = Handler.nextShouldNotInterrupt

  implicit def toNativeCallback(f:AccessibilityEvent => Boolean):NativeCallback = new NativeCallback(f)

  val dispatches = collection.mutable.Map[String, Callback]()

  protected def onNotificationStateChanged(c:Callback) = dispatches("notificationStateChanged") = c

  protected def onViewClicked(c:Callback) = dispatches("viewClicked") = c

  protected def onViewFocused(c:Callback) = dispatches("viewFocused") = c

  protected def onViewLongClicked(c:Callback) = dispatches("viewLongClicked") = c

  protected def onViewSelected(c:Callback) = dispatches("viewSelected") = c

  protected def onViewTextChanged(c:Callback) = dispatches("viewTextChanged") = c

  protected def onWindowStateChanged(c:Callback) = dispatches("windowStateChanged") = c

  protected def byDefault(c:Callback) = dispatches("default") = c

  protected def utterancesFor(e:AccessibilityEvent) = {
    val rv:collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer[String]()
    if(e.getContentDescription != null)
      rv += e.getContentDescription.toString
    if(e.getText.isEmpty)
      rv += ""
    e.getText.foreach { text =>
      if(text != null) rv += text.toString
    }
    rv.toList
  }

  def apply(e:AccessibilityEvent):Boolean = {

    def dispatchTo(callback:String):Boolean = dispatches.get(callback) match {
      case Some(h) => h(e)
      case None => false
    }

    val fallback = dispatchers.get(e.getEventType) match {
      case Some(d) => dispatchTo(d)
      case None => false
    }

    if(!fallback)
      dispatchTo("default")
    else fallback
  }

}

trait GenericButtonHandler extends Handler {
  onViewFocused { e:AccessibilityEvent =>
    speak(utterancesFor(e)++("button" :: Nil))
    true
  }
}

class Handlers {

  class AlertDialog extends Handler("android.app.AlertDialog") {
    onWindowStateChanged { e:AccessibilityEvent =>
      speak("Alert!" +=: e.getText, true)
      nextShouldNotInterrupt
      true
    }
  }

  class Button extends Handler("android.widget.Button") with GenericButtonHandler

  class CheckBox extends Handler("android.widget.CheckBox") {
    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak("checked")
      else
        speak("not checked")
      true
    }

    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e)++("checkbox" :: Nil))
      true
    }

  }

  class Dialog extends Handler("android.app.Dialog") {
    onWindowStateChanged { e:AccessibilityEvent =>
      speak(e.getText, true)
      nextShouldNotInterrupt
      true
    }
  }

  class EditText extends Handler("android.widget.EditText") {
    onViewFocused { e:AccessibilityEvent =>
      if(e.getCurrentItemIndex != -1) {
        if(!e.isPassword) {
          speak(utterancesFor(e), false)
          speak("edit text", false)
        } else
          speak(utterancesFor(e)++("password" :: Nil))
      }
      true
    }
  } 

  class ImageButton extends Handler("android.widget.ImageButton") with GenericButtonHandler

  class Menu extends Handler("com.android.internal.view.menu.MenuView") {

    onViewSelected { e:AccessibilityEvent =>
      speak(utterancesFor(e))
      true
    }

    onWindowStateChanged { e:AccessibilityEvent =>
      if(e.getCurrentItemIndex == -1) {
        speak("menu", true)
        nextShouldNotInterrupt
      }
      true
    }

  }

  class RadioButton extends Handler("android.widget.RadioButton") {

    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak("checked")
      else
        speak("not checked")
      true
    }

    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e)++("radio button" :: Nil))
      true
    }

  }

  class SearchBox extends Handler("android.app.SearchDialog$SearchAutoComplete") {
    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e))
      speak("search text", false)
      true
    }
  }

  class Tab extends Handler("android.widget.RelativeLayout") {
    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e)++("tab" :: Nil), true)
      true
    }
  }

  class Default extends Handler {

    onNotificationStateChanged { e:AccessibilityEvent =>
      Log.d("spiel", "onNotificationStateChanged")
      if(e.getText.size > 0)
        speak(utterancesFor(e), false)
      true
    }

    onViewClicked { e:AccessibilityEvent =>
      Log.d("spiel", "onViewClicked")
      true
    }

    onViewFocused { e:AccessibilityEvent =>
      Log.d("spiel", "onViewFocused")
      if(e.isFullScreen || (e.getItemCount == 0 && e.getCurrentItemIndex == -1))
        true
      else {
        if(Handler.shouldNextInterrupt) TTS.stop
        //if(utterancesFor(e).length > 0)
          speak(utterancesFor(e))
        true
      }
    }

    onViewLongClicked { e:AccessibilityEvent =>
      Log.d("spiel", "onViewLongClicked")
      true
    }

    onViewSelected { e:AccessibilityEvent =>
      Log.d("spiel", "onViewSelected")
      if(utterancesFor(e).length > 0)
        speak(utterancesFor(e))
      true
    }

    onViewTextChanged { e:AccessibilityEvent =>
      Log.d("spiel", "onViewTextChanged")
      if(e.getAddedCount > 0 || e.getRemovedCount > 0) {
        if(e.isPassword)
          speak("*", true)
        else
          if(e.getAddedCount > 0)
            speak(("" /: e.getText) (_ + _).substring(e.getFromIndex,   e.getFromIndex+e.getAddedCount), true)
          else if(e.getRemovedCount > 0)
            speak(e.getBeforeText.toString.substring(e.getFromIndex, e.getFromIndex+e.getRemovedCount), true)
        else
          speak(e.getText, false)
      }
      true
    }

    onWindowStateChanged { e:AccessibilityEvent =>
      Log.d("spiel", "onWindowStateChanged")
      // Needed because menus send their contents as a non-fullscreen 
      // onWindowStateChanged event and we don't want to read an entire menu 
      // when it focuses.
      if(!e.isFullScreen)
        true
      else {
        TTS.speak(utterancesFor(e), true)
        nextShouldNotInterrupt
        true
      }
    }

    byDefault { e:AccessibilityEvent =>
      Log.d("spiel", "Unhandled event: "+e.toString)
      speak(utterancesFor(e))
      true
    }

  }
}
