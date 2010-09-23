package info.spielproject.spiel

import android.content.{Context, Intent}
import android.media.AudioManager
import android.util.Log

/**
 * Singleton which registers many callbacks initiated by <code>StateObserver</code>.
*/

object StateReactor {
  import StateObserver._

  private[spiel] var ringerOn:Boolean = false

  /**
   * Initializes based on the specified <code>SpielService</code>, setting initial states.
  */

  def apply(service:SpielService) {
    val audioManager = service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    ringerOn = audioManager.getRingerMode != AudioManager.RINGER_MODE_SILENT
  }

  onApplicationAdded { intent =>
    val packageName = intent.getData().getSchemeSpecificPart
    Log.d("spiel", "Package added: "+packageName)
    val i = new Intent(Intent.ACTION_VIEW)
    i.addCategory(scripting.BazaarProvider.newScriptsView)
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    //i.putExtra("scripts", Nil)
    //service.startActivity(i)
  }

  onApplicationRemoved { intent =>
    Log.d("spiel", "Application removed: "+intent)
  }

  // Manage repeating of caller ID information, stopping when appropriate.

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
    screenOn = false
    TTS.speak("Locked.", false)
  }

  onScreenOn { () =>
    screenOn = true
    TTS.speak("Locked.", false) 
  }

}
