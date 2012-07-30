package info.spielproject.spiel
package routing

import android.content.Context
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

abstract class Handler[PayloadType](val directive:Option[HandlerDirective] = None) {
  def apply(payload:PayloadType):Boolean
}

class Router[PayloadType](before:Option[() => Handler[PayloadType]] = None, after:Option[() => Handler[PayloadType]] = None) {

  var context:Context = null

  def apply(c:Context) {
    context = c
  }

  private var table = Map[HandlerDirective, Handler[PayloadType]]()

  def register(h:Handler[PayloadType]) = h.directive.foreach { d =>
    table += (d -> h)
  }

  def unregister(h:Handler[PayloadType]) = {
    table = table.filter(_._2 != h)
  }

  def unregisterPackage(pkg:String) = {
    table = table.filter(_._1.pkg != Value(pkg))
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
      table.filter { v =>
        v._1.pkg == All && v._1.cls != All && v._1.cls == directive.cls
      }.headOption.map { v =>
        dispatchTo(v._2)
      }.getOrElse(false)
    }

    // Check Handler superclasses.
    def dispatchToSubclass() = {
      Log.d("spiel", "Subclass match dispatch")

      val originator = utils.classForName(directive.cls.value, directive.pkg.value)
      originator.flatMap { o =>
        val a = utils.ancestors(o)
        val candidates = table.filter { h =>
          h._1.pkg == All && h._1.cls != All && h._1.cls != Value("")
        }.toList.map { h =>
          val target:Class[_] = try {
            context.getClassLoader.loadClass(h._1.cls.asInstanceOf[Value].value)
          } catch {
            case e:ClassNotFoundException => o
          }
          (a.indexOf(target), h)
        }.filter(_._1 >= 0).sortBy((v:Tuple2[Int, _]) => v._1)
        Some(candidates.exists { v =>
          dispatchTo(v._2._2)
        })
      }.getOrElse(false)
    }

    // Now dispatch to the default, catch-all Handler.
    def dispatchToDefault() = {
      Log.d("spiel", "Default dispatch")
      table.get(HandlerDirective(All, All))
      .orElse(table.get(new HandlerDirective("", "")))
      .map(_(payload)).getOrElse(false)
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
