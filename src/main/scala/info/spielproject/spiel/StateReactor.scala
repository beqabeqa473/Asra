package info.spielproject.spiel

object StateReactor {
  import StateObserver._


  var callerIDRepeaterID = ""

  onCallRinging { number =>
    if(Preferences.talkingCallerID_?)
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
    if(Preferences.voicemailAlerts_?)
      TTS.speakEvery(180, "New voicemail")
  }

  onMessageNoLongerWaiting { () =>
    voicemailIndicator.foreach { i => TTS.stopRepeatedSpeech(i) }
  }

}
