package info.spielproject.spiel
package handlers

import actors.Actor
import Actor._

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import collection.JavaConversions._
import collection.mutable.Map

/**
 * Represents code that is called when a specific <code>AccessibilityEvent</code> is received.
*/

abstract class Callback {

  /**
   * Called with an <code>AccessibilityEvent</code>.
   * @return <code>true</code> if event processing should stop, false otherwise
  */

  def apply(e:AccessibilityEvent):Boolean

}

/**
 * Represents a callback that is written in native Scala code.
*/

class NativeCallback(f:AccessibilityEvent => Boolean) extends Callback{
  def apply(e:AccessibilityEvent) = f(e)
}

/**
 * Wrapper that pretty-prints <code>AccessibilityEvent</code>s.
*/

class PrettyAccessibilityEvent(e:AccessibilityEvent) {
  override lazy val toString = {
    val eventType = Handler.dispatchers(e.getEventType)
    val text = if(e.getText.length == 0)
      "no text: " 
    else
      "Text: "+e.getText.foldLeft("") ((acc, v) => acc+" "+v)+": "
    val contentDescription = if(e.getContentDescription != null)
      e.getContentDescription+": "
    else ""
    val className = e.getClassName
    val packageName = e.getPackageName
    eventType+": "+text+contentDescription+" class: "+className+": package: "+packageName
  }
}

/**
 * Singleton that stores the 50 most recent <code>AccessibilityEvent</code> objects for review.
*/

object EventReviewQueue extends collection.mutable.Queue[PrettyAccessibilityEvent] {

  /**
   * Adds an event to the queue, stripping excess items if necessary.
  */

  def apply(e:PrettyAccessibilityEvent) = {
    enqueue(e)
    if(length > 50) drop(length-50)
  }

}

/**
 * Companion for <code>Handler</code> class.
*/

object Handler extends Actor {

  // Maps (packageName, className) tuples to specific Handler instances.
  private var handlers = Map[(String, String), Handler]()

  /**
   * Unregisters the given <code>Handler</code>.
  */

  def unregisterHandler(h:Handler) = {
    handlers = handlers.filter(v => v._2 != h)
  }

  // Track and report state of whether next AccessibilityEvent should interrupt speech.
  private var myNextShouldNotInterrupt = false
  def shouldNextInterrupt = !myNextShouldNotInterrupt

  private var nextShouldNotInterruptCalled = false

  /**
   * In some instances, speech for the next <code>AccessibilityEvent</code> 
   * shouldn't interrupt. Calling this method from a handler indicates this 
   * to be the case.
  */

  def nextShouldNotInterrupt = {
    Log.d("spiel", "Next accessibility event should not interrupt speech.")
    nextShouldNotInterruptCalled = true
    myNextShouldNotInterrupt = true
  }

  private[spiel] var service:SpielService = null

  /**
   * Initialize handlers for the given <code>SpielService</code>.
  */

  def apply(s:SpielService) {
    start
    service = s
    // By iterating through the members of this class, we can add handlers 
    // without manual registration.
    val h = new Handlers
    h.getClass.getDeclaredClasses.foreach { cls =>
      try {
        val cons = cls.getConstructor(classOf[Handlers])
        if(cons != null)
          cons.newInstance(h)
      } catch { case _ => }
    }
  }

  def onDestroy {
    exit
  }

  // How long to wait before processing a given AccessibilityEvent.
  private val timeout = 200

  // Record an event and the time that event is to be presented. This helps 
  // in instances where we receive lots of events and don't necessarily want 
  // to process every single one.

  private case class Item(event:AccessibilityEvent, presentationTime:Long)

  /**
   * Handle a given <code>AccessibilityEvent</code> by delegating to an 
   * actor on another thread.
  */

  def handle(event:AccessibilityEvent) = {
    this ! Item(event, System.currentTimeMillis+timeout)
  }

  // This gets fun. First we receive the message passed to an actor, 
  // immediately passing it off to another method.

  def act = react {
    case i:Item =>
      // How long should we wait before presenting this event?
      val delay = i.presentationTime-System.currentTimeMillis
      // If we must delay then do so.
      if(delay > 0) Thread.sleep(delay)
      actWithLookahead(i)
  }

  import AccessibilityEvent._

  private def actWithLookahead(i:Item):Unit = {

    // Sometimes we want to discard events--if someone is scrolling through 
    // a list too quickly for us to speak, for instance. In those instances, 
    // we want to throw away earlier events in favor of those representing 
    // the latest item to which you've scrolled. It's a complex boolean 
    // expression which I won't document in comments should it need to change later.
    def shouldDiscardOld(newEvent:AccessibilityEvent, oldEvent:AccessibilityEvent) = {
      (
        oldEvent.getEventType == TYPE_NOTIFICATION_STATE_CHANGED &&
        newEvent.getEventType == TYPE_NOTIFICATION_STATE_CHANGED &&
        oldEvent.getText == newEvent.getText
      ) || (
        oldEvent.getEventType != TYPE_NOTIFICATION_STATE_CHANGED &&
        newEvent.getEventType == oldEvent.getEventType
      )
    }

    // We've been handed an old event and have waited until its presentation 
    // time. Now let's see if there is another event available. We wait for 
    // a short interval and, if we receive a new event, we determine if it 
    // can replace the old event and continue. If we have no new events and 
    // time out, then we simply present what we last received.
    reactWithin(timeout) {
      case actors.TIMEOUT =>
        process(i.event)
        act
      case i2:Item =>
        // Do we need to delay before handling this item? If so, do.
        val delay = i2.presentationTime-System.currentTimeMillis
        if(delay > 0) Thread.sleep(delay)
        if(shouldDiscardOld(i2.event, i.event))
          actWithLookahead(i2)
        else {
          process(i.event)
          actWithLookahead(i2.copy(presentationTime = System.currentTimeMillis+timeout))
        }
    }
  }

  // Process an AccessibilityEvent, sending it to Handlers for presentation.
  private def process(e:AccessibilityEvent) {
    if(Preferences.viewRecentEvents) {
      EventReviewQueue(new PrettyAccessibilityEvent(e))
      Log.d("spiel", "Event "+e.toString)
    }

    nextShouldNotInterruptCalled = false

    // If echo-by-word is enabled and we've just received a non-text-change, 
    // speak the buffered characters.
    if(e.getEventType != TYPE_VIEW_TEXT_CHANGED && Preferences.echoByWord)
      TTS.speakCharBuffer()

    // Now we engage in the complex process of dispatching events. This 
    // happens in several steps, but let's first create a variable to 
    // indicate whether dispatch should continue.
    var continue = true 

    // Store handlers we've called so we don't match them again.
    var alreadyCalled = List[Handler]()

    // Call the specified handler, setting state appropriately.
    def dispatchTo(pkg:String, cls:String):Boolean = handlers.get(pkg -> cls) match {
      case Some(h) =>
        if(alreadyCalled.contains(h)) {
          Log.d("spiel", "Already called this handler, skipping.")
          true
        } else {
          Log.d("spiel", "Dispatching to "+pkg+":"+cls)
          alreadyCalled ::= h
          // If we don't already have a handler for this exact package and 
          // class, then associate the one we're calling with it. This 
          // allows for similar AccessibilityEvents to skip several dispatch steps
          if(handlers.get((e.getPackageName.toString, e.getClassName.toString)) == None)
            handlers(e.getPackageName.toString -> e.getClassName.toString) = h
          !h(e)
        }
      case None => true
    }

    // First let's check if there's an event for this exact package and 
    // class. If one was cached above then dispatch ends here.
    if(continue) {
      continue = dispatchTo(e.getPackageName.toString, e.getClassName.toString)
    }

    // Now check for just the class name.
    if(continue) {
      continue = dispatchTo("", e.getClassName.toString)
    }

    // Now we check superclasses. Basically, if a given class is a subclass 
    // of a widget for which we already have a Handler (I.e. a subclass of 
    // Button) then it should be delegated to the handler for buttons. 
    // Surround this in a try block to catch the various exceptions that can 
    // bubble up. While this is a heavy step, previous caching minimizes the 
    // need to do it.
    try {
      if(continue) {
        val testClass = service.getClassLoader.loadClass(e.getClassName.toString)
        handlers.foreach { v =>
          if(v._1._2 != "" && continue) {
            try {
              val testClass2 = service.getClassLoader.loadClass(v._1._2)
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
    // Now dispatch to the default, catch-all handler.
    if(continue) dispatchTo("", "")

    if(!nextShouldNotInterruptCalled)
      myNextShouldNotInterrupt = false

  }

  /**
   * Map of <code>AccessibilityEvent</code> types to more human-friendly strings.
  */

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

/**
 * Maps a given <code>Callback</code> to events originating from a given 
 * package and class.
 *
 * Passing a blank string for either indicates events from all packages or all classes.
*/

class Handler(pkg:String, cls:String) {

  def this() = this("", "")
  def this(c:String) = this("", c)

  import Handler._

  handlers(pkg -> cls) = this

  // Convenience functions for calling TTS, used from scripting subsystem.

  def speak(text:String, interrupt:Boolean):Unit = TTS.speak(text, interrupt)
  def speak(text:String):Unit = TTS.speak(text, !myNextShouldNotInterrupt)
  def speak(list:List[String], interrupt:Boolean):Unit = TTS.speak(list, interrupt)
  def speak(list:List[String]):Unit = TTS.speak(list, !myNextShouldNotInterrupt)
  def speakNotification(text:String) = TTS.speakNotification(text)
  def speakNotification(text:List[String]) = TTS.speakNotification(text)

  /**
   * Indicates that the next <code>AccessibilityEvent</code> should not interrupt speech.
  */

  def nextShouldNotInterrupt = Handler.nextShouldNotInterrupt

  // Convenience method for converting functions to callbacks.

  implicit def toNativeCallback(f:AccessibilityEvent => Boolean):NativeCallback = new NativeCallback(f)

  /**
   * Maps strings to <code>Callback</code> classes for specific event types 
   * related to the specified package and class.
  */

  val dispatches = collection.mutable.Map[String, Callback]()

  // Register <code>Callback</code> instances for the various <code>AccessibilityEvent</code> types.

  protected def onNotificationStateChanged(c:Callback) = dispatches("notificationStateChanged") = c

  protected def onViewClicked(c:Callback) = dispatches("viewClicked") = c

  protected def onViewFocused(c:Callback) = dispatches("viewFocused") = c

  protected def onViewLongClicked(c:Callback) = dispatches("viewLongClicked") = c

  protected def onViewSelected(c:Callback) = dispatches("viewSelected") = c

  protected def onViewTextChanged(c:Callback) = dispatches("viewTextChanged") = c

  protected def onWindowStateChanged(c:Callback) = dispatches("windowStateChanged") = c

  // Called if no other events match.

  protected def byDefault(c:Callback) = dispatches("default") = c

  /**
   * Converts a given <code>AccessibilityEvent</code> to a list of 
   * utterances incorporating text and content description, optionally 
   * adding a blank utterance.
  */

  protected def utterancesFor(e:AccessibilityEvent, addBlank:Boolean = true) = {
    var rv = List[String]()
    if(e.isChecked && !e.getText.contains(service.getString(R.string.checked))) rv ::= service.getString(R.string.checked)
    if(e.getText.size == 0 && e.getContentDescription == null && addBlank)
      rv ::= ""
    rv :::= e.getText.filter(_ != null).map(_.toString).toList
    if(e.getContentDescription != null) rv ::= e.getContentDescription.toString
    rv
  }

  /**
   * Run a given <code>AccessibilityEvent</code> through this <code>Handler</code>
   *
   * @return <code>true</code> if processing should stop, <code>false</code> otherwise.
  */

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

// Now, finally, we reach the presentation logic for generic Android widgets.

/**
 * Encapsulates generic handling for multiple types of buttons.
*/

trait GenericButtonHandler extends Handler {
  onViewFocused { e:AccessibilityEvent =>
    speak(utterancesFor(e, false)++(Handler.service.getString(R.string.button) :: Nil))
    true
  }
}

/**
 * By placing all <code>Handler</code> classes here, we can use the power of 
 * reflection to avoid manually registering each and every one.
*/

class Handlers {

  class AlertDialog extends Handler("android.app.AlertDialog") {
    onWindowStateChanged { e:AccessibilityEvent =>
      speak(Handler.service.getString(R.string.alert) :: utterancesFor(e), true)
      nextShouldNotInterrupt
      true
    }
  }

  class Button extends Handler("android.widget.Button") with GenericButtonHandler

  class CheckBox extends Handler("android.widget.CheckBox") {
    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak(Handler.service.getString(R.string.checked))
      else
        speak(Handler.service.getString(R.string.notChecked))
      true
    }

    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e, false)++(Handler.service.getString(R.string.checkbox) :: Nil))
      true
    }

  }

  class Dialog extends Handler("android.app.Dialog") {
    onWindowStateChanged { e:AccessibilityEvent =>
      speak(utterancesFor(e), true)
      nextShouldNotInterrupt
      true
    }
  }

  class EditText extends Handler("android.widget.EditText") {
    onViewFocused { e:AccessibilityEvent =>
      if(e.getCurrentItemIndex != -1) {
        if(!e.isPassword) {
          speak(utterancesFor(e), false)
          speak(Handler.service.getString(R.string.editText), false)
        } else
          speak(utterancesFor(e)++(Handler.service.getString(R.string.password) :: Nil))
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
        speak(Handler.service.getString(R.string.menu), true)
        nextShouldNotInterrupt
      }
      true
    }

  }

  class RadioButton extends Handler("android.widget.RadioButton") {

    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak(Handler.service.getString(R.string.checked))
      else
        speak(Handler.service.getString(R.string.notChecked))
      true
    }

    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e, false)++(Handler.service.getString(R.string.radioButton) :: Nil))
      true
    }

  }

  class SearchBox extends Handler("android.app.SearchDialog$SearchAutoComplete") {
    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e))
      speak(Handler.service.getString(R.string.searchText), false)
      true
    }
  }

  class Tab extends Handler("android.widget.RelativeLayout") {
    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e)++(Handler.service.getString(R.string.tab) :: Nil), true)
      true
    }
  }

  /**
   * Default catch-all handler which catches unresolved <code>AccessibilityEvent</code>s.
  */

  class Default extends Handler {

    onNotificationStateChanged { e:AccessibilityEvent =>
      //Log.d("spiel", "onNotificationStateChanged")
      if(e.getText.size > 0) {
        nextShouldNotInterrupt
        speakNotification(utterancesFor(e))
      }
      true
    }

    onViewClicked { e:AccessibilityEvent =>
      //Log.d("spiel", "onViewClicked")
      true
    }

    onViewFocused { e:AccessibilityEvent =>
      //Log.d("spiel", "onViewFocused")
      if(e.isFullScreen || (e.getItemCount == 0 && e.getCurrentItemIndex == -1))
        true
      else {
        //if(utterancesFor(e).length > 0)
          speak(utterancesFor(e))
        true
      }
    }

    onViewLongClicked { e:AccessibilityEvent =>
      //Log.d("spiel", "onViewLongClicked")
      true
    }

    onViewSelected { e:AccessibilityEvent =>
      //Log.d("spiel", "onViewSelected")
      if(utterancesFor(e).length > 0)
        speak(utterancesFor(e))
      true
    }

    onViewTextChanged { e:AccessibilityEvent =>
      //Log.d("spiel", "onViewTextChanged")
      if(e.getAddedCount > 0 || e.getRemovedCount > 0) {
        if(e.isPassword)
          TTS.speakCharacter("*")
        else
          if(e.getAddedCount > 0)
            TTS.speakCharacter(("" /: e.getText) (_ + _).substring(e.getFromIndex,   e.getFromIndex+e.getAddedCount))
          else if(e.getRemovedCount > 0) {
            val start = e.getFromIndex
            val end = e.getFromIndex+e.getRemovedCount
            if(Preferences.echoByWord)
              TTS.clearCharBuffer()
            speak(e.getBeforeText.toString.substring(start, end), true)
          }
        else
          speak(utterancesFor(e), false)
      }
      true
    }

    onWindowStateChanged { e:AccessibilityEvent =>
      //Log.d("spiel", "onWindowStateChanged")
      // Needed because menus send their contents as a non-fullscreen 
      // onWindowStateChanged event and we don't want to read an entire menu 
      // when it focuses.
      if(!e.isFullScreen)
        true
      else {
        speak(utterancesFor(e), true)
        nextShouldNotInterrupt
        true
      }
    }

    byDefault { e:AccessibilityEvent =>
      //Log.d("spiel", "Unhandled event: "+e.toString)
      speak(utterancesFor(e))
      true
    }

  }
}
