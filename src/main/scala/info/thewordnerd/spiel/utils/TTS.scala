package info.thewordnerd.spiel.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech._
import android.util.Log

object TTS extends OnInitListener {

  private var tts:TextToSpeech = null

  def apply(context:Context) {
    tts = new TextToSpeech(context, this)
  }

  def onInit(i:Int) {
    tts.setLanguage(java.util.Locale.getDefault)
  }

  def speak(text:Any, flush:Boolean) = {
    val mode = if(flush) QUEUE_FLUSH else QUEUE_ADD
    text match {
      case t:String =>
        if(t.length == 0)
          tts.speak("blank", mode, null)
        else
          tts.speak(t, mode, null)
      case t:java.util.List[String] =>
        if(t.size == 0)
          tts.speak("blank", mode, null)
        else {
          val str = t.toString
          tts.speak(
            str.substring(1, str.length-1),
            mode, null
          )
        }
      case _ => Log.e(this.toString, "Invalid text format")
    }
  }

  def stop = tts.stop
}
