package info.spielproject.spiel

import actors.Actor._
import collection.JavaConversions._

import android.content.Context
import android.util.Log

import com.google.tts.TextToSpeechBeta
import TextToSpeechBeta._

object TTS extends OnInitListener with OnUtteranceCompletedListener {

  private var tts:TextToSpeechBeta = null

  private var context:Context = null

  def apply(c:Context) {
    tts = new TextToSpeechBeta(c, this)
    context = c
  }

  def onInit(status:Int, version:Int) {
    tts.setLanguage(java.util.Locale.getDefault)
    tts.setOnUtteranceCompletedListener(this)
    speak(context.getString(R.string.welcomeMsg), true)
  }

  def shutdown = tts.shutdown

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
    if(text.length == 0)
      tts.speak("blank", mode, null)
    else if(text == " ")
      tts.speak("space", mode, null)
    else if(text.length == 1 && text >= "A" && text <= "Z") {
      tts.setPitch(1.5f)
      tts.speak("cap "+text, mode, null)
      tts.setPitch(1)
    } else
      tts.speak(text, mode, null)
  }

  def speak(list:List[String], flush:Boolean):Unit = if(list != Nil) {
    speak(list.head, flush)
    list.tail.foreach { str => speak(str, false) }
  }

  def stop {
    Log.d("spiel", "Stopping speech")
    tts.stop
  }

  private def speakWithUtteranceID(text:String, uid:String) {
    Log.d("spiel", "Speaking: "+text)
    val params = new java.util.HashMap[String, String]()
    params.put("utteranceId", uid) // TODO: Why won't Scala see Engine?
    tts.speak(text, QUEUE_FLUSH, params).toString
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

  private def performRepeatedSpeech(key:String):Unit = repeatedSpeech.get(key) match {
    case Some(v) if(Preferences.repeatedSpeechWhenRingerOff == false && StateObserver.isRingerOff) => actor {
      Thread.sleep(v._1*1000)
      performRepeatedSpeech(key)
    }
    case Some(v) => speakWithUtteranceID(v._2, key)
    case None =>
  }

}
