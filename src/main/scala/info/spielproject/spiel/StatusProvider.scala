package info.spielproject.spiel

import android.content.{ContentProvider, ContentValues}
import android.database.{Cursor, MatrixCursor}
import android.net.Uri;
import android.util.Log

/**
 * Indicates to other apps that a screen reader is active.
*/

class StatusProvider extends ContentProvider {

  // This code was blatantly ripped off from TalkBack, Scalafied and 
  // simplified. I don't fully understand it myself and as such won't 
  // document it, but it does seem to work.

  private val cursorValue = new Array[String](1)
  cursorValue(0) = ""

  private class StatusCursor(val status:Int) extends MatrixCursor(cursorValue) {

    override def getCount = 1

    override def getString(column:Int) = status.toString

    override def getInt(column:Int) = status

  }

  override def delete(uri:Uri, selection:String, selectionArgs:Array[String]) = 0

  override def getType(uri:Uri):String = null

  override def insert(uri:Uri, values:ContentValues):Uri = null

  def onCreate() = true

  override def query(uri:Uri, projection:Array[String], selection:String, selectionArgs:Array[String], sortOrder:String):Cursor = {
    val status = if(SpielService.enabled) 1 else 0
    new StatusCursor(status)
  }

  override def update(uri:Uri, values:ContentValues, selection:String, selectionArgs:Array[String]) = 0

}
