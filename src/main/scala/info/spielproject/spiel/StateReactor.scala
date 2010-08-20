package info.spielproject.spiel

import android.content.Intent
import android.util.Log

object StateReactor {
  import StateObserver._

  private var service:SpielService = null

  def apply(svc:SpielService) {
    service = svc
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
