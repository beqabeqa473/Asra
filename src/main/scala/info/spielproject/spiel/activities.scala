package info.spielproject.spiel
package activities

import collection.JavaConversions._

import android.app.{Activity, ListActivity, TabActivity}
import android.content.{Context, Intent}
import android.os.Bundle
import android.preference.{ListPreference, Preference, PreferenceActivity}
import android.util.Log
import android.view.{Menu, MenuInflater, MenuItem, View, ViewGroup}
import android.view.accessibility.AccessibilityEvent
import android.widget.{ArrayAdapter, Button, CheckBox, ListView, TabHost}

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
      .setContent(new Intent(this, classOf[Scripts]))
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

/**
 * Trait implementing a "Refresh" menu item and action.
*/

trait Refreshable {
  this: Activity =>

  override def onOptionsItemSelected(item:MenuItem) = {
    item.getItemId match {
      case R.id.refresh => refresh
    }
    true
  }

  def refresh()

}

import android.widget.SimpleCursorAdapter

class Scripts extends ListActivity with Refreshable {

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    refresh()
  }

  def refresh() = {
    val cursor = managedQuery(scripting.Provider.uri, scripting.Provider.columns.projection, null, null, null)
    setListAdapter(
      new SimpleCursorAdapter(this,
        R.layout.script_row,
        cursor,
        List(scripting.Provider.columns.pkg).toArray,
        List(R.id.script_title).toArray
      )
    )
    BazaarProvider.checkRemoteScripts()
  }

  private var menu:Option[Menu] = None

  override def onCreateOptionsMenu(m:Menu):Boolean = {
    menu = Some(m)
    new MenuInflater(this).inflate(R.menu.scripts, menu.get)
    super.onCreateOptionsMenu(m)
  }

}

/**
 * Lists most recently-received AccessibilityEvents.
*/

class Events extends ListActivity with Refreshable {

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    refresh()
  }

  def refresh() {
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

}

/**
 * Activity that handles the installation of scripts.
*/

class ScriptInstaller extends Activity {

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.script_installer)

    val scripts = findViewById(R.id.scripts).asInstanceOf[ListView]
    scripts.setAdapter(
      new ArrayAdapter[Script](
        this,
        android.R.layout.simple_list_item_multiple_choice,
        BazaarProvider.newOrUpdatedScripts.toArray
      )
    )
    scripts.setItemsCanFocus(false)
    scripts.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)

    findViewById(R.id.selectAll).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener {
        def onClick(v:View) {
          for(p <- 0.to(BazaarProvider.newOrUpdatedScripts.size-1)) {
            scripts.setItemChecked(p, true)
          }
        }
      }
    )

    findViewById(R.id.deselectAll).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener {
        def onClick(v:View) = scripts.clearChoices()
      }
    )

    findViewById(R.id.install).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener {
        def onClick(v:View) {
          val checked = scripts.getCheckedItemPositions()
          for(scriptID <- 0.to(BazaarProvider.newOrUpdatedScripts.size-1)) {
            if(checked.get(scriptID)) {
              val script = BazaarProvider.newOrUpdatedScripts(scriptID)
              script.run()
              script.save()
            }
          }
          finish()
        }
      }
    )

    findViewById(R.id.cancel).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener {
        def onClick(v:View) = finish()
      }
    )

  }

}
