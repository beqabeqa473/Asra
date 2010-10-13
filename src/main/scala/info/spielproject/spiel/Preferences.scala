package info.spielproject.spiel

import android.content.{Context, SharedPreferences}
import android.media.AudioManager
import android.preference.PreferenceManager
import android.util.Log

import triggers._

/**
 * Singleton for convenient access to preference values. Also tracks 
 * preference changes and updates relevant subsystems.
*/

object Preferences extends SharedPreferences.OnSharedPreferenceChangeListener {

  private var prefs:SharedPreferences = null

  private var audioManager:AudioManager = null

  /**
   * Initialized based on the provided <code>SpielService</code>.
  */

  def apply(service:SpielService) {
    prefs = PreferenceManager.getDefaultSharedPreferences(service)
    prefs.registerOnSharedPreferenceChangeListener(this)
    audioManager = service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
  }

  /**
   * Indicates desired speech engine.
  */

  def speechEngine = prefs.getString("speechEngine", TTS.defaultEngine)

  /**
   * Sets the desired speech engine.
  */

  def speechEngine_=(e:String) {
    val editor = prefs.edit()
    editor.putString("speechEngine", TTS.defaultEngine)
    editor.commit()
  }

  /**
   * Indicates the desired rate scale.
  */

  def rateScale = prefs.getString("rateScale", "1").toFloat

  /**
   * Indicates the desired pitch scale.
  */

  def pitchScale = prefs.getString("pitchScale", "1").toFloat

  /**
   * Indicates whether character echo is enabled.
  */

  def echoByChar = prefs.getBoolean("echoByChar", true)

  /**
   * Indicates whether word echo is enabled.
  */

  def echoByWord = prefs.getBoolean("echoByWord", false)

  /**
   * Indicates whether the multi-voice fix is to be used.
  */

  def fixMultivoice = prefs.getBoolean("fixMultivoice", false)

  /**
   * Indicates whether punctuation speech is to be managed by Spiel.
  */

  def managePunctuationSpeech = prefs.getBoolean("managePunctuationSpeech", false)

  /**
   * Indicates whether in-call volume of speech is to be increased.
  */

  def increaseInCallVolume = prefs.getBoolean("increaseInCallVolume", false)

  /**
   * Indicates whether the talking caller ID is enabled.
  */

  def talkingCallerID = prefs.getBoolean("talkingCallerID", true)

  /**
   * Indicates whether talking voicemail alerts are enabled.
  */

  def voicemailAlerts = prefs.getBoolean("voicemailAlerts", true)

  /**
   * Indicates whether notifications are to be spoken when the screen is off.
  */

  def speakNotificationsWhenScreenOff = prefs.getBoolean("speakNotificationsWhenScreenOff", true)

  /**
   * Indicates whether repeated speech alerts occur when the ringer is off.
  */

  def repeatedSpeechWhenRingerOff = prefs.getBoolean("repeatedSpeechWhenRingerOff", false)

  private def triggerPreference(trigger:String, default:String = "") = prefs.getString(trigger, default) match {
    case "" => None
    case str => Some(Triggers.actions(str))
  }

  /**
   * Indicates <code>Action</code> to run on proximity nearness.
  */

  def onProximityNear = triggerPreference("onProximityNear", "stopSpeech")

  /**
   * Indicates <code>Action</code> to run when shaking starts.
  */

  def onShakingStarted = triggerPreference("onShakingStarted")

  /**
   * Indicates whether backtraces are to be sent.
  */

  def sendBacktraces = prefs.getBoolean("sendBacktraces", false)

  /**
   * Indicates whether Bluetooth SCO is to be used when available.
  */

  def useBluetoothSCO = prefs.getBoolean("useBluetoothSCO", false)

  /**
   * Indicates whether recent <code>AccessibilityEvent</code>s are to be viewed/logged.
  */

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
