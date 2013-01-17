package info.spielproject.spiel

import actors.Actor.actor
import collection.mutable.ListBuffer
import collection.JavaConversions._

import android.bluetooth.{BluetoothAdapter, BluetoothClass, BluetoothDevice, BluetoothProfile}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.database.ContentObserver
import android.hardware.{Sensor, SensorEvent, SensorEventListener, SensorManager}
import android.media.AudioManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Handler
import android.provider.Settings.Secure
import android.util.Log

import events._

/**
 * Singleton holding a variety of callbacks triggered by <code>StateReactor</code>
 * when something changes. Generally, these take the following form:
 *
 * A list of callback functions.
 * Methods which add and remove a callback.
 * Methods which execute the given callbacks, run in response to some event.
*/

object StateObserver extends BluetoothProfile.ServiceListener {

  private lazy val sensorManager = service.getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager]

  private var service:SpielService = null

  /**
   * Initialize this <code>StateReactor</code> based off of the specified <code>SpielService</code>.
  */

  def apply(_service:SpielService) {

    service = _service

    val audioManager = service.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]

    def registerReceiver(r:(Context, Intent) => Unit, intents:List[String], dataScheme:Option[String] = None) {
      val f = new IntentFilter
      intents.foreach(f.addAction(_))
      dataScheme.foreach(f.addDataScheme(_))
      service.registerReceiver(r, f)
    }

    ScreenOff on Intent.ACTION_SCREEN_OFF

    ScreenOn on Intent.ACTION_SCREEN_ON

    Unlocked on Intent.ACTION_USER_PRESENT

    registerReceiver((c, i) => ApplicationAdded(i), Intent.ACTION_PACKAGE_ADDED :: Nil, Some("package"))

    registerReceiver((c, i) => ApplicationRemoved(i), Intent.ACTION_PACKAGE_REMOVED :: Nil, Some("package"))

    PowerConnected on Intent.ACTION_POWER_CONNECTED

    PowerDisconnected on Intent.ACTION_POWER_DISCONNECTED

    registerReceiver({ (c, i) =>
      val extra = i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
      val mode = extra match {
        case AudioManager.RINGER_MODE_SILENT => "silent"
        case AudioManager.RINGER_MODE_VIBRATE => "vibrate"
        case _ => "normal"
      }
      RingerModeChanged(mode)
    }, AudioManager.RINGER_MODE_CHANGED_ACTION :: Nil)

    Option(BluetoothAdapter.getDefaultAdapter).foreach(_.getProfileProxy(service, this, BluetoothProfile.A2DP))

    registerReceiver({(c, i) =>
      val device = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE).asInstanceOf[BluetoothDevice]
      device.getBluetoothClass.getDeviceClass match {
        case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET => actor {
          Thread.sleep(3000)
          val isSCO = a2dp.map(!_.getConnectedDevices.contains(device)).getOrElse(true)
          if(isSCO)
            BluetoothSCOHeadsetConnected()
        }
        case _ =>
      }
    }, android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED :: Nil)

    registerReceiver({(c, i) =>
      val device = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE).asInstanceOf[BluetoothDevice]
      device.getBluetoothClass.getDeviceClass match {
        case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET if(audioManager.isBluetoothScoOn()) => BluetoothSCOHeadsetDisconnected()
        case _ =>
      }
    }, android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED :: Nil)

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

  // Make enabling/disabling sensors look like setting boolean values.

  private var _shakerEnabled = false

  def shakerEnabled = _shakerEnabled

  // This may need tweaking, but this seems like a good value for an intentional shake.
  private val shakerThreshold = 1.2

  private val shaker = new SensorEventListener {

    def onSensorChanged(e:SensorEvent) {
      val netForce = math.sqrt(math.pow(e.values(0)/SensorManager.GRAVITY_EARTH, 2)
      +math.pow(e.values(1)/SensorManager.GRAVITY_EARTH, 2)
      +math.pow(e.values(2)/SensorManager.GRAVITY_EARTH, 2))
      if(netForce > shakerThreshold)
        StateObserver.isShaking()
      else
        StateObserver.isNotShaking()
    }

    def onAccuracyChanged(sensor:Sensor, accuracy:Int) { }

  }

  private var lastShakeAt = 0l
  private var shakingStartedAt = 0l

  private def isShaking() {
    val now = System.currentTimeMillis
    if(lastShakeAt == 0)
      shakingStartedAt = now
    lastShakeAt = now
    if(shakingStartedAt != 0 && now-shakingStartedAt >= 500) {
      shakingStartedAt = 0
      ShakingStarted()
    }
  }

  private val gap = 150

  private def isNotShaking() {
    val now = System.currentTimeMillis
    if(lastShakeAt != 0 && now-lastShakeAt > gap) {
      lastShakeAt = 0
      shakingStartedAt = 0
      ShakingStopped()
    }
  }  

  def shakerEnabled_=(v:Boolean) {
    if(v && !shakerEnabled)
      sensorManager.registerListener(shaker, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
    else if(!v && _shakerEnabled)
      sensorManager.unregisterListener(shaker)
    _shakerEnabled = v
  }

  private var maxProximitySensorRange = 0f

  private val proximityListener = new SensorEventListener {

    def onSensorChanged(e:SensorEvent) {
      val distance = e.values(0)
      if(distance < maxProximitySensorRange)
        ProximityNear()
      else
        ProximityFar()
      
    }

    def onAccuracyChanged(sensor:Sensor, accuracy:Int) { }

  }

  private var _proximitySensorEnabled = false
  def proximitySensorEnabled = _proximitySensorEnabled

  def proximitySensorEnabled_=(v:Boolean) {
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    if(sensor != null && v && !proximitySensorEnabled) {
      maxProximitySensorRange = sensor.getMaximumRange
      sensorManager.registerListener(proximityListener, sensor, SensorManager.SENSOR_DELAY_UI)
    } else if(!v && proximitySensorEnabled)
      sensorManager.unregisterListener(proximityListener)
    _proximitySensorEnabled = v
  }

  private var a2dp:Option[BluetoothProfile] = None

  def onServiceConnected(profile:Int, proxy:BluetoothProfile) {
    Log.d("spielcheck", "Connected: "+profile+", "+proxy)
    a2dp = Some(proxy)
  }

  def onServiceDisconnected(profile:Int) {
    Log.d("spielcheck", "Disconnected "+profile)
    a2dp = None
  }


}
