package info.spielproject.spiel
package scripting

import java.io.{File, FileInputStream, FileOutputStream, InputStream}
import java.util.UUID

import android.content.{BroadcastReceiver, Context => AContext}
import android.database.Cursor
import android.os.Environment
import android.util.Log
import android.view.accessibility.AccessibilityEvent

//import com.neodatis.odb.plugin.engine.berkeleydb.NeoDatisBerkeleyDBPlugin
import org.mozilla.javascript.{Context, Function, RhinoException, ScriptableObject}
//import org.neodatis.odb.{NeoDatis, NeoDatisGlobalConfig, Objects, ODB}

import handlers.{Callback, Handler}

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

class Script(
  code:String,
  filename:String = "",
  val pkg:String = "",
  val id:String = UUID.randomUUID.toString,
  val version:Int = 0
) {

  def this(c:Cursor) = this(
    c.getString(c.getColumnIndex(Provider.columns.code)),
    c.getString(c.getColumnIndex(Provider.columns._id)),
    c.getString(c.getColumnIndex(Provider.columns._id)),
    c.getString(c.getColumnIndex(Provider.columns.remoteID)),
    c.getInt(c.getColumnIndex(Provider.columns.version))
  )

  def run() = {
    Scripter.scope.put("__pkg__", Scripter.scope, pkg)
    try {
      Scripter.context.evaluateString(Scripter.scope, code, filename, 1, null)
    } catch {
      case e:RhinoException => Log.e(this.toString, e.getMessage)
      case e => Log.e("spiel", e.toString)
    }finally {
      Scripter.scope.put("__pkg__", Scripter.scope, null)
    }
  }

  private var handlers = List[Handler]()

  def registerHandlerFor(cls:String, s:Object) {
    val scr = s.asInstanceOf[ScriptableObject]
    val h = new Handler(pkg, cls)

    scr.getIds.foreach { property =>

      val id = property.asInstanceOf[String]
      val chars = id.substring(2, id.length).toCharArray
      chars(0) = chars(0).toLower
      val func = new String(chars)

      if(Handler.dispatchers.valuesIterator.contains(func)) {
        val f = scr.get(id, Scripter.scope)
        if(f.isInstanceOf[Function])
          h.dispatches(func) = new RhinoCallback(f.asInstanceOf[Function])
      } else
        Log.e("spiel", func+" is not a valid handler. Skipping.")
    }

    handlers ::= h

    h
  }

}

object Scripter {

  private var myCx:Context = null
  private var myScope:ScriptableObject = null

  def context = myCx
  def scope = myScope

  private var script:Option[Script] = None

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

    def run(code:String, filename:String) = {
      val p = filename.substring(0, filename.lastIndexOf("."))
      val pkg = if(p.startsWith("_")) p.substring(1, p.size) else p
      script = Some(new Script(code, filename, pkg))
      script.map(_.run())
    }

    val assets = service.getAssets

    def readAllAvailable(is:InputStream):String = {
      val a = is.available
      val b = new Array[Byte](a)
      is.read(b)
      new String(b)
    }

    def runScriptAsset(f:String) = {
      val is = assets.open("scripts/"+f)
      run(readAllAvailable(is), f)
    }

    runScriptAsset("api.js")
    for(fn <- assets.list("scripts") if(fn != "api.js")) {
      runScriptAsset(fn)
    }

    val cursor = service.getContentResolver.query(Provider.uri, Provider.columns.projection, null, null, null)
    cursor.moveToFirst()
    while(!cursor.isAfterLast) {
      val s = new Script(cursor)
      s.run()
      cursor.moveToNext()
    }
    cursor.close()

    Bazaar(service)

    true
  }

  def onDestroy = {
    Context.exit
  }

  def registerHandlerFor(cls:String, scr:Object) = script.map(_.registerHandlerFor(cls, scr))

}

import collection.JavaConversions._

import android.content.{ContentProvider, ContentUris, ContentValues, UriMatcher}
import android.database.SQLException
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper, SQLiteQueryBuilder}
import android.net.Uri
import android.provider.BaseColumns
import android.text.TextUtils

object Provider {

  val uri = Uri.parse("content://info.spielproject.spiel.scripting.Provider/scripts")

  object columns extends BaseColumns {

    val _id = "_id"
    val pkg = "pkg"
    val code = "code"
    val remoteID = "remote_id"
    val version = "version"

    val projection = List(_id, pkg, code, remoteID, version).toArray

    val projectionMap = Map(projection.map { k =>
      k -> k
    }:_*)

    val defaultSortOrder = pkg

  }

}

class Provider extends ContentProvider {
  import Provider._

  private val databaseName = "spiel.db"

  private class DatabaseHelper(context:AContext) extends SQLiteOpenHelper(context, databaseName, null, 1) {

    override def onCreate(db:SQLiteDatabase) {
      val c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='scripts'", null);
      if(c.getCount == 0) {
        db.execSQL("CREATE TABLE scripts (_id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, code TEXT NOT NULL, remote_id TEXT DEFAULT NULL, version INT DEFAULT NULL);")
      }
    }

    override def onUpgrade(db:SQLiteDatabase, oldVersion:Int, newVersion:Int) {
    }

  }

  object uriTypes extends Enumeration {
    val collection = 0
    val item = 1
  }

  private val matcher = new UriMatcher(UriMatcher.NO_MATCH)
  matcher.addURI("info.spielproject.spiel.scripting.Provider", "scripts", uriTypes.collection)
  matcher.addURI("info.spielproject.spiel.scripting.Provider", "scripts/*", uriTypes.item)

  private var db:SQLiteDatabase = null

  override def onCreate() = {
    db=(new DatabaseHelper(getContext)).getWritableDatabase
    if(db == null) false else true
  }

  private def collectionURI_?(u:Uri) = matcher.`match`(u) == uriTypes.collection

  private val mimeType = "vnd.spiel.script"
  private val collectionType = "vnd.android.cursor.dir/"+mimeType
  private val itemType = "vnd.android.cursor.item/"+mimeType

  override def getType(u:Uri) =  if(collectionURI_?(u)) collectionType else itemType

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
    else if(collectionURI_?(u)) 
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
import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._
import JsonAST._
import JsonParser._
import Serialization.{read, write}

object Bazaar {

  val newScriptsView = "info.spielproject.spiel.intent.NEW_SCRIPTS_VIEW"

  private var pm:PackageManager = null

  private var service:SpielService = null

  def apply(svc:SpielService) {
    service = svc
    pm = service.getPackageManager
    actor { installOrUpdateScripts }
  }

  private val http = new Http
  private val apiRoot = :/("192.168.1.2", 7070) / "api" / "v1" <:< Map("Accept" -> "application/json")

  def installOrUpdateScripts {
    val packages = pm.getInstalledPackages(0).map { i => i.packageName }
    request(
      apiRoot / "scripts" <<?
      Map("packages" -> packages.reduceLeft[String] { (acc, n) =>
        acc+","+(service.getContentResolver.query(
          Provider.uri, Provider.columns.projection, "pkg = ?", List(n).toArray, null
        ) match {
          case c:Cursor if(c.getCount > 1) =>
            c.getString(c.getColumnIndex(Provider.columns.pkg))+" "+c.getString(c.getColumnIndex(Provider.columns.version))
          case _ => n
        })
      })
    ){ response =>
      Log.d("spiel", "Response: "+response)
      //val updates = response.values
      //Log.d("spiel", "Updates: "+updates)
    }
  }

  private def request(req:Request)(f:(JValue) => Unit) = {
    try {
      http(req ># { v => f(v) } )
    } catch {
      case e => Log.d("spiel", e.getMessage)
    }
  }

}
