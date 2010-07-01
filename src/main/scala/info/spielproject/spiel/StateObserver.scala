package info.spielproject.spiel

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.media.AudioManager
import android.util.Log

object StateObserver {

  def apply(service:SpielService) {

    def registerReceiver(r:(Context, Intent) => Unit, i:String) {
      service.registerReceiver(new BroadcastReceiver {
        override def onReceive(c:Context, i:Intent) = r(c, i)
      }, new IntentFilter(i))
    }

    registerReceiver((c, i) => StateReactor.ringerModeChanged, AudioManager.RINGER_MODE_CHANGED_ACTION)

    registerReceiver((c, i) => StateReactor.screenOff , Intent.ACTION_SCREEN_OFF)

    registerReceiver((c, i) => StateReactor.screenOn, Intent.ACTION_SCREEN_ON)

  }

  StateReactor.onScreenOff { () => TTS.speak("Locked.", false) }
  StateReactor.onScreenOn { () => TTS.speak("Locked.", false) }

  private var voicemailIndicator:Option[String] = None

  StateReactor.onMessageWaiting { () =>
    if(Preferences.voicemailAlerts_?)
      TTS.speakEvery(180, "New voicemail")
  }

  StateReactor.onMessageNoLongerWaiting { () =>
    voicemailIndicator.foreach { i => TTS.stopRepeatedSpeech(i) }
  }

}
