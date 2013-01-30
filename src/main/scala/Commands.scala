package info.spielproject.spiel

import android.content._
import android.util.Log

object Commands extends BroadcastReceiver {

  val Speak = "info.spielproject.spiel.commands.SPEAK"

  def apply(c:Context) {
    c.registerReceiver(this, new IntentFilter(Speak))
  }

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
