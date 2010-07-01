package info.spielproject.spiel

import android.os.Bundle
import android.preference.PreferenceActivity

class PreferencesActivity extends PreferenceActivity {
  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
    addPreferencesFromResource(R.xml.preferences)
  }
}
