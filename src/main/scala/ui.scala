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
import macroid._
import macroid.FullDsl._
import org.droidparts.preference.MultiSelectListPreference
import org.scaloid.common.{Preferences => _, _}

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

class NotificationFiltersPreferenceFragment extends StockPreferenceFragment {
  override def onCreate(b:Bundle) {
    super.onCreate(b)
    val pm = getActivity.getPackageManager
    Future {
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

/**
 * Activity for setting preferences.
*/

class Settings extends PreferenceActivity {

  protected val context = this

  override def onBuildHeaders(target:java.util.List[PreferenceActivity.Header]) {
    val intent = getIntent
    setIntent(intent)
    loadHeadersFromResource(R.xml.preference_headers, target)
  }

}