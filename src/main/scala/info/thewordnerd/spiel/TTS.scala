package info.thewordnerd.spiel

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech._
import android.util.Log

protected object TTS extends OnInitListener {

  private var tts:TextToSpeech = null

  def apply(context:Context) {
    tts = new TextToSpeech(context, this)
  }

  def onInit(i:Int) {
    tts.setLanguage(java.util.Locale.getDefault)
  }

  def speak(text:Any, flush:Boolean):Unit = text match {
    case t:String =>
      val mode = if(flush) QUEUE_FLUSH else QUEUE_ADD
      Log.d(this.toString, "Speaking: "+t)
      if(t.length == 0)
        tts.speak("blank", mode, null)
      else
        tts.speak(t, mode, null)
    case t:java.util.List[String] =>
      val str = t.toString
      speak(str.substring(1, str.length-1), flush)
    case _ => Log.e(this.toString, "Invalid text format")
  }

  def stop = {
    Log.d(this.toString, "Stopping speech")
    tts.stop
  }
}
