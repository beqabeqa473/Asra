package info.spielproject.spiel

import actors.Actor._
import collection.JavaConversions._

import android.app.Service
import android.content.{Context, Intent}
import android.content.pm.ResolveInfo
import android.media.{AudioManager, SoundPool}
import android.os.Build.VERSION
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.util.Log

import presenters.Presenter

/**
 * Singleton facade around TTS functionality.
*/

object TTS extends TextToSpeech.OnInitListener with TextToSpeech.OnUtteranceCompletedListener with AudioManager.OnAudioFocusChangeListener {

  private var tts:TextToSpeech = null

  private var service:Service = null

  private lazy val audioManager = service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]

  private lazy val pool = new SoundPool(8, AudioManager.STREAM_MUSIC, 0)

  object Sounds {
    var tick = 0
  }

  /**
   * Initialize TTS based on specified <code>Service</code>.
  */

  def apply(s:Service) {
    service = s
    Sounds.tick = pool.load(service, R.raw.tick, 1)
    init()
  }

  private var desiredEngine:Option[String] = None

  private var reinitializing = false

  /**
   * Initialize or re-initialize TTS.
  */

  def init() {
    guard {
      reinitializing = true
      if(tts != null) {
        tts.shutdown()
        tts = null
      }
      desiredEngine.getOrElse(desiredEngine = Some(Preferences.speechEngine))
      tts = if(VERSION.SDK_INT < 14)
        new TextToSpeech(service, this)
      else {
        val tmp = desiredEngine.map(new TextToSpeech(service, this, _)).getOrElse(new TextToSpeech(service, this))
        desiredEngine = None
        tmp
      }
      TextToSpeech.SUCCESS
    }
  }

  private var welcomed = false

  def onInit(status:Int) {
    reinitializing = false
    if(status == TextToSpeech.ERROR)
      return service.stopSelf()
    desiredEngine.foreach { engine =>
      tts.setEngineByPackageName(engine)
      desiredEngine = None
    }
    tts.setOnUtteranceCompletedListener(this)
    tts.addEarcon("tick", "info.spielproject.spiel", R.raw.tick)
    pitch = Preferences.pitchScale
    if(!welcomed) {
      speak(service.getString(R.string.welcomeMsg), true)
      welcomed = true
    }
  }

  /**
   * @return default engine, or empty string if unknown
  */

  def defaultEngine = Option(tts.getDefaultEngine).flatMap { e => e match {
    case "" => None
    case v => Some(v)
  } }

  def engines = {
    val pm = service.getPackageManager
    val intent = new Intent(if(VERSION.SDK_INT < 14) "android.intent.action.START_TTS_ENGINE" else tts.Engine.INTENT_ACTION_TTS_SERVICE)
    def iter(engine:ResolveInfo):Tuple2[String, String] = {
      var label = engine.loadLabel(pm).toString()
      if(label == "") label = Option(engine.activityInfo).getOrElse(engine.serviceInfo).name.toString()
      (label, Option(engine.serviceInfo).getOrElse(engine.activityInfo).packageName)
    }
    if(VERSION.SDK_INT >= 14)
      pm.queryIntentServices(intent, 0).map (iter)
    else
      pm.queryIntentActivities(intent, 0).map (iter)
  }

  def platformEngine =
    engines.find(_._2 == "com.google.android.tts").map(_._2)
    .orElse(engines.find(_._2 == "com.svox.pico").map(_._2))
    .orElse(defaultEngine)

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

  def shutdown() {
    tts.shutdown
    pool.release()
  }

  private def speaking_? = {
    try {
      tts.isSpeaking
    } catch {
      case e =>
        if(!reinitializing)
          failures += 1
        Log.e("spiel", "TTS error:", e)
        if(failures >= 3)
          reInitOnFailure()
        false
    }
  }

  private def abandonFocus() {
    actor {
      Thread.sleep(200)
      if(!speaking_?)
        audioManager.abandonAudioFocus(this)
    }
  }

  def onUtteranceCompleted(id:String) {
    if(id == lastUtteranceID)
      abandonFocus()
    repeatedSpeech.get(id).foreach { v =>
      abandonFocus()
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
    "\n" -> R.string.newline,
    "\t" -> R.string.tab
  )

  private def requestFocus() {
    if(Preferences.duckNonSpeechAudio)
      audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
  }

  private var failures = 0

  private def reInitOnFailure() {
    desiredEngine = platformEngine.orElse(Some(Preferences.speechEngine))
    failures = 0
    val intent = new Intent()
    intent.setAction(tts.Engine.ACTION_CHECK_TTS_DATA)
    if(Preferences.speechEngine != "")
      intent.setPackage(Preferences.speechEngine)
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      service.startActivity(intent)
    } catch {
      case e:android.content.ActivityNotFoundException => Log.e("spiel", "Error reinitializing speech", e)
    }
    init()
  }

  def guard(f: => Int) {
    try {
      if(f == TextToSpeech.SUCCESS)
        failures = 0
      else
        if(!reinitializing)
          failures += 1
    } catch {
      case e =>
        if(!reinitializing)
          failures += 1
        Log.e("spiel", "TTS error:", e)
    } finally {
      if(failures >= 3)
        reInitOnFailure()
    }
  }

  val lastUtteranceID = "last"

  /**
   * Speaks the specified text, optionally flushing current speech.
  */

  def speak(text:String, flush:Boolean, utteranceID:Option[String] = Some(lastUtteranceID)) {
    if(!SpielService.enabled) return
    if(text.length > 1 && text.contains("\n"))
      return speak(text.split("\n").toList, flush)
    Log.d("spiel", "Speaking "+text+": "+flush)
    val mode = if(flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
    requestFocus()
    val params = new java.util.HashMap[String, String]()
    utteranceID.foreach(params.put(tts.Engine.KEY_PARAM_UTTERANCE_ID, _))
    guard { if(text.length == 0)
      tts.speak(service.getString(R.string.blank), mode, params)
    else if(text.length == 1 && Character.isUpperCase(text(0))) {
      pitch = Preferences.pitchScale*1.5f
      tts.speak(service.getString(R.string.cap, text), mode, params)
      pitch = Preferences.pitchScale
    } else if(text.length == 1 && Preferences.managePunctuationSpeech && managedPunctuations.get(text) != None)
      tts.speak(service.getString(managedPunctuations(text)), mode, params)
    else
      tts.speak(text, mode, params)
    }
  }

  def speak(text:String, flush:Boolean) {
    speak(text, flush, Some(lastUtteranceID))
  }

  /**
   * Speaks the specified List of strings, optionally flushing speech.
  */

  def speak(list:List[String], flush:Boolean) {
    if(list != Nil && flush) stop()
    list match {
      case Nil =>
      case hd :: Nil => speak(hd, false)
      case hd :: tl =>
        speak(hd, false, None)
        speak(tl, false)
    }
  }

  def play(id:Int, pitch:Double = 1d) {
    pool.play(id, 1f, 1f, 0, 0, pitch.toFloat)
  }

  /**
   * Play a tick.
  */

  def tick(pitchScale:Option[Double] = None) = {
    pitchScale.map { s =>
      play(Sounds.tick, s)
    }.getOrElse(guard { tts.playEarcon("tick", 0, null) })
    true
  }

  def tick():Unit = tick(None)

  /**
   * Stops speech.
  */

  def stop() {
    if(!SpielService.enabled) return
    Log.d("spiel", "Stopping speech")
    abandonFocus()
    guard { tts.stop() }
  }

  def presentPercentage(percentage:Double) = {
    tick(Some(0.5+percentage/200))
    true
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
    case Some(v) => speak(v._2, true, Some(key))
    case None =>
  }

  private def shouldSpeakNotification:Boolean = {
    if(StateReactor.ringerOff_? || StateReactor.inCall_?) return false
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
      Presenter.nextShouldNotInterrupt
    }
  }

  /**
   * Handle speaking of the specified notification List of strings based on 
   * preferences and phone state.
  */

  def speakNotification(text:List[String]) {
    if(shouldSpeakNotification) {
      speak(text, false)
      Presenter.nextShouldNotInterrupt
    }
  }

  def onAudioFocusChange(f:Int) { }

}
