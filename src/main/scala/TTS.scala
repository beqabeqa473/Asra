package info.spielproject.spiel

import actors.Actor._
import collection.JavaConversions._

import android.app.Service
import android.content.Context
import android.media.AudioManager
import android.os.Build.VERSION
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.util.Log

/**
 * Singleton facade around TTS functionality.
*/

object TTS extends TextToSpeech.OnInitListener with TextToSpeech.OnUtteranceCompletedListener with AudioManager.OnAudioFocusChangeListener {

  private var tts:TextToSpeech = null

  private var service:Service = null

  private var audioManager:Option[AudioManager] = None

  /**
   * Initialize TTS based on specified <code>Service</code>.
  */

  def apply(s:Service) {
    service = s
    if(VERSION.SDK_INT >= 8)
      audioManager = Some(service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager])
    init()
  }

  /**
   * Initialize or re-initialize TTS.
  */

  def init() = {
    tts = new TextToSpeech(service, this)
  }

  private var welcomed = false

  def onInit(status:Int) {
    if(status == TextToSpeech.ERROR)
      return service.stopSelf()
    tts.setLanguage(java.util.Locale.getDefault)
    if(Environment.getExternalStorageState == Environment.MEDIA_MOUNTED)
      engine = Preferences.speechEngine
    else
      engine = "com.svox.pico"
    tts.setOnUtteranceCompletedListener(this)
    this.rate = Preferences.rateScale
    this.pitch = Preferences.pitchScale
    tts.addEarcon("tick", "info.spielproject.spiel", R.raw.tick)
    if(!welcomed) {
      speak(service.getString(R.string.welcomeMsg), true)
      welcomed = true
    }
  }

  def defaultsEnforced_? = {
    if(tts == null) true
    else if(VERSION.SDK_INT >= 8) defaultsEnforcedV8_? else false
  }

  private def defaultsEnforcedV8_? = tts.areDefaultsEnforced()

  /**
   * @return default engine, or empty string if unknown
  */

  def defaultEngine = {
    Log.d("spiel", "Delegating defaultEngine() for API level "+VERSION.SDK_INT)
    if(VERSION.SDK_INT >= 8) defaultEngineV8 else ""
  }

  private def defaultEngineV8 = {
    Log.d("spiel", "defaultEngineV8")
    tts.getDefaultEngine
  }

  /**
   * @return desired speech engine
  */

  def engine = Preferences.speechEngine

  /**
   * Set desired speech engine
  */

  def engine_=(e:String) = {
    Log.d("spiel", "Delegating setEngine() for API level "+VERSION.SDK_INT)
    if(VERSION.SDK_INT >= 8)
      setEngineV8(e)
  }

  private def setEngineV8(e:String) {
    Log.d("spiel", "TTS.setEngineV8("+e+")")
    if(tts.setEngineByPackageName(e) != TextToSpeech.SUCCESS) {
      tts.setEngineByPackageName(defaultEngine)
      Log.d("spiel", "Error setting speech engine. Reverting to "+defaultEngine)
    }
  }

  /**
   * @return desired rate scale
  */

  def rate = 1f // No-op needed for setter

  /**
   * Set desired rate scale
  */

  def rate_=(r:Float) = tts.setSpeechRate(r)

  /**
   * @return desired pitch scale
  */

  def pitch = 1f // No-op needed for setter

  /**
   * Set desired pitch scale
  */

  def pitch_=(p:Float) = tts.setPitch(p)

  /**
   * Shut down TTS, freeing up resources.
  */

  def shutdown = tts.shutdown

  private def abandonFocus() {
    audioManager.foreach(_.abandonAudioFocus(this))
  }

  def onUtteranceCompleted(id:String) {
    audioManager.foreach { a => 
      if(!tts.isSpeaking) abandonFocus()
    }
    repeatedSpeech.get(id).foreach { v =>
      actor {
        Thread.sleep(v._1*1000)
        performRepeatedSpeech(id)
      }
    }
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
    ":" -> R.string.colon,
    "-" -> R.string.dash,
    "+" -> R.string.plus,
    "\n" -> R.string.newline
  )

  private def requestAudioFocus() {
    if(Preferences.duckNonSpeechAudio)
      audioManager.foreach { a =>
        a.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
      }
  }

  /**
   * Speaks the specified text, optionally flushing current speech.
  */

  def speak(text:String, flush:Boolean) {
    if(!SpielService.enabled) return
    Log.d("spiel", "Speaking "+text+": "+flush)
    val mode = if(flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
    //if(flush) stop
    audioManager.foreach { a => requestAudioFocus() }
    val params = new java.util.HashMap[String, String]()
    params.put("utteranceId", "speech") // TODO: Why won't Scala see Engine?
    if(text.length == 0)
      tts.speak(service.getString(R.string.blank), mode, params)
    else if(text.length == 1 && text >= "A" && text <= "Z") {
      pitch = 1.5f
      tts.speak(service.getString(R.string.cap, text), mode, params)
      pitch = 1
    } else if(text.length == 1 && Preferences.managePunctuationSpeech && managedPunctuations.get(text) != None)
      tts.speak(service.getString(managedPunctuations(text)), mode, params)
    else
      tts.speak(text, mode, params)
  }

  /**
   * Speaks the specified List of strings, optionally flushing speech.
  */

  def speak(list:List[String], flush:Boolean):Unit = if(list != Nil) {
    speak(list.reduceLeft[String] { (acc, v) => 
      val text = if(v == "") "blank" else v
      acc+": "+text
    }, flush)
  }

  /**
   * Play a tick.
  */

  def tick() = tts.playEarcon("tick", 0, null)

  /**
   * Stops speech.
  */

  def stop {
    if(!SpielService.enabled) return
    Log.d("spiel", "Stopping speech")
    tts.stop
  }

  private def speakWithUtteranceID(text:String, uid:String) {
    if(!SpielService.enabled) return
    Log.d("spiel", "Speaking: "+text)
    val params = new java.util.HashMap[String, String]()
    params.put("utteranceId", uid) // TODO: Why won't Scala see Engine?
    requestAudioFocus()
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, params)
  }

  private var repeatedSpeech = collection.mutable.Map[String, Tuple2[Int, String]]()

  private val random = new java.util.Random

  /**
   * Speaks the specified text every N seconds.
   *
   * @return ID used to stop repeated speech
  */

  def speakEvery(seconds:Int, text:String) = {
    val key = random.nextInt.toString
    repeatedSpeech(key) = (seconds, text)
    performRepeatedSpeech(key)
    key
  }

  /**
   * Stops repeated speech using the ID returned by <code>speakEvery</code>.
  */

  def stopRepeatedSpeech(key:String) = repeatedSpeech -= key

  private def performRepeatedSpeech(key:String):Unit = repeatedSpeech.get(key) match {
    case Some(v) if(!shouldSpeakNotification && StateReactor.ringerOff_?) => actor {
      Thread.sleep(v._1*1000)
      performRepeatedSpeech(key)
    }
    case Some(v) => speakWithUtteranceID(v._2, key)
    case None =>
  }

  private var charBuffer = ""

  /**
   * Handle speech for the specified character. Either speak immediately, or 
   * add to a buffer for speaking later.
  */

  def speakCharacter(char:String) {
    if(Preferences.echoByChar)
      speak(char, true)
    if(Preferences.echoByWord) {
      charBuffer += char
      if(!(char >= "a" && char <= "z") && !(char >= "A" && char <= "Z"))
        speakCharBuffer()
    }
  }

  /**
   * Clear the buffer of characters to be spoken.
  */

  def clearCharBuffer() {
    charBuffer = ""
  }

  /**
   * Speak and clear the buffer of characters. Since clearing should always 
   * happen after speaking, these two operations are done in a single call. 
   * Should I ever discover cases where this behavior isn't desired, then 
   * the behavior will be made optional.
  */

  def speakCharBuffer() {
    if(charBuffer != "") {
      speak(charBuffer, true)
      handlers.Handler.nextShouldNotInterrupt
    }
    clearCharBuffer()
  }

  private def shouldSpeakNotification:Boolean = {
    if(StateReactor.ringerOff_?) return false
    if(!Preferences.speakNotificationsWhenScreenOff && StateReactor.screenOff_?) return false
    true
  }

  /**
   * Handle speaking of the specified notification string based on preferences 
   * and phone state.
  */

  def speakNotification(text:String) {
    if(shouldSpeakNotification) {
      speak(text, false)
      handlers.Handler.nextShouldNotInterrupt
    }
  }

  /**
   * Handle speaking of the specified notification List of strings based on 
   * preferences and phone state.
  */

  def speakNotification(text:List[String]) {
    if(shouldSpeakNotification)
      speak(text, false)
  }

  def onAudioFocusChange(f:Int) { }

}
