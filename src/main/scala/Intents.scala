package info.spielproject.spiel

import android.content._
import android.util.Log

class Intents extends BroadcastReceiver {

  val Speak = "info.spielproject.spiel.intents.SPEAK"

  def onReceive(c:Context, i:Intent) {
    if(i.getAction == Speak)
      for(
        text <- Option(i.getStringExtra("text"));
        flush = i.getBooleanExtra("flush", false)
      ) {
        TTS.speak(text, flush)
      }
  }

}
