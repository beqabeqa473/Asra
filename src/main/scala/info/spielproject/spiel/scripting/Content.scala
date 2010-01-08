package info.spielproject.spiel.scripting

import android.content.{ContentProvider, ContentValues, Context, UriMatcher}
import android.database.{Cursor, SQLException}
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper, SQLiteQueryBuilder}
import android.net.Uri;

abstract class Content extends ContentProvider {
  def databaseName:String
  def databaseVersion:Int

  def authority = this.getClass.toString

  def createSQL:String
  def onUpgradeDatabase(db:SQLiteDatabase, oldVer:Int, newVer:Int)

  protected val uriMatcher = new UriMatcher(UriMatcher.NO_MATCH)
  protected def addUri(path:String, matchTo:Int) = uriMatcher.addURI(authority, path, matchTo)

  private var matchWith:Int = -1

  type InsertFn = (Uri, ContentValues) => Uri
  type QueryFn = (Uri, Array[String], String, Array[String], String) => Cursor
  type UpdateFn = (Uri, ContentValues, String, Array[String]) => Int
  type DeleteFn = (Uri, String, Array[String]) => Int

  protected def onInsert(f:InsertFn) {
    uriData(matchWith)('insert) = f
  }

  protected def onQuery(f:QueryFn) {
    uriData(matchWith)('query) = f
  }

  protected def onUpdate(f:UpdateFn) {
    uriData(matchWith)('update) = f
  }

  protected def onDelete(f:DeleteFn) {
    uriData(matchWith)('delete) = f
  }

  private var nextUriID = 0
  private var uriData = Map[Int, Map[Symbol, Any]]()

  def on(u:String, t:String, f: => Unit) {
    val id = nextUriID
    nextUriID += 1
    addUri(u, id)
    matchWith = id
    uriData(matchWith) = Map[Symbol, Any]()
    uriData(matchWith)('type) = t
    f
    matchWith = -1
  }

  private var dbHelper:DatabaseHelper = null
  def databaseHelper:DatabaseHelper = dbHelper

  class DatabaseHelper(context:Context) extends SQLiteOpenHelper(context, databaseName, null, databaseVersion) {

    override def onCreate(db:SQLiteDatabase) {
      db.execSQL(createSQL)
    }

    override def onUpgrade(db:SQLiteDatabase, oldVer:Int, newVer:Int) = onUpgradeDatabase(db, oldVer, newVer)
  }

  override def onCreate:Boolean = {
    dbHelper = new DatabaseHelper(getContext)
    true
  }

  override def getType(uri:Uri) = uriData.get(uriMatcher.`match`(uri)) match {
    case Some(v) => v.get('type) match {
      case Some(t) => t.asInstanceOf[String]
      case _ => throw new IllegalArgumentException("Type not defined for URI: "+uri)
    }
    case _ => throw new IllegalArgumentException("Invalid URI: "+uri)
  }

  override def insert(uri:Uri, values:ContentValues):Uri = uriData.get(uriMatcher.`match`(uri)) match {
    case Some(m) => m.get('insert) match {
      case Some(f) =>
        val u = f.asInstanceOf[InsertFn](uri, values)
        getContext.getContentResolver.notifyChange(u, null)
        u
      case _ => throw new IllegalArgumentException("Insert not defined for "+uri)
    }
    case _ => throw new IllegalArgumentException("Invalid URI: "+uri)
  }

  override def query(uri:Uri, projection:Array[String], selection:String, selectionArgs:Array[String], sortOrder:String):Cursor = uriData.get(uriMatcher.`match`(uri)) match {
    case Some(m) => m.get('query) match {
      case Some(f) =>
        val cursor = f.asInstanceOf[QueryFn](uri, projection, selection, selectionArgs, sortOrder)
        cursor.setNotificationUri(getContext.getContentResolver, uri)
        cursor
      case _ => throw new IllegalArgumentException("query not defined for "+uri)
    }
    case _ => throw new IllegalArgumentException("Invalid URI: "+uri)
  }

  override def update(uri:Uri, values:ContentValues , where:String, whereArgs:Array[String]):Int = uriData.get(uriMatcher.`match`(uri)) match {
    case Some(m) => m.get('update) match {
      case Some(f) =>
        val count = f.asInstanceOf[UpdateFn](uri, values, where, whereArgs)
        getContext.getContentResolver.notifyChange(uri, null)
        count
      case _ => throw new IllegalArgumentException("update not defined for "+uri)
    }
    case _ => throw new IllegalArgumentException("Invalid URI: "+uri)
  }

  override def delete(uri:Uri, where:String, whereArgs:Array[String]):Int = uriData.get(uriMatcher.`match`(uri)) match {
    case Some(m) => m.get('delete) match {
      case Some(f) =>
        val count = f.asInstanceOf[DeleteFn](uri, where, whereArgs)
        getContext.getContentResolver.notifyChange(uri, null)
        count
      case _ => throw new IllegalArgumentException("Delete not defined for "+uri)
    }
    case _ => throw new IllegalArgumentException("Invalid URI: "+uri)
  }

}
