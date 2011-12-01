package info.spielproject.spiel
package scripting

import java.io.{File, FileInputStream, FileOutputStream, FileWriter, InputStream}
import java.lang.Integer

import handlers.PrettyAccessibilityEvent

import android.content.{BroadcastReceiver, ContentValues, Context => AContext, Intent}
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Environment
import android.os.Build.VERSION
import android.util.Log
import android.view.accessibility.AccessibilityEvent

import org.mozilla.javascript.{Context, Function, RhinoException, ScriptableObject}

import handlers.{Callback, Handler}

/**
 * Handler callback that executes a Rhino script.
*/

class RhinoCallback(f:Function) extends Callback {
  def apply(e:AccessibilityEvent):Boolean = {
    Context.enter
    var args = new Array[Object](2)
    args(0) = e
    args(1) = Handler.currentActivity
    try {
      Scripter.scope.put("__pkg__", Scripter.scope, e.getPackageName)
      Context.toBoolean(f.call(Scripter.context, Scripter.scope, Scripter.scope, args))
    } catch {
      case e =>
        TTS.speak("Script error: "+e.getMessage, true)
        Log.e("spiel", "Error running script: "+e.getMessage)
        false
    } finally {
      Scripter.scope.put("__pkg__", Scripter.scope, null)
    }
  }
}

class Script(
  context:AContext,
  private[scripting] var code:String,
  filename:String = "",
  val pkg:String = "",
  val id:Option[Int] = None,
  val remoteID:Option[String] = None,
  owner:Option[String] = None,
  description:Option[String] = None,
  val version:Int = 0,
  asset:Boolean = false
) {

  def this(context:AContext, c:Cursor) = this(
    context,
    c.getString(c.getColumnIndex(Provider.columns.code)),
    c.getString(c.getColumnIndex(Provider.columns.pkg)),
    c.getString(c.getColumnIndex(Provider.columns.pkg)),
    if(c.getColumnIndex(Provider.columns._id) == -1)
      None
    else Some(c.getInt(c.getColumnIndex(Provider.columns._id))),
    Option(c.getString(c.getColumnIndex(Provider.columns.remoteID))),
    Option(    c.getString(c.getColumnIndex(Provider.columns.owner))),
    Option(c.getString(c.getColumnIndex(Provider.columns.description))),
    c.getInt(c.getColumnIndex(Provider.columns.version))
  )

  def this(context:AContext, is:InputStream, filename:String, asset:Boolean) = this(
    context,
    Script.readAllAvailable(is),
    filename,
    filename.substring(0, filename.lastIndexOf(".")),
    None, None, None, None, 0, asset
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
    Scripter.scope.put("__pkg__", Scripter.scope, pkg)
    Scripter.script = Some(this)
    try {
      Scripter.context.evaluateString(Scripter.scope, code, filename, 1, null)
      successfullyRan = true
    } catch {
      case e:RhinoException => Log.e("spiel", "Error creating script: "+e.getMessage)
      case e => Log.e("spiel", e.toString)
    }finally {
      Scripter.scope.put("__pkg__", Scripter.scope, null)
      Scripter.script = None
    }
    successfullyRan
  }

  private var handlers = List[Handler]()

  /**
   * Register a Handler for a given package and class.
  */

  def registerHandlerFor(cls:String, s:Object) {
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
        val f = scr.get(id, Scripter.scope)
        if(f.isInstanceOf[Function])
          // Map the script to an Android AccessibilityEvent type.
          h.dispatches(func) = new RhinoCallback(f.asInstanceOf[Function])
      } else
        Log.e("spiel", func+" is not a valid handler. Skipping.")
    }

    handlers ::= h

    h
  }

  def save() {
    val resolver = context.getContentResolver
    val values = new ContentValues
    values.put(Provider.columns.pkg, pkg)
    values.put(Provider.columns.label, Script.labelFor(pkg, context))
    values.put(Provider.columns.owner, owner.get)
    values.put(Provider.columns.description, description.getOrElse(""))
    values.put(Provider.columns.code, code)
    values.put(Provider.columns.remoteID, remoteID.getOrElse(null))
    values.put(Provider.columns.version, new java.lang.Integer(version))
    id.map { i =>
      resolver.update(Provider.uri, values, "_id = ?", List(i.toString).toArray)
    }.orElse {
      remoteID.map { rid =>
        val cursor = resolver.query(Provider.uri, null, "remote_id = ?", List(rid).toArray, null)
        var rv:Any = null
        if(cursor.getCount > 0)
          rv = resolver.update(Provider.uri, values, "remote_id = ?", List(rid).toArray)
        else
          rv = resolver.insert(Provider.uri, values)
        cursor.close()
        rv
      }
    }.getOrElse(resolver.insert(Provider.uri, values))
  }

  override val toString = {
    Script.labelFor(pkg, context)+description.map { d =>
      if(d != "") ": "+d else ""
    }.getOrElse("")
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
    handlers.foreach(Handler.unregisterHandler(_))
    handlers = Nil
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
    new String(b)
  }

  def labelFor(pkg:String, c:AContext = Scripter.service) = {
    try {
      val pm = c.getPackageManager
      pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString
    } catch {
      case _ => pkg
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

  private[scripting] var script:Option[Script] = None

  private var _scriptsDir:File = null

  def scriptsDir = _scriptsDir

  private[scripting] var service:SpielService = null

  /**
   * Initialize the scripting subsystem based on the specified service.
  */

  def apply(svc:SpielService)  {
    service = svc
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

    myScope.put("ANDROID_PLATFORM_VERSION", myScope, VERSION.SDK_INT)

    val pm = svc.getPackageManager.asInstanceOf[PackageManager]
    val pi = pm.getPackageInfo(svc.getPackageName(), 0)
    myScope.put("SPIEL_VERSION_NAME", myScope, pi.versionName)
    myScope.put("SPIEL_VERSION_CODE", myScope, pi.versionCode)

    val spielDir = new File(Environment.getExternalStorageDirectory, "spiel")
    if(!spielDir.isDirectory) spielDir.mkdir
    _scriptsDir = new File(spielDir, "scripts")
    if(!scriptsDir.isDirectory) scriptsDir.mkdir

    new Script(service, "scripts/api.js", true).run()

    val assets = service.getAssets
    for(fn <- assets.list("scripts") if(fn != "api.js")) {
      new Script(service, fn, true).run()
    }

    val cursor = service.getContentResolver.query(Provider.uri, Provider.columns.projection, null, null, null)
    Log.d("spielscript", "Beginning run.")
    cursor.moveToFirst()
    while(!cursor.isAfterLast) {
      val s = new Script(service, cursor)
      s.run()
      cursor.moveToNext()
    }
    cursor.close()

    // Load scripts from /spiel/scripts folder on SD card.

    userScripts.foreach(_.run())

    true
  }

  def onDestroy = {
    Context.exit
  }

  def userScripts = {
    val list = scriptsDir.list()
    if(list == null) List[Script]()
    else list.toList.map(new Script(service, _, false))
  }

  def registerHandlerFor(cls:String, scr:Object) = script.map(_.registerHandlerFor(cls, scr))

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
        Preferences.sharedPreferences.getBoolean(pkg+"_"+key, default)
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

  def createTemplateFor(event:PrettyAccessibilityEvent) = {
    val handler = "on"+Handler.dispatchers(event.e.getEventType).capitalize
    val code = "forClass(\""+event.e.getClassName+"\", {\n  "+handler+": function(e, activity) {\n    // "+event.toString+"\n    return false\n  }\n})\n"
    val file = new File(scriptsDir, event.e.getPackageName+".js")
    try {
      val writer = new FileWriter(file, true)
      writer.write(code)
      writer.close()
      Some(scriptsDir+"/"+file.getName)
    } catch {
      case _ => None
    }
  }

}

import collection.JavaConversions._

import android.content.{ContentProvider, ContentUris, ContentValues, UriMatcher}
import android.database.SQLException
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper, SQLiteQueryBuilder}
import android.net.Uri
import android.provider.BaseColumns
import android.text.TextUtils

trait AbstractProvider {
  this: ContentProvider =>

  val matcher = new UriMatcher(UriMatcher.NO_MATCH)

  object uriTypes extends Enumeration {
    val collection = 0
    val item = 1
  }

  def authority:String
  def path:String

  def addUris() {
    matcher.addURI(authority, path, uriTypes.collection)
    matcher.addURI(authority, path+"/#", uriTypes.item)
  }

  def collectionURI_?(u:Uri) = matcher.`match`(u) == uriTypes.collection

  def mimeType:String
  private val collectionType = "vnd.android.cursor.dir/"+mimeType
  private val itemType = "vnd.android.cursor.item/"+mimeType

  override def getType(u:Uri) =  if(collectionURI_?(u)) collectionType else itemType
}

object Provider {

  val uri = Uri.parse("content://info.spielproject.spiel.scripting.Provider/scripts")

  object columns extends BaseColumns {

    val _id = "_id"
    val pkg = "pkg"
    val label = "label"
    val owner = "owner"
    val description = "description"
    val code = "code"
    val remoteID = "remote_id"
    val version = "version"

    val projection = List(_id, pkg, label, owner, description, code, remoteID, version).toArray

    val projectionMap = Map(projection.map { k =>
      k -> k
    }:_*)

    val defaultSortOrder = label

  }

}

class Provider extends ContentProvider with AbstractProvider {
  import Provider._

  private val databaseName = "spiel.db"

  private class DatabaseHelper(context:AContext) extends SQLiteOpenHelper(context, databaseName, null, 3) {

    override def onCreate(db:SQLiteDatabase) {
      val c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='scripts'", null);
      if(c.getCount == 0) {
        db.execSQL("CREATE TABLE scripts (_id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, label TEXT NOT NULL, owner TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', code TEXT NOT NULL, remote_id TEXT NOT NULL, version INT NOT NULL);")
      }
      c.close()
    }

    override def onUpgrade(db:SQLiteDatabase, oldVersion:Int, newVersion:Int) {
      Log.d("spielup", "On upgrade from "+oldVersion+" to "+newVersion)
      if(oldVersion < 2) {
        db.execSQL("CREATE TABLE tmp (_id, pkg, code, remote_id, version);")
        db.execSQL("INSERT into TMP select _id, pkg, code, remote_id, version from scripts;")
        db.execSQL("DROP TABLE scripts;")
        db.execSQL("CREATE TABLE scripts (_id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, label TEXT NOT NULL, code TEXT NOT NULL, description TEXT NOT NULL default '', remote_id TEXT NOT NULL, version INT NOT NULL);")
        val c = db.rawQuery("SELECT * FROM tmp;", null)
        c.moveToFirst()
        while(!c.isAfterLast) {
          val values = new ContentValues()
          values.put(Provider.columns._id, new Integer(c.getInt(c.getColumnIndex(Provider.columns._id))))
          val pkg = c.getString(c.getColumnIndex(Provider.columns.pkg))
          values.put(Provider.columns.pkg, pkg)
          values.put(Provider.columns.label, Script.labelFor(pkg, getContext))
          values.put(Provider.columns.code, c.getString(c.getColumnIndex(Provider.columns.code)))
          values.put(Provider.columns.remoteID, c.getString(c.getColumnIndex(Provider.columns.remoteID)))
          values.put(Provider.columns.version, new Integer(c.getInt(c.getColumnIndex(Provider.columns.version))))
          db.insert("scripts", null, values)
          c.moveToNext()
        }
        c.close()
        db.execSQL("DROP TABLE tmp;")
      }
      if(oldVersion < 3) {
        db.execSQL("CREATE TABLE tmp (_id, pkg, label, code, remote_id, description, version);")
        db.execSQL("INSERT into TMP select _id, pkg, label, code, remote_id, description, version from scripts;")
        db.execSQL("DROP TABLE scripts;")
        db.execSQL("CREATE TABLE scripts (_id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, label TEXT NOT NULL, owner TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', code TEXT NOT NULL, remote_id TEXT NOT NULL, version INT NOT NULL);")
        val c = db.rawQuery("SELECT * FROM tmp;", null)
        c.moveToFirst()
        while(!c.isAfterLast) {
          val values = new ContentValues()
          values.put(Provider.columns._id, new Integer(c.getInt(c.getColumnIndex(Provider.columns._id))))
          values.put(Provider.columns.pkg, c.getString(c.getColumnIndex(Provider.columns.pkg)))
          values.put(Provider.columns.label, c.getString(c.getColumnIndex(Provider.columns.label)))
          values.put(Provider.columns.owner, "")
          values.put(Provider.columns.code, c.getString(c.getColumnIndex(Provider.columns.code)))
          values.put(Provider.columns.remoteID, c.getString(c.getColumnIndex(Provider.columns.remoteID)))
          values.put(Provider.columns.version, new Integer(c.getInt(c.getColumnIndex(Provider.columns.version))))
          db.insert("scripts", null, values)
          c.moveToNext()
        }
        c.close()
        db.execSQL("DROP TABLE tmp;")
      }
    }

  }

  private var db:SQLiteDatabase = null

  override def onCreate() = {
    db=(new DatabaseHelper(getContext)).getWritableDatabase
    if(db == null) false else true
  }

  val mimeType = "vnd.spiel.script"
  val authority = "info.spielproject.spiel.scripting.Provider"
  val path = "scripts"
  addUris()

  private val tableName = "scripts"

  override def query(uri:Uri, projection:Array[String], selection:String, selectionArgs:Array[String], sort:String) = {

    val qb=new SQLiteQueryBuilder
    qb.setTables(tableName)

    if(collectionURI_?(uri))
      qb.setProjectionMap(columns.projectionMap)
    else
      qb.appendWhere(columns._id+"="+uri.getPathSegments().get(1))

    val orderBy = if (TextUtils.isEmpty(sort)) columns.defaultSortOrder else sort

    val c=qb.query(db, projection, selection, selectionArgs, null, null, orderBy)
    c.setNotificationUri(getContext().getContentResolver(), uri)
    c
  }

  override def insert(u:Uri, values:ContentValues) = {
    if(values == null)
      throw new IllegalArgumentException("values cannot be null")
    else if(!collectionURI_?(u)) 
      throw new IllegalArgumentException("Unknown URI: "+u)

    val rowID = db.insert(tableName, null, values)
    if(rowID > 0) {
      val ur = ContentUris.withAppendedId(uri, rowID)
      getContext().getContentResolver().notifyChange(ur, null)
      ur
    } else
      throw new SQLException("Failed to insert row into "+u);
  }

  override def update(u:Uri, values:ContentValues, where:String, whereArgs:Array[String]) = {

    val count = if(collectionURI_?(u)) {
      db.update(tableName, values, where, whereArgs);
    } else {
      val segment = u.getPathSegments.get(1)
      val w = if(TextUtils.isEmpty(where)) "" else " AND ("+where+")"
      db.update(tableName, values, columns._id+"="+segment+w, whereArgs)
    }

    getContext().getContentResolver().notifyChange(u, null);
    count
  }

  override def delete(u:Uri, where:String, whereArgs:Array[String]) = {

    val count = if(collectionURI_?(u)) {
      db.delete(tableName, where, whereArgs);
    } else {
      val segment = u.getPathSegments.get(1)
      val w = if(TextUtils.isEmpty(where)) "" else " AND ("+where+")"
      db.delete(tableName, columns._id+"="+segment+w, whereArgs)
    }

    getContext().getContentResolver().notifyChange(u, null);
    count
  }

}

import actors.Actor._

import android.content.pm.PackageManager
import android.database.MatrixCursor
import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._
import JsonAST._
import JsonParser._
import Serialization.{read, write}

class BazaarProvider extends ContentProvider with AbstractProvider {

  val mimeType = "vnd.spiel.bazaar.script"
  val authority = "info.spielproject.spiel.scripting.bazaar.Provider"
  val path = "scripts"
  addUris()

  def onCreate() = true

  private var http = new Http
  private val apiRoot = :/("bazaar.spielproject.info") / "api" / "v1" <:< Map("Accept" -> "application/json")

  private def request(req:Request)(f:(JValue) => Unit) = {
    try {
      http(req ># { v => f(v) } )
    } catch {
      case e =>
        Log.d("spiel", e.getMessage)
        http = new Http
    }
  }

  override def query(uri:Uri, projection:Array[String], where:String, whereArgs:Array[String], sort:String) = {
    val cursor = if(collectionURI_?(uri)) {
      val c = new MatrixCursor(List(Provider.columns.remoteID, Provider.columns.pkg, Provider.columns.owner, Provider.columns.description, Provider.columns.code, Provider.columns.version).toArray)
      request(
        apiRoot / "scripts" <<
        Map("q" -> where)
      ) { response =>
        response.values.asInstanceOf[List[Map[String, Any]]].foreach { r =>
          val rb = c.newRow()
          rb.add(r("id"))
          rb.add(r("package"))
          rb.add(r("owner"))
          rb.add(r("description"))
          rb.add(r("code"))
          rb.add(r("version"))
        }
      }
      c
    } else {
      new MatrixCursor(List("").toArray)
    }
    cursor.setNotificationUri(getContext().getContentResolver(), uri)
    cursor
  }

  override def insert(u:Uri, values:ContentValues) = {
    val parameters = collection.mutable.Map[String, String]()
    parameters("code") = values.getAsString("code")
    parameters("changes") = values.getAsString("changes")
    Log.d("spielcheck", "Parameters: "+http+": "+apiRoot)
    http((
      apiRoot / "script" / values.getAsString("package") << parameters as (Preferences.bazaarUsername, Preferences.bazaarPassword)
    ) >~ { response =>
    })
    Log.d("spielcheck", "Here")
    null
  }

  override def update(u:Uri, values:ContentValues, where:String, whereArgs:Array[String]) = 0

  override def delete(u:Uri, where:String, whereArgs:Array[String]) = 0

}

object BazaarProvider {

  val newScriptsView = "info.spielproject.spiel.intent.NEW_SCRIPTS_VIEW"

  val uri = Uri.parse("content://info.spielproject.spiel.scripting.bazaar.Provider/scripts")

  private var context:AContext = null

  private var pm:PackageManager = null

  /**
   * Initialize the Bazaar based off the specified <code>Context</code>.
  */

  def apply(c:AContext) {
    context = c
    pm = context.getPackageManager
    checkRemoteScripts()
  }

  private var _newOrUpdatedScripts:List[Script] = Nil
  def newOrUpdatedScripts = _newOrUpdatedScripts

  def checkRemoteScripts() = actor {
    val installedPackages = pm.getInstalledPackages(0).map { i => i.packageName }
    val userPackages = Scripter.userScripts.map(_.pkg)
    val packages = installedPackages.filterNot(userPackages.contains(_))
    val where = packages.reduceLeft[String] { (acc, n) =>
      acc+","+(context.getContentResolver.query(
        Provider.uri, Provider.columns.projection, "pkg = ?", List(n).toArray, null
      ) match {
        case c:Cursor if(c.getCount == 1) =>
          c.moveToFirst()
          val rv = c.getString(c.getColumnIndex(Provider.columns.remoteID))+" "+c.getString(c.getColumnIndex(Provider.columns.version))
          c.close()
          rv
        case c:Cursor =>
          c.close()
          n
      })
    }

    val cursor = context.getContentResolver.query(BazaarProvider.uri, null, where, null, null)
    _newOrUpdatedScripts = Nil
    cursor.moveToFirst()
    while(!cursor.isAfterLast) {
      val script = new Script(context, cursor)
      _newOrUpdatedScripts ::= script
      cursor.moveToNext()
    }
    cursor.close()
    if(newOrUpdatedScripts != Nil) {
      val intent = new Intent(Intent.ACTION_VIEW)
      intent.addCategory(newScriptsView)
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    }
  }

  def post(script:Script, changes:String) {
    val values = new ContentValues()
    values.put("package", script.pkg)
    values.put("code", script.code)
    values.put("changes", changes)
    context.getContentResolver.insert(BazaarProvider.uri, values)
  }

}
