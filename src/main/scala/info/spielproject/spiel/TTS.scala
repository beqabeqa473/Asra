package info.spielproject.spiel

import android.content.Context
import android.speech.tts.TextToSpeech
import TextToSpeech._
import android.util.Log
import scala.actors.Actor._

protected object TTS extends OnInitListener {

  private var tts:TextToSpeech = null

  def apply(context:Context) {
    tts = new TextToSpeech(context, this)
  }

  def onInit(i:Int) {
    tts.setLanguage(java.util.Locale.getDefault)
    tts.setOnUtteranceCompletedListener(
      new OnUtteranceCompletedListener {
        def onUtteranceCompleted(id:String) {
          repeatedSpeech.get(id) match {
            case Some(v) =>
              actor {
                Thread.sleep(v._1*1000)
                performRepeatedSpeech(id)
              }
            case None =>
          }
        }
      }
    )
    speak("Welcome to spiel!", true)
  }

  def speak(text:Any, flush:Boolean):Unit = text match {
    case t:String =>
      val mode = if(flush) QUEUE_FLUSH else QUEUE_ADD
      Log.d(this.toString, "Speaking: "+t+": "+flush)
      if(t.length == 0)
        tts.speak("blank", mode, null)
      else if(t.length == 1 && t >= "A" && t <= "Z") {
        tts.setPitch(1.5f)
        tts.speak("cap "+t, mode, null)
        tts.setPitch(1)
      } else
        tts.speak(t, mode, null)
    case t:java.util.List[CharSequence] => speak(Util.toFlatString(t), flush)
    case _ => Log.e(this.toString, "Invalid text format")
  }

  def stop {
    Log.d(this.toString, "Stopping speech")
    tts.stop
  }

  private def speakWithUtteranceID(text:String, uid:String) {
    val params = new java.util.HashMap[String, String]()
    params.put("utteranceId", uid) // TODO: Why won't Scala see Engine?
    tts.speak(text, QUEUE_ADD, params).toString
  }

  private var repeatedSpeech = collection.mutable.Map[String, Tuple2[Int, String]]()

  private val random = new java.util.Random

  def speakEvery(seconds:Int, text:String) = {
    val key = random.nextInt.toString
    repeatedSpeech(key) = (seconds, text)
    performRepeatedSpeech(key)
    key
  }

  def stopRepeatedSpeech(key:String) = repeatedSpeech -= key

  private def performRepeatedSpeech(key:String) = repeatedSpeech.get(key) match {
    case Some(v) => speakWithUtteranceID(v._2, key)
    case None =>
  }

}
