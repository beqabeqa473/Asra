package info.thewordnerd.spiel.scripting

import android.util.Log
import android.view.accessibility.AccessibilityEvent

import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

trait AccessibilityEventHandler {
  def run(e:AccessibilityEvent):Boolean
}

object Scripter {

  def apply() = {
    val cx = Context.enter
    cx.setOptimizationLevel(-1)
    val  scope = cx.initStandardObjects()
    val wrappedTTS = Context.javaToJS(TTS, scope)
    ScriptableObject.putProperty(scope, "tts", wrappedTTS)
    def run(code:String, filename:String) = cx.evaluateString(scope, code, filename, 1, null)
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
