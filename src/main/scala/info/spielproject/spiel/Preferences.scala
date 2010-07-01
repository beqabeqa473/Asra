package info.spielproject.spiel

import android.content.SharedPreferences
import android.preference.PreferenceManager

object Preferences {

  private var prefs:SharedPreferences = null

  def apply(service:SpielService) {
    prefs = PreferenceManager.getDefaultSharedPreferences(service)
  }

  def repeatedSpeechWhenRingerOff = prefs.getBoolean("repeatedSpeechWhenRingerOff", false)

  def talkingCallerID = prefs.getBoolean("talkingCallerID", true)

  def voicemailAlerts = prefs.getBoolean("voicemailAlerts", true)

}
