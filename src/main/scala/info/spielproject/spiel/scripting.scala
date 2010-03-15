package info.spielproject.spiel.scripting

import android.util.Log
import android.view.accessibility.AccessibilityEvent

import org.mozilla.javascript.{Context, Function, RhinoException, ScriptableObject}

import info.spielproject.spiel._
import handlers.{Callback, Handler}
import tts.TTS

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


object Scripter {

  private var myCx:Context = null
  private var myScope:ScriptableObject = null

  def context = myCx
  def scope = myScope

  def apply(service:SpielService)  {
    myCx = Context.enter
    myCx.setOptimizationLevel(-1)
    myScope = myCx.initStandardObjects()

    val wrappedHandler = Context.javaToJS(Handler, myScope)
    ScriptableObject.putProperty(myScope, "Handler", wrappedHandler)

    val wrappedScripter = Context.javaToJS(this, myScope)
    ScriptableObject.putProperty(myScope, "Scripter", wrappedScripter)

    val wrappedTTS = Context.javaToJS(TTS, myScope)
    ScriptableObject.putProperty(myScope, "TTS", wrappedTTS)

    def run(code:String, filename:String) = try {
      myCx.evaluateString(myScope, code, filename, 1, null)
    } catch {
      case e:RhinoException => Log.e(this.toString, e.getMessage)
      case e => Log.e("spiel", e.toString)
    }

    val assets = service.getAssets
    def runScriptFile(f:String) = {
      val is = assets.open("scripts/"+f)
      val a = is.available
      val b = new Array[Byte](a)
      is.read(b)
      val code = new String(b)
      run(code, f)
    }
    runScriptFile("api.js")
    for(fn <- assets.list("scripts")) {
      if(fn != "api.js") runScriptFile(fn)
    }
    Context.exit
    true
  }

  def registerHandlerFor(pkg:String, cls:String, s:Object) {
    Log.d("spiel", "Registering handler for "+pkg+":"+cls)
    val scr = s.asInstanceOf[ScriptableObject]
    val h = new Handler(pkg, cls)

    scr.getIds.foreach { property =>

      val id = property.asInstanceOf[String]
      val chars = id.substring(2, id.length).toCharArray
      chars(0) = chars(0).toLower
      val func = new String(chars)

      if(Handler.dispatchers.values.contains(func)) {
        val f = scr.get(id, myScope)
        if(f.isInstanceOf[Function]) {
          Log.d("spiel", "Registering dispatch for "+func)
          h.dispatches(func) = new RhinoCallback(f.asInstanceOf[Function])
        }
      } else
        Log.e("spiel", func+" is not a valid handler. Skipping.")
    }

    h
  }

}
