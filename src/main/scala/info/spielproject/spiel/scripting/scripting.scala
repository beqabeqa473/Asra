package info.spielproject.spiel.scripting

import android.util.Log
import android.view.accessibility.AccessibilityEvent

import org.mozilla.javascript.{Context, Function, RhinoException, ScriptableObject}

import info.spielproject.spiel.handlers.Handler

object Scripter {

  private var myCx:Context = null
  private var myScope:ScriptableObject = null

  def context = myCx
  def scope = myScope

  def apply() = {
    myCx = Context.enter
    myCx.setOptimizationLevel(-1)
    myScope = myCx.initStandardObjects()

    val wrappedHandler = Context.javaToJS(Handler, myScope)
    ScriptableObject.putProperty(myScope, "Handler", wrappedHandler)

    val wrappedScripter = Context.javaToJS(this, myScope)
    ScriptableObject.putProperty(myScope, "Scripter", wrappedScripter)

    def run(code:String, filename:String) = try {
      myCx.evaluateString(myScope, code, filename, 1, null)
    } catch {
      case e:RhinoException => Log.e(this.toString, e.getMessage)
      case e => {
        Log.e(this.toString, e.toString)
        Log.e(this.toString, e.getStackTrace.toString)
      }
    }

    val assets = SpielService().getAssets
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
    val scr = s.asInstanceOf[ScriptableObject]
    Log.d("spiel", "Registering "+scr+" as handler for "+cls+" in "+pkg)

    def getFunctionFor(handler:String):Option[Function] = {
      val h = if(handler == "default")
        "byDefault"
      else
        "on"+handler.capitalize
      val f = scr.get(h, myScope)
      if(f.isInstanceOf[Function])
        Some(f.asInstanceOf[Function])
      else
        None
    }

    val h = new Handler(pkg, cls) {

      getFunctionFor("notificationStateChanged") match {
        case Some(f) => onNotificationStateChanged(f)
        case None =>
      }

      getFunctionFor("viewClicked") match {
        case Some(f) => onViewClicked(f)
        case None =>
      }

      getFunctionFor("viewFocused") match {
        case Some(f) => onViewFocused(f)
        case None =>
      }

      getFunctionFor("viewSelected") match {
        case Some(f) => onViewSelected(f)
        case None =>
      }

      getFunctionFor("viewTextChanged") match {
        case Some(f) => onViewTextChanged(f)
        case None =>
      }

      getFunctionFor("windowStateChanged") match {
        case Some(f) => onWindowStateChanged(f)
        case None =>
      }

      getFunctionFor("default") match {
        case Some(f) => byDefault(f)
        case None =>
      }

    }

    h
  
  }

}
