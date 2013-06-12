package info.spielproject.spiel
package routing

import android.content.Context
import android.os.Vibrator
import android.util.Log

sealed trait Component

case object All extends Component
case class Value(value:String) extends Component

case class Directive(pkg:Component, cls:Component) {
  def this(pkg:String, cls:String) = this(Value(pkg), Value(cls))
  def this(cls:String) = this(All, Value(cls))
}

abstract class Handler[PayloadType](router:Router[_], val directive:Option[Directive] = None) {

  // Convenience functions for calling TTS, used from scripting subsystem.

  def speak(text:String, interrupt:Boolean) = {
    TTS.speak(text, interrupt)
    router.spoke = true
    true
  }

  def speak(text:String):Boolean = speak(text, !router.myNextShouldNotInterrupt)

  def speak(list:List[String], interrupt:Boolean) = {
    TTS.speak(list, interrupt)
    router.spoke = true
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

  private[spiel] var spoke = false

  private val table = collection.mutable.Map[Directive, Handler[PayloadType]]()

  def register(h:Handler[PayloadType]) = h.directive.foreach { d =>
    table += (d -> h)
  }

  def unregister(h:Handler[PayloadType]) = {
    table.filter(_._2 != h)
  }

  def unregisterPackage(pkg:String) = {
    table.filter(_._1.pkg != Value(pkg))
  }

  def dispatch(payload:PayloadType, directive:Directive) = {

    var alreadyCalled:List[Handler[PayloadType]] = Nil

    def dispatchTo(h:Handler[PayloadType], cache:Boolean = true):Boolean = {
      if(alreadyCalled.contains(h)) {
        Log.d("spiel", "Already called "+h.getClass.getName+", skipping.")
        false
      } else {
        Log.d("spiel", "Dispatching to "+h.getClass.getName)
        alreadyCalled ::= h
        val rv = h(payload)
        if(rv && table.get(directive) == None && cache) {
          Log.d("spiel", "Caching "+h.getClass.getName)
          table += directive -> h
        }
        rv
      }
    }

    // Always run this Handler before an event.
    def dispatchToBefore() = {
      Log.d("spiel", "Before dispatch")
      before.map(_()(payload)).getOrElse(false)
    }

    // Let's check if there's a Presenter for this exact package and 
    // class.
    def dispatchToExact() = {
      Log.d("spiel", "Exact match dispatch")
      table.get(directive).map(dispatchTo(_)).getOrElse(false)
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
    def dispatchToSubclass():Boolean = {

      if(directive.cls == All)
        return false

      Log.d("spiel", "Subclass match dispatch")

      val cls = directive.cls.asInstanceOf[Value].value
      val pkg = directive.pkg match {
        case All => ""
        case Value(v) => v
      }

      val originator = utils.classForName(cls, pkg)
      originator.flatMap { o =>
        val a = utils.ancestors(o)
        //Log.d("spiel", "Ancestors: "+a.mkString(", "))
        val candidates = table.filter { h =>
          h._1.pkg == All && h._1.cls != All && h._1.cls != Value("")
        }.toList.map { h =>
          utils.classForName(h._1.cls.asInstanceOf[Value].value).map { target =>
            val i = a.indexOf(target)
            val index = if(i == -1 && target.isInterface && target.isAssignableFrom(o))
              0
            else if(i >= 0)
              i+1
            else i
            (index, h)
          }
        }.flatten.filter(_._1 >= 0).sortBy((v:Tuple2[Int, _]) => v._1)
        //Log.d("spielcheck", "Candidates: "+candidates)
        Some(candidates.exists { v =>
          dispatchTo(v._2._2)
        })
      }.getOrElse(false)
    }

    // Now dispatch to the default, catch-all Handler.
    def dispatchToDefault() = {
      Log.d("spiel", "Default dispatch")
      val handler = table.get(Directive(All, All))
      .orElse(table.get(new Directive("", "")))
      handler.map(dispatchTo(_, false)).getOrElse(false)
    }

    def dispatchToAfter() = {
      Log.d("spiel", "After dispatch")
      after.map(_()(payload)).getOrElse(false)
    }

    val rv = dispatchToBefore() || dispatchToExact() || dispatchToClass() || dispatchToSubclass() || dispatchToDefault()
    dispatchToAfter()

    rv
  }

}
