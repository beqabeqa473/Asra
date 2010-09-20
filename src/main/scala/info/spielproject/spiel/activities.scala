package info.spielproject.spiel
package activities

import collection.JavaConversions._

import android.app.{ListActivity, TabActivity}
import android.content.Intent
import android.os.Bundle
import android.preference.{ListPreference, Preference, PreferenceActivity}
import android.util.Log
import android.view.{Menu, MenuInflater, MenuItem}
import android.view.accessibility.AccessibilityEvent
import android.widget.{ArrayAdapter, TabHost}

import scripting._

import handlers._
import triggers.Triggers

/**
 * Activity that serves as a host for other tabs.
*/

class Spiel extends TabActivity {
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

    host.addTab(host.newTabSpec("events")
      .setIndicator(getString(R.string.events))
      .setContent(new Intent(this, classOf[Events]))
    )
  }
}

/**
 * Activity for setting preferences.
*/

class PreferencesActivity extends PreferenceActivity {
  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    addPreferencesFromResource(R.xml.preferences)

    // We need to set some preferences dynamically. First, set engines.
    val intent = new Intent("android.intent.action.START_TTS_ENGINE")
    val pm = getPackageManager
    val engines = pm.queryIntentActivities(intent, 0).map { engine =>
      var label = engine.loadLabel(pm).toString()
      if(label == "") label = engine.activityInfo.name.toString()
      (label, engine.activityInfo.packageName)
    }
    val enginesPreference = findPreference("speechEngine").asInstanceOf[ListPreference]
    enginesPreference.setEntries(engines.map(_._1).toArray[CharSequence])
    enginesPreference.setEntryValues(engines.map(_._2).toArray[CharSequence])

    // Now set the shortcut to system-wide TTS settings.
    val ttsPreference = findPreference("textToSpeechSettings")
    ttsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener {
      def onPreferenceClick(p:Preference) = {
        val intent = new Intent
        intent.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings")
        startActivity(intent)
        false
      }
    })

    // Set up triggers. First add an action for "None," then concat others.
    val actions = ("None", "") :: Triggers.actions.map((v) => (v._2.name, v._1)).toList
    val onShake = findPreference("onShakingStarted").asInstanceOf[ListPreference]
    onShake.setEntries(actions.map(_._1).toArray[CharSequence])
    onShake.setEntryValues(actions.map(_._2).toArray[CharSequence])
    val onProximityNear = findPreference("onProximityNear").asInstanceOf[ListPreference]
    onProximityNear.setEntries(actions.map(_._1).toArray[CharSequence])
    onProximityNear.setEntryValues(actions.map(_._2).toArray[CharSequence])
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

/**
 * Lists most recently-received AccessibilityEvents.
*/

class Events extends ListActivity {

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    refresh()
  }

  private def refresh() {
    if(Preferences.viewRecentEvents)
      setListAdapter(
        new ArrayAdapter[PrettyAccessibilityEvent](
          this,
          android.R.layout.simple_list_item_1,
          EventReviewQueue.toArray
        )
      )
    else
      setListAdapter(
        new ArrayAdapter[String](
          this,
          android.R.layout.simple_list_item_1,
          List(getString(R.string.noEvents)).toArray
        )
      )
  }

  private var menu:Option[Menu] = None

  override def onCreateOptionsMenu(m:Menu):Boolean = {
    menu = Some(m)
    new MenuInflater(this).inflate(R.menu.events, menu.get)
    super.onCreateOptionsMenu(m)
  }

  override def onOptionsItemSelected(item:MenuItem) = {
    item.getItemId match {
      case R.id.refresh => refresh
    }
    true
  }

}
