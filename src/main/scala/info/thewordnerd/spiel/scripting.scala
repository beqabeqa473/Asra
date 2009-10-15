package info.thewordnerd.spiel.scripting

import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable

class MyContextFactory extends ContextFactory {
  override protected def onContextCreated(cx:Context) {
    cx.setOptimizationLevel(-1)
    super.onContextCreated(cx)
  }
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
    for(k in script) {
      if(k == "onViewClicked")
      else if(k == "onViewFocused")
      else if(k == "onViewSelected")
      else if(k == "onViewTextChanged")
      else if(k == "onWindowStateChanged")
      else
        print("Invalid event: ",k);
    }
  }
  """

  def apply() = {
    ContextFactory.initGlobal(new MyContextFactory)
    val cx = Context.enter
    cx.setOptimizationLevel(-1)
    val scope = cx.initStandardObjects
    val result = cx.evaluateString(scope, js, "<spiel>", 1, null)
    Context.exit
    true
  }
}
