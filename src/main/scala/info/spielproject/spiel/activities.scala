package info.spielproject.spiel

import android.app.{ListActivity, TabActivity}
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceActivity
import android.view.{Menu, MenuInflater, MenuItem}
import android.widget.{ArrayAdapter, TabHost}

import scripting._


class SpielActivity extends TabActivity {
  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    val host = getTabHost

    host.addTab(host.newTabSpec("preferences")
      .setIndicator(getString(R.string.preferences))
      .setContent(new Intent(this, classOf[PreferencesActivity]))
    )

    host.addTab(host.newTabSpec("scripts")
      .setIndicator(getString(R.string.scripts))
      .setContent(new Intent(this, classOf[ScriptsActivity]))
    )

  }
}

class PreferencesActivity extends PreferenceActivity {
  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    addPreferencesFromResource(R.xml.preferences)
  }
}

import android.widget.SimpleCursorAdapter

class ScriptsActivity extends ListActivity {

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    refresh()
  }

  private def refresh() = {
    val cursor = managedQuery(scripting.Provider.uri, scripting.Provider.columns.projection, null, null, null)
    setListAdapter(
      new SimpleCursorAdapter(this,
        R.layout.script_row,
        cursor,
        scripting.Provider.columns.projection,
        List(R.id.script_title).toArray
      )
    )
  }

  private var menu:Option[Menu] = None

  override def onCreateOptionsMenu(m:Menu):Boolean = {
    menu = Some(m)
    new MenuInflater(this).inflate(R.menu.scripts, menu.get)
    super.onCreateOptionsMenu(m)
  }

  override def onOptionsItemSelected(item:MenuItem) = {
    item.getItemId match {
      case R.id.refresh => refresh
    }
    true
  }

}
