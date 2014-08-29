package info.spielproject.spiel
package ui

import collection.JavaConversions._
import concurrent._
import ExecutionContext.Implicits.global

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
import org.scaloid.common.{Preferences => _, _}

import presenters._
import scripting._

/**
 * Activity that serves as a host for other tabs.
*/

class Spiel extends SActivity with ActionBar.TabListener {

  onCreate {

    val bar = getActionBar
    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)

    bar.addTab(bar.newTab.setText(R.string.scripts).setTabListener(this))

    bar.addTab(bar.newTab.setText(R.string.events).setTabListener(this))

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

  override def onOptionsItemSelected(item:MenuItem) = item.getItemId match {
    case R.id.settings =>
      startActivity[Settings]
      true
    case _ => super.onOptionsItemSelected(item)
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
    val engines = TTS.engines(getActivity)
    enginesPreference.setEntries((getString(R.string.systemDefault) :: engines.map(_._1).toList).toArray[CharSequence])
    enginesPreference.setEntryValues(("" :: engines.map(_._2).toList).toArray[CharSequence])

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

class NotificationFiltersPreferenceFragment extends StockPreferenceFragment {
  override def onCreate(b:Bundle) {
    super.onCreate(b)
    val pm = getActivity.getPackageManager
    future {
      val notificationFilters = findPreference("notificationFilters").asInstanceOf[MultiSelectListPreference]
      notificationFilters.setShouldDisableView(true)
      notificationFilters.setEnabled(false)
      val packages = utils.installedPackages.map { pkg =>
        (pkg.packageName, try {
          pm.getApplicationInfo(pkg.packageName, 0).loadLabel(pm).toString
        } catch {
          case _:Throwable => pkg.packageName
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

class ScriptsPreferenceFragment extends PreferenceFragment with HasScriptPreferences {

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
      val frag = new PreferenceFragment {
        override def onCreate(b:Bundle) {
          super.onCreate(b)
          setPreferenceScreen(scriptPreferencesFor(pkg))
        }
      }
      startPreferenceFragment(frag, false)
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

class Scripts extends ListFragment with Refreshable {

  override def onViewCreated(v:View, b:Bundle) {
    super.onViewCreated(v, b)
    registerForContextMenu(getListView)
  }

  def refresh() {
    setListAdapter(
      new ArrayAdapter[Script](
        getActivity,
        android.R.layout.simple_list_item_1,
        Scripter.userScripts
      )
    )
  }

  override def onCreateContextMenu(menu:ContextMenu, v:View, info:ContextMenu.ContextMenuInfo) {
    new MenuInflater(getActivity).inflate(R.menu.scripts_context, menu)
    val script = Scripter.userScripts(info.asInstanceOf[AdapterView.AdapterContextMenuInfo].position)
    if(!script.preferences_?) {
      val item = menu.findItem(R.id.settings)
      item.setEnabled(false)
      item.setVisible(false)
    }
  }

  override def onContextItemSelected(item:MenuItem) = {
    val script = Scripter.userScripts(item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].position)
    item.getItemId match {
      case R.id.settings =>
        val intent = SIntent(getActivity, reflect.ClassTag(classOf[Settings]))
        intent.putExtra("package", script.pkg)
        startActivity(intent)
      case R.id.delete =>
        new AlertDialogBuilder("", getString(R.string.confirmDelete, script.pkg))(getActivity) {
          positiveButton(getString(android.R.string.yes), {
            script.delete()
            script.uninstall()
            refresh()
          })
          negativeButton(getString(android.R.string.no))
        }.show()
    }
    true
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
        alert(
          "",
          filename.map { fn =>
            getString(R.string.templateCreated, fn)
          }.getOrElse {
            getString(R.string.templateCreationError)
          }
        )(getActivity)
    }
    true
  }

}
