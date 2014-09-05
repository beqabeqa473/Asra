package info.spielproject.spiel
package ui

import java.util.HashSet

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
import macroid._
import macroid.FullDsl._
import org.scaloid.common.{Preferences => _, _}

import plugins._
import presenters._

/**
 * Activity that serves as a host for other tabs.
*/

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

class NotificationFiltersPreferenceFragment extends PreferenceFragment {

  var filters:MultiSelectListPreference = _

  override def onCreate(b:Bundle) {
    super.onCreate(b)
    filters = new MultiSelectListPreference(getActivity)
    val preferenceScreen = getPreferenceManager.createPreferenceScreen(getActivity)
    setPreferenceScreen(preferenceScreen)
    preferenceScreen.addPreference(filters)
    val pm = getActivity.getPackageManager
    val packages = utils.installedPackages.map { pkg =>
      (pkg.packageName, try {
        pm.getApplicationInfo(pkg.packageName, 0).loadLabel(pm).toString
      } catch {
        case _:Throwable => pkg.packageName
      })
    }.sortWith((v1, v2) => v1._2 < v2._2)
    getActivity.runOnUiThread {
      filters.setEntryValues(packages.map(_._1.asInstanceOf[CharSequence]).toArray)
      filters.setEntries(packages.map(_._2.asInstanceOf[CharSequence]).toArray)
      val set = new HashSet[String]()
      set.addAll(Preferences.notificationFilters)
      filters.setValues(set)
    }
  }

  override def onStart() {
    super.onStart()
    getPreferenceScreen.onItemClick(null, null, 0, 0)
  }

}

class EnabledPluginsPreferenceFragment extends PreferenceFragment {

  var plugins:MultiSelectListPreference = _

  override def onCreate(b:Bundle) {
    super.onCreate(b)
    plugins = new MultiSelectListPreference(getActivity)
    val preferenceScreen = getPreferenceManager.createPreferenceScreen(getActivity)
    setPreferenceScreen(preferenceScreen)
    preferenceScreen.addPreference(plugins)
    getActivity.runOnUiThread {
      val installedPlugins = PluginManager.plugins.map(p => (p._1, p._2.name+": "+p._2.description))
      plugins.setEntryValues(installedPlugins.map(_._1.asInstanceOf[CharSequence]).toArray)
      plugins.setEntries(installedPlugins.map(_._2.asInstanceOf[CharSequence]).toArray)
      val set = new HashSet[String]()
      set.addAll(Preferences.enabledPlugins)
      Log.d("spielcheck", "Enabled: "+set)
      plugins.setValues(set)
    }
  }

  override def onStart() {
    super.onStart()
    getPreferenceScreen.onItemClick(null, null, 0, 0)
  }

}

/**
 * Activity for setting preferences.
*/

class Settings extends PreferenceActivity {

  override def onBuildHeaders(target:java.util.List[PreferenceActivity.Header]) {
    loadHeadersFromResource(R.xml.preference_headers, target)
  }

  override def isValidFragment(fragment:String) = true

}