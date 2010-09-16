package info.spielproject.spiel.scripting

import android.os.Environment
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.{File, FileInputStream, FileOutputStream, InputStream}

import org.mozilla.javascript.{Context, Function, RhinoException, ScriptableObject}

import info.spielproject.spiel._
import handlers.{Callback, Handler}

/**
 * Handler callback that executes a Rhino script.
*/

class RhinoCallback(f:Function) extends Callback {
  def apply(e:AccessibilityEvent):Boolean = {
    Context.enter
    var args = new Array[Object](1)
    args(0) = e
    try {
      Context.toBoolean(f.call(Scripter.context, Scripter.scope, Scripter.scope, args))
    } catch {
      case e =>
        TTS.speak("Script error: "+e.getMessage, true)
        Log.e("spiel", "Error running script: "+e.getMessage)
        false
    }
  }
}

/**
 * Singleton granting convenient access to the Rhino scripting subsystem.
*/

object Scripter {

  private var myCx:Context = null
  private var myScope:ScriptableObject = null

  def context = myCx
  def scope = myScope

  /**
   * Initialize the scripting subsystem based on the specified service.
  */

  def apply(service:SpielService)  {
    myCx = Context.enter
    myCx.setOptimizationLevel(-1)
    myScope = myCx.initStandardObjects()

    // Inject some Spiel objects into the scripting environment.
    val wrappedHandler = Context.javaToJS(Handler, myScope)
    ScriptableObject.putProperty(myScope, "Handler", wrappedHandler)

    val wrappedScripter = Context.javaToJS(this, myScope)
    ScriptableObject.putProperty(myScope, "Scripter", wrappedScripter)

    val wrappedTTS = Context.javaToJS(TTS, myScope)
    ScriptableObject.putProperty(myScope, "TTS", wrappedTTS)

    // Run the given code as a string, reporting the specified filename in 
    // warnings and errors.
    def run(code:String, filename:String) = try {
      val p = filename.substring(0, filename.lastIndexOf("."))
      val pkg = if(p.startsWith("_")) p.substring(1, p.size) else p
      // Make this script's package name available in the context.
      myScope.put("__pkg__", scope, pkg)
      myCx.evaluateString(myScope, code, filename, 1, null)
    } catch {
      case e:RhinoException => Log.e(this.toString, e.getMessage)
      case e => Log.e("spiel", e.toString)
    } finally {
      myScope.put("__pkg__", scope, null)
    }

    // Load scripts from /spiel/scripts folder on SD card.
    val spielDir = new File(Environment.getExternalStorageDirectory, "spiel")
    if(!spielDir.isDirectory) spielDir.mkdir
    val scriptsDir = new File(spielDir, "scripts")
    if(!scriptsDir.isDirectory) scriptsDir.mkdir

    // Now that we've created the folder, copy the assets.
    val assets = service.getAssets

    // Handy function to read all data from a stream into a string.
    def readAllAvailable(is:InputStream):String = {
      val a = is.available
      val b = new Array[Byte](a)
      is.read(b)
      new String(b)
    }

    try {
      for(fn <- assets.list("scripts") if(fn != "api.js")) {
        val list = scriptsDir.list()
        if(list == null || !list.contains(fn)) {
          val script = new FileOutputStream(new File(scriptsDir, "_"+fn))
          script.write(readAllAvailable(assets.open("scripts/"+fn)).getBytes)
          script.close
        }
      }
    } catch {
      // TODO: Handle this better. We'll launch, just without scripts.
      case _ =>
    }

    // Run a script, optionally directly from the assets folder in the package.
    def runScriptFile(f:String, asset:Boolean = false) = {
      val is = if(asset) 
        assets.open("scripts/"+f)
      else
        new FileInputStream(new File(scriptsDir, f))
      run(readAllAvailable(is), f)
    }

    runScriptFile("api.js", true)
    if(scriptsDir.list() != null)
      scriptsDir.list().foreach { script => runScriptFile(script) }

    Context.exit
    true
  }

  /**
   * Register a Handler for a given package and class.
  */

  def registerHandlerFor(pkg:String, cls:String, s:Object) {
    val scr = s.asInstanceOf[ScriptableObject]
    val h = new Handler(pkg, cls)

    scr.getIds.foreach { property =>

      // Iterate through object properties, mangling them slightly and 
      // mapping them to AccessibilityEvent types.
      val id = property.asInstanceOf[String]
      val chars = id.substring(2, id.length).toCharArray
      chars(0) = chars(0).toLower
      val func = new String(chars)

      // Check to ensure that the property name maps to a valid AccessibilityEvent type.
      if(Handler.dispatchers.valuesIterator.contains(func)) {
        val f = scr.get(id, myScope)
        if(f.isInstanceOf[Function])
          // Map the script to an Android AccessibilityEvent type.
          h.dispatches(func) = new RhinoCallback(f.asInstanceOf[Function])
      } else
        Log.e("spiel", func+" is not a valid handler. Skipping.")
    }

    h
  }

}
