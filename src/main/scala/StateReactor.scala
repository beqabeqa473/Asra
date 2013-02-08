package info.spielproject.spiel

import java.text.SimpleDateFormat
import java.util.Date

import collection.JavaConversions._

import android.content.{BroadcastReceiver, ContentUris, Context, Intent, IntentFilter}
import android.net.Uri
import android.media._
import android.os.Build.VERSION
import android.os.Environment
import android.text.format.DateFormat
import android.util.Log

import events._
import scripting._

/**
 * Singleton which registers many callbacks initiated by <code>StateObserver</code>.
*/

object StateReactor {

  private[spiel] var ringerOn:Option[Boolean] = None

  private var service:SpielService = null

  /**
   * Initializes based on the specified <code>SpielService</code>, setting initial states.
  */

  def apply(svc:SpielService) {
    service = svc
  }

  // Check Bazaar for new scripts on app installation.

  ApplicationAdded += { intent:Intent =>
    BazaarProvider.checkRemoteScripts()
  }

  ApplicationRemoved += { intent:Intent =>
    val packageName = intent.getData().getSchemeSpecificPart
    val cursor = service.getContentResolver.query(Provider.uri, null, "pkg = ?", List(packageName).toArray, null)
    if(cursor.getCount > 0) {
      cursor.moveToFirst()
      while(!cursor.isAfterLast) {
        val script = new Script(service, cursor)
        script.uninstall()
        val scriptURI = ContentUris.withAppendedId(Provider.uri, script.id.get)
        service.getContentResolver.delete(scriptURI, null, null)
        cursor.moveToNext()
      }
    }
    cursor.close()
  }

  private def speakBatteryPercentage(ps:Option[String] = None) {
    utils.batteryPercentage(p => TTS.speak(p+"%" :: ps.map(_ :: Nil).getOrElse(Nil), false))
  }

  PowerConnected += speakBatteryPercentage(Some(service.getString(R.string.charging)))

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

  def ringerOn_? = ringerOn.getOrElse(true)
  def ringerOff_? = !ringerOn_?

  RingerModeChanged += { mode:RingerMode.Value =>
    val shouldSpeak = ringerOn != None
    ringerOn = Some(mode == RingerMode.Normal)
    if(shouldSpeak)
      mode match {
        case RingerMode.Normal => TTS.speak(service.getString(R.string.ringerOn), false)
        case RingerMode.Silent => TTS.speak(service.getString(R.string.ringerOff), false)
        case RingerMode.Vibrate => TTS.speak(service.getString(R.string.ringerVibrate), false)
      }
  }

  OrientationLandscape += {
    TTS.speak(service.getString(R.string.landscape), false)
  }

  OrientationPortrait += {
    TTS.speak(service.getString(R.string.portrait), false)
  }

  // Note screen state, silencing notification speech if desired and speaking "Locked."

  private var screenOn = true
  def screenOn_? = screenOn
  def screenOff_? = !screenOn_?

  private var locked = screenOff_?

  ScreenOff += {
    if(screenOn) {
      TTS.speak(service.getString(R.string.screenOff), false)
      screenOn = false
      locked = true
    }
  }

  ScreenOn += {
    if(!screenOn) {
      screenOn = true
      val sdf = new SimpleDateFormat(
        if(DateFormat.is24HourFormat(service))
          "H:mm"
        else
          "h:mm a"
      )
      TTS.speak(sdf.format(new Date(System.currentTimeMillis)), false)
    }
  }

  Unlocked += {
    if(locked) {
      TTS.speak(service.getString(R.string.unlocked), false)
      presenters.Presenter.nextShouldNotInterrupt
      locked = false
    }
  }

}
