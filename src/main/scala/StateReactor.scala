package info.spielproject.spiel

import java.text.SimpleDateFormat
import java.util.Date

import actors.Actor._
import collection.JavaConversions._

import android.bluetooth.{BluetoothDevice, BluetoothHeadset}
import android.content.{BroadcastReceiver, ContentUris, Context, Intent, IntentFilter}
import android.media.AudioManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Environment
import android.telephony.PhoneNumberUtils
import android.text.format.DateFormat
import android.util.Log

import events._
import scripting._

/**
 * Singleton which registers many callbacks initiated by <code>StateObserver</code>.
*/

object StateReactor {
  import StateObserver._

  private[spiel] var ringerOn:Option[Boolean] = None

  private var service:SpielService = null

  private lazy val audioManager:AudioManager = service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]

  /**
   * Initializes based on the specified <code>SpielService</code>, setting initial states.
  */

  def apply(svc:SpielService) {
    service = svc
  }

  // Check Bazaar for new scripts on app installation.

  ApplicationAdded += { intent:Intent =>
    BazaarProvider.checkRemoteScripts()
  }

  ApplicationRemoved += { intent:Intent =>
    val packageName = intent.getData().getSchemeSpecificPart
    val cursor = service.getContentResolver.query(Provider.uri, null, "pkg = ?", List(packageName).toArray, null)
    if(cursor.getCount > 0) {
      cursor.moveToFirst()
      while(!cursor.isAfterLast) {
        val script = new Script(service, cursor)
        script.uninstall()
        val scriptURI = ContentUris.withAppendedId(Provider.uri, script.id.get)
        service.getContentResolver.delete(scriptURI, null, null)
        cursor.moveToNext()
      }
    }
    cursor.close()
  }

  private def speakBatteryPercentage(ps:Option[String] = None) {
    utils.batteryPercentage(p => TTS.speak(p+"%" :: ps.map(_ :: Nil).getOrElse(Nil), false))
  }

  PowerConnected += speakBatteryPercentage(Some(service.getString(R.string.charging)))

  PowerDisconnected += speakBatteryPercentage()

  // Manage repeating of caller ID information, stopping when appropriate.

  var callerIDRepeaterID = ""

  CallRinging += { number:String =>
    if(usingSco)
      btReceiver.foreach(_.connect())
    if(Preferences.talkingCallerID)
      callerIDRepeaterID = TTS.speakEvery(3, PhoneNumberUtils.formatNumber(number))
  }

  private var _inCall = false

  /**
   * Returns <code>true</code> if in call, <code>false</code> otherwise.
  */

  def inCall_? = _inCall

  CallAnswered += {
    _inCall = true
    TTS.stop
    TTS.stopRepeatedSpeech(callerIDRepeaterID)
    callerIDRepeaterID = ""
  }

  CallIdle += {
    _inCall = false
    TTS.stopRepeatedSpeech(callerIDRepeaterID)
    callerIDRepeaterID = ""
    if(usingSco) {
      actor {
        // Wait until dialer sets audio mode so we can alter it for SCO reconnection.
        Thread.sleep(1000)
        btReceiver.foreach(_.connect())
      }
    }
  }

  private var usingSco = false

  private class BTReceiver extends BroadcastReceiver {

    private var wasConnected = false

    private var musicVolume:Option[Int] = None
    private var voiceVolume:Option[Int] = None

    connect()

    def connect() {
      cleanupState()
      if(audioManager.isBluetoothScoAvailableOffCall) {
        Log.d("spielcheck", "Connecting")
        val f = new IntentFilter
        f.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        service.registerReceiver(this, f)
        audioManager.startBluetoothSco()
      }
    }

    private def cleanupState() {
      Log.d("spielcheck", "Cleaning up state.")
      usingSco = false
      wasConnected = false
      if(!inCall_?) audioManager.setMode(AudioManager.MODE_NORMAL)
      musicVolume.foreach(
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, _, 0)
      )
      musicVolume = None
      voiceVolume.foreach(
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, _, 0)
      )
      voiceVolume = None
    }

    private def cleanup() {
      Log.d("spielcheck", "Cleaning up.")
      audioManager.stopBluetoothSco()
      cleanupState()
      try {
        service.unregisterReceiver(this)
      } catch {
        case _ =>
      }
    }

    override def onReceive(c:Context, i:Intent) {
      val state = i.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
      Log.d("spielcheck", "Got "+i+", "+state)
      if(state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
        Log.d("spielcheck", "here1")
        usingSco = true
        musicVolume = Option(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        voiceVolume = Option(audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL))
        audioManager.setMode(AudioManager.MODE_IN_CALL)
        wasConnected = true
      } else if(state == AudioManager.SCO_AUDIO_STATE_ERROR) {
        Log.d("spielcheck", "here2")
        cleanupState()
      } else if(usingSco && wasConnected && state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
        Log.d("spielcheck", "here3")
        cleanupState()
        audioManager.startBluetoothSco()
      } else if(wasConnected) {
        Log.d("spielcheck", "here4")
        cleanup()
      }
    }

    def disconnect() {
      Log.d("spielcheck", "Disconnecting")
      audioManager.stopBluetoothSco()
      cleanup()
    }

  }

  private var btReceiver:Option[BTReceiver] = None

  private def startBluetoothSCO() {
    Log.d("spielcheck", "startBluetoothSCO()")
    if(Preferences.useBluetoothSCO) {
      val r = new BTReceiver()
      r.connect()
      btReceiver = Some(r)
    }
  }

  private def stopBluetoothSCO() {
    btReceiver.foreach { r =>
      r.disconnect()
      btReceiver = None
    }
  }

  BluetoothSCOHeadsetConnected +=startBluetoothSCO()

  BluetoothSCOHeadsetDisconnected += stopBluetoothSCO()

  // Manage speaking of occasional voicemail notification.

  private var voicemailIndicator:Option[String] = None

  MessageWaiting += startVoicemailAlerts()

  def startVoicemailAlerts() {
    if(Preferences.voicemailAlerts)
      voicemailIndicator.getOrElse {
        voicemailIndicator = Some(TTS.speakEvery(180, service.getString(R.string.newVoicemail)))
      }
  }

  MessageNoLongerWaiting += stopVoicemailAlerts()

  def stopVoicemailAlerts() {
    voicemailIndicator.foreach { i => TTS.stopRepeatedSpeech(i) }
    voicemailIndicator = None
  }

  RingerModeChangedIntent += { i:Intent =>
    val extra = i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
    val mode = extra match {
      case AudioManager.RINGER_MODE_SILENT => RingerMode.Silent
      case AudioManager.RINGER_MODE_VIBRATE => RingerMode.Vibrate
      case _ => RingerMode.Normal
    }
    RingerModeChanged(mode)
  }

  // Note ringer state, silencing spoken notifications if desired.

  def ringerOn_? = ringerOn.getOrElse(true)
  def ringerOff_? = !ringerOn_?

  RingerModeChanged += { mode:RingerMode.Value =>
    ringerOn = Some(mode == RingerMode.Normal)
    mode match {
      case RingerMode.Normal => TTS.speak(service.getString(R.string.ringerOn), false)
      case RingerMode.Silent => TTS.speak(service.getString(R.string.ringerOff), false)
      case RingerMode.Vibrate => TTS.speak(service.getString(R.string.ringerVibrate), false)
    }
  }

  OrientationLandscape += {
    TTS.speak(service.getString(R.string.landscape), false)
  }

  OrientationPortrait += {
    TTS.speak(service.getString(R.string.portrait), false)
  }

  // Note screen state, silencing notification speech if desired and speaking "Locked."

  private var screenOn = true
  def screenOn_? = screenOn
  def screenOff_? = !screenOn_?

  private var locked = screenOff_?

  ScreenOff += {
    if(screenOn) {
      TTS.speak(service.getString(R.string.screenOff), false)
      screenOn = false
      locked = true
    }
  }

  ScreenOn += {
    if(!screenOn) {
      screenOn = true
      val sdf = new SimpleDateFormat(
        if(DateFormat.is24HourFormat(service))
          "H:mm"
        else
          "h:mm a"
      )
      TTS.speak(sdf.format(new Date(System.currentTimeMillis)), false)
    }
  }

  Unlocked += {
    if(locked) {
      TTS.speak(service.getString(R.string.unlocked), false)
      presenters.Presenter.nextShouldNotInterrupt
      locked = false
    }
  }

  TTSEngineChanged += {
    if(TTS.defaultEngine != Preferences.speechEngine)
      TTS.init() 
  }

}
