package info.spielproject.spiel

import android.content._
import android.media._
import android.os._
import Build.VERSION
import events._

object Device extends Commands {

  def apply() {
    ApplicationAdded on(Intent.ACTION_PACKAGE_ADDED, dataScheme = Some("package"))
    ApplicationRemoved on(Intent.ACTION_PACKAGE_REMOVED, dataScheme = Some("package"))
    BatteryChanged on Intent.ACTION_BATTERY_CHANGED
    PowerConnected on Intent.ACTION_POWER_CONNECTED
    PowerDisconnected on Intent.ACTION_POWER_DISCONNECTED
    RingerModeChangedIntent on AudioManager.RINGER_MODE_CHANGED_ACTION
    ScreenOff on Intent.ACTION_SCREEN_OFF
    ScreenOn on Intent.ACTION_SCREEN_ON
    Unlocked on Intent.ACTION_USER_PRESENT
  }

  BatteryChanged += { i:Intent =>
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if(level >= 0 && scale > 0)
      BatteryLevelChanged(level*100/scale)
  }

  PowerConnected += speakBatteryPercentage(Some(SpielService.context.getString(R.string.charging)))

  PowerDisconnected += speakBatteryPercentage()

  RingerModeChangedIntent += { i:Intent =>
    val extra = i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
    val mode = extra match {
      case AudioManager.RINGER_MODE_SILENT => RingerMode.Silent
      case AudioManager.RINGER_MODE_VIBRATE => RingerMode.Vibrate
      case _ => RingerMode.Normal
    }
    RingerModeChanged(mode)
  }

  // Note ringer state, silencing spoken notifications if desired.

  private[spiel] var ringerOn:Option[Boolean] = None

  def ringerOn_? = ringerOn.getOrElse(true)
  def ringerOff_? = !ringerOn_?

  RingerModeChanged += { mode:RingerMode.Value =>
    val shouldSpeak = ringerOn != None
    ringerOn = Some(mode == RingerMode.Normal)
    if(shouldSpeak)
      mode match {
        case RingerMode.Normal => TTS.speak(SpielService.context.getString(R.string.ringerOn), false)
        case RingerMode.Silent => TTS.speak(SpielService.context.getString(R.string.ringerOff), false)
        case RingerMode.Vibrate => TTS.speak(SpielService.context.getString(R.string.ringerVibrate), false)
      }
  }

  OrientationLandscape += {
    TTS.speak(SpielService.context.getString(R.string.landscape), false)
  }

  OrientationPortrait += {
    TTS.speak(SpielService.context.getString(R.string.portrait), false)
  }

  // Note screen state, silencing notification speech if desired and speaking "Locked."

  private var screenOn = true
  def screenOn_? = screenOn
  def screenOff_? = !screenOn_?

  private var locked = screenOff_?

  ScreenOff += {
    if(screenOn) {
      TTS.speak(SpielService.context.getString(R.string.screenOff), false)
      screenOn = false
      locked = true
    }
  }

  ScreenOn += {
    if(!screenOn) {
      SpielService.enabled = true
      speakTime()
      screenOn = true
    }
  }

  Unlocked += {
    if(VERSION.SDK_INT < 16 && locked) {
      TTS.speak(SpielService.context.getString(R.string.unlocked), false)
      presenters.Presenter.nextShouldNotInterrupt
      locked = false
    }
  }

}
