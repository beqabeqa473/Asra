package info.spielproject.spiel

import collection.JavaConversions._

import android.app.TabActivity
import android.content.Intent
import android.os.Bundle
import android.preference.{ListPreference, Preference, PreferenceActivity}
import android.util.Log
import android.widget.TabHost

class SpielActivity extends TabActivity {
  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    val host = getTabHost

    host.addTab(host.newTabSpec("preferences")
      .setIndicator(getString(R.string.preferences))
      .setContent(new Intent(this, classOf[PreferencesActivity]))
    )

  }
}

class PreferencesActivity extends PreferenceActivity {
  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    addPreferencesFromResource(R.xml.preferences)
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
    val ttsPreference = findPreference("textToSpeechSettings")
    ttsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener {
      def onPreferenceClick(p:Preference) = {
        val intent = new Intent
        intent.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings")
        startActivity(intent)
        false
      }
    })
  }
}
