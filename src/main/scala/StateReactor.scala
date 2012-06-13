package info.spielproject.spiel

import actors.Actor._
import collection.JavaConversions._

import android.bluetooth.{BluetoothDevice, BluetoothHeadset}
import android.content.{BroadcastReceiver, ContentUris, Context, Intent, IntentFilter}
import android.media.AudioManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Environment

import android.telephony.PhoneNumberUtils
import android.text.format.{DateFormat, DateUtils}
import android.util.Log

import scripting._

/**
 * Singleton which registers many callbacks initiated by <code>StateObserver</code>.
*/

object StateReactor {
  import StateObserver._

  private[spiel] var ringerOn:Option[Boolean] = None

  private var service:SpielService = null

  private var audioManager:AudioManager = null

  /**
   * Initializes based on the specified <code>SpielService</code>, setting initial states.
  */

  def apply(svc:SpielService) {
    service = svc
    audioManager = service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
  }

  // Check Bazaar for new scripts on app installation.

  onApplicationAdded { intent =>
    BazaarProvider.checkRemoteScripts()
  }

  onApplicationRemoved { intent =>
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

  // Manage repeating of caller ID information, stopping when appropriate.

  var callerIDRepeaterID = ""

  onCallRinging { number =>
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

  onCallAnswered { () =>
    _inCall = true
    TTS.stop
    TTS.stopRepeatedSpeech(callerIDRepeaterID)
    callerIDRepeaterID = ""
  }

  onCallIdle { () =>
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

    connect()

    def connect() {
      cleanupState()
      if(audioManager.isBluetoothScoAvailableOffCall) {
        Log.d("spielcheck", "Connecting")
        val f = new IntentFilter
        if(VERSION.SDK_INT < 14)
          f.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED)
        else
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

  onBluetoothSCOHeadsetConnected { () => startBluetoothSCO() }

  onBluetoothSCOHeadsetDisconnected { () => stopBluetoothSCO() }

  onMediaMounted { path =>
    if(path == Uri.fromFile(Environment.getExternalStorageDirectory)) {
      TTS.engine = Preferences.speechEngine
      scripting.Scripter.initExternalScripts()
    }
  }

  onMediaUnmounted { path =>
    if(path == Uri.fromFile(Environment.getExternalStorageDirectory))
      TTS.init()
  }

  // Manage speaking of occasional voicemail notification.

  private var voicemailIndicator:Option[String] = None

  onMessageWaiting { () => startVoicemailAlerts() }

  def startVoicemailAlerts() {
    if(Preferences.voicemailAlerts)
      voicemailIndicator.getOrElse {
        voicemailIndicator = Some(TTS.speakEvery(180, service.getString(R.string.newVoicemail)))
      }
  }

  onMessageNoLongerWaiting { () => stopVoicemailAlerts() }

  def stopVoicemailAlerts() {
    voicemailIndicator.foreach { i => TTS.stopRepeatedSpeech(i) }
    voicemailIndicator = None
  }

  // Note ringer state, silencing spoken notifications if desired.

  def ringerOn_? = ringerOn.getOrElse(true)
  def ringerOff_? = !ringerOn_?

  onRingerModeChanged { (mode) =>
    val shouldSpeak = ringerOn != None
    val v = mode == "normal"
    ringerOn = Some(v)
    if(shouldSpeak)
      if(v)
        TTS.speak(service.getString(R.string.ringerOn), false)
      else
        TTS.speak(service.getString(R.string.ringerOff), false)
  }

  // Note screen state, silencing notification speech if desired and speaking "Locked."

  private var screenOn = true
  def screenOn_? = screenOn
  def screenOff_? = !screenOn_?

  onScreenOff { () =>
    if(screenOn) {
      TTS.speak(service.getString(R.string.screenOff), false)
      screenOn = false
    }
  }

  onScreenOn { () =>
    if(!screenOn) {
      screenOn = true
      var timeFlags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_CAP_NOON_MIDNIGHT
      if(DateFormat.is24HourFormat(service))
        timeFlags |= DateUtils.FORMAT_24HOUR
      val time = DateUtils.formatDateTime(service, System.currentTimeMillis, timeFlags)
      TTS.speak(time, false)
    }
  }

  onUnlocked { () => 
    TTS.speak(service.getString(R.string.unlocked), false)
    handlers.Handler.nextShouldNotInterrupt
  }

  onTTSEngineChanged { () => 
    if(TTS.defaultEngine != Preferences.speechEngine)
      TTS.init() 
  }

}
