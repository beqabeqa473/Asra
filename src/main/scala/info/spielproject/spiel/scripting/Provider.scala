package info.spielproject.spiel.scripting

import android.content.{ContentValues, ContentUris}
import android.database.{Cursor, SQLException}
import android.database.sqlite.{SQLiteDatabase, SQLiteQueryBuilder}
import android.net.Uri;

object Provider {
  val URI = Uri.parse("content://"+Provider.getClass.toString+"/scripts")
}

class Provider extends Content {

  override def databaseName = "scripts.db"
  override def databaseVersion = 1

  override def createSQL = """CREATE TABLE scripts (
    _id INTEGER PRIMARY KEY,
    title STRING NOT NULL,
    content TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
    );"""

  override def onUpgradeDatabase(db:SQLiteDatabase, oldVer:Int, newVer:Int) { }

  on("scripts", "vnd.android.cursor.dir/vnd.spiel.script", {

    onInsert { (uri:Uri, values:ContentValues) =>
      if(values == null)
        throw new IllegalArgumentException("Null values not allowed")
      val now = System.currentTimeMillis
      values.put("created_at", now)
      values.put("updated_at", now)
      val db = databaseHelper.getWritableDatabase
      val rowId = db.insert("scripts", "", values)
      if(rowId > 0) 
        ContentUris.withAppendedId(uri, rowId)
      else
        throw new SQLException("Error inserting row")
    }

    onQuery { (uri:Uri, projection:Array[String], selection:String, selectionArgs:Array[String], sortOrder:String) =>
      val qb = new SQLiteQueryBuilder
      qb.setTables("scripts")
      val db = databaseHelper.getReadableDatabase
      qb.query(db, projection, selection, selectionArgs, null, null, sortOrder)
    }

    onUpdate { (uri:Uri, values:ContentValues, where:String, whereArgs:Array[String]) =>
      val db = databaseHelper.getWritableDatabase
      db.update("scripts", values, where, whereArgs)
    }

    onDelete { (uri:Uri, where:String, whereArgs:Array[String]) =>
      val db = databaseHelper.getWritableDatabase
      db.delete("scripts", where, whereArgs)
    }

  })

  on("scripts/#", "vnd.android.cursor.item/vnd.spiel.script", {

    onQuery { (uri:Uri, projection:Array[String], selection:String, selectionArgs:Array[String], sortOrder:String) =>
      val qb = new SQLiteQueryBuilder
      qb.setTables("scripts")
      qb.appendWhere( "_id = "+uri.getPathSegments.get(1))
      val db = databaseHelper.getReadableDatabase
      qb.query(db, projection, selection, selectionArgs, null, null, sortOrder)
    }

    onUpdate { (uri:Uri, values:ContentValues , where:String, whereArgs:Array[String]) =>
      val db = databaseHelper.getWritableDatabase
      val scriptId = uri.getPathSegments.get(1)
      val w = if(where != null) " AND ("+where+")" else ""
      db.update("scripts", values, "_id = "+scriptId
              +w, whereArgs)
    }

    onDelete {(uri:Uri, where:String, whereArgs:Array[String]) =>
      val db = databaseHelper.getWritableDatabase
      val scriptId = uri.getPathSegments.get(1)
      val w = if(where != null) " AND ("+where+")" else ""
      db.delete("scripts", "_id = "+scriptId
              +w, whereArgs)
    }

  })

}
