package info.spielproject.spiel.tts

import android.content.Context
import com.google.tts.TextToSpeechBeta
import TextToSpeechBeta._
import android.util.Log
import scala.actors.Actor._

private abstract class Speaker(context:Context) {

  def speak(text:String, flush:Boolean)

  def speak(list:java.util.List[String], flush:Boolean):Unit = List.fromArray(list.toArray).foreach { i =>
    speak(i.asInstanceOf[String], flush)
  }

  def stop

  def speakEvery(seconds:Int, text:String):String

  def stopRepeatedSpeech(key:String):Unit

}

private class GenericSpeaker(context:Context) extends Speaker(context) with OnInitListener with OnUtteranceCompletedListener {

  private val tts = new TextToSpeechBeta(context, this)

  def onInit(status:Int, version:Int) {
    tts.setLanguage(java.util.Locale.getDefault)
    tts.setOnUtteranceCompletedListener(this)
    speak("Welcome to spiel!", true)
  }

  def onUtteranceCompleted(id:String) = repeatedSpeech.get(id) match {
    case Some(v) =>
      actor {
        Thread.sleep(v._1*1000)
        performRepeatedSpeech(id)
      }
    case None =>
  }

  def speak(text:String, flush:Boolean) {
    val mode = if(flush) QUEUE_FLUSH else QUEUE_ADD
    Log.d("spiel", "Speaking: "+text+": "+flush)
    if(text.length == 0)
      tts.speak("blank", mode, null)
    else if(text.length == 1 && text >= "A" && text <= "Z") {
      tts.setPitch(1.5f)
      tts.speak("cap "+text, mode, null)
      tts.setPitch(1)
    } else
      tts.speak(text, mode, null)
  }

  def stop = tts.stop

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

object TTS {

  private var speaker:Speaker = null

  def apply(context:Context) {
    speaker = new GenericSpeaker(context)
  }

  def speak(text:String, flush:Boolean) = speaker.speak(text, flush)

  def speak(list:java.util.List[String], flush:Boolean) = speaker.speak(list, flush)

  def stop {
    Log.d("spiel", "Stopping speech.")
    speaker.stop
  }

  def speakEvery(seconds:Int, text:String) = speaker.speakEvery(seconds, text)

  def stopRepeatedSpeech(key:String) = speaker.stopRepeatedSpeech(key)

}
