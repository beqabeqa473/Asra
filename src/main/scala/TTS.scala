package info.spielproject.spiel

import collection.JavaConversions._
import concurrent._
import ExecutionContext.Implicits.global

import android.app._
import android.content._
import android.content.pm._
import android.database._
import android.media._
import android.os.Build.VERSION
import android.os._
import android.provider.Settings.Secure
import android.speech.tts._
import android.util.Log

import events._
import presenters.Presenter

/**
 * Singleton facade around TTS functionality.
*/

object TTS extends UtteranceProgressListener with TextToSpeech.OnInitListener with AudioManager.OnAudioFocusChangeListener {

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

    service.getContentResolver.registerContentObserver(Secure.getUriFor(Secure.TTS_DEFAULT_SYNTH), false, new ContentObserver(new Handler) {
      override def onChange(bySelf:Boolean) = TTSEngineChanged()
    })

    service.getContentResolver.registerContentObserver(Secure.getUriFor(Secure.TTS_DEFAULT_RATE), false, new ContentObserver(new Handler) {
      override def onChange(bySelf:Boolean) = RateChanged()
    })

    service.getContentResolver.registerContentObserver(Secure.getUriFor(Secure.TTS_DEFAULT_PITCH), false, new ContentObserver(new Handler) {
      override def onChange(bySelf:Boolean) = PitchChanged()
    })

  }

  private var reinitializing = false

  /**
   * Initialize or re-initialize TTS.
  */

  def init() {
    guard {
      reinitializing = true
      if(tts != null)
        tts.shutdown()
      val desiredEngine = if(failures >= 3 && !spokeSuccessfully)
        platformEngine
      else if(Preferences.speechEngine != "") {
        val enginePackages = engines.map(_._2)
        if(enginePackages.contains(Preferences.speechEngine))
          Some(Preferences.speechEngine)
        else None
      } else None
      spokeSuccessfully = false
      tts= desiredEngine.map(new TextToSpeech(service, this, _)).getOrElse(new TextToSpeech(service, this))
      TextToSpeech.SUCCESS
    }
  }

  private var welcomed = false

  def onInit(status:Int) {
    reinitializing = false
    if(status == TextToSpeech.ERROR)
      return service.stopSelf()
    tts.setOnUtteranceProgressListener(this)
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

  def engines(context:Context = service):Seq[Tuple2[String, String]] = {
    val pm = Option(context).map(_.getPackageManager)
    val intent = new Intent(tts.Engine.INTENT_ACTION_TTS_SERVICE)
    def iter(engine:ResolveInfo):Tuple2[String, String] = {
      var label = engine.loadLabel(pm.get).toString()
      if(label == "") label = Option(engine.activityInfo).getOrElse(engine.serviceInfo).name.toString()
      (label, Option(engine.serviceInfo).getOrElse(engine.activityInfo).packageName)
    }
    pm.map(_.queryIntentServices(intent, 0).map (iter)).getOrElse(Nil)
  }

  def engines:Seq[Tuple2[String, String]] = engines()

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
      case e:Throwable =>
        if(!reinitializing)
          failures += 1
        UnhandledException(e)
        Log.e("spiel", "TTS error:", e)
        if(failures >= 3)
          reInitOnFailure()
        false
    }
  }

  private def abandonFocus() {
    if(audioManager.isMusicActive)
      audioManager.abandonAudioFocus(this)
  }

  private val utterances = collection.mutable.Set[String]()

  def onStart(id:String) {
    if(Preferences.duckNonSpeechAudio && audioManager.isMusicActive)
      audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    utterances += id
    UtteranceStarted(id)
  }

  def onError(id:String) {
    abandonFocus()
    UtteranceError(id)
    utterances -= id
    if(utterances.isEmpty) SpeechQueueEmpty()
  }

  def onDone(id:String) {
    abandonFocus()
    UtteranceEnded(id)
    utterances -= id
    if(utterances.isEmpty) SpeechQueueEmpty()
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

  private var failures = 0

  private def reInitOnFailure() {
    val wasEmpty = utterances.isEmpty
    utterances.clear()
    if(!wasEmpty) SpeechQueueEmpty()
    if(!spokeSuccessfully) {
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
    }
    init()
    failures = 0
  }

  def guard(f: => Int) {
    try {
      if(f == TextToSpeech.SUCCESS)
        failures = 0
      else {
        abandonFocus()
        if(!reinitializing)
          failures += 1
      }
    } catch {
      case e:Throwable =>
        abandonFocus()
        if(!reinitializing)
          failures += 1
          UnhandledException(e)
          Log.e("spiel", "TTS error:", e)
    } finally {
      if(failures >= 3)
        reInitOnFailure()
    }
  }

  private var spokeSuccessfully = false

  var noFlush = false

  /**
   * Speaks the specified text, optionally flushing current speech.
  */

  def speak(text:String, flush:Boolean, utteranceID:Option[String] = None) {
    if(!SpielService.enabled) return
    if(text.length > 1 && text.contains("\n"))
      return speak(text.split("\n").toList, flush)
    Log.d("spiel", "Speaking "+text+": "+flush)
    val mode = if(flush && !noFlush) 2 else TextToSpeech.QUEUE_ADD
    val params = new java.util.HashMap[String, String]()
    val uid = utteranceID.getOrElse(java.util.UUID.randomUUID.toString)
    params.put(tts.Engine.KEY_PARAM_UTTERANCE_ID, uid)
    guard {
      if(flush) {
        tts.speak("", mode, null)
        val wasEmpty = utterances.isEmpty
        utterances.clear()
        if(!wasEmpty) SpeechQueueEmpty()
      }
      pitch = Preferences.pitchScale
      val rv = if(text.length == 0)
        tts.speak(service.getString(R.string.blank), mode, params)
      else if(text.length == 1 && Character.isUpperCase(text(0))) {
        pitch = Preferences.pitchScale*1.5f
        val rv2 = tts.speak(service.getString(R.string.cap, text), mode, params)
        pitch = Preferences.pitchScale
        rv2
      } else if(text.length == 1 && Preferences.managePunctuationSpeech && managedPunctuations.get(text) != None)
        tts.speak(service.getString(managedPunctuations(text)), mode, params)
      else
        tts.speak(text, mode, params)
      if(rv == TextToSpeech.SUCCESS)
        spokeSuccessfully = true
      rv
    }
  }

  def speak(text:String, flush:Boolean) {
    speak(text, flush, None)
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
    if(noFlush || !SpielService.enabled) return
    Log.d("spiel", "Stopping speech")
    val wasEmpty = utterances.isEmpty
    utterances.clear()
    guard { tts.stop() }
    SpeechStopped()
    if(!wasEmpty) SpeechQueueEmpty()
  }

  def presentPercentage(percentage:Double) = {
    Log.d("spiel", "Presenting "+percentage+"%")
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

  private def performRepeatedSpeech(key:String):Unit = repeatedSpeech.get(key) foreach { v =>
    if(shouldSpeakNotification(true))
      speak(v._2, false, Some(key))
    future {
      Thread.sleep(v._1*1000)
      performRepeatedSpeech(key)
    }
  }

  private def shouldSpeakNotification(skipScreenCheck:Boolean = false):Boolean =
    if(Device.ringerOff_? || Telephony.inCall_?)
      false
    else if(!skipScreenCheck && !Preferences.speakNotificationsWhenScreenOff && Device.screenOff_?)
      false
    else true

  private def shouldSpeakNotification:Boolean = shouldSpeakNotification()

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

  TTSEngineChanged += {
    if(TTS.defaultEngine != Preferences.speechEngine)
      TTS.init() 
  }

  private var mutedForSpeech = false

  UtteranceStarted += { text:String =>
    if(!audioManager.isMicrophoneMute && (!Telephony.inCall_? || !audioManager.isSpeakerphoneOn)) {
      audioManager.setMicrophoneMute(true)
      mutedForSpeech = true
    }
  }

  private def unmuteIfNecessary() {
    if(mutedForSpeech)
      audioManager.setMicrophoneMute(false)
    mutedForSpeech = false
  }

  UtteranceEnded += { text:String => unmuteIfNecessary() }

  UtteranceError += { text:String => unmuteIfNecessary() }

}
