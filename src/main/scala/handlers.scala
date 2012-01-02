package info.spielproject.spiel
package handlers

import collection.JavaConversions._
import collection.mutable.Map

import android.app.{ActivityManager, Service}
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import AccessibilityEvent._

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

class PrettyAccessibilityEvent(val e:AccessibilityEvent) {

  val activityName = Handler.currentActivity

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
    eventType+": "+text+contentDescription+" index: "+e.getCurrentItemIndex+" count: "+e.getItemCount+" package: "+packageName+" activity: "+activityName+" class: "+className
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

object Handler {

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

  def nextShouldNotInterrupt() = {
    Log.d("spiel", "Next accessibility event should not interrupt speech.")
    nextShouldNotInterruptCalled = true
    myNextShouldNotInterrupt = true
    true
  }

  private[handlers] var context:Context = null

  /**
   * Initialize handlers for the given <code>Context</code>.
  */

  def apply(c:Context) {
    context = c
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
  }

  private var _lastEvent:AccessibilityEvent = null
  def lastEvent = _lastEvent

  def process(e:AccessibilityEvent) {
    _lastEvent = e
    if(Preferences.viewRecentEvents) {
      EventReviewQueue(new PrettyAccessibilityEvent(e))
      Log.d("spiel", "Event "+e.toString+"; Activity: "+currentActivity)
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

    // Next let's check if there's a handler for this exact package and 
    // class. If one was cached above then dispatch ends here.
    if(continue)
      continue = dispatchTo(e.getPackageName.toString, e.getClassName.toString)

    // Now check for just the class name.
    if(continue)
      continue = dispatchTo("", e.getClassName.toString)

    // Now we check superclasses. Basically, if a given class is a subclass 
    // of a widget for which we already have a Handler (I.e. a subclass of 
    // Button) then it should be delegated to the handler for buttons. 
    // Surround this in a try block to catch the various exceptions that can 
    // bubble up. While this is a heavy step, previous caching minimizes the 
    // need to do it.
    if(continue) {
      def test(originator:Class[_], target:Class[_]) =
        target.isAssignableFrom(originator)
      val originator = try {
        Some(context.getClassLoader.loadClass(e.getClassName.toString))
      } catch {
        case _ => try {
          val c = context.createPackageContext(e.getPackageName.toString, Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY)
          Some(Class.forName(e.getClassName.toString, true, c.getClassLoader))
        } catch {
          case _ => None
        }
      }
      originator.foreach { o =>
        handlers.foreach { v =>
          if(v._1._1 == "" && v._1._2 != "" && continue) {
            try {
              val target = context.getClassLoader.loadClass(v._1._2)
              if(test(o, target))
                continue = continue && dispatchTo(v._1._1, v._1._2)
            } catch {
              case _ =>
            }
          }
        }
      }
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
    TYPE_TOUCH_EXPLORATION_GESTURE_END -> "touchExplorationGestureEnd",
    TYPE_TOUCH_EXPLORATION_GESTURE_START -> "touchExplorationGestureStart",
    TYPE_VIEW_CLICKED -> "viewClicked",
    TYPE_VIEW_FOCUSED -> "viewFocused",
    TYPE_VIEW_HOVER_ENTER -> "viewHoverEnter",
    TYPE_VIEW_HOVER_EXIT -> "viewHoverExit",
    TYPE_VIEW_LONG_CLICKED -> "viewLongClicked",
    TYPE_VIEW_SCROLLED -> "viewScrolled",
    TYPE_VIEW_SELECTED -> "viewSelected",
    TYPE_VIEW_TEXT_CHANGED -> "viewTextChanged",
    TYPE_VIEW_TEXT_SELECTION_CHANGED -> "viewTextSelectionChanged",
    TYPE_WINDOW_CONTENT_CHANGED -> "windowContentChanged",
    TYPE_WINDOW_STATE_CHANGED -> "windowStateChanged"
  )

  /**
   * @return <code>Activity</code> currently in foreground
  */

  def currentActivity = {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
    val tasks = manager.getRunningTasks(1)
    if(!tasks.isEmpty)
      tasks.head.topActivity.getClassName
    else null
  }

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

  def speak(text:String, interrupt:Boolean) = {
    TTS.speak(text, interrupt)
    true
  }

  def speak(text:String):Boolean = speak(text, !myNextShouldNotInterrupt)

  def speak(list:List[String], interrupt:Boolean) = {
    TTS.speak(list, interrupt)
    true
  }

  def speak(list:List[String]):Boolean = speak(list, !myNextShouldNotInterrupt)

  def speakNotification(text:String) = {
    TTS.speakNotification(text)
    true
  }

  def speakNotification(text:List[String]) = {
    TTS.speakNotification(text)
    true
  }

  /**
   * Indicates that the next <code>AccessibilityEvent</code> should not interrupt speech.
  */

  def nextShouldNotInterrupt() = Handler.nextShouldNotInterrupt()

  // Convenience method for converting functions to callbacks.

  implicit def toNativeCallback(f:AccessibilityEvent => Boolean):NativeCallback = new NativeCallback(f)

  /**
   * Maps strings to <code>Callback</code> classes for specific event types 
   * related to the specified package and class.
  */

  val dispatches = collection.mutable.Map[String, Callback]()

  // Register <code>Callback</code> instances for the various <code>AccessibilityEvent</code> types.

  protected def onNotificationStateChanged(c:Callback) = dispatches(dispatchers(TYPE_NOTIFICATION_STATE_CHANGED)) = c

  protected def onTouchExplorationGestureEnd(c:Callback) = dispatches(dispatchers(TYPE_TOUCH_EXPLORATION_GESTURE_END)) = c

  protected def onTouchExplorationGestureStart(c:Callback) = dispatches(dispatchers(TYPE_TOUCH_EXPLORATION_GESTURE_START)) = c

  protected def onViewClicked(c:Callback) = dispatches(dispatchers(TYPE_VIEW_CLICKED)) = c

  protected def onViewFocused(c:Callback) = dispatches(dispatchers(TYPE_VIEW_FOCUSED)) = c

  protected def onViewHoverEnter(c:Callback) = dispatches(dispatchers(TYPE_VIEW_HOVER_ENTER)) = c

  protected def onViewHoverExit(c:Callback) = dispatches(dispatchers(TYPE_VIEW_HOVER_EXIT)) = c

  protected def onViewLongClicked(c:Callback) = dispatches(dispatchers(TYPE_VIEW_LONG_CLICKED)) = c

  protected def onViewScrolled(c:Callback) = dispatches(dispatchers(TYPE_VIEW_SCROLLED)) = c

  protected def onViewSelected(c:Callback) = dispatches(dispatchers(TYPE_VIEW_SELECTED)) = c

  protected def onViewTextChanged(c:Callback) = dispatches(dispatchers(TYPE_VIEW_TEXT_CHANGED)) = c

  protected def onViewTextSelectionChanged(c:Callback) = dispatches(dispatchers(TYPE_VIEW_TEXT_SELECTION_CHANGED)) = c

  protected def onWindowContentChanged(c:Callback) = dispatches(dispatchers(TYPE_WINDOW_CONTENT_CHANGED)) = c

  protected def onWindowStateChanged(c:Callback) = dispatches(dispatchers(TYPE_WINDOW_STATE_CHANGED)) = c

  // Called if no other events match.

  protected def byDefault(c:Callback) = dispatches("default") = c

  /**
   * Converts a given <code>AccessibilityEvent</code> to a list of 
   * utterances incorporating text and content description, optionally 
   * adding a blank utterance.
  */

  protected def utterancesFor(e:AccessibilityEvent, addBlank:Boolean = true) = {
    var rv = List[String]()
    val text = Option(e.getText.toList).getOrElse(Nil)
    if(e.isChecked && !text.contains(context.getString(R.string.checked))) rv ::= context.getString(R.string.checked)
    if(text.size == 0 && e.getContentDescription == null && addBlank)
      rv ::= ""
    rv :::= text.filter(_ != null).map(_.toString)
    if(e.getContentDescription != null && e.getContentDescription != "")
      rv match {
        case hd :: Nil if(hd == e.getContentDescription) =>
        case _ =>
          rv ::= e.getContentDescription.toString
        }
    rv
  }

  /**
   * Run a given <code>AccessibilityEvent</code> through this <code>Handler</code>
   *
   * @return <code>true</code> if processing should stop, <code>false</code> otherwise.
  */

  def apply(e:AccessibilityEvent):Boolean = {

    def dispatchTo(callback:String):Boolean = dispatches.get(callback).map(_(e)).getOrElse(false)

    val fallback = dispatchers.get(e.getEventType).map(dispatchTo(_)).getOrElse(false)

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
    val text = utterancesFor(e, false).mkString(": ")
    if(text == "")
      speak(Handler.context.getString(R.string.button).toString)
    else
      speak(Handler.context.getString(R.string.labeledButton, text))
  }
}

/**
 * By placing all <code>Handler</code> classes here, we can use the power of 
 * reflection to avoid manually registering each and every one.
*/

class Handlers {

  class AlertDialog extends Handler("android.app.AlertDialog") {
    onWindowStateChanged { e:AccessibilityEvent =>
      speak(Handler.context.getString(R.string.alert, utterancesFor(e).mkString(": ")), true)
      nextShouldNotInterrupt()
    }
  }

  class Button extends Handler("android.widget.Button") with GenericButtonHandler

  class CheckBox extends Handler("android.widget.CheckBox") {
    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak(Handler.context.getString(R.string.checked))
      else
        speak(Handler.context.getString(R.string.notChecked))
    }

    onViewFocused { e:AccessibilityEvent =>
      speak(Handler.context.getString(R.string.checkbox, utterancesFor(e, false).mkString(": ")))
    }

  }

  class Dialog extends Handler("android.app.Dialog") {
    onWindowStateChanged { e:AccessibilityEvent =>
      speak(utterancesFor(e), true)
      nextShouldNotInterrupt()
    }
  }

  class EditText extends Handler("android.widget.EditText") {
    onViewFocused { e:AccessibilityEvent =>
      if(e.getCurrentItemIndex != -1) {
        if(e.isPassword)
          speak(Handler.context.getString(R.string.password))
        else {
          speak(utterancesFor(e, true), false)
          speak(Handler.context.getString(R.string.editText), false)
        }
      }
      true
    }
  } 

  class ImageButton extends Handler("android.widget.ImageButton") with GenericButtonHandler

  class ImageView extends Handler("android.widget.ImageView") {
    onViewFocused { e:AccessibilityEvent =>
      val text = utterancesFor(e, false).mkString(": ")
      if(text == "")
        speak(Handler.context.getText(R.string.image).toString)
      else
        speak(Handler.context.getString(R.string.labeledImage, text))
    }
  }

  class ListView extends Handler("android.widget.ListView") {
    onViewSelected { e:AccessibilityEvent =>
      if(e.getCurrentItemIndex >= 0)
        speak(Handler.context.getString(R.string.listItem, utterancesFor(e).mkString(": "), (e.getCurrentItemIndex+1).toString, e.getItemCount.toString))
      else if(e.getItemCount == 0)
        speak(Handler.context.getString(R.string.emptyList))
      true
    }
  }

  class Menu extends Handler("com.android.internal.view.menu.MenuView") {

    onViewSelected { e:AccessibilityEvent =>
      speak(utterancesFor(e))
    }

    onWindowStateChanged { e:AccessibilityEvent =>
      if(e.getCurrentItemIndex == -1) {
        speak(Handler.context.getString(R.string.menu), true)
        nextShouldNotInterrupt()
      }
      true
    }

  }

  class RadioButton extends Handler("android.widget.RadioButton") {

    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak(Handler.context.getString(R.string.checked))
      else
        speak(Handler.context.getString(R.string.notChecked))
    }

    onViewFocused { e:AccessibilityEvent =>
      speak(Handler.context.getString(R.string.radioButton, utterancesFor(e).mkString(": ")))
    }

  }

  class RelativeLayout extends Handler("android.widget.RelativeLayout") {
    onViewFocused { e:AccessibilityEvent =>
      val utterances = utterancesFor(e)
      if(utterances.size > 0) {
        //speak(Handler.context.getString(R.string.tab, utterances.mkString(": ")), true)
        speak(utterances.mkString(": "), true)
        nextShouldNotInterrupt()
      }
      true
    }
  }

  class SearchBox extends Handler("android.app.SearchDialog$SearchAutoComplete") {
    onViewFocused { e:AccessibilityEvent =>
      speak(Handler.context.getString(R.string.searchText, utterancesFor(e).mkString(": ")), false)
    }
  }

  class TextView extends Handler("android.widget.TextView") {
    onViewFocused { e:AccessibilityEvent =>
      if(e.getCurrentItemIndex != -1)
        speak(utterancesFor(e, true))
      true
    }
  }

  class WebView extends Handler("android.webkit.WebView") {

    def textFor(x:xml.Node) =
      x.descendant.map(_.text).mkString(" ")

    onViewSelected { e:AccessibilityEvent =>
      val x = Option(e.getText.map(v => if(v == null) "<span/>" else v)
      .mkString).map { t =>
        if(t == "")
          <span/>
        else
          utils.HtmlParser(t.replace("&nbsp;", " "))
      }.getOrElse(<span/>)
      speak(textFor(x))
    }
  }

  /**
   * Default catch-all handler which catches unresolved <code>AccessibilityEvent</code>s.
  */

  class Default extends Handler {

    onNotificationStateChanged { e:AccessibilityEvent =>
      val utterances = utterancesFor(e, false)
      if(!utterances.isEmpty) {
        nextShouldNotInterrupt()
        speakNotification(utterances)
      }
      true
    }

    onTouchExplorationGestureEnd { e:AccessibilityEvent =>
      false
    }

    onTouchExplorationGestureStart { e:AccessibilityEvent =>
      false
    }

    onViewClicked { e:AccessibilityEvent => true }

    onViewFocused { e:AccessibilityEvent =>
      var utterances = utterancesFor(e, false)
      if(utterances == Nil || utterances.isEmpty)
        utterances = e.getClassName.toString.split("\\.").last :: Nil
      speak(utterances)
    }

    onViewHoverEnter { e:AccessibilityEvent =>
      false
    }

    onViewHoverExit { e:AccessibilityEvent =>
      false
    }

    onViewLongClicked { e:AccessibilityEvent => true }

    onViewScrolled { e:AccessibilityEvent =>
      val utterances = utterancesFor(e, false)
      if(!utterances.isEmpty) {
        speak(utterancesFor(e))
        nextShouldNotInterrupt()
      }
      true
    }

    onViewSelected { e:AccessibilityEvent =>
      val utterances = utterancesFor(e, false)
      if(utterances.length > 0) {
        if(e.getCurrentItemIndex == -1)
          if(e.getItemCount == 1)
            speak(Handler.context.getString(R.string.item, utterances.mkString(" ")))
          else if(e.getItemCount >= 0)
            speak(Handler.context.getString(R.string.items, utterances.mkString(" "), e.getItemCount.toString))
          else
            speak(utterances)
        else
          speak(utterances)
      } else
        speak("")
    }

    onViewTextChanged { e:AccessibilityEvent =>
      if(e.getAddedCount > 0 || e.getRemovedCount > 0) {
        if(e.isPassword)
          TTS.speakCharacter("*")
        else
          if(e.getAddedCount > 0)
            // We're getting an exception here due to what appear to be 
            // malformed AccessibilityEvents.
            try {
              TTS.speakCharacter(e.getText.mkString.substring(e.getFromIndex,   e.getFromIndex+e.getAddedCount))
            } catch {
              case e => Log.d("spiel", "Think we have a malformed event. Got "+e.getMessage)
            }
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

    private var oldSelectionFrom:Option[Int] = None
    private var oldSelectionTo:Option[Int] = None

    onViewTextSelectionChanged { e:AccessibilityEvent =>
      Option(e.getSource).map(_.getText).foreach { text =>
        val txt = if(e.isPassword) Some("*"*e.getItemCount) else Option(text)
        txt.map { t =>
          val from = if(e.getFromIndex == t.length) e.getFromIndex-1 else e.getFromIndex
          val to = if(e.getToIndex == 0)
            1
          else if(e.getToIndex < t.length)
            e.getToIndex+1
          else
            e.getToIndex-1
          val width = to-from
          if(from >= 0 && to >= 0) {
            val selection = t.subSequence(from, to)
            (for(
              osf <- oldSelectionFrom;
              ost <- oldSelectionTo;
              distance = List(
                math.abs(osf-from),
                math.abs(osf-to),
                math.abs(ost-from),
                math.abs(ost-to)
              ).min if(distance > 1)
            ) yield {
              val interval = (if(ost < from) text.subSequence(ost, from) else text.subSequence(to, osf)).toString
              if(interval.contains("\n")) {
                val ending = t.subSequence(from, t.length).toString
                val nextNewLine = if(ending.indexOf("\n") == -1) t.length else from+ending.indexOf("\n")
                val start = t.subSequence(0, from).toString.reverse
                val previousNewLine = if(start.indexOf("\n") == -1) from-start.length else from-start.indexOf("\n")
                speak(t.subSequence(previousNewLine, nextNewLine).toString, true)
              } else {
                speak(selection.toString, true)
                false
              }
            }).getOrElse(speak(selection.toString, true))
            oldSelectionFrom = Some(from)
            oldSelectionTo = Some(to)
          } else if(from == -1 || to == -1) {
            oldSelectionFrom = None
            oldSelectionTo = None
          }
        }
      }
      true
    }

    onWindowContentChanged { e:AccessibilityEvent =>
      false
    }

    onWindowStateChanged { e:AccessibilityEvent =>
      // Needed because menus send their contents as a non-fullscreen 
      // onWindowStateChanged event and we don't want to read an entire menu 
      // when it focuses.
      if(!e.isFullScreen)
        true
      else {
        speak(utterancesFor(e), true)
        nextShouldNotInterrupt()
      }
    }

    byDefault { e:AccessibilityEvent =>
      //Log.d("spiel", "Unhandled event: "+e.toString)
      //speak(utterancesFor(e))
      true
    }

  }
}
