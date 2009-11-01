package info.thewordnerd.spiel.scripting

import android.util.Log
import android.view.accessibility.AccessibilityEvent

import org.mozilla.javascript.{Context, RhinoException, ScriptableObject}

import presenters._

object Scripter {

  private var myCx:Context = null
  private var myScope:ScriptableObject = null

  def context = myCx
  def scope = myScope

  def apply() = {
    myCx = Context.enter
    myCx.setOptimizationLevel(-1)
    myScope = myCx.initStandardObjects()
    val wrappedTTS = Context.javaToJS(TTS, myScope)
    ScriptableObject.putProperty(myScope, "TTS", wrappedTTS)

    ScriptableObject.putProperty(myScope, "NotificationStateChanged", Context.javaToJS(NotificationStateChanged, myScope))
    ScriptableObject.putProperty(myScope, "ViewClicked", Context.javaToJS(ViewClicked, myScope))
    ScriptableObject.putProperty(myScope, "ViewFocused", Context.javaToJS(ViewFocused, myScope))
    ScriptableObject.putProperty(myScope, "ViewSelected", Context.javaToJS(ViewSelected, myScope))
    ScriptableObject.putProperty(myScope, "ViewTextChanged", Context.javaToJS(ViewTextChanged, myScope))
    ScriptableObject.putProperty(myScope, "WindowStateChanged", Context.javaToJS(WindowStateChanged, myScope))

    def run(code:String, filename:String) = try {
      myCx.evaluateString(myScope, code, filename, 1, null)
    } catch {
      case e:RhinoException => Log.e(this.toString, e.getMessage)
      case e => Log.e(this.toString, e.getStackTrace.toString)
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
}
