package info.spielproject.spiel

import android.app.backup.BackupManager
import android.content.{Context, SharedPreferences}
import android.media.AudioManager
import android.os.Debug
import android.preference.PreferenceManager
import android.util.Log

import triggers._

/**
 * Singleton for convenient access to preference values. Also tracks 
 * preference changes and updates relevant subsystems.
*/

object Preferences extends SharedPreferences.OnSharedPreferenceChangeListener {

  private var service:SpielService = null

  lazy val prefs = PreferenceManager.getDefaultSharedPreferences(service)

  private lazy val audioManager:AudioManager = service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]

  private lazy val backupManager = new BackupManager(service)

  /**
   * Initialized based on the provided <code>SpielService</code>.
  */

  def apply(svc:SpielService) {
    service = svc
    prefs.registerOnSharedPreferenceChangeListener(this)
    backupManager.dataChanged()
  }

  /**
   * Indicates desired speech engine.
  */

  def speechEngine = prefs.getString("speechEngine", "")

  /**
   * Sets the desired speech engine.
  */

  def speechEngine_=(e:String) {
    val editor = prefs.edit()
    editor.putString("speechEngine", e)
    editor.commit()
  }

  private def resetFloatPreference(name:String) = {
    val editor = prefs.edit()
    editor.putString(name, "1")
    editor.commit()
    1f
  }

  /**
   * Indicates the desired rate scale.
  */

  def rateScale = {
    try {
      prefs.getString("rateScale", "1").toFloat
    } catch {
      case _ => resetFloatPreference("rateScale")
    }
  }

  /**
   * Indicates the desired pitch scale.
  */

  def pitchScale = {
    try {
      prefs.getString("pitchScale", "1").toFloat
    } catch {
      case _ => resetFloatPreference("pitchScale")
    }
  }

  /**
   * Indicates whether character echo is enabled.
  */

  def echoByChar = prefs.getBoolean("echoByChar", true)

  /**
   * Indicates whether word echo is enabled.
  */

  def echoByWord = prefs.getBoolean("echoByWord", false)

  /**
   * Indicates whether punctuation speech is to be managed by Spiel.
  */

  def managePunctuationSpeech = prefs.getBoolean("managePunctuationSpeech", true)

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
   * Duck non-speech audio while speaking.
  */

  def duckNonSpeechAudio = prefs.getBoolean("duckNonSpeechAudio", true)

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

  def sendBacktraces = prefs.getBoolean("sendBacktraces", true)

  /**
   * Indicates whether Bluetooth SCO is to be used when available.
  */

  def useBluetoothSCO = prefs.getBoolean("useBluetoothSCO", false)

  def profiling = prefs.getBoolean("profiling", false)

  /**
   * Returns the Bazaar username.
  */

  def bazaarUsername = prefs.getString("bazaarUsername", "")

  /**
   * Sets the Bazaar username.
  */

  def bazaarUsername_=(v:String) {
    val editor = prefs.edit()
    editor.putString("bazaarUsername", v)
    editor.commit()
  }

  /**
   * Returns the Bazaar username.
  */

  def bazaarPassword = prefs.getString("bazaarPassword", "")

  /**
   * Sets the Bazaar password.
  */

  def bazaarPassword_=(v:String) {
    val editor = prefs.edit()
    editor.putString("bazaarPassword", v)
    editor.commit()
  }

  def notificationFilters = prefs.getString("notificationFilters", "").split("\\|")

  def hapticFeedback_? = prefs.getBoolean("hapticFeedback", true)

  def onSharedPreferenceChanged(p:SharedPreferences, key:String) {
    key match {
      case "pitchScale" => TTS.pitch = pitchScale
      case "rateScale" => TTS.rate = rateScale
      case "speechEngine" => TTS.init()
      case "onProximityNear" => ProximityNear(onProximityNear)
      case "onShakingStarted" => ShakingStarted(onShakingStarted)
      case "profiling" if(profiling) => Debug.startMethodTracing("spiel")
      case "profiling" if(!profiling) => Debug.stopMethodTracing()
      case "voicemailAlerts" =>
        if(voicemailAlerts) Telephony.startVoicemailAlerts()
        else Telephony.stopVoicemailAlerts()
      case _ =>
    }
    backupManager.dataChanged()
  }

}
