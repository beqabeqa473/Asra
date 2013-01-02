package info.spielproject.spiel
package ui

import actors.Actor.actor
import collection.JavaConversions._

import android.app._
import android.bluetooth._
import android.content._
import android.content.pm._
import android.database._
import android.net._
import android.os._
import android.preference._
import android.util._
import android.view._
import android.view.accessibility._
import android.widget._
import org.droidparts.preference.MultiSelectListPreference

import presenters._
import scripting._
import triggers.Triggers

/**
 * Activity that serves as a host for other tabs.
*/

class Spiel extends Activity with ActionBar.TabListener {

  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)

    val bar = getActionBar
    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)

    bar.addTab(bar.newTab.setText(R.string.scripts).setTabListener(this))

    bar.addTab(bar.newTab.setText(R.string.events).setTabListener(this))

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
          getActionBar.setSelectedNavigationItem(0)
      }
    }
  }

  def onTabReselected(tab:ActionBar.Tab, ft:FragmentTransaction) { }

  private var fragment:Option[Fragment] = None

  def onTabSelected(tab:ActionBar.Tab, ft:FragmentTransaction) {
    fragment.foreach(ft.remove(_))
    val frag = tab.getPosition match {
      case 0 => new Scripts
      case 1 => new Events
    }
    fragment = Some(frag)
    ft.add(android.R.id.content, frag)
  }

  def onTabUnselected(tab:ActionBar.Tab, ft:FragmentTransaction) {
    fragment.foreach { frag =>
      ft.remove(frag)
      fragment = None
    }
  }

  override def onCreateOptionsMenu(menu:Menu) = {
    getMenuInflater.inflate(R.menu.spiel, menu)
    super.onCreateOptionsMenu(menu)
  }

  override def onOptionsItemSelected(item:MenuItem) = {
    item.getItemId match {
      case R.id.settings =>
        startActivity(new Intent(this, classOf[Settings]))
    }
    true
  }

}

trait HasScriptPreferences {

  protected def context:Context

  def getPreferenceManager:PreferenceManager

  protected def scriptPreferencesFor(pkg:String) = {
    val screen = getPreferenceManager.createPreferenceScreen(context)
    screen.setTitle(Script.labelFor(pkg))
    screen.setEnabled(true)
    screen.setSelectable(true)
    Scripter.preferences(pkg).foreach { pkgpref =>
      val pref = pkgpref._2
      val preference = new CheckBoxPreference(context)
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

class StockPreferenceFragment extends PreferenceFragment {
  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    val res = getActivity.getResources.getIdentifier(getArguments.getString("resource"), "xml", getActivity.getPackageName)
    addPreferencesFromResource(res)
  }
}

class SpeechPreferenceFragment extends StockPreferenceFragment {
  override def onCreate(b:Bundle) {
    super.onCreate(b)

    val enginesPreference = findPreference("speechEngine").asInstanceOf[ListPreference]
    enginesPreference.setEntries((getString(R.string.systemDefault) :: TTS.engines.map(_._1).toList).toArray[CharSequence])
    enginesPreference.setEntryValues(("" :: TTS.engines.map(_._2).toList).toArray[CharSequence])

    Option(BluetoothAdapter.getDefaultAdapter).getOrElse {
      getPreferenceScreen.removePreference(findPreference("useBluetoothSCO"))
    }

    // Now set the shortcut to system-wide TTS settings.
    val ttsPreference = findPreference("textToSpeechSettings")
    ttsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener {
      def onPreferenceClick(p:Preference) = {
        startActivity(new Intent("com.android.settings.TTS_SETTINGS"))
        false
      }
    })

  }
}

class AlertsPreferenceFragment extends StockPreferenceFragment {
  override def onCreate(b:Bundle) {
    super.onCreate(b)
    val vibrator = getActivity.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[android.os.Vibrator]
    if(!vibrator.hasVibrator)
      getPreferenceScreen.removePreference(findPreference("hapticFeedback"))
  }
}

class TriggersPreferenceFragment extends StockPreferenceFragment {
  override def onCreate(b:Bundle) {
    super.onCreate(b)
    val pm = getActivity.getPackageManager

    if(pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) || pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)) {

      // Set up triggers. First add an action for "None," then concat others.
      val actions = (getString(R.string.none), "") :: Triggers.actions.map((v) => (v._2.name, v._1)).toList

      if(pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
        val onShake = findPreference("onShakingStarted").asInstanceOf[ListPreference]
        onShake.setEntries(actions.map(_._1).toArray[CharSequence])
        onShake.setEntryValues(actions.map(_._2).toArray[CharSequence])
      } else
        getPreferenceScreen.asInstanceOf[PreferenceGroup].removePreference(findPreference("onShakingStarted"))

      if(pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)) {
        val onProximityNear = findPreference("onProximityNear").asInstanceOf[ListPreference]
        onProximityNear.setEntries(actions.map(_._1).toArray[CharSequence])
        onProximityNear.setEntryValues(actions.map(_._2).toArray[CharSequence])
      } else
        getPreferenceScreen.asInstanceOf[PreferenceGroup].removePreference(findPreference("onProximityNear"))
    }
  }
}

class NotificationFiltersPreferenceFragment extends StockPreferenceFragment {
  override def onCreate(b:Bundle) {
    super.onCreate(b)
    val pm = getActivity.getPackageManager
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
      getActivity.runOnUiThread(new Runnable { def run() {
        notificationFilters.setEntryValues(packages.map(_._1.asInstanceOf[CharSequence]).toArray)
        notificationFilters.setEntries(packages.map(_._2.asInstanceOf[CharSequence]).toArray)
        notificationFilters.setEnabled(true)
      }})
    }
  }
}

class ScriptsPreferenceFragment extends StockPreferenceFragment with HasScriptPreferences {

  lazy val context = getActivity

  override def onCreate(b:Bundle) {
    super.onCreate(b)
    val scripts = getPreferenceScreen
    if(Scripter.preferences == Map.empty) {
      //getPreferenceScreen.removePreference(scripts)
    } else {
      scripts.removeAll()
      Scripter.preferences.foreach { pkg =>
        scripts.addPreference(scriptPreferencesFor(pkg._1))
      }
    }
  }
}

/**
 * Activity for setting preferences.
*/

class Settings extends PreferenceActivity with HasScriptPreferences {

  protected val context = this

  override def onBuildHeaders(target:java.util.List[PreferenceActivity.Header]) {
    val intent = getIntent
    setIntent(intent)
    Option(intent.getStringExtra("package")).foreach { pkg =>
      setPreferenceScreen(scriptPreferencesFor(pkg))
      return super.onBuildHeaders(target)
    }
    loadHeadersFromResource(R.xml.preference_headers, target)
  }

}

/**
 * Trait implementing a "Refresh" menu item and action.
*/

trait Refreshable extends Fragment {
  this: Fragment =>

  override def onActivityCreated(b:Bundle) {
    super.onActivityCreated(b)
    setHasOptionsMenu(true)
  }

  override def onCreateOptionsMenu(menu:Menu, inflater:MenuInflater) {
    inflater.inflate(R.menu.refreshable, menu)
    super.onCreateOptionsMenu(menu, inflater)
  }

  override def onOptionsItemSelected(item:MenuItem) = {
    item.getItemId match {
      case R.id.refresh => refresh
    }
    true
  }

  def refresh()

}

class Scripts extends Fragment with Refreshable with RadioGroup.OnCheckedChangeListener with LoaderManager.LoaderCallbacks[Cursor] {

  private lazy val listView = getView.findViewById(R.id.scripts).asInstanceOf[ListView]

  private var system = true

  override def onCreateView(inflater:LayoutInflater, container:ViewGroup, bundle:Bundle) = {
    inflater.inflate(R.layout.scripts, container, false)
  }

  override def onViewCreated(v:View, b:Bundle) {
    super.onViewCreated(v, b)
    registerForContextMenu(listView)
    val mode = getView.findViewById(R.id.mode).asInstanceOf[RadioGroup]
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
    getActivity,
    R.layout.script_row,
    null,
    List(Provider.columns.label).toArray,
    List(R.id.script_title).toArray,
    0
  )

  def onCreateLoader(id:Int, bundle:Bundle) = {
    new CursorLoader(getActivity, Provider.uri, Provider.columns.projection, null, null, null)
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
        getActivity,
        android.R.layout.simple_list_item_1,
        Scripter.userScripts
      )
    )
  }

  override def onCreateContextMenu(menu:ContextMenu, v:View, info:ContextMenu.ContextMenuInfo) {
    val menuID = if(system) R.menu.system_scripts_context else R.menu.user_scripts_context
    new MenuInflater(getActivity).inflate(menuID, menu)
    if(system) {
      val selection = info.asInstanceOf[AdapterView.AdapterContextMenuInfo].id
      val uri = ContentUris.withAppendedId(Provider.uri, selection)
      val c = getActivity.getContentResolver.query(uri, null, null, null, null)
      c.moveToFirst()
      val script:Option[Script] = if(c.isAfterLast)
        None
      else
        Some(new Script(getActivity, c))
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
      val c = getActivity.getContentResolver.query(uri, null, null, null, null)
      c.moveToFirst()
      val script:Option[Script] = if(c.isAfterLast)
        None
      else
        Some(new Script(getActivity, c))
      c.close()
      item.getItemId match {
        case R.id.preferences =>
          script.foreach { s =>
            val intent = new Intent(getActivity, classOf[Settings])
            intent.putExtra("package", s.pkg)
            startActivity(intent)
          }
        case R.id.copyToExternalStorage =>
          script.foreach { s =>
            val filename = s.writeToExternalStorage()
            new AlertDialog.Builder(getActivity)
            .setMessage(getString(R.string.scriptCopied, filename))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
            
          }
        case R.id.delete =>
          script.foreach { s =>
            new AlertDialog.Builder(getActivity)
            .setMessage(getString(R.string.confirmDelete, s.pkg))
            .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener {
              def onClick(i:DialogInterface, what:Int) {
                getActivity.getContentResolver.delete(uri, null, null)
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
          val intent = new Intent(getActivity, classOf[Settings])
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
            new AlertDialog.Builder(getActivity)
            .setMessage(getString(R.string.script_reload_error))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
          }
        case R.id.delete =>
          new AlertDialog.Builder(getActivity)
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
      val dialog = new Dialog(getActivity)
      dialog.setContentView(R.layout.bazaar_credentials)
      val message = dialog.findViewById(R.id.message).asInstanceOf[TextView]
      if(Preferences.bazaarUsername != "" || Preferences.bazaarPassword != "")
        message.setText(getString(R.string.bazaar_credentials_invalid))
      else
        message.setText(getString(R.string.bazaar_credentials))
      val username = dialog.findViewById(R.id.username).asInstanceOf[EditText]
      username.setText(Preferences.bazaarUsername)
      val password = dialog.findViewById(R.id.password).asInstanceOf[EditText]
      password.setText(Preferences.bazaarPassword)
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
            postToBazaar()
          } else
            dialog.show()
        }
      })
      dialog.findViewById(R.id.signup).asInstanceOf[Button].setOnClickListener(new View.OnClickListener {
        def onClick(v:View) {
          val url = "http://bazaar.spielproject.info/signup?returnTo=spiel:scripts"
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
          intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
          startActivity(intent)
          dialog.dismiss()
        }
      })
      dialog.findViewById(R.id.cancel).asInstanceOf[Button].setOnClickListener(new View.OnClickListener {
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
      val dialog = new Dialog(getActivity)
      dialog.setContentView(R.layout.post_script)
      val changesField = dialog.findViewById(R.id.changes).asInstanceOf[EditText]
      changesField.setText(scriptChanges)
      def clearValues() {
        changesField.setText("")
      }
      dialog.findViewById(R.id.ok).asInstanceOf[Button].setOnClickListener(new View.OnClickListener {
        def onClick(v:View) {
          scriptChanges = changesField.getText.toString
          dialog.dismiss()
          postToBazaar()
        }
      })
      dialog.findViewById(R.id.cancel).asInstanceOf[Button].setOnClickListener(new View.OnClickListener {
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
    val dialog = new AlertDialog.Builder(getActivity)
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

class Events extends ListFragment with Refreshable {

  override def onViewCreated(v:View, b:Bundle) {
    super.onViewCreated(v, b)
    registerForContextMenu(getListView  )
    refresh()
  }

  def refresh() {
    setListAdapter(
      new ArrayAdapter[AccessibilityEvent](
        getActivity,
        android.R.layout.simple_list_item_1,
        EventReviewQueue.toArray
      )
    )
  }

  override def onCreateContextMenu(menu:ContextMenu, v:View, info:ContextMenu.ContextMenuInfo) {
    new MenuInflater(getActivity).inflate(R.menu.events_context, menu)
  }

  override def onContextItemSelected(item:MenuItem):Boolean = {
    val position = item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].position
    if(!EventReviewQueue.isDefinedAt(position)) return true
    val event = EventReviewQueue(position)
    item.getItemId match {
      case R.id.createTemplate =>
        val filename = Scripter.createTemplateFor(event)
        val dialog = new AlertDialog.Builder(getActivity)
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
