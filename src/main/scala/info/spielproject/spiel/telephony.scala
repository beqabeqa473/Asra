package info.spielproject.spiel.telephony

import android.content.Context
import android.telephony.{PhoneStateListener, TelephonyManager}
import android.util.Log

import info.spielproject.spiel.tts.TTS

private class Listener extends PhoneStateListener {

  Log.d("spiel", "Initializing.")

  import TelephonyManager._

  private var repeaterID = ""

  override def onCallStateChanged(state:Int, number:String) = state match {
    case CALL_STATE_IDLE =>
      TTS.stopRepeatedSpeech(repeaterID)
      repeaterID = ""
    case CALL_STATE_RINGING =>
      repeaterID = TTS.speakEvery(3, number.toString)
    case CALL_STATE_OFFHOOK =>
      TTS.stop
      TTS.stopRepeatedSpeech(repeaterID)
      repeaterID = ""
  }

}

object TelephonyListener {

  def apply(service:SpielService) {
    val manager = service.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
    manager.listen(new Listener, PhoneStateListener.LISTEN_CALL_STATE)
  }

}
