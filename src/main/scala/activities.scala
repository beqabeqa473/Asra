package info.spielproject.spiel
package activities

import actors.Actor.actor
import collection.JavaConversions._

import android.app.{Activity, AlertDialog, Dialog, DialogFragment, ListActivity, LoaderManager, TabActivity}
import android.bluetooth.BluetoothAdapter
import android.content.{ContentUris, Context, CursorLoader, DialogInterface, Intent, Loader}
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build.VERSION
import android.os.Bundle
import android.preference.{CheckBoxPreference, ListPreference, Preference, PreferenceActivity, PreferenceCategory, PreferenceScreen}
import android.util.Log
import android.view.{ContextMenu, KeyEvent, Menu, MenuInflater, MenuItem, View, ViewGroup}
import android.view.accessibility.{AccessibilityEvent, AccessibilityNodeInfo}
import android.widget.{AdapterView, ArrayAdapter, ListView, RadioGroup, SimpleCursorAdapter, TabHost}
import org.droidparts.preference.MultiSelectListPreference

import presenters._
import scripting._
import triggers.Triggers

import TypedResource._

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

    handleCustomUri()

  }

  override def onResume() {
    super.onResume()
    handleCustomUri()
  }

  private def handleCustomUri() {
    Option(getIntent).flatMap((i) => Option(i.getData)).foreach { uri =>
      if(uri.getScheme == "spiel") {
        val parts = uri.getSchemeSpecificPart.split("?")
        if(!parts.isEmpty && parts.head == "scripts")
          setDefaultTab(1)
      }
    }
  }

}

/**
 * Activity for setting preferences.
*/

class PreferencesActivity extends PreferenceActivity {

  override def onResume() {
    super.onResume()
    val intent = getIntent
    setIntent(intent)
    Option(intent.getStringExtra("package")).map { pkg =>
      setPreferenceScreen(scriptPreferencesFor(pkg))
    }.getOrElse {
      initGlobalPreferences()
    }
  }

  private def initGlobalPreferences() {
    Option(getPreferenceScreen).foreach(_.removeAll())
    addPreferencesFromResource(R.xml.preferences)

    val enginesPreference = findPreference("speechEngine").asInstanceOf[ListPreference]
    if(!TTS.defaultsEnforced_?) {
      enginesPreference.setEntries((getString(R.string.systemDefault) :: TTS.engines.map(_._1).toList).toArray[CharSequence])
      enginesPreference.setEntryValues(("" :: TTS.engines.map(_._2).toList).toArray[CharSequence])
    }

    def enableOrDisablePreference(p:Preference) {
      if(TTS.defaultsEnforced_?)
        getPreferenceScreen.getPreference(0).asInstanceOf[PreferenceScreen].removePreference(p)
      else {
        p.setEnabled(true)
        p.setShouldDisableView(false)
        p.setSelectable(true)
      }
    }

    enableOrDisablePreference(enginesPreference)
    enableOrDisablePreference(findPreference("rateScale"))
    enableOrDisablePreference(findPreference("pitchScale"))

    Option(BluetoothAdapter.getDefaultAdapter).getOrElse {
      getPreferenceScreen.getPreference(0).asInstanceOf[PreferenceScreen].removePreference(findPreference("useBluetoothSCO"))
    }

    // Now set the shortcut to system-wide TTS settings.
    val ttsPreference = findPreference("textToSpeechSettings")
    ttsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener {
      def onPreferenceClick(p:Preference) = {
        if(VERSION.SDK_INT < 11) {
          val intent = new Intent
          intent.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings")
          startActivity(intent)
        } else {
          startActivity(new Intent("com.android.settings.TTS_SETTINGS"))
        }
        false
      }
    })

    val pm = getPackageManager

    if(pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) || pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)) {

      // Set up triggers. First add an action for "None," then concat others.
      val actions = (getString(R.string.none), "") :: Triggers.actions.map((v) => (v._2.name, v._1)).toList

      if(pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
        val onShake = findPreference("onShakingStarted").asInstanceOf[ListPreference]
        onShake.setEntries(actions.map(_._1).toArray[CharSequence])
        onShake.setEntryValues(actions.map(_._2).toArray[CharSequence])
      } else
        getPreferenceScreen.getPreference(2).asInstanceOf[PreferenceScreen].removePreference(findPreference("onShakingStarted"))

      if(pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)) {
        val onProximityNear = findPreference("onProximityNear").asInstanceOf[ListPreference]
        onProximityNear.setEntries(actions.map(_._1).toArray[CharSequence])
        onProximityNear.setEntryValues(actions.map(_._2).toArray[CharSequence])
      } else
        getPreferenceScreen.getPreference(2).asInstanceOf[PreferenceScreen].removePreference(findPreference("onProximityNear"))

    } else
      getPreferenceScreen.removePreference(getPreferenceScreen.getPreference(2))

    actor {
      val notificationFilters = findPreference("notificationFilters").asInstanceOf[MultiSelectListPreference]
      notificationFilters.setShouldDisableView(true)
      notificationFilters.setEnabled(false)
      val packages = utils.installedPackages.map { pkg =>
        (pkg.packageName, try {
          pm.getApplicationInfo(pkg.packageName, 0).loadLabel(pm).toString
        } catch {
          case _ => pkg.packageName
        })
      }.sortWith((v1, v2) => v1._2 < v2._2)
      runOnUiThread(new Runnable { def run() {
        notificationFilters.setEntryValues(packages.map(_._1.asInstanceOf[CharSequence]).toArray)
        notificationFilters.setEntries(packages.map(_._2.asInstanceOf[CharSequence]).toArray)
        notificationFilters.setEnabled(true)
      }})
    }



    val scripts = findPreference("scripts").asInstanceOf[PreferenceScreen]
    if(Scripter.preferences == Map.empty)
      getPreferenceScreen.removePreference(scripts)
    else {
      scripts.removeAll()
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

class Scripts extends Activity with TypedActivity with Refreshable with RadioGroup.OnCheckedChangeListener with LoaderManager.LoaderCallbacks[Cursor] {

  private lazy val listView = findView(TR.scripts)

  private var system = true

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.scripts)
    registerForContextMenu(listView)
    val mode = findView(TR.mode)
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

  private lazy val adapter = new SimpleCursorAdapter(
    this,
    R.layout.script_row,
    null,
    List(Provider.columns.label).toArray,
    List(R.id.script_title).toArray,
    0
  )

  def onCreateLoader(id:Int, bundle:Bundle) = {
    new CursorLoader(this, Provider.uri, Provider.columns.projection, null, null, null)
  }

  def onLoaderReset(loader:Loader[Cursor]) {
    adapter.swapCursor(null)
  }

  def onLoadFinished(loader:Loader[Cursor], cursor:Cursor) {
    adapter.swapCursor(cursor)
  }

  private def refreshSystem() {
    getLoaderManager.initLoader(0, null, this)
    listView.setAdapter(adapter)
    BazaarProvider.checkRemoteScripts()
  }

  private def refreshUser() {
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
                refreshSystem()
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
        case R.id.postToBazaar =>
          if(script.reload()) {
            scriptToPost = Some(script)
            if(Preferences.bazaarUsername == "" || Preferences.bazaarPassword == "")
              (new CredentialsDialog).show(getFragmentManager, "credentials")
            else
              (new PostDialog).show(getFragmentManager, "post")
          } else {
            new AlertDialog.Builder(this)
            .setMessage(getString(R.string.script_reload_error))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
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

  private class CredentialsDialog extends DialogFragment {
    override def onCreateDialog(bundle:Bundle) = {
      val dialog = new Dialog(Scripts.this) with TypedDialog
      dialog.setContentView(R.layout.bazaar_credentials)
      val message = dialog.findView(TR.message)
      if(Preferences.bazaarUsername != "" || Preferences.bazaarPassword != "")
        message.setText(getString(R.string.bazaar_credentials_invalid))
      else
        message.setText(getString(R.string.bazaar_credentials))
      val username = dialog.findView(TR.username)
      username.setText(Preferences.bazaarUsername)
      val password = dialog.findView(TR.password)
      password.setText(Preferences.bazaarPassword)
      def clearValues() {
        username.setText("")
        password.setText("")
      }
      dialog.findView(TR.ok).setOnClickListener(new View.OnClickListener {
        def onClick(v:View) {
          if(username.getText.toString != "" && password.getText.toString != "") {
            Preferences.bazaarUsername = username.getText.toString
            Preferences.bazaarPassword = password.getText.toString
            dialog.dismiss()
            postToBazaar()
          } else
            dialog.show()
        }
      })
      dialog.findView(TR.signup).setOnClickListener(new View.OnClickListener {
        def onClick(v:View) {
          val url = "http://bazaar.spielproject.info/signup?returnTo=spiel:scripts"
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
          intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
          startActivity(intent)
          dialog.dismiss()
        }
      })
      dialog.findView(TR.cancel).setOnClickListener(new View.OnClickListener {
        def onClick(v:View) {
          clearValues()
          scriptToPost = None
          dialog.dismiss()
        }
      })
      dialog
    }
  }

  private class PostDialog extends DialogFragment {
    override def onCreateDialog(bundle:Bundle) = {
      val dialog = new Dialog(Scripts.this) with TypedDialog
      dialog.setContentView(R.layout.post_script)
      val changesField = dialog.findView(TR.changes)
      changesField.setText(scriptChanges)
      def clearValues() {
        changesField.setText("")
      }
      dialog.findView(TR.ok).setOnClickListener(new View.OnClickListener {
        def onClick(v:View) {
          scriptChanges = changesField.getText.toString
          dialog.dismiss()
          postToBazaar()
        }
      })
      dialog.findView(TR.cancel).setOnClickListener(new View.OnClickListener {
        def onClick(v:View) {
          clearValues()
          scriptToPost = None
          scriptChanges = ""
          dialog.dismiss()
        }
      })
      dialog
    }
  }

  private var scriptToPost:Option[Script] = None
  private var scriptChanges = ""

  private def postToBazaar() = scriptToPost.foreach { script =>
    val dialog = new AlertDialog.Builder(this)
    dialog.setPositiveButton(getString(R.string.ok), null)
    try {
      BazaarProvider.post(script, scriptChanges)
      scriptToPost = None
      scriptChanges = ""
      dialog.setMessage(getString(R.string.script_posted))
      dialog.show()
    } catch {
      case e:scripting.AuthorizationFailed =>
        Preferences.bazaarPassword = ""
        (new CredentialsDialog).show(getFragmentManager, "credentials")
      case e =>
        scriptToPost = None
        dialog.setMessage(getString(R.string.script_posting_error))
        dialog.show()
    }
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
    setListAdapter(
      new ArrayAdapter[PrettyAccessibilityEvent](
        this,
        android.R.layout.simple_list_item_1,
        EventReviewQueue.toArray
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

  override def onContextItemSelected(item:MenuItem):Boolean = {
    val position = item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].position
    if(!EventReviewQueue.isDefinedAt(position)) return true
    val event = EventReviewQueue(position)
    item.getItemId match {
      case R.id.createTemplate =>
        val filename = Scripter.createTemplateFor(event)
        val dialog = new AlertDialog.Builder(this)
        filename.map { fn =>
          dialog.setMessage(getString(R.string.templateCreated, fn))
        }.getOrElse {
          dialog.setMessage(getString(R.string.templateCreationError))
        }
        dialog.setPositiveButton(getString(R.string.ok), null)
        dialog.show()
    }
    true
  }

}

/**
 * Activity that handles the installation of scripts.
*/

class ScriptInstaller extends TypedActivity with AdapterView.OnItemClickListener {

  private var scripts:List[Script] = Nil

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.script_installer)

    val scriptsList = findView(TR.scripts)
    scripts = BazaarProvider.newOrUpdatedScripts
    scriptsList.setAdapter(
      new ArrayAdapter[Script](
        this,
        android.R.layout.simple_list_item_multiple_choice,
        scripts.toArray
      )
    )
    scriptsList.setOnItemClickListener(this)
    // Pre-ICS compatibility: CHOICE_MODE_MULTIPLE moved from ListView to AbsListView
    import android.widget.AbsListView._
    import android.widget.ListView._
    scriptsList.setChoiceMode(CHOICE_MODE_MULTIPLE)

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

    findView(TR.selectAll).setOnClickListener(
      new View.OnClickListener {
        def onClick(v:View) {
          selectAll()
        }
      }
    )

    findView(TR.deselectAll).setOnClickListener(
      new View.OnClickListener {
        def onClick(v:View) = scriptsList.clearChoices()
      }
    )

    findView(TR.install).setOnClickListener(
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

    findView(TR.cancel).setOnClickListener(
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
