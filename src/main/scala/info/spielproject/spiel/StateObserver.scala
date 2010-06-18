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

  StateReactor.onScreenOff { () =>
    Log.d("spiel", "Screen off.")
    TTS.speak("Locked.", false)
  }

  StateReactor.onScreenOn { () =>
    Log.d("spiel", "Screen on.")
    TTS.speak("Locked.", false)
  }


}
