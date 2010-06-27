package info.spielproject.spiel

import actors.Actor._
import collection.JavaConversions._

import android.content.Context
import android.util.Log

import android.speech.tts.TextToSpeech
import TextToSpeech._

object TTS extends OnInitListener with OnUtteranceCompletedListener {

  private var tts:TextToSpeech = null

  def apply(context:Context) {
    tts = new TextToSpeech(context, this)
  }

  def onInit(status:Int) {
    tts.setLanguage(java.util.Locale.getDefault)
    tts.setOnUtteranceCompletedListener(this)
    speak(context.getString(R.string.welcomeMsg), true)
  }

  def onUtteranceCompleted(id:String) = repeatedSpeech.get(id) match {
    case Some(v) =>
      actor {
        Thread.sleep(v._1*1000)
        performRepeatedSpeech(id)
      }
    case None => if(!queue.isEmpty) processUtterance(queue.dequeue)
  }

  private val queue = new collection.mutable.Queue[String]

  def speak(text:String, flush:Boolean) {
    if(text.contains("\n"))
      speak(text.split("\n").toList, flush)
    else {
      if(flush)
        stop
      if(tts.isSpeaking || !queue.isEmpty) {
        Log.d("spiel", "Queuing: "+text)
        queue.enqueue(text)
      } else processUtterance(text)
    }
  }

  def speak(list:List[String], flush:Boolean):Unit = if(list != Nil) {
    speak(list.head, flush)
    list.tail.foreach { str => speak(str, false) }
  }

  def stop {
    Log.d("spiel", "Stopping speech")
    queue.clear
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

  private def performRepeatedSpeech(key:String) = repeatedSpeech.get(key) match {
    case Some(v) => speakWithUtteranceID(v._2, key)
    case None =>
  }

  private def processUtterance(u:String) {
    Log.d("spiel", "Processing: "+u)
    if(u.length == 0)
      speakWithUtteranceID("blank", "queue")
    else if(u == " ")
      speakWithUtteranceID("space", "queue")
    else if(u.length == 1 && u >= "A" && u <= "Z") {
      tts.setPitch(1.5f)
      speakWithUtteranceID("cap "+u, "queue")
      tts.setPitch(1)
    } else
      speakWithUtteranceID(u, "queue")
  }

}
