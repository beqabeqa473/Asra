package info.spielproject.spiel

import android.content.SharedPreferences
import android.preference.PreferenceManager

object Preferences {

  private var prefs:SharedPreferences = null

  def apply(service:SpielService) {
    prefs = PreferenceManager.getDefaultSharedPreferences(service)
  }

  def talkingCallerID_? = prefs.getBoolean("talkingCallerID", true)

  def voicemailAlerts_? = prefs.getBoolean("voicemailAlerts", true)

}
