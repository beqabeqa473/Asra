package info.spielproject.spiel
package presenters

import collection.JavaConversions._

import android.app.{ActivityManager, Service}
import android.content.Context
import android.os.Build.VERSION
import android.os.{SystemClock, Vibrator}
import android.util.Log
import android.view.accessibility.{AccessibilityEvent, AccessibilityNodeInfo}
import AccessibilityEvent._
import AccessibilityNodeInfo._

import routing._

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

  val activityName = Presenter.currentActivity

  override val toString = {
    val eventType = Presenter.dispatchers.get(e.getEventType).getOrElse("Unknown")
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

case class EventPayload(event:AccessibilityEvent, eventType:Int)

/**
 * Maps a given <code>Callback</code> to events originating from a given 
 * package and class.
 *
 * Passing a blank string for either indicates events from all packages or all classes.
*/

class Presenter(directive:Option[HandlerDirective] = None) extends Handler[EventPayload](directive) {

  def this(pkg:String, cls:String) = this(Some(new HandlerDirective(pkg, cls)))
  def this(c:String) = this(Some(new HandlerDirective(c)))

  import Presenter._

  Presenter.register(this)

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

  def nextShouldNotInterrupt() = Presenter.nextShouldNotInterrupt()

  // Convenience method for converting functions to callbacks.

  implicit def toNativeCallback(f:AccessibilityEvent => Boolean):NativeCallback = new NativeCallback(f)

  /**
   * Maps strings to <code>Callback</code> classes for specific event types 
   * related to the specified package and class.
  */

  val dispatches = collection.mutable.Map[String, Callback]()

  // Register <code>Callback</code> instances for the various <code>AccessibilityEvent</code> types.

  protected def onAnnouncement(c:Callback) = dispatches(dispatchers(TYPE_ANNOUNCEMENT)) = c

  protected def onNotificationStateChanged(c:Callback) = dispatches(dispatchers(TYPE_NOTIFICATION_STATE_CHANGED)) = c

  protected def onTouchExplorationGestureEnd(c:Callback) = dispatches(dispatchers(TYPE_TOUCH_EXPLORATION_GESTURE_END)) = c

  protected def onTouchExplorationGestureStart(c:Callback) = dispatches(dispatchers(TYPE_TOUCH_EXPLORATION_GESTURE_START)) = c

  protected def onViewAccessibilityFocusCleared(c:Callback) = dispatches(dispatchers(TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)) = c

  protected def onViewAccessibilityFocused(c:Callback) = dispatches(dispatchers(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) = c

  protected def onViewClicked(c:Callback) = dispatches(dispatchers(TYPE_VIEW_CLICKED)) = c

  protected def onViewFocused(c:Callback) = dispatches(dispatchers(TYPE_VIEW_FOCUSED)) = c

  protected def onViewHoverEnter(c:Callback) = dispatches(dispatchers(TYPE_VIEW_HOVER_ENTER)) = c

  protected def onViewHoverExit(c:Callback) = dispatches(dispatchers(TYPE_VIEW_HOVER_EXIT)) = c

  protected def onViewLongClicked(c:Callback) = dispatches(dispatchers(TYPE_VIEW_LONG_CLICKED)) = c

  protected def onViewScrolled(c:Callback) = dispatches(dispatchers(TYPE_VIEW_SCROLLED)) = c

  protected def onViewSelected(c:Callback) = dispatches(dispatchers(TYPE_VIEW_SELECTED)) = c

  protected def onViewTextChanged(c:Callback) = dispatches(dispatchers(TYPE_VIEW_TEXT_CHANGED)) = c

  protected def onViewTextSelectionChanged(c:Callback) = dispatches(dispatchers(TYPE_VIEW_TEXT_SELECTION_CHANGED)) = c

  protected def onViewTextTraversedAtMovementGranularity(c:Callback) = dispatches(dispatchers(TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY)) = c

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
        rv :::= Option(e.getSource).flatMap(_.label).map(_.getText.toString).map { v =>
          removeBlank()
          List(v)
        }.getOrElse(Nil)
      else if(guessLabelIfContentDescriptionMissing && e.getContentDescription == null)
        rv :::= Option(e.getSource).flatMap(_.label).map(_.getText.toString).map { v =>
          removeBlank()
          List(v)
        }.getOrElse(Nil)
      else guessLabelIfTextShorterThan.foreach { v =>
        if(VERSION.SDK_INT >= 16 || text.length < v)
          rv :::= Option(e.getSource).flatMap(_.label).map(_.getText.toString).map { v =>
            removeBlank()
            List(v)
          }.getOrElse(Nil)
      }
    }
    rv
  }

  protected def interactables(source:AccessibilityNodeInfo) = 
    (source :: source.descendants).filter(_.interactive_?)

  /**
   * Run a given <code>AccessibilityEvent</code> through this <code>Presenter</code>
   *
   * @return <code>true</code> if processing should stop, <code>false</code> otherwise.
  */

  def apply(payload:EventPayload):Boolean = {

    def dispatchTo(callback:String):Boolean = dispatches.get(callback).map(_(payload.event)).getOrElse(false)

    val fallback = dispatchers.get(payload.eventType).map(dispatchTo(_)).getOrElse(false)

    if(!fallback)
      dispatchTo("default")
    else fallback
  }

}

// Now, finally, we reach the presentation logic for generic Android widgets.

/**
 * Encapsulates generic handling for multiple types of buttons.
*/

trait GenericButtonPresenter extends Presenter {
  onViewFocused { e:AccessibilityEvent =>
    val text = utterancesFor(e, addBlank=false).mkString(": ")
    if(text == "") {
      if(VERSION.SDK_INT >= 14) {
        Option(e.getSource).flatMap { source =>
          val descendants = source.root.descendants
          val index = descendants.indexOf(source)+1
          if(index > 0)
            Some(speak(getString(R.string.listItem, getString(R.string.button), index.toString, descendants.size.toString)))
          else None
        }.getOrElse(speak(getString(R.string.button).toString))
        true
      } else
        speak(getString(R.string.button).toString)
    } else
      speak(getString(R.string.labeledButton, text))
  }
}

/**
 * Run before every event. Cannot pre-empt other Presenters.
*/

object Before extends Presenter {

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

object After extends Presenter {

  onViewFocused { e:AccessibilityEvent =>
    if(VERSION.SDK_INT >= 14)
      Option(e.getSource).foreach { source =>
        if(source.getChildCount == 0 && source.interactive_? && !e.isEnabled)
          speak(getString(R.string.disabled), false)
      }
    false
  }

}

/**
 * By placing all <code>Presenter</code> classes here, we can use the power of 
 * reflection to avoid manually registering each and every one.
*/

class Presenters {

  class ActionMenuItemView extends Presenter("com.android.internal.view.menu.ActionMenuItemView") {

    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e, stripBlanks=true) ::: ("Menu item" :: Nil))
    }

    onViewHoverEnter { e:AccessibilityEvent => Presenter.process(e, Some(TYPE_VIEW_FOCUSED)) }

  }

  class AdapterView extends Presenter("android.widget.AdapterView") {

    private def focusedOnList(e:AccessibilityEvent) = {
      val utterances = utterancesFor(e, stripBlanks = true)
      if(utterances != Nil && e.getCurrentItemIndex != -1)
        speak(getString(R.string.listItem, utterances.mkString(": "), (e.getCurrentItemIndex+1).toString, e.getItemCount.toString))
      else
        if(e.getItemCount == 0)
          speak(getString(R.string.emptyList))
        else if(e.getItemCount == 1)
          speak(getString(R.string.listWithItem))
        else if(e.getItemCount > 1)
          speak(getString(R.string.listWithItems, e.getItemCount.toString))
      true
    }

    onViewFocused { e:AccessibilityEvent => focusedOnList(e) }

    onViewScrolled { e:AccessibilityEvent =>
      if(e.getToIndex >= 0 && e.getItemCount > 0) {
        val percentage = e.getToIndex.toDouble/e.getItemCount
        TTS.tick(Some(0.5+percentage/2))
      }
      true
    }

    onViewSelected { e:AccessibilityEvent =>
      if(e.getCurrentItemIndex >= 0)
        speak(getString(R.string.listItem, utterancesFor(e).mkString(": "), (e.getCurrentItemIndex+1).toString, e.getItemCount.toString))
      else if(e.getItemCount == 0)
        speak(getString(R.string.emptyList))
      true
    }

    onWindowStateChanged { e:AccessibilityEvent => focusedOnList(e) }

  }

  class Button extends Presenter("android.widget.Button") with GenericButtonPresenter

  class CheckBox extends Presenter("android.widget.CheckBox") {

    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak(getString(R.string.checked))
      else
        speak(getString(R.string.notChecked))
    }

    onViewFocused { e:AccessibilityEvent =>
      speak(getString(R.string.checkbox, utterancesFor(e, addBlank=false, guessLabelIfTextShorterThan = Some(2)).mkString(": ")))
      if(VERSION.SDK_INT >= 16)
        speak(getString((if(e.isChecked) R.string.checked else R.string.notChecked)), false)
      true
    }

  }

  class Dialog extends Presenter("android.app.Dialog") {
    onWindowStateChanged { e:AccessibilityEvent =>
      speak(utterancesFor(e, stripBlanks=true).mkString(": "), true)
      nextShouldNotInterrupt()
    }
  }

  class EditText extends Presenter("android.widget.EditText") {

    onViewFocused { e:AccessibilityEvent =>
      speak(utterancesFor(e, addBlank=false, guessLabelIfContentDescriptionMissing = true), false)
      if(e.isPassword) {
        speak(getString(R.string.password))
        val length = if(e.getItemCount > 0)
          e.getItemCount
        else if(e.getText != null && e.getText.mkString.length > 0)
          e.getText.mkString.length
        else 0
        if(length > 0)
          speak("*"*length, false)
      }
      speak(getString(R.string.editText), false)
    }

    onViewTextChanged { e:AccessibilityEvent =>
      if(e.getAddedCount > 0 || e.getRemovedCount > 0) {
        if(e.isPassword)
          speak("*", true)
        else {
          val text = e.getText.mkString
          val before = e.getBeforeText.toString
          if(before == text)
            true
          else {
            val diff = if(before.length > text.length) before.diff(text) else text.diff(before)
            if(diff.length == 1 && before.length < text.length) {
              var flush = true
              if(Preferences.echoByChar) {
                speak(diff, true)
                flush = false
              }
              if(Preferences.echoByWord && !Character.isLetterOrDigit(diff(0))) {
                val word = (text.substring(0, e.getFromIndex)
                .reverse.takeWhile(_.isLetterOrDigit).reverse+diff).trim
                if(word.length > 1)
                  speak(word, flush)
                }
              true
            } else
              speak(diff, true)
          }
        }
      } else true
    }

  }

  trait MenuView {
    self: Presenter =>
    onViewFocused { e:AccessibilityEvent => speak(getString(R.string.menu)) }
  }

  class ExpandedMenuView extends Presenter("com.android.internal.view.menu.ExpandedMenuView") with MenuView

  class HomeView extends Presenter("com.android.internal.widget.ActionBarView$HomeView") {

    private def process(e:AccessibilityEvent) = {
      val utterances = utterancesFor(e, addBlank = false)
      if(utterances != Nil)
        speak(utterances.mkString(" "))
      true
    }

    onViewAccessibilityFocused { e:AccessibilityEvent => process(e) }

    onViewFocused { e:AccessibilityEvent => process(e) }

    onViewHoverEnter { e:AccessibilityEvent => process(e) }

  }

  class ImageButton extends Presenter("android.widget.ImageButton") with GenericButtonPresenter

  class ImageView extends Presenter("android.widget.ImageView") {
    onViewFocused { e:AccessibilityEvent =>
      val text = utterancesFor(e, addBlank=false).mkString(": ")
      if(text == "")
        if(e.getItemCount > 0 && e.getCurrentItemIndex >= 0)
          speak(getString(R.string.listItem, getString(R.string.image), (e.getCurrentItemIndex+1).toString, e.getItemCount.toString))
        else if(VERSION.SDK_INT >= 14 && e.getSource != null) {
          val source = e.getSource
          val descendants = source.root.descendants
          val index = descendants.indexOf(source)+1
          if(index > 0)
            speak(getString(R.string.listItem, getString(R.string.image), index.toString, descendants.length.toString))
          else
            speak(getString(R.string.image).toString)
        } else
          speak(getString(R.string.image).toString)
      else
        speak(getString(R.string.labeledImage, text))
    }
  }

  class IconMenuView extends Presenter("com.android.internal.view.menu.IconMenuView") with MenuView

  class Menu extends Presenter("com.android.internal.view.menu.MenuView") {

    onViewSelected { e:AccessibilityEvent =>
      speak(utterancesFor(e))
    }

    onWindowStateChanged { e:AccessibilityEvent =>
      if(e.getCurrentItemIndex == -1) {
        speak(getString(R.string.menu), true)
        nextShouldNotInterrupt()
      }
      true
    }

  }

  class ProgressBar extends Presenter("android.widget.ProgressBar") {

    onViewFocused { e:AccessibilityEvent =>
      val percent = (e.getCurrentItemIndex.toDouble/e.getItemCount*100).toInt
      speak(utterancesFor(e, addBlank = false, guessLabelIfContentDescriptionMissing = true, providedText=Some(percent+"%")))
    }

    onViewSelected { e:AccessibilityEvent =>
      val percent = (e.getCurrentItemIndex.toFloat/e.getItemCount*100).toInt
      TTS.presentPercentage(percent)
    }

  }

  class RadioButton extends Presenter("android.widget.RadioButton") {

    onViewClicked { e:AccessibilityEvent =>
      if(e.isChecked)
        speak(getString(R.string.selected))
      else
        speak(getString(R.string.notSelected))
    }

    onViewFocused { e:AccessibilityEvent =>
      speak(getString(R.string.radioButton, utterancesFor(e, guessLabelIfTextShorterThan = Some(2)).mkString(": ")))
    }

  }

  class RatingBar extends Presenter("android.widget.RatingBar") {

    onViewFocused { e:AccessibilityEvent =>
      val label = Option(e.getSource).flatMap(_.label).map(_.getText.toString).getOrElse(getString(R.string.rating))
      val rating = getString(R.string.listItem, label, e.getCurrentItemIndex.toString, e.getItemCount.toString)
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

  class ScrollView extends Presenter("android.widget.ScrollView") {

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

  class SearchBox extends Presenter("android.app.SearchDialog$SearchAutoComplete") {
    onViewFocused { e:AccessibilityEvent =>
      speak(getString(R.string.searchText, utterancesFor(e).mkString(": ")), false)
    }
  }

  class TextView extends Presenter("android.widget.TextView") {
    onViewFocused { e:AccessibilityEvent => speak(utterancesFor(e, stripBlanks=true)) }
  }

  class ViewGroup extends Presenter("android.view.ViewGroup") {

    onViewFocused { e:AccessibilityEvent => 
      if(VERSION.SDK_INT >= 14) {
        val utterances = utterancesFor(e, stripBlanks = true, addBlank=false)
        if(utterances != Nil)
          speak(utterances)
        else
          Option(e.getSource).map { source =>
            if(source.interactive_?)
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
          val textCount = source.descendants.map { v =>
            if((v.getText != null && v.getText.length != 0) || (v.getContentDescription != null && v.getContentDescription.length != 0)) 1 else 0
          }.foldLeft(0) { (acc, v) => acc+v }
          //Log.d("spielcheck", "textCount: "+textCount+": Children: "+source.getChildCount+": Interactables: "+interactables(source))
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

  class WebView extends Presenter("android.webkit.WebView") {

    private def utterancesFor(x:xml.Node):List[String] = {

      def name(n:xml.Node):String =
        n.nameToString(new StringBuilder()).toString

      def recurse(nodes:List[xml.Node]):List[String] = nodes match {
        case Nil => Nil
        case hd :: tl if(name(hd) == "a" && hd.text != null) =>
          hd.text :: getString(R.string.link) :: Nil
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
   * Default catch-all Presenter which catches unresolved <code>AccessibilityEvent</code>s.
  */

  class Default extends Presenter(Some(HandlerDirective(All, All))) {

    onAnnouncement { e:AccessibilityEvent => speak(utterancesFor(e, addBlank = false, stripBlanks = true)) }

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

    onViewAccessibilityFocused { e:AccessibilityEvent => Presenter.process(e, Some(TYPE_VIEW_FOCUSED)) }

    onViewFocused { e:AccessibilityEvent =>
      val utterances = utterancesFor(e, addBlank=false, stripBlanks=true) match {
        case Nil if(!List(TYPE_VIEW_ACCESSIBILITY_FOCUSED, TYPE_VIEW_HOVER_ENTER).contains(e.getEventType)) => 
          e.getClassName.toString.split("\\.").last :: Nil
        case u => u
      }
      if(!utterances.isEmpty)
        speak(utterances)
      true
    }

    onViewHoverEnter { e:AccessibilityEvent => Presenter.process(e, Some(TYPE_VIEW_FOCUSED)) }

    onViewHoverExit { e:AccessibilityEvent => true }

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
            speak(getString(R.string.item, utterances.mkString(" ")))
          else if(e.getItemCount >= 0)
            speak(getString(R.string.items, utterances.mkString(" "), e.getItemCount.toString))
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
            }).getOrElse {
              if(selection == "")
                true
              else
                speak(selection.toString, true)
            }
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

    onViewTextTraversedAtMovementGranularity { e:AccessibilityEvent =>
      val text = e.getText.mkString
      if(e.getToIndex <= text.length)
        speak(text.substring(e.getFromIndex, e.getToIndex), true)
      true
    }

    onWindowContentChanged { e:AccessibilityEvent => true }

    onWindowStateChanged { e:AccessibilityEvent =>
      speak(utterancesFor(e, addBlank = false, stripBlanks = true), true)
      nextShouldNotInterrupt()
    }

    byDefault { e:AccessibilityEvent =>
      //Log.d("spiel", "Unhandled event: "+e.toString)
      speak(utterancesFor(e, addBlank = false, stripBlanks = true))
    }

  }
}

/**
 * Companion for <code>Presenter</code> class.
*/

object Presenter extends Router[EventPayload](Some(() => Before), Some(() => After)) {

  // Track and report state of whether next AccessibilityEvent should interrupt speech.
  private var myNextShouldNotInterrupt = false
  def shouldNextInterrupt = !myNextShouldNotInterrupt

  private var nextShouldNotInterruptCalled = false

  /**
   * In some instances, speech for the next <code>AccessibilityEvent</code> 
   * shouldn't interrupt. Calling this method from a presenter indicates this 
   * to be the case.
  */

  def nextShouldNotInterrupt() = {
    Log.d("spiel", "Next accessibility event should not interrupt speech.")
    nextShouldNotInterruptCalled = true
    myNextShouldNotInterrupt = true
    true
  }

  private lazy val vibrator:Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]

  /**
   * Initialize presenters for the given <code>Context</code>.
  */

  override def apply(c:Context) {
    super.apply(c)
    // By iterating through the members of this class, we can add presenters
    // without manual registration.
    utils.instantiateAllMembers(classOf[Presenters])
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

    val payload = EventPayload(e, eType)
    val directive = new PayloadDirective(e.getPackageName.toString, e.getClassName.toString)

    dispatch(payload, directive)

    if(!nextShouldNotInterruptCalled && eventType == None)
      myNextShouldNotInterrupt = false

    true
  }

  /**
   * Map of <code>AccessibilityEvent</code> types to more human-friendly strings.
  */

  val dispatchers = Map(
    TYPE_ANNOUNCEMENT -> "announcement",
    TYPE_NOTIFICATION_STATE_CHANGED -> "notificationStateChanged",
    TYPE_TOUCH_EXPLORATION_GESTURE_END -> "touchExplorationGestureEnd",
    TYPE_TOUCH_EXPLORATION_GESTURE_START -> "touchExplorationGestureStart",
    TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> "viewAccessibilityFocusCleared",
    TYPE_VIEW_ACCESSIBILITY_FOCUSED -> "viewAccessibilityFocused",
    TYPE_VIEW_CLICKED -> "viewClicked",
    TYPE_VIEW_FOCUSED -> "viewFocused",
    TYPE_VIEW_HOVER_ENTER -> "viewHoverEnter",
    TYPE_VIEW_HOVER_EXIT -> "viewHoverExit",
    TYPE_VIEW_LONG_CLICKED -> "viewLongClicked",
    TYPE_VIEW_SCROLLED -> "viewScrolled",
    TYPE_VIEW_SELECTED -> "viewSelected",
    TYPE_VIEW_TEXT_CHANGED -> "viewTextChanged",
    TYPE_VIEW_TEXT_SELECTION_CHANGED -> "viewTextSelectionChanged",
    TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> "viewTextTraversedAtMovementGranularity",
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
