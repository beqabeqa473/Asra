package info.spielproject.spiel
package activities

import collection.JavaConversions._

import android.app.{Activity, AlertDialog, Dialog, ListActivity, TabActivity}
import android.content.{ContentUris, Context, DialogInterface, Intent}
import android.database.Cursor
import android.os.Build.VERSION
import android.os.Bundle
import android.preference.{CheckBoxPreference, ListPreference, Preference, PreferenceActivity, PreferenceCategory, PreferenceScreen}
import android.util.Log
import android.view.{ContextMenu, Menu, MenuInflater, MenuItem, View, ViewGroup}
import android.view.accessibility.AccessibilityEvent
import android.widget.{AdapterView, ArrayAdapter, Button, CheckBox, EditText, ListView, RadioButton, RadioGroup, TabHost}

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
    val intent = getIntent
    if(intent.getStringExtra("package") == null)
      initGlobalPreferences()
    else {
      setPreferenceScreen(scriptPreferencesFor(intent.getStringExtra("package")))
    }
  }

  private def initGlobalPreferences() {
    addPreferencesFromResource(R.xml.preferences)

    val enginesPreference = findPreference("speechEngine").asInstanceOf[ListPreference]
    if(VERSION.SDK_INT < 8) {
      enginesPreference.setEnabled(false)
      enginesPreference.setShouldDisableView(true)
      enginesPreference.setSelectable(false)
      val bluetoothPreference = findPreference("useBluetoothSCO")
      bluetoothPreference.setEnabled(false)
      bluetoothPreference.setShouldDisableView(true)
      bluetoothPreference.setSelectable(false)
    } else if(!TTS.defaultsEnforced_?) {
      val intent = new Intent("android.intent.action.START_TTS_ENGINE")
      val pm = getPackageManager
      val engines = pm.queryIntentActivities(intent, 0).map { engine =>
        var label = engine.loadLabel(pm).toString()
        if(label == "") label = engine.activityInfo.name.toString()
        (label, engine.activityInfo.packageName)
      }
      enginesPreference.setEntries(engines.map(_._1).toArray[CharSequence])
      enginesPreference.setEntryValues(engines.map(_._2).toArray[CharSequence])
    }

    if(TTS.defaultsEnforced_?) {
      enginesPreference.setEnabled(false)
      enginesPreference.setShouldDisableView(true)
      enginesPreference.setSelectable(false)
      val rateScale = findPreference("rateScale")
      rateScale.setEnabled(false)
      rateScale.setShouldDisableView(true)
      rateScale.setSelectable(false)
      val pitchScale = findPreference("pitchScale")
      pitchScale.setEnabled(false)
      pitchScale.setShouldDisableView(true)
      pitchScale.setSelectable(false)
    }

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

    val scripts = findPreference("scripts").asInstanceOf[PreferenceScreen]
    if(Scripter.preferences == Map.empty) {
      scripts.setEnabled(false)
      scripts.setSelectable(false)
    } else {
      Scripter.preferences.foreach { pkg =>
        scripts.addPreference(scriptPreferencesFor(pkg._1))
      }
    }
  }

  private def scriptPreferencesFor(pkg:String) = {
    val screen = getPreferenceManager.createPreferenceScreen(this)
    screen.setTitle(Script.labelFor(pkg))
    screen.setEnabled(true)
    screen.setSelectable(true)
    Scripter.preferences(pkg).foreach { pkgpref =>
      val pref = pkgpref._2
      val preference = new CheckBoxPreference(this)
      val key = pkg+"_"+pkgpref._1
      preference.setKey(key)
      preference.setTitle(pref("title").asInstanceOf[String])
      preference.setSummary(pref("summary").asInstanceOf[String])
      preference.setChecked(pref("default").asInstanceOf[Boolean])
      screen.addPreference(preference)
    }
    screen
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

class Scripts extends Activity with Refreshable with RadioGroup.OnCheckedChangeListener {

  private var listView:ListView = null

  private var system = true

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.scripts)
    listView = findViewById(R.id.scripts).asInstanceOf[ListView]
    registerForContextMenu(listView)
    val mode = findViewById(R.id.mode).asInstanceOf[RadioGroup]
    mode.setOnCheckedChangeListener(this)
    mode.check(R.id.system)
  }

  def onCheckedChanged(group:RadioGroup, id:Int) {
    system = id == R.id.system
    refresh()
  }

  def refresh() = {
    if(system)
      refreshSystem()
    else
      refreshUser()
  }

  private var cursor:Cursor = null

  private def refreshSystem() {
    cursor = managedQuery(Provider.uri, Provider.columns.projection, null, null, null)
    listView.setAdapter(
      new SimpleCursorAdapter(this,
        R.layout.script_row,
        cursor,
        List(Provider.columns.label).toArray,
        List(R.id.script_title).toArray
      )
    )
    BazaarProvider.checkRemoteScripts()
  }

  private def refreshUser() {
    Log.d("spielscript", "refreshUser(): "+Scripter.userScripts)
    listView.setAdapter(
      new ArrayAdapter[Script](
        this,
        android.R.layout.simple_list_item_1,
        Scripter.userScripts
      )
    )
  }

  private var menu:Option[Menu] = None

  override def onCreateOptionsMenu(m:Menu):Boolean = {
    menu = Some(m)
    new MenuInflater(this).inflate(R.menu.scripts, menu.get)
    super.onCreateOptionsMenu(m)
  }

  override def onCreateContextMenu(menu:ContextMenu, v:View, info:ContextMenu.ContextMenuInfo) {
    val menuID = if(system) R.menu.system_scripts_context else R.menu.user_scripts_context
    new MenuInflater(this).inflate(menuID, menu)
    if(system) {
      val selection = info.asInstanceOf[AdapterView.AdapterContextMenuInfo].id
      val uri = ContentUris.withAppendedId(Provider.uri, selection)
      val c = getContentResolver.query(uri, null, null, null, null)
      c.moveToFirst()
      val script:Option[Script] = if(c.isAfterLast)
        None
      else
        Some(new Script(this, c))
      c.close()
      script.foreach { scr =>
        if(!scr.preferences_?) {
          val item = menu.findItem(R.id.preferences)
          item.setEnabled(false)
          item.setVisible(false)
        }
      }
    } else {
      val script = Scripter.userScripts(info.asInstanceOf[AdapterView.AdapterContextMenuInfo].position)
      if(!script.preferences_?) {
        val item = menu.findItem(R.id.preferences)
        item.setEnabled(false)
        item.setVisible(false)
      }
      if(!script.successfullyRan_?) {
        val item = menu.findItem(R.id.postToBazaar)
        item.setEnabled(false)
        item.setVisible(false)
      }
    }
  }

  override def onContextItemSelected(item:MenuItem) = {
    if(system) {
      val selection = item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].id
      val uri = ContentUris.withAppendedId(Provider.uri, selection)
      val c = getContentResolver.query(uri, null, null, null, null)
      c.moveToFirst()
      val script:Option[Script] = if(c.isAfterLast)
        None
      else
        Some(new Script(this, c))
      c.close()
      item.getItemId match {
        case R.id.preferences =>
          script.foreach { s =>
            val intent = new Intent(this, classOf[PreferencesActivity])
            intent.putExtra("package", s.pkg)
            startActivity(intent)
          }
        case R.id.copyToExternalStorage =>
          script.foreach { s =>
            val filename = s.writeToExternalStorage()
            new AlertDialog.Builder(this)
            .setMessage(getString(R.string.scriptCopied, filename))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
            
          }
        case R.id.delete =>
          script.foreach { s =>
            new AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirmDelete, s.pkg))
            .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener {
              def onClick(i:DialogInterface, what:Int) {
                getContentResolver.delete(uri, null, null)
                s.uninstall()
                cursor.requery()
              }
            })
            .setNegativeButton(getString(R.string.no), null)
            .show()
          }
      }
    } else {
      val script = Scripter.userScripts(item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].position)
      item.getItemId match {
        case R.id.preferences =>
          val intent = new Intent(this, classOf[PreferencesActivity])
          intent.putExtra("package", script.pkg)
          startActivity(intent)
        case R.id.reload => script.reload()
        case R.id.postToBazaar =>
          if(Preferences.bazaarUsername == "" || Preferences.bazaarPassword == "") {
            showDialog(credentialsDialog)
          }
        case R.id.delete =>
          new AlertDialog.Builder(this)
          .setMessage(getString(R.string.confirmDelete, script.pkg))
          .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener {
            def onClick(i:DialogInterface, what:Int) {
              script.delete()
              script.uninstall()
              refreshUser()
            }
          })
          .setNegativeButton(getString(R.string.no), null)
          .show()
      }
    }
    true
  }

  private val credentialsDialog = 1

  override protected def onCreateDialog(dialogType:Int) = dialogType match {
    case credentialsDialog =>
      val dialog = new Dialog(this)
      dialog.setContentView(R.layout.bazaar_credentials)
      val username = dialog.findViewById(R.id.username).asInstanceOf[EditText]
      val password = dialog.findViewById(R.id.username).asInstanceOf[EditText]
      def clearValues() {
        username.setText("")
        password.setText("")
      }
      dialog.findViewById(R.id.ok).asInstanceOf[Button].setOnClickListener(new View.OnClickListener {
        def onClick(v:View) {
          if(username.getText.toString != "" && password.getText.toString != "") {
            Preferences.bazaarUsername = username.getText.toString
            Preferences.bazaarPassword = password.getText.toString
            dialog.dismiss()
          } else {
            dialog.show()
          }
        }
      })
      dialog.findViewById(R.id.cancel).asInstanceOf[Button].setOnClickListener(new View.OnClickListener {
        def onClick(v:View) = {
          clearValues()
          dialog.dismiss()
        }
      })
      dialog
  }

}

/**
 * Lists most recently-received AccessibilityEvents.
*/

class Events extends ListActivity with Refreshable {

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    registerForContextMenu(getListView)
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

  override def onCreateContextMenu(menu:ContextMenu, v:View, info:ContextMenu.ContextMenuInfo) {
    new MenuInflater(this).inflate(R.menu.events_context, menu)
  }

  override def onContextItemSelected(item:MenuItem) = {
    val event = EventReviewQueue(item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].position)
    item.getItemId match {
      case R.id.createTemplate =>
        val filename = Scripter.createTemplateFor(event)
        new AlertDialog.Builder(this)
        .setMessage(getString(R.string.templateCreated, filename))
        .setPositiveButton(getString(R.string.ok), null)
        .show()
    }
    true
  }

}

/**
 * Activity that handles the installation of scripts.
*/

class ScriptInstaller extends Activity with AdapterView.OnItemClickListener {

  private var scripts:List[Script] = Nil

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.script_installer)

    val scriptsList = findViewById(R.id.scripts).asInstanceOf[ListView]
    scripts = BazaarProvider.newOrUpdatedScripts
    scriptsList.setAdapter(
      new ArrayAdapter[Script](
        this,
        android.R.layout.simple_list_item_multiple_choice,
        scripts.toArray
      )
    )
    scriptsList.setOnItemClickListener(this)
    scriptsList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)

    def selectAll() {
      var seen:List[String] = Nil
      for(p <- 0.to(scriptsList.getCount-1)) {
        val script = scripts(p)
        if(!seen.contains(script.pkg)) {
          seen ::= script.pkg
          scriptsList.setItemChecked(p, true)
        }
      }
    }

    selectAll()

    findViewById(R.id.selectAll).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener {
        def onClick(v:View) {
          selectAll()
        }
      }
    )

    findViewById(R.id.deselectAll).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener {
        def onClick(v:View) = scriptsList.clearChoices()
      }
    )

    findViewById(R.id.install).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener {
        def onClick(v:View) {
          val checked = scriptsList.getCheckedItemPositions()
          for(scriptID <- 0.to(scripts.size-1)) {
            if(checked.get(scriptID)) {
              val script = scripts(scriptID)
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

  def onItemClick(parent:AdapterView[_], view:View, position:Int, id:Long) {
    val list = parent.asInstanceOf[ListView]
    // Needed because clicking a list without a listener doesn't raise AccessibilityEvents.
    if(list.isItemChecked(position)) {
      TTS.speak(getString(R.string.checked), true)
      val current = scripts(position)
      for(i <- 0.to(list.getCount-1)) {
        val script = scripts(position)
        if(script != current && script.pkg == current.pkg)
          list.setItemChecked(i, false)
      }
    } else
      TTS.speak(getString(R.string.notChecked), true)
  }

}
