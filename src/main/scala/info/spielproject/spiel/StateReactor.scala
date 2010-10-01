package info.spielproject.spiel

import actors.Actor._

import android.content.{ContentUris, Context, Intent}
import android.media.AudioManager
import android.util.Log

import scripting._

/**
 * Singleton which registers many callbacks initiated by <code>StateObserver</code>.
*/

object StateReactor {
  import StateObserver._

  private[spiel] var ringerOn:Boolean = false

  private var service:SpielService = null

  private var audioManager:AudioManager = null

  /**
   * Initializes based on the specified <code>SpielService</code>, setting initial states.
  */

  def apply(svc:SpielService) {
    service = svc
    audioManager = service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    ringerOn = audioManager.getRingerMode != AudioManager.RINGER_MODE_SILENT
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
    if(Preferences.talkingCallerID)
      callerIDRepeaterID = TTS.speakEvery(3, number)
  }

  private var preCallMediaVolume:Option[Int] = None

  onCallAnswered { () =>
    TTS.stop
    TTS.stopRepeatedSpeech(callerIDRepeaterID)
    callerIDRepeaterID = ""
    if(Preferences.increaseInCallVolume) {
      val pcmv = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
      preCallMediaVolume = Some(pcmv)
      audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
    }
  }

  onCallIdle { () =>
    TTS.stopRepeatedSpeech(callerIDRepeaterID)
    callerIDRepeaterID = ""
    preCallMediaVolume.foreach { pcmv =>
      audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, pcmv, 0)
      preCallMediaVolume = None
    }
  }

  // Manage speaking of occasional voicemail notification.

  private var voicemailIndicator:Option[String] = None

  onMessageWaiting { () =>
    if(Preferences.voicemailAlerts)
      voicemailIndicator = Some(TTS.speakEvery(180, "New voicemail"))
  }

  onMessageNoLongerWaiting { () =>
    voicemailIndicator.foreach { i => TTS.stopRepeatedSpeech(i) }
  }

  // Note ringer state, silencing spoken notifications if desired.

  def ringerOn_? = ringerOn
  def ringerOff_? = !ringerOn_?

  onRingerModeChanged { (mode) =>
    Log.d("spiel", "Ringer mode changed: "+mode)
    ringerOn = mode != "silent"
  }

  // Note screen state, silencing notification speech if desired and speaking "Locked."

  private var screenOn = true
  def screenOn_? = screenOn
  def screenOff_? = !screenOn_?

  onScreenOff { () =>
    if(screenOn) {
      TTS.speak("Locked.", false)
      screenOn = false
    }
  }

  onScreenOn { () =>
    if(!screenOn) {
      screenOn = true
      TTS.speak("Locked.", false) 
    }
  }

}
