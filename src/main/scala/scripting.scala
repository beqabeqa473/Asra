package info.spielproject.spiel
package scripting

import java.io._
import java.lang.Integer

import concurrent._

import android.content.{BroadcastReceiver, ContentValues, Context => AContext, Intent}
import android.content.pm._
import android.database._
import android.os._
import android.os.Build.VERSION
import android.util.Log
import android.view.accessibility.AccessibilityEvent

import com.ning.http.client._
import dispatch._
import Defaults._
import org.json4s._
import org.mozilla.javascript.{Callable, Context, ContextFactory, Function, RhinoException, ScriptableObject}

import events._
import presenters.{Callback, Presenter}

/**
 * Presenter callback that executes a Rhino script.
*/

class RhinoCallback(f:Function) extends Callback {
  def apply(e:AccessibilityEvent):Boolean = {
    var args = new Array[Object](2)
    args(0) = e
    args(1) = Presenter.currentActivity
    val cx = ContextFactory.getGlobal.enterContext()
    cx.setOptimizationLevel(-1)
    try {
      Scripter.global.put("__pkg__", Scripter.global, e.getPackageName)
      Context.toBoolean(f.call(cx, Scripter.global, Scripter.global, args))
    } catch {
      case e:Throwable =>
        TTS.speak("Script error: "+e.getMessage, true)
        Log.e("spiel", "Error running script: "+e.getMessage)
        false
    } finally {
      Scripter.global.put("__pkg__", Scripter.global, null)
      Context.exit()
    }
  }
}

class Script(
  context:AContext,
  private[scripting] var code:String,
  filename:String = "",
  val pkg:String = "",
  asset:Boolean = false
) {

  def this(context:AContext, is:InputStream, filename:String, asset:Boolean) = this(
    context,
    Script.readAllAvailable(is),
    filename,
    filename.substring(0, filename.lastIndexOf(".")),
    asset
  )

  def this(context:AContext, filename:String, asset:Boolean) = this(
    context,
    if(asset)
      context.getAssets.open(filename)
    else new FileInputStream(new File(Scripter.scriptsDir, filename)),
    filename, asset = asset
  )

  private var successfullyRan = false

  def successfullyRan_? = successfullyRan

  def run() = {
    Log.d("spiel", "Running "+pkg)
    uninstall()
    val cx = ContextFactory.getGlobal.enterContext()
    val scope = cx.newObject(Scripter.global)
    scope.setPrototype(Scripter.global)
    scope.setParentScope(null)
    scope.put("__pkg__", scope, pkg)
    Scripter.script = Some(this)
    try {
      cx.evaluateString(Scripter.global, code, filename, 1, null)
      successfullyRan = true
    } catch {
      case e:RhinoException => Log.e("spiel", "Error running script: "+e.getMessage)
      case e:Throwable => Log.e("spiel", e.toString)
    }finally {
      scope.put("__pkg__", scope, null)
      Scripter.script = None
      Context.exit()
    }
    successfullyRan
  }

  private var presenters = List[Presenter]()

  /**
   * Register a Presenter for a given package and class.
  */

  def registerPresenterFor(cls:String, s:Object) {
    val scr = s.asInstanceOf[ScriptableObject]
    Log.d("spiel", "Registering presenter for "+pkg+"/"+cls)
    val p = new Presenter(pkg, cls)

    scr.getIds.foreach { property =>

      // Iterate through object properties, mangling them slightly and 
      // mapping them to AccessibilityEvent types.

      val id = property.asInstanceOf[String]
      val chars = id.substring(2, id.length).toCharArray
      chars(0) = chars(0).toLower
      val func = new String(chars)

      // Check to ensure that the property name maps to a valid AccessibilityEvent type.

      if(Presenter.dispatchers.valuesIterator.contains(func)) {
        val f = scr.get(id, Scripter.global)
        if(f.isInstanceOf[Function])
          // Map the script to an Android AccessibilityEvent type.
          p.dispatches(func) = new RhinoCallback(f.asInstanceOf[Function])
      } else
        Log.e("spiel", func+" is not a valid presenter. Skipping.")
    }

    presenters ::= p
  }

  override val toString = {
    Script.labelFor(pkg, context)
  }

  def writeToExternalStorage() = {
    val file = new File(Environment.getExternalStorageDirectory+"/spiel/scripts/"+pkg+".js")
    val writer = new FileWriter(file, false)
    writer.write(code)
    writer.close()
    file.getAbsolutePath
  }

  def reload() = {
    val inputStream = if(asset)
      context.getAssets.open(filename)
    else new FileInputStream(new File(Scripter.scriptsDir, filename))
    code = Script.readAllAvailable(inputStream)
    run()
  }

  def delete() {
    if(!asset)
      new File(Scripter.scriptsDir, filename).delete()
  }

  def uninstall() {
    presenters.foreach(Presenter.unregister(_))
    Presenter.unregisterPackage(pkg)
    presenters = Nil
    Scripter.unsetStringsFor(pkg)
    Scripter.removePreferencesFor(pkg)
  }

  def preferences_? = Scripter.preferences.get(pkg) != None

}

object Script {

  private def readAllAvailable(is:InputStream):String = {
    val a = is.available
    val b = new Array[Byte](a)
    is.read(b)
    is.close()
    new String(b)
  }

  def labelFor(pkg:String, c:AContext = Scripter.service) = {
    try {
      val pm = c.getPackageManager
      pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString
    } catch {
      case _:Throwable => pkg
    }
  }

}

class Observer(context:AContext, path:String) extends FileObserver(path) {
  import FileObserver._
  def onEvent(event:Int, path:String) = event match {
    case CREATE |  MOVED_TO =>
      TTS.speak(context.getString(R.string.scriptInstalling, path), true)
      (new Script(context, path, false)).reload()
    case MODIFY =>
      TTS.speak(context.getString(R.string.scriptUpdating, path), true)
      (new Script(context, path, false)).reload()
    case DELETE | MOVED_FROM =>
      TTS.speak(context.getString(R.string.scriptUninstalling, path), true)
      Presenter.unregisterPackage(path.split("\\.").head)
    case _ =>
  }
}

/**
 * Singleton granting convenient access to the Rhino scripting subsystem.
*/

object Scripter {

  private lazy val context = ContextFactory.getGlobal.enterContext()

  lazy val global = context.initStandardObjects()

  private[scripting] var script:Option[Script] = None

  private[scripting] var service:AContext = null

  private lazy val spielDir = new File(Environment.getExternalStorageDirectory, "spiel")

  lazy val scriptsDir = new File(spielDir, "scripts")

  private lazy val observer = new Observer(service, scriptsDir.getPath)

  /**
   * Initialize the scripting subsystem based on the specified Context.
  */

  def apply(svc:AContext) {
    import com.ning.http.client._
    new providers.netty.NettyAsyncHttpProvider(
      new AsyncHttpClientConfig.Builder().build
    )
    service = svc
    ContextFactory.getGlobal.enterContext(context)
    context.setOptimizationLevel(-1)

    // Inject some Spiel objects into the scripting environment.
    val wrappedPresenter = Context.javaToJS(Presenter, global)
    ScriptableObject.putProperty(global, "Presenter", wrappedPresenter)

    val wrappedScripter = Context.javaToJS(this, global)
    ScriptableObject.putProperty(global, "Scripter", wrappedScripter)

    val wrappedTTS = Context.javaToJS(TTS, global)
    ScriptableObject.putProperty(global, "TTS", wrappedTTS)

    ScriptableObject.putProperty(global, "ANDROID_PLATFORM_VERSION", VERSION.SDK_INT)
    global.setAttributes("ANDROID_PLATFORM_VERSION", ScriptableObject.READONLY)

    val pm = svc.getPackageManager.asInstanceOf[PackageManager]
    val pi = pm.getPackageInfo(svc.getPackageName(), 0)
    ScriptableObject.putProperty(global, "SPIEL_VERSION_NAME", pi.versionName)
    global.setAttributes("SPIEL_VERSION_NAME", ScriptableObject.READONLY)
    ScriptableObject.putProperty(global, "SPIEL_VERSION_CODE", pi.versionCode)
    global.setAttributes("SPIEL_VERSION_CODE", ScriptableObject.READONLY)

    new Script(service, "scripts/api.js", true).run()
    val assets = service.getAssets
    for(fn <- assets.list("scripts") if(fn != "api.js")) {
      new Script(service, fn, true).run()
    }

    val userPackages = try {
      scriptsDir.list().map { str =>
        str.substring(0, str.lastIndexOf("."))
      }.toList
    } catch {
      case e:NullPointerException => Nil
    }

    initExternalScripts()

    Context.exit()

  }

  def initExternalScripts() {
    if(spielDir == null || scriptsDir == null) return
    if(!spielDir.isDirectory) spielDir.mkdir
    if(!scriptsDir.isDirectory) scriptsDir.mkdir
    observer.startWatching()
    userScripts.foreach(_.run())
  }

  def shutdown() {
    observer.stopWatching()
  }

  def userScripts = {
    Option(scriptsDir.list()).map { scripts =>
      scripts.toList.map { script =>
        new Script(service, script, false)
      }
    }.getOrElse(Nil)
  }

  def registerPresenterFor(cls:String, scr:Object) = script.map(_.registerPresenterFor(cls, scr))

  private var _preferences = Map[String, Map[String, Map[String, Any]]]()

  def preferences = _preferences

  def addBooleanPreference(key:String, title:String, summary:String, default:Boolean) {
    val pkg = script.map(_.pkg).getOrElse("")
    val preference = Map(
      "title" -> title,
      "summary" -> summary,
      "type" -> 'boolean,
      "default" -> default
    )
    if(_preferences.get(pkg) == None)
      _preferences = _preferences.updated(pkg, Map.empty)
    val current = _preferences(pkg)
    _preferences = _preferences.updated(pkg, current.updated(key, preference))
  }

  def getBooleanPreference(pkg:String, key:String):Boolean = {
    preferences.get(pkg).flatMap { prefs =>
      prefs.get(key).map { pref =>
        val default = pref("default").asInstanceOf[Boolean]
        Preferences.prefs.getBoolean(pkg+"_"+key, default)
      }
    }.getOrElse(false)
  }

  def removePreferencesFor(pkg:String) {
    _preferences -= pkg
  }

  private var strings = collection.mutable.Map[List[String], String]()

  def setString(name:String, value:String) {
    val pkg = script.map(_.pkg).getOrElse("")
    strings(List(pkg, name)) = value
  }

  def setString(name:String, value:String, language:String) {
    val pkg = script.map(_.pkg).getOrElse("")
    strings(List(pkg, name, language)) = value
  }

  def setString(name:String, value:String, language:String, country:String) {
    val pkg = script.map(_.pkg).getOrElse("")
    strings(List(pkg, name, language, country)) = value
  }

  def setString(name:String, value:String, language:String, country:String, variant:String) {
    val pkg = script.map(_.pkg).getOrElse("")
    strings(List(pkg, name, language, country, variant)) = value
  }

  def unsetStringsFor(pkg:String) {
    strings = strings.filter (v => v._1.head != pkg)
  }

  def getString(pkg:String, name:String):String = {
    val locale = java.util.Locale.getDefault
    val language = locale.getLanguage
    val country = locale.getCountry
    val variant = locale.getVariant
    strings.get(List(pkg, name, language, country, variant))
    .orElse(strings.get(List(pkg, name, language, country)))
    .orElse(strings.get(List(pkg, name, language)))
    .orElse(strings.get(List(pkg, name)))
    .getOrElse("")
  }

  def log(pkg:String, msg:String) = Log.d("spiel", pkg+": "+msg)

  /**
   * Create or append to a script template for the specified <code>AccessibilityEvent</code>.
  */

  def createTemplateFor(event:AccessibilityEvent) = {
    try {
      val presenter = "on"+Presenter.dispatchers(event.getEventType).capitalize
      val code = "forClass(\""+event.getClassName+"\", {\n  "+presenter+": function(e, activity) {\n    // "+event.toString+"\n    return false\n  }\n})\n"
      val file = new File(scriptsDir, event.getPackageName+".js")
      val writer = new FileWriter(file, true)
      writer.write(code)
      writer.close()
      Some(scriptsDir+"/"+file.getName)
    } catch {
      case e:Throwable =>
        Log.e("spiel", "Error writing script template", e)
        None
    }
  }

}
