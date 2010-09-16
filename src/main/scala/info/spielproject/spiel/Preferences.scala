package info.spielproject.spiel

import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log

import triggers._

object Preferences extends SharedPreferences.OnSharedPreferenceChangeListener {

  private var prefs:SharedPreferences = null

  def apply(service:SpielService) {
    prefs = PreferenceManager.getDefaultSharedPreferences(service)
    prefs.registerOnSharedPreferenceChangeListener(this)
  }

  def speechEngine = prefs.getString("speechEngine", TTS.defaultEngine)

  def speechEngine_=(e:String) {
    val editor = prefs.edit()
    editor.putString("speechEngine", TTS.defaultEngine)
    editor.commit()
  }

  def rateScale = prefs.getString("rateScale", "1").toFloat

  def pitchScale = prefs.getString("pitchScale", "1").toFloat

  def echoByChar = prefs.getBoolean("echoByChar", true)

  def echoByWord = prefs.getBoolean("echoByWord", false)

  def fixMultivoice = prefs.getBoolean("fixMultivoice", false)

  def managePunctuationSpeech = prefs.getBoolean("managePunctuationSpeech", false)

  def talkingCallerID = prefs.getBoolean("talkingCallerID", true)

  def voicemailAlerts = prefs.getBoolean("voicemailAlerts", true)

  def speakNotificationsWhenScreenOff = prefs.getBoolean("speakNotificationsWhenScreenOff", true)

  def repeatedSpeechWhenRingerOff = prefs.getBoolean("repeatedSpeechWhenRingerOff", false)

  private def triggerPreference(trigger:String) = prefs.getString(trigger, "") match {
    case "" => None
    case str => Some(Triggers.actions(str))
  }

  def onProximityNear = triggerPreference("onProximityNear")

  def onShakingStarted = triggerPreference("onShakingStarted")

  def sendBacktraces = prefs.getBoolean("sendBacktraces", false)

  def viewRecentEvents = prefs.getBoolean("viewRecentEvents", false)

  def onSharedPreferenceChanged(p:SharedPreferences, key:String) {
    key match {
      case "pitchScale" => TTS.pitch = pitchScale
      case "rateScale" => TTS.rate = rateScale
      case "echoByWord" if(!echoByWord) => TTS.clearCharBuffer()
      case "speechEngine" =>
        TTS.engine = speechEngine
      case "onProximityNear" => ProximityNear(onProximityNear)
      case "onShakingStarted" => ShakingStarted(onShakingStarted)
      case "viewRecentEvents" if(!viewRecentEvents) => handlers.EventReviewQueue.clear()
      case _ =>
    }
  }

}
