package info.thewordnerd.spiel.scripting

import android.util.Log
import android.view.accessibility.AccessibilityEvent

import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

import info.thewordnerd.spiel.utils.TTS

trait AccessibilityEventHandler {
  def run(e:AccessibilityEvent):Boolean
}

object Scripter {

  val js = """
    var pkg = null;

    function forPackage(pk) {
      pkg = pk;
    }

    function forClass(cls, script) {
      if(cls[0] == ".")
        cls = pkg+cls;
      key = new java.util.ArrayList();
      key.add(pkg);
      key.add(cls);
      h = new info.thewordnerd.spiel.scripting.AccessibilityEventHandler(script);
      for(e in script) {
        if(e == "onViewClicked")
          info.thewordnerd.spiel.presenters.ViewClicked.handlers.put(k, h);
        else if(e == "onViewFocused")
          info.thewordnerd.spiel.presenters.ViewFocused.handlers.put(k, h);
        else if(e == "onViewSelected")
          info.thewordnerd.spiel.presenters.ViewSelected.handlers.put(k, h);
        else if(e == "onViewTextChanged")
          info.thewordnerd.spiel.presenters.ViewTextChanged.handlers.put(k, h);
        else if(e == "onWindowStateChanged")
          info.thewordnerd.spiel.presenters.WindowStateChanged.handlers.put(k, h);
        else
          print("Invalid event: ",e);
      }
    }
  """

  def apply() = {
    val cx = Context.enter
    cx.setOptimizationLevel(-1)
    val  scope = cx.initStandardObjects()
    val wrappedTTS = Context.javaToJS(TTS, scope)
    ScriptableObject.putProperty(scope, "tts", wrappedTTS)
    val result = cx.evaluateString(scope, """print("Hello, world.");""", "<spiel>", 1, null)
    Log.d(this.toString, Context.toString(result))
    Context.exit
    true
  }
}
