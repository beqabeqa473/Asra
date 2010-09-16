package info.spielproject.spiel

import actors.Actor._
import collection.JavaConversions._

import android.content.Context
import android.os.Build.VERSION
import android.speech.tts.TextToSpeech
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

  private var usingTTSExtended = false

  def onInit(status:Int, version:Int) {
    usingTTSExtended = version != -1
    Log.d("spiel", "Initialized TTS: "+version+", "+usingTTSExtended)
    engine = Preferences.speechEngine
    tts.setLanguage(java.util.Locale.getDefault)
    tts.setOnUtteranceCompletedListener(this)
    this.rate = Preferences.rateScale
    this.pitch = Preferences.pitchScale
    speak(context.getString(R.string.welcomeMsg), true)
  }

  def defaultEngine = {
    Log.d("spiel", "Delegating defaultEngine() for API level "+VERSION.SDK_INT)
    if(VERSION.SDK_INT >= 8) defaultEngineV8 else ""
  }

  private def defaultEngineV8 = {
    Log.d("spiel", "defaultEngineV8")
    if(usingTTSExtended)
      defaultEngineExtended
    else
      tts.getDefaultEngine
  }

  def defaultEngineExtended = {
    Log.d("spiel", "defaultEngineExtended")
    tts.getDefaultEngineExtended
  }
  def engine = Preferences.speechEngine

  def engine_=(e:String) = {
    Log.d("spiel", "Delegating setEngine() for API level "+VERSION.SDK_INT)
    if(VERSION.SDK_INT >= 8)
      setEngineV8(e)
  }

  private def setEngineV8(e:String) {
    Log.d("spiel", "TTS.setEngineV8("+e+")")
    if(usingTTSExtended)
      setEngineExtended(e)
    else if(tts.setEngineByPackageName(e) != TextToSpeech.SUCCESS) {
      tts.setEngineByPackageName(defaultEngine)
      Log.d("spiel", "Error setting speech engine. Reverting to "+defaultEngine)
    }
  }

  private def setEngineExtended(e:String) {
    Log.d("spiel", "setEngineExtended("+e+")")
    if(tts.setEngineByPackageNameExtended(e) != TextToSpeechBeta.SUCCESS) {
      tts.setEngineByPackageName(defaultEngine)
      Log.d("spiel", "Error setting speech engine. Reverting to "+defaultEngine)
    }
  }

  def rate = 1f // No-op needed for setter
  def rate_=(r:Float) = tts.setSpeechRate(r)

  def pitch = 1f // No-op needed for setter
  def pitch_=(p:Float) = tts.setPitch(p)

  def shutdown = tts.shutdown

  def onUtteranceCompleted(id:String) = repeatedSpeech.get(id) match {
    case Some(v) =>
      actor {
        Thread.sleep(v._1*1000)
        performRepeatedSpeech(id)
      }
    case None =>
  }

  private val managedPunctuations = Map(
    "!" -> R.string.exclamation,
    "@" -> R.string.at,
    "#" -> R.string.pound,
    "$" -> R.string.dollar,
    "%" -> R.string.percent,
    "^" -> R.string.caret,
    "&" -> R.string.ampersand,
    "*" -> R.string.asterisk,
    "(" -> R.string.leftParen,
    ")" -> R.string.rightParen,
    "_" -> R.string.underscore,
    " " -> R.string.space,
    "." -> R.string.period,
    "," -> R.string.comma,
    "<" -> R.string.lessThan,
    ">" -> R.string.greaterThan,
    "/" -> R.string.slash,
    "\\" -> R.string.backslash,
    "?" -> R.string.questionMark,
    "\"" -> R.string.quote,
    "'" -> R.string.apostrophe,
    ";" -> R.string.semiColon,
    ":" -> R.string.colon
  )

  def speak(text:String, flush:Boolean) {
    val mode = if(flush) QUEUE_FLUSH else QUEUE_ADD
    if(text.length == 0)
      tts.speak("blank", mode, null)
    else if(text.length == 1 && text >= "A" && text <= "Z") {
      pitch = 1.5f
      tts.speak("cap "+text, mode, null)
      pitch = 1
    } else if(text.length == 1 && Preferences.managePunctuationSpeech && managedPunctuations.get(text) != None)
      tts.speak(context.getString(managedPunctuations(text)), mode, null)
    else if(text.length == 1 && Preferences.fixMultivoice)
      tts.speak(text+" ", mode, null)
    else
      tts.speak(text, mode, null)
  }

  def speak(list:List[String], flush:Boolean):Unit = if(list != Nil) {
    if(flush) {
      stop
      // Let queue empty before adding new items. Avoids jumbled speech.
      Thread.sleep(40)
    }
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
    case Some(v) if(Preferences.repeatedSpeechWhenRingerOff == false && StateReactor.ringerOff_?) => actor {
      Thread.sleep(v._1*1000)
      performRepeatedSpeech(key)
    }
    case Some(v) => speakWithUtteranceID(v._2, key)
    case None =>
  }

  private var charBuffer = ""

  def speakCharacter(char:String) {
    if(Preferences.echoByChar)
      speak(char, true)
    if(Preferences.echoByWord) {
      charBuffer += char
      if(!(char >= "a" && char <= "z") && !(char >= "A" && char <= "Z"))
        speakCharBuffer()
    }
  }

  def clearCharBuffer() {
    charBuffer = ""
  }

  def speakCharBuffer() {
    if(charBuffer != "")
      speak(charBuffer, true)
    clearCharBuffer()
  }

  private def shouldSpeakNotification:Boolean = {
    if(StateReactor.ringerOff_?) return false
    if(!Preferences.speakNotificationsWhenScreenOff && StateReactor.screenOff_?) return false
    true
  }

  def speakNotification(text:String) {
    if(shouldSpeakNotification)
      speak(text, false)
  }

  def speakNotification(text:List[String]) {
    if(shouldSpeakNotification)
      speak(text, false)
  }

}
