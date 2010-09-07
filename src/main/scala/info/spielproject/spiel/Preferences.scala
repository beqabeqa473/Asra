package info.spielproject.spiel

import android.content.SharedPreferences
import android.preference.PreferenceManager

object Preferences extends SharedPreferences.OnSharedPreferenceChangeListener {

  private var prefs:SharedPreferences = null

  def apply(service:SpielService) {
    prefs = PreferenceManager.getDefaultSharedPreferences(service)
    prefs.registerOnSharedPreferenceChangeListener(this)
  }

  def rateScale = prefs.getString("rateScale", "1").toFloat

  def pitchScale = prefs.getString("pitchScale", "1").toFloat

  def repeatedSpeechWhenRingerOff = prefs.getBoolean("repeatedSpeechWhenRingerOff", false)

  def talkingCallerID = prefs.getBoolean("talkingCallerID", true)

  def voicemailAlerts = prefs.getBoolean("voicemailAlerts", true)

  def fixMultivoice = prefs.getBoolean("fixMultivoice", false)

  def onSharedPreferenceChanged(p:SharedPreferences, key:String) {
    key match {
      case "rateScale" => TTS.rate = rateScale
      case "pitchScale" => TTS.pitch = pitchScale
      case _ =>
    }
  }

}
