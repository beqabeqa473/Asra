package info.spielproject.spiel

import android.content._
import android.util.Log

class Commands extends BroadcastReceiver {

  val Speak = "info.spielproject.spiel.commands.SPEAK"

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
