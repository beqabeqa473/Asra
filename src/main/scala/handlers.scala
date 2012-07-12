package info.spielproject.spiel
package handlers

import collection.JavaConversions._
import collection.mutable.Map

import android.app.{ActivityManager, Service}
import android.content.Context
import android.graphics.Rect
import android.os.Build.VERSION
import android.os.{SystemClock, Vibrator}
import android.util.Log
import android.view.accessibility.{AccessibilityEvent, AccessibilityNodeInfo}
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

class NativeCallback(f:AccessibilityEvent => Boolean) extends Callback {
  def apply(e:AccessibilityEvent) = f(e)
}

/**
 * Wrapper that pretty-prints <code>AccessibilityEvent</code>s.
*/

class PrettyAccessibilityEvent(val e:AccessibilityEvent) {

  val activityName = Handler.currentActivity

  override val toString = {
    val eventType = Handler.dispatchers.get(e.getEventType).getOrElse("Unknown")
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
    while(length > 50) dequeue()
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
    handlers = handlers.filter(_._2 != h)
  }

  def unregisterPackage(pkg:String) = {
    handlers = handlers.filter(_._1._1 != pkg)
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

  private lazy val vibrator:Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]

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
        Option(cls.getConstructor(classOf[Handlers])).foreach(_.newInstance(h))
      } catch { case _ => }
    }
  }

  def process(e:AccessibilityEvent, eventType:Option[Int] = None):Boolean = {

    if(e == null || e.getClassName == null || e.getPackageName == null)
      return true

    if(
      SystemClock.uptimeMillis-e.getEventTime > 100 &&
      List(TYPE_TOUCH_EXPLORATION_GESTURE_END, TYPE_TOUCH_EXPLORATION_GESTURE_START, TYPE_VIEW_HOVER_ENTER, TYPE_VIEW_HOVER_EXIT).contains(e.getEventType)
    )
      return true

    if(eventType == None) {
      EventReviewQueue(new PrettyAccessibilityEvent(e))
      Log.d("spiel", "Event "+e.toString+"; Activity: "+currentActivity)
    }

    if(!StateReactor.screenOn_? && e.getEventType != TYPE_NOTIFICATION_STATE_CHANGED)
      return true

    if(e.getEventType == TYPE_NOTIFICATION_STATE_CHANGED && Preferences.notificationFilters.contains(e.getPackageName))
      return true

    if(eventType == None)
      nextShouldNotInterruptCalled = false

    val eType = eventType.getOrElse(e.getEventType)

    // Now we engage in the complex process of dispatching events. This 
    // happens in several steps.

    // Store handlers we've called so we don't match them again.
    var alreadyCalled = List[Handler]()

    // Call the specified handler, setting state appropriately.
    def dispatchTo(pkg:String, cls:String):Boolean = handlers.get(pkg -> cls).map { h =>
      if(alreadyCalled.contains(h)) {
        Log.d("spiel", "Already called "+h.getClass.getName+", skipping.")
        false
      } else {
        Log.d("spiel", "Dispatching to "+h.getClass.getName)
        alreadyCalled ::= h
        h(e, eType)
      }
    }.getOrElse(false)

    // Always run this handler before an event. This cannot block others from executing.
    def dispatchToBefore() = {
      Log.d("spiel", "Before dispatch")
      Before(e, eType)
      false
    }

    // Let's check if there's a handler for this exact package and 
    // class.
    def dispatchToExact() = {
      Log.d("spiel", "Exact match dispatch")
      dispatchTo(e.getPackageName.toString, e.getClassName.toString)
    }

    // Now check for just the class name.
    def dispatchToClass() = {
      Log.d("spiel", "Class match dispatch")
      dispatchTo("", e.getClassName.toString)
    }

    // Now we check superclasses. Basically, if a given class is a subclass 
    // of a widget for which we already have a Handler (I.e. a subclass of 
    // Button) then it should be delegated to the handler for buttons. 
    // Surround this in a try block to catch the various exceptions that can 
    // bubble up.

    def dispatchToSubclass() = {
      Log.d("spiel", "Subclass match dispatch")

      def ancestors(cls:Class[_]):List[Class[_]] = {
        def iterate(start:Class[_], classes:List[Class[_]] = Nil):List[Class[_]] = start.getSuperclass match {
          case null => classes
          case v => iterate(v, v :: classes)
        }
        iterate(cls).reverse
      }

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

      originator.flatMap { o =>
        val a = ancestors(o)
        Log.d("spiel", "Ancestors: "+a)
        val candidates = handlers.filter { h =>
          h._1._1 == "" && h._1._2 != "" && h._1._2 != "*"
        }.toList.map { h =>
          val target:Class[_] = try {
            context.getClassLoader.loadClass(h._1._2)
          } catch {
            case e:ClassNotFoundException => o
          }
          (a.indexOf(target), h)
        }.filter(_._1 >= 0).sortBy((v:Tuple2[Int, _]) => v._1)
        Some(candidates.exists { v =>
          dispatchTo(v._2._1._1, v._2._1._2)
        })
      }.getOrElse(false)
    }

    // Now dispatch to the default, catch-all handler.
    def dispatchToDefault() = {
      Log.d("spiel", "Default dispatch")
      dispatchTo("", "")
    }

    def dispatchToAfter() = {
      Log.d("spiel", "After dispatch")
      After(e, eType)
      true
    }

    (dispatchToBefore() || dispatchToExact() || dispatchToClass() || dispatchToSubclass() || dispatchToDefault())

    dispatchToAfter()

    if(!nextShouldNotInterruptCalled && eventType == None)
      myNextShouldNotInterrupt = false

    true
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
    TTS.speak(text, interrupt, Some("last"))
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

  def stopSpeaking() = {
    if(!nextShouldNotInterruptCalled)
      TTS.stop()
    true
  }

  def vibrate(millis:Long) = {
    vibrator.vibrate(millis)
    true
  }

  def shortVibration() = vibrate(20)

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

  protected def utterancesFor(e:AccessibilityEvent, addBlank:Boolean = true, stripBlanks:Boolean = false, guessLabelIfTextMissing:Boolean = false, guessLabelIfContentDescriptionMissing:Boolean = false, guessLabelIfTextShorterThan:Option[Int] = None, providedText:Option[String] = None):List[String] = {
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
        rv :::= guessLabelFor(e).map { v =>
          removeBlank()
          List(v)
        }.getOrElse(Nil)
      else if(guessLabelIfContentDescriptionMissing && e.getContentDescription == null)
        rv :::= guessLabelFor(e).map { v =>
          removeBlank()
          List(v)
        }.getOrElse(Nil)
      else guessLabelIfTextShorterThan.foreach { v =>
        if(text.length < v)
          rv :::= guessLabelFor(e).map { v =>
            removeBlank()
            List(v)
          }.getOrElse(Nil)
      }
    }
    rv
  }

  protected def descendantsOf(n:AccessibilityNodeInfo):List[AccessibilityNodeInfo] = {
    (n.getChildCount match {
      case 0 => Nil
      case v =>
        (for(
          i <- 0 to v-1;
          c = n.getChild(i) if(c != null)
        ) yield(List(c)++descendantsOf(c))
        ).toList.flatten
    }).filter(_ != null)
  }

  protected def rootOf(node:AccessibilityNodeInfo):Option[AccessibilityNodeInfo] = Option(node).map { n =>
    def iterate(v:AccessibilityNodeInfo):AccessibilityNodeInfo = v.getParent match {
      case null => v
      case v2 => iterate(v2)
    }
    iterate(n)
  }

  protected def guessLabelFor(e:AccessibilityEvent):Option[String] = {
    if(VERSION.SDK_INT < 14) return None
    val source = e.getSource
    rootOf(source).flatMap { root =>
      val descendants = descendantsOf(root).map { leaf =>
        val rect = new Rect()
        leaf.getBoundsInScreen(rect)
        (leaf, rect)
      }
      val sr = new Rect()
      source.getBoundsInScreen(sr)
      val sourceRect = new Rect(0, sr.top, Int.MaxValue, sr.bottom)
      val row = descendants.filter(_._2.intersect(sourceRect))
      row.find((v) => v._1.getClassName == "android.widget.TextView" && v._1.getText != null && v._1.getText.length > 0).map(
        _._1.getText.toString
      ).orElse {
        descendants.filter(_._2.bottom <= sourceRect.top)
        .filter((v) => v._1 != null && v._1.getClassName == "android.widget.TextView" && v._1.getText != null && v._1.getText.length > 0)
        .sortBy(_._2.bottom)
        .reverse.headOption.map(_._1.getText.toString)
      }
    }
  }

  protected def interactive_?(source:AccessibilityNodeInfo) =
    source.isCheckable || source.isClickable || source.isLongClickable || source.isFocusable

  protected def interactables(source:AccessibilityNodeInfo) = 
    (source :: descendantsOf(source)).filter(interactive_?(_))

  /**
   * Run a given <code>AccessibilityEvent</code> through this <code>Handler</code>
   *
   * @return <code>true</code> if processing should stop, <code>false</code> otherwise.
  */

  def apply(e:AccessibilityEvent, eventType:Int):Boolean = {

    def dispatchTo(callback:String):Boolean = dispatches.get(callback).map(_(e)).getOrElse(false)

    val fallback = dispatchers.get(eventType).map(dispatchTo(_)).getOrElse(false)

    if((pkg == "" && cls == "*") || !fallback)
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
    val text = utterancesFor(e, addBlank=false).mkString(": ")
    if(text == "") {
      if(VERSION.SDK_INT >= 14) {
        Option(e.getSource).flatMap { source =>
          rootOf(source).map { root =>
            val descendants = descendantsOf(root)
            val index = descendants.indexOf(source)+1
            if(index > 0)
              speak(Handler.context.getString(R.string.listItem, Handler.context.getText(R.string.button), index.toString, descendants.size.toString))
            else None
          }
        }.getOrElse(speak(Handler.context.getString(R.string.button).toString))
        true
      } else
        speak(Handler.context.getString(R.string.button).toString)
    } else
      speak(Handler.context.getString(R.string.labeledButton, text))
  }
}

/**
 * Run before every event. Cannot pre-empt other handlers.
*/

object Before extends Handler("*") {

  onViewHoverEnter { e:AccessibilityEvent =>
    stopSpeaking()
    if(SystemClock.uptimeMillis-e.getEventTime <= 100)
      shortVibration()
    false
  }

  onViewHoverExit { e:AccessibilityEvent =>
    if(SystemClock.uptimeMillis-e.getEventTime <= 100)
      shortVibration()
    false
  }

  /*byDefault { e:AccessibilityEvent =>
    if(VERSION.SDK_INT >= 14) {
      if(e.getRecordCount > 0) {
        Log.d("spielcheck", "E: "+e)
        for(i <- 0 to e.getRecordCount-1) {
          Log.d("spielcheck", e.getRecord(i).toString)
        }
      }
    }
    false
  }*/

}

/**
 * Run after every event.
*/

object After extends Handler("", "*") {

  onViewFocused { e:AccessibilityEvent =>
    if(VERSION.SDK_INT >= 14)
      Option(e.getSource).foreach { source =>
        if(source.getChildCount == 0 && interactive_?(source) && !e.isEnabled)
          speak(Handler.context.getString(R.string.disabled), false)
      }
    false
  }

}

/**
 * By placing all <code>Handler</code> classes here, we can use the power of 
 * reflection to avoid manually registering each and every one.
*/

class Handlers {

  class ActionMenuItemView extends Handler("com.android.internal.view.menu.ActionMenuItemView") {

    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e, stripBlanks=true) ::: ("Menu item" :: Nil))
    }

    onViewHoverEnter { e:AccessibilityEvent => Handler.process(e, Some(TYPE_VIEW_FOCUSED)) }

  }

  class AdapterView extends Handler("android.widget.AdapterView") {
    onViewScrolled { e:AccessibilityEvent =>
      if(e.getToIndex >= 0 && e.getItemCount > 0) {
        val percentage = e.getToIndex.toDouble/e.getItemCount
        TTS.tick(Some(0.5+percentage/2))
      }
      true
    }
  }

  class AlertDialog extends Handler("android.app.AlertDialog") {
    onWindowStateChanged { e:AccessibilityEvent =>
      speak(Handler.context.getString(R.string.alert, utterancesFor(e, stripBlanks=true).mkString(": ")), true)
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
      speak(Handler.context.getString(R.string.checkbox, utterancesFor(e, addBlank=false, guessLabelIfTextShorterThan = Some(2)).mkString(": ")))
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
      if(e.isPassword) {
        speak(utterancesFor(e, addBlank=false, guessLabelIfContentDescriptionMissing = true), false)
        speak(Handler.context.getString(R.string.password))
        speak(Handler.context.getString(R.string.editText), false)
      } else {
        speak(utterancesFor(e, addBlank=true, guessLabelIfContentDescriptionMissing = true), false)
        speak(Handler.context.getString(R.string.editText), false)
      }
      true
    }

    onViewTextChanged { e:AccessibilityEvent =>
      if(e.getBeforeText == e.getText.mkString)
        true
      else if(e.getFromIndex == -1 || e.getToIndex == -1) {
        val oldText = e.getBeforeText.toString
        val newText = e.getText.mkString
        if(newText.length > oldText.length)
          speak(newText.replace(oldText, ""), true)
        else
          speak(oldText.replace(newText, ""), true)
      } else if(e.getAddedCount > 0 || e.getRemovedCount > 0) {
        if(e.isPassword)
          speak("*", true)
        else
          if(e.getAddedCount > 0)
            // We're getting an exception here due to what appear to be 
            // malformed AccessibilityEvents.
            try {
              val text = e.getText.mkString
              val str = text.substring(e.getFromIndex,   e.getFromIndex+e.getAddedCount)
              var flush = true
              if(Preferences.echoByChar) {
                speak(str, flush)
                flush = false
              }
              if(Preferences.echoByWord && !Character.isLetterOrDigit(str(0))) {
                val word = (text.substring(0, e.getFromIndex)
                .reverse.takeWhile(_.isLetterOrDigit).reverse+str).trim
                if(word.length > 0)
                  speak(word, flush)
              }
            } catch {
              case e => Log.d("spiel", "Think we have a malformed event. Got "+e.getMessage)
            }
          else if(e.getRemovedCount > 0) {
            val start = e.getFromIndex
            val end = e.getFromIndex+e.getRemovedCount
            Option(e.getBeforeText).foreach(v => speak(v.toString.substring(start, end), true))
          }
        else
          speak(utterancesFor(e), true)
      }
      true
    }

  }

  trait MenuView {
    self: Handler =>
    onViewFocused { e:AccessibilityEvent => speak(Handler.context.getString(R.string.menu)) }
  }

  class ExpandedMenuView extends Handler("com.android.internal.view.menu.ExpandedMenuView") with MenuView

  class HomeView extends Handler("com.android.internal.widget.ActionBarView$HomeView") {

    private def process(e:AccessibilityEvent) = {
      val utterances = utterancesFor(e, addBlank = false)
      if(utterances != Nil)
        speak(utterances.mkString(" "))
      true
    }

    onViewFocused { e:AccessibilityEvent => process(e) }

    onViewHoverEnter { e:AccessibilityEvent => process(e) }

  }

  class ImageButton extends Handler("android.widget.ImageButton") with GenericButtonHandler

  class ImageView extends Handler("android.widget.ImageView") {
    onViewFocused { e:AccessibilityEvent =>
      val text = utterancesFor(e, addBlank=false).mkString(": ")
      if(text == "")
        if(e.getItemCount > 0 && e.getCurrentItemIndex >= 0)
          speak(Handler.context.getString(R.string.listItem, Handler.context.getText(R.string.image), (e.getCurrentItemIndex+1).toString, e.getItemCount.toString))
        else if(VERSION.SDK_INT >= 14 && e.getSource != null) {
          val source = e.getSource
          rootOf(source).map { root =>
            val descendants = descendantsOf(root)
            val index = descendants.indexOf(source)+1
            if(index > 0)
              speak(Handler.context.getString(R.string.listItem, Handler.context.getText(R.string.image), index.toString, descendants.length.toString))
            else
              speak(Handler.context.getText(R.string.image).toString)
          }.getOrElse(speak(Handler.context.getText(R.string.image).toString))
        } else
          speak(Handler.context.getText(R.string.image).toString)
      else
        speak(Handler.context.getString(R.string.labeledImage, text))
    }
  }

  class IconMenuView extends Handler("com.android.internal.view.menu.IconMenuView") with MenuView

  class ListView extends Handler("android.widget.ListView") {

    onViewFocused { e:AccessibilityEvent =>
      val utterances = utterancesFor(e, stripBlanks = true)
      if(utterances != Nil && e.getCurrentItemIndex != -1)
        speak(Handler.context.getString(R.string.listItem, utterances.mkString(": "), (e.getCurrentItemIndex+1).toString, e.getItemCount.toString))
      else
        if(e.getItemCount == 0)
          speak(Handler.context.getString(R.string.emptyList))
        else if(e.getItemCount == 1)
          speak(Handler.context.getString(R.string.listWithItem))
        else if(e.getItemCount > 1)
          speak(Handler.context.getString(R.string.listWithItems, e.getItemCount.toString))
      true
    }

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

  class ProgressBar extends Handler("android.widget.ProgressBar") {

    onViewFocused { e:AccessibilityEvent =>
      val percent = (e.getCurrentItemIndex.toDouble/e.getItemCount*100).toInt
      speak(utterancesFor(e, addBlank = false, guessLabelIfContentDescriptionMissing = true, providedText=Some(percent+"%")))
    }

    onViewSelected { e:AccessibilityEvent =>
      val percent = (e.getCurrentItemIndex.toFloat/e.getItemCount*100).toInt
      TTS.presentPercentage(percent)
    }

  }

  class RadioButton extends Handler("android.widget.RadioButton") {

    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak(Handler.context.getString(R.string.selected))
      else
        speak(Handler.context.getString(R.string.notSelected))
    }

    onViewFocused { e:AccessibilityEvent =>
      speak(Handler.context.getString(R.string.radioButton, utterancesFor(e, guessLabelIfTextShorterThan = Some(2)).mkString(": ")))
    }

  }

  class RatingBar extends Handler("android.widget.RatingBar") {

    onViewFocused { e:AccessibilityEvent =>
      val label = guessLabelFor(e).getOrElse(Handler.context.getString(R.string.rating))
      val rating = Handler.context.getString(R.string.listItem, label, e.getCurrentItemIndex.toString, e.getItemCount.toString)
      speak(utterancesFor(e, addBlank = false, providedText=Some(rating)))
    }

    onViewSelected { e:AccessibilityEvent =>
      if(VERSION.SDK_INT >= 14) {
        Option(e.getSource).map { source =>
          if(source.isFocused)
            speak(e.getCurrentItemIndex.toString)
          else true
        }.getOrElse(true)
      } else true
    }

  }

  class ScrollView extends Handler("android.widget.ScrollView") {

    onViewFocused { e:AccessibilityEvent => true }

    onViewScrolled { e:AccessibilityEvent =>
      val percent = (if(e.getMaxScrollX > 0 && e.getMaxScrollY == 0)
        e.getScrollX.toFloat/e.getMaxScrollX
      else if(e.getMaxScrollX == 0 && e.getMaxScrollY > 0)
        e.getScrollY.toFloat/e.getMaxScrollY
      else
        0
      )*100
      TTS.presentPercentage(percent)
    }

  }

  class SearchBox extends Handler("android.app.SearchDialog$SearchAutoComplete") {
    onViewFocused { e:AccessibilityEvent =>
      speak(Handler.context.getString(R.string.searchText, utterancesFor(e).mkString(": ")), false)
    }
  }

  class TextView extends Handler("android.widget.TextView") {
    onViewFocused { e:AccessibilityEvent => speak(utterancesFor(e, stripBlanks=true)) }
  }

  class ViewGroup extends Handler("android.view.ViewGroup") {

    onViewFocused { e:AccessibilityEvent => 
      if(VERSION.SDK_INT >= 14) {
        val utterances = utterancesFor(e, stripBlanks = true, addBlank=false)
        if(utterances != Nil)
          speak(utterances)
        else
          Option(e.getSource).map { source =>
            if(interactive_?(source))
              speak(utterances)
            else
              true
          }.getOrElse(speak(utterancesFor(e, stripBlanks = true)))
      } else
        speak(utterancesFor(e, stripBlanks = true))
    }

    onViewHoverEnter { e:AccessibilityEvent =>
      Option(e.getSource).map { source=>
        val utterances = utterancesFor(e, addBlank=false, stripBlanks=true)
        if(utterances != Nil) {
          val textCount = descendantsOf(source).map { v =>
            if((v.getText != null && v.getText.length != 0) || (v.getContentDescription != null && v.getContentDescription.length != 0)) 1 else 0
          }.foldLeft(0) { (acc, v) => acc+v }
          Log.d("spielcheck", "textCount: "+textCount+": Children: "+source.getChildCount+": Interactables: "+interactables(source))
          if(textCount == 0)
            speak(utterances)
          else if(textCount > 1)
            if(interactables(source).size > 0)
              true
            else
              false
          else
            true
        } else true
      }.getOrElse(true)
    }

  }

  class WebView extends Handler("android.webkit.WebView") {

    private def utterancesFor(x:xml.Node):List[String] = {

      def name(n:xml.Node):String =
        n.nameToString(new StringBuilder()).toString

      def recurse(nodes:List[xml.Node]):List[String] = nodes match {
        case Nil => Nil
        case hd :: tl if(name(hd) == "a" && hd.text != null) =>
          hd.text :: Handler.context.getString(R.string.link) :: Nil
        case hd :: tl if(hd.descendant.size == 0 && hd.text != null && hd.text != "") =>
          hd.text :: recurse(tl)
        case hd :: tl => recurse(tl)
      }

      try {
        recurse(x.descendant)
      } catch {
        case e =>
          Log.e("spiel", "Error parsing HTML", e)
          Nil
      }
    }

    onViewSelected { e:AccessibilityEvent =>
      val x = Option(e.getText.map(v => if(v == null) "<span/>" else v)
      .mkString).map { t =>
        if(t == "")
          <span/>
        else
          utils.htmlToXml(t)
      }.getOrElse(<span/>)
      speak(utterancesFor(x))
    }

  }

  /**
   * Default catch-all handler which catches unresolved <code>AccessibilityEvent</code>s.
  */

  class Default extends Handler {

    onNotificationStateChanged { e:AccessibilityEvent =>
      val utterances = utterancesFor(e, addBlank=false, stripBlanks=true)
      if(!utterances.isEmpty) {
        nextShouldNotInterrupt()
        speakNotification(utterances)
      }
      true
    }

    onTouchExplorationGestureEnd { e:AccessibilityEvent => true }

    onTouchExplorationGestureStart { e:AccessibilityEvent => stopSpeaking() }

    onViewClicked { e:AccessibilityEvent => true }

    onViewFocused { e:AccessibilityEvent =>
      val utterances = utterancesFor(e, addBlank=false, stripBlanks=true) match {
        case Nil if(e.getEventType != TYPE_VIEW_HOVER_ENTER) => 
          e.getClassName.toString.split("\\.").last :: Nil
        case u => u
      }
      if(!utterances.isEmpty)
        speak(utterances)
      true
    }

    onViewHoverEnter { e:AccessibilityEvent => Handler.process(e, Some(TYPE_VIEW_FOCUSED)) }

    onViewLongClicked { e:AccessibilityEvent => true }

    onViewScrolled { e:AccessibilityEvent =>
      val utterances = utterancesFor(e, addBlank=false, stripBlanks=true)
      if(!utterances.isEmpty) {
        speak(utterances)
        nextShouldNotInterrupt()
      }
      true
    }

    onViewSelected { e:AccessibilityEvent =>
      val utterances = utterancesFor(e, addBlank=false)
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

    onViewTextChanged { e:AccessibilityEvent => speak(utterancesFor(e, addBlank = false, stripBlanks = true)) }

    private var oldSelectionFrom:Option[Int] = None
    private var oldSelectionTo:Option[Int] = None

    onViewTextSelectionChanged { e:AccessibilityEvent =>
      Option(e.getSource).map(_.getText).foreach { text =>
        val txt = if(e.isPassword) Some("*"*e.getItemCount) else Option(text)
        txt.map { t =>
          //var from = if(e.getFromIndex == t.length) e.getFromIndex-1 else e.getFromIndex
          var from = e.getFromIndex
          var to = if(e.getToIndex == e.getFromIndex && e.getToIndex < t.length)
            e.getToIndex+1
          else
            e.getToIndex
          val width = to-from
          val source = e.getSource
          if(from >= 0 && to >= 0 && source != null && source.isFocused) {
            if(from > to) {
              val tmp = to
              to = from
              from = tmp
            }
            val selection = try {
              t.subSequence(from, to).toString
            } catch {
              case e =>
                Log.d("spiel", "Error determining selection", e)
                ""
            }
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
              val interval = try {
                (if(ost < from)
                  text.subSequence(ost, from)
                else
                  text.subSequence(to, math.min(osf, text.length-1))
                ).toString
              } catch {
                case _ => selection
              }
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
