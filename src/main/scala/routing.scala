package info.spielproject.spiel
package routing

import android.content.Context
import android.os.Vibrator
import android.util.Log

sealed trait Component

case object All extends Component
case class Value(value:String) extends Component

case class HandlerDirective(pkg:Component, cls:Component) {
  def this(pkg:String, cls:String) = this(Value(pkg), Value(cls))
  def this(cls:String) = this(All, Value(cls))
}

case class PayloadDirective(pkg:Value, cls:Value) {
  def this(p:String, c:String) = this(Value(p), Value(c))
}

abstract class Handler[PayloadType](router:Router[_], val directive:Option[HandlerDirective] = None) {

  // Convenience functions for calling TTS, used from scripting subsystem.

  def speak(text:String, interrupt:Boolean) = {
    TTS.speak(text, interrupt)
    true
  }

  def speak(text:String):Boolean = speak(text, !router.myNextShouldNotInterrupt)

  def speak(list:List[String], interrupt:Boolean) = {
    TTS.speak(list, interrupt)
    true
  }

  def speak(list:List[String]):Boolean = speak(list, !router.myNextShouldNotInterrupt)

  def speakNotification(text:String) = {
    TTS.speakNotification(text)
    true
  }

  def speakNotification(text:List[String]) = {
    TTS.speakNotification(text)
    true
  }

  def stopSpeaking() = {
    if(!router.nextShouldNotInterruptCalled)
      TTS.stop()
    true
  }

  /**
   * Indicates that the next <code>AccessibilityEvent</code> should not interrupt speech.
  */

  def nextShouldNotInterrupt() = router.nextShouldNotInterrupt()

  protected def getString(resID:Int) = SpielService.context.getString(resID)

  protected def getString(resID:Int, formatArgs:AnyRef*) = SpielService.context.getString(resID, formatArgs: _*)

  private lazy val vibrator:Vibrator = SpielService.context.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]

  protected def vibrate(millis:Long) = {
    if(Preferences.hapticFeedback_?)
      vibrator.vibrate(millis)
    true
  }

  protected def shortVibration() = vibrate(20)

  def apply(payload:PayloadType):Boolean

}

class Router[PayloadType](before:Option[() => Handler[PayloadType]] = None, after:Option[() => Handler[PayloadType]] = None) {

  var context:Context = null

  // Track and report state of whether next AccessibilityEvent should interrupt speech.
  protected[routing] var myNextShouldNotInterrupt = false
  def shouldNextInterrupt = !myNextShouldNotInterrupt

  protected[routing] var nextShouldNotInterruptCalled = false

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

  def apply(c:Context) {
    context = c
  }

  private val table = collection.mutable.Map[HandlerDirective, Handler[PayloadType]]()

  def register(h:Handler[PayloadType]) = h.directive.foreach { d =>
    table += (d -> h)
  }

  def unregister(h:Handler[PayloadType]) = {
    table.filter(_._2 != h)
  }

  def unregisterPackage(pkg:String) = {
    table.filter(_._1.pkg != Value(pkg))
  }

  def dispatch(payload:PayloadType, directive:PayloadDirective) = {

    var alreadyCalled:List[Handler[PayloadType]] = Nil

    def dispatchTo(h:Handler[PayloadType]):Boolean = {
      if(alreadyCalled.contains(h)) {
        Log.d("spiel", "Already called "+h.getClass.getName+", skipping.")
        false
      } else {
        Log.d("spiel", "Dispatching to "+h.getClass.getName)
        alreadyCalled ::= h
        h(payload)
      }
    }

    // Always run this Handler before an event. This cannot block others from executing.
    def dispatchToBefore() {
      Log.d("spiel", "Before dispatch")
      before.foreach(_()(payload))
    }

    // Let's check if there's a Presenter for this exact package and 
    // class.
    def dispatchToExact() = {
      Log.d("spiel", "Exact match dispatch")
      table.get(HandlerDirective(directive.pkg, directive.cls)).map(dispatchTo(_)).getOrElse(false)
    }

    // Now check for just the class name.
    def dispatchToClass() = {
      Log.d("spiel", "Class match dispatch")
      table.find { v =>
        v._1.pkg == All && v._1.cls != All && v._1.cls == directive.cls
      }.map { v =>
        dispatchTo(v._2)
      }.getOrElse(false)
    }

    // Check Handler superclasses.
    def dispatchToSubclass() = {
      Log.d("spiel", "Subclass match dispatch")

      val originator = utils.classForName(directive.cls.value, directive.pkg.value)
      originator.flatMap { o =>
        val a = utils.ancestors(o)
        Log.d("spiel", "Ancestors: "+a.mkString(", "))
        val candidates = table.filter { h =>
          h._1.pkg == All && h._1.cls != All && h._1.cls != Value("")
        }.toList.map { h =>
          val target:Class[_] = try {
            context.getClassLoader.loadClass(h._1.cls.asInstanceOf[Value].value)
          } catch {
            case e:ClassNotFoundException => o
          }
          val i = a.indexOf(target)
          val index = if(i == -1 && target.isInterface && target.isAssignableFrom(o))
            0
          else if(i == -1)
            -2
          else i+1
          (index, h)
        }.filter(_._1 >= 0).sortBy((v:Tuple2[Int, _]) => v._1)
        //Log.d("spielcheck", "Candidates: "+candidates)
        Some(candidates.exists { v =>
          dispatchTo(v._2._2)
        })
      }.getOrElse(false)
    }

    // Now dispatch to the default, catch-all Handler.
    def dispatchToDefault() = {
      Log.d("spiel", "Default dispatch")
      val handler = table.get(HandlerDirective(All, All))
      .orElse(table.get(new HandlerDirective("", "")))
      
      handler.map { h =>
        if(!alreadyCalled.contains(h))
          h(payload)
        else
          false
      }.getOrElse(false)
    }

    def dispatchToAfter() {
      Log.d("spiel", "After dispatch")
      after.foreach(_()(payload))
    }

    dispatchToBefore()
    val rv = dispatchToExact() || dispatchToClass() || dispatchToSubclass() || dispatchToDefault()
    dispatchToAfter()
    rv
  }

}
