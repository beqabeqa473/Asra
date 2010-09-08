package info.spielproject.spiel

import android.content.Context
import android.media.AudioManager
import android.util.Log

object StateReactor {
  import StateObserver._

  private[spiel] var _ringerOn:Boolean = false

  def apply(service:SpielService) {
    val audioManager = service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    _ringerOn = audioManager.getRingerMode != AudioManager.RINGER_MODE_SILENT
  }

  var callerIDRepeaterID = ""

  onCallRinging { number =>
    if(Preferences.talkingCallerID)
      callerIDRepeaterID = TTS.speakEvery(3, number)
  }

  onCallAnswered { () =>
    TTS.stop
    TTS.stopRepeatedSpeech(callerIDRepeaterID)
    callerIDRepeaterID = ""
  }

  onCallIdle { () =>
    TTS.stopRepeatedSpeech(callerIDRepeaterID)
    callerIDRepeaterID = ""
  }

  def isRingerOn = _ringerOn
  def isRingerOff = !isRingerOn

  onRingerModeChanged { (mode) =>
    Log.d("spiel", "Ringer mode changed: "+mode)
    _ringerOn = mode != "silent"
  }

  onScreenOff { () => TTS.speak("Locked.", false) }

  onScreenOn { () => TTS.speak("Locked.", false) }

  private var voicemailIndicator:Option[String] = None

  onMessageWaiting { () =>
    if(Preferences.voicemailAlerts)
      TTS.speakEvery(180, "New voicemail")
  }

  onMessageNoLongerWaiting { () =>
    voicemailIndicator.foreach { i => TTS.stopRepeatedSpeech(i) }
  }

}
