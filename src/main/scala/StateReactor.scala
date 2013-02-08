package info.spielproject.spiel

import android.content._
import android.net.Uri

import events._
import scripting._

/**
 * Singleton which registers many callbacks initiated by <code>StateObserver</code>.
*/

object StateReactor {

  private var service:SpielService = null

  /**
   * Initializes based on the specified <code>SpielService</code>, setting initial states.
  */

  def apply(svc:SpielService) {
    service = svc
  }

  // Check Bazaar for new scripts on app installation.

  ApplicationAdded += { intent:Intent =>
    BazaarProvider.checkRemoteScripts()
  }

  ApplicationRemoved += { intent:Intent =>
    val packageName = intent.getData().getSchemeSpecificPart
    val cursor = service.getContentResolver.query(Provider.uri, null, "pkg = ?", List(packageName).toArray, null)
    if(cursor.getCount > 0) {
      cursor.moveToFirst()
      while(!cursor.isAfterLast) {
        val script = new Script(service, cursor)
        script.uninstall()
        val scriptURI = ContentUris.withAppendedId(Provider.uri, script.id.get)
        service.getContentResolver.delete(scriptURI, null, null)
        cursor.moveToNext()
      }
    }
    cursor.close()
  }

}
