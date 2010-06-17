package info.spielproject.spiel

import actors.Actor._
import collection.JavaConversions._

import android.content.Context
import android.util.Log

import com.google.tts.TextToSpeechBeta
import TextToSpeechBeta._

object TTS extends OnInitListener with OnUtteranceCompletedListener {

  private var tts:TextToSpeechBeta = null

  def apply(context:Context) {
    tts = new TextToSpeechBeta(context, this)
  }

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
    case None => if(!queue.isEmpty) processUtterance(queue.dequeue)
  }

  private case class Utterance(text:String, flush:Boolean)

  private val queue = new collection.mutable.Queue[Utterance]

  def speak(text:String, flush:Boolean) {
    if(text.contains("\n"))
      speak(text.split("\n").toList, flush)
    else {
      val utterance = Utterance(text, flush)
      if(tts.isSpeaking || !queue.isEmpty) {
        if(utterance.flush) {
          stopAndClear
          processUtterance(utterance)
        } else {
          Log.d("spiel", "Queuing: "+utterance)
          queue.enqueue(utterance)
        }
      } else processUtterance(utterance)
    }
  }

  def speak(list:List[String], flush:Boolean):Unit = if(list != Nil) {
    speak(list.head, flush)
    list.tail.foreach { str => speak(str, false) }
  }

  def stop = stopAndClear

  private def speakWithUtteranceID(text:String, uid:String, flush:Boolean = false) {
    //if(flush)  stopAndClear
    Log.d("spiel", "Speaking: "+text+": "+flush)
    val params = new java.util.HashMap[String, String]()
    params.put("utteranceId", uid) // TODO: Why won't Scala see Engine?
    val mode = if(flush) QUEUE_FLUSH else QUEUE_ADD
    tts.speak(text, mode, params).toString
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

  private def processUtterance(u:Utterance) {
    Log.d("spiel", "Processing: "+u)
    if(u.text.length == 0)
      speakWithUtteranceID("blank", "queue", u.flush)
    else if(u.text == " ")
      speakWithUtteranceID("space", "queue", u.flush)
    else if(u.text.length == 1 && u.text >= "A" && u.text <= "Z") {
      tts.setPitch(1.5f)
      speakWithUtteranceID("cap "+u.text, "queue", u.flush)
      tts.setPitch(1)
    } else
      speakWithUtteranceID(u.text, "queue", u.flush)
  }

  private def stopAndClear {
    Log.d("spiel", "Stopping speech and clearing queue.")
    tts.stop
    queue.clear
  }

}
