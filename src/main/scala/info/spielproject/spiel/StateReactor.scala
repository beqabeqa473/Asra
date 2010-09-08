package info.spielproject.spiel

import android.content.Context
import android.media.AudioManager
import android.util.Log

object StateReactor {
  import StateObserver._

  private[spiel] var ringerOn:Boolean = false

  def apply(service:SpielService) {
    val audioManager = service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    ringerOn = audioManager.getRingerMode != AudioManager.RINGER_MODE_SILENT
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

  def ringerOn_? = ringerOn
  def ringerOff_? = !ringerOn_?

  onRingerModeChanged { (mode) =>
    Log.d("spiel", "Ringer mode changed: "+mode)
    ringerOn = mode != "silent"
  }

  private var screenOn = true
  def screenOn_? = screenOn
  def screenOff_? = !screenOn_?

  onScreenOff { () =>
    screenOn = false
    TTS.speak("Locked.", false)
  }

  onScreenOn { () =>
    screenOn = true
    TTS.speak("Locked.", false) 
  }

  onShakingStarted { () =>
    //TTS.speak("Shaking", true)
  }

  onShakingStopped { () =>
    //TTS.speak("Stopped shaking", true)
  }

  private var voicemailIndicator:Option[String] = None

  onMessageWaiting { () =>
    if(Preferences.voicemailAlerts)
      TTS.speakEvery(180, "New voicemail")
  }

  onMessageNoLongerWaiting { () =>
    voicemailIndicator.foreach { i => TTS.stopRepeatedSpeech(i) }
  }

}
