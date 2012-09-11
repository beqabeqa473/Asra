package info.spielproject.spiel

import actors.Actor.actor
import collection.mutable.ListBuffer
import collection.JavaConversions._

import android.bluetooth.{BluetoothClass, BluetoothDevice}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.database.ContentObserver
import android.hardware.{Sensor, SensorEvent, SensorEventListener, SensorManager}
import android.media.AudioManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Handler
import android.provider.Settings.Secure
import android.util.Log

/**
 * Singleton holding a variety of callbacks triggered by <code>StateReactor</code>
 * when something changes. Generally, these take the following form:
 *
 * A list of callback functions.
 * Methods which add and remove a callback.
 * Methods which execute the given callbacks, run in response to some event.
*/

object StateObserver {

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
      service.registerReceiver(new BroadcastReceiver {
        override def onReceive(c:Context, i:Intent) = r(c, i)
      }, f)
    }

    registerReceiver((c, i) => screenOff, Intent.ACTION_SCREEN_OFF :: Nil)

    registerReceiver((c, i) => screenOn, Intent.ACTION_SCREEN_ON :: Nil)

    registerReceiver((c, i) => unlocked, Intent.ACTION_USER_PRESENT :: Nil)

    registerReceiver((c, i) => applicationAdded(i), Intent.ACTION_PACKAGE_ADDED :: Nil, Some("package"))

    registerReceiver((c, i) => applicationRemoved(i), Intent.ACTION_PACKAGE_REMOVED :: Nil, Some("package"))

    registerReceiver((c, i) => powerConnected(), Intent.ACTION_POWER_CONNECTED :: Nil)

    registerReceiver((c, i) => powerDisconnected(), Intent.ACTION_POWER_DISCONNECTED :: Nil)

    registerReceiver({ (c, i) =>
      val extra = i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
      val mode = extra match {
        case AudioManager.RINGER_MODE_SILENT => "silent"
        case AudioManager.RINGER_MODE_VIBRATE => "vibrate"
        case _ => "normal"
      }
      ringerModeChanged(mode)
    }, AudioManager.RINGER_MODE_CHANGED_ACTION :: Nil)

    registerReceiver({ (c, i) =>
      val bluetooth = i.getIntExtra("android.bluetooth.headset.extra.STATE", -1)
      val on = i.getIntExtra("state", 0) == 1 || bluetooth == 2
      if(bluetooth != -1) {
        if(on) {
          actor {
            Thread.sleep(10000)
            if(!audioManager.isBluetoothA2dpOn)
              bluetoothSCOHeadsetConnected()
          }
        } else {
          bluetoothSCOHeadsetDisconnected()
        }
      }
    }, Intent.ACTION_HEADSET_PLUG :: "android.bluetooth.headset.action.STATE_CHANGED" :: Nil)

    if(VERSION.SDK_INT > 8) {
      registerReceiver({(c, i) =>
        val device = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE).asInstanceOf[BluetoothDevice]
        device.getBluetoothClass.getDeviceClass match {
          case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET => actor {
            Thread.sleep(5000)
            if(!audioManager.isBluetoothA2dpOn)
              bluetoothSCOHeadsetConnected()
          }
          case _ =>
        }
      }, android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED :: Nil)

      registerReceiver({(c, i) =>
        val device = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE).asInstanceOf[BluetoothDevice]
        device.getBluetoothClass.getDeviceClass match {
          case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET if(audioManager.isBluetoothScoOn()) => bluetoothSCOHeadsetDisconnected()
          case _ =>
        }
      }, android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED :: Nil)
    }

    registerReceiver((c, i) => screenOff() , Intent.ACTION_SCREEN_OFF :: Nil)

    registerReceiver((c, i) => screenOn(), Intent.ACTION_SCREEN_ON :: Nil)

    service.getContentResolver.registerContentObserver(Secure.getUriFor(Secure.TTS_DEFAULT_SYNTH), false, new ContentObserver(new Handler) {
      override def onChange(bySelf:Boolean) = ttsEngineChanged()
    })

    service.getContentResolver.registerContentObserver(Secure.getUriFor(Secure.TTS_DEFAULT_RATE), false, new ContentObserver(new Handler) {
      override def onChange(bySelf:Boolean) = ttsRateChanged()
    })

    service.getContentResolver.registerContentObserver(Secure.getUriFor(Secure.TTS_DEFAULT_PITCH), false, new ContentObserver(new Handler) {
      override def onChange(bySelf:Boolean) = ttsPitchChanged()
    })

  }

  private val applicationAddedHandlers = ListBuffer[(Intent) => Unit]()

  def onApplicationAdded(h:(Intent) => Unit) = applicationAddedHandlers += h

  def applicationAdded(i:Intent) = {
    if(!i.getBooleanExtra(Intent.EXTRA_REPLACING, false))
      applicationAddedHandlers.foreach { f => f(i) }
  }

  private val applicationRemovedHandlers = ListBuffer[(Intent) => Unit]()

  def onApplicationRemoved(h:(Intent) => Unit) = applicationRemovedHandlers += h

  def applicationRemoved(i:Intent) = {
    if(!i.getBooleanExtra(Intent.EXTRA_REPLACING, false))
      applicationRemovedHandlers.foreach { f => f(i) }
  }

  private val powerConnectedHandlers = ListBuffer[() => Unit]()

  /**
   * Register handler to be run when power connects.
  */

  def onPowerConnected(h:() => Unit) =
    powerConnectedHandlers += h

  /**
   * Run registered handlers when power connects.
  */

  def powerConnected() = powerConnectedHandlers.foreach { f => f() }

  /**
   * Remove handler from being run when power is connected.
  */

  def removePowerConnected(h:() => Unit) = powerConnectedHandlers -= h

  private val powerDisconnectedHandlers = ListBuffer[() => Unit]()

  /**
   * Register handler to be run when power disconnects.
  */

  def onPowerDisconnected(h:() => Unit) =
    powerDisconnectedHandlers += h

  /**
   * Run registered handlers when power disconnects.
  */

  def powerDisconnected() = powerDisconnectedHandlers.foreach { f => f() }

  /**
   * Remove handler from being run when power is disconnected.
  */

  def removePowerDisconnected(h:() => Unit) = powerDisconnectedHandlers -= h

  private val bluetoothSCOHeadsetConnectedHandlers = ListBuffer[() => Unit]()

  /**
   * Registers handler to be run if a bluetooth SCO headset connects.
  */

  def onBluetoothSCOHeadsetConnected(h:() => Unit) =
    bluetoothSCOHeadsetConnectedHandlers += h

  /**
   * Run handlers when bluetooth SCO headset connects.
  */

  def bluetoothSCOHeadsetConnected() = bluetoothSCOHeadsetConnectedHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when bluetooth SCO headset connects.
  */

  def removeBluetoothSCOHeadsetConnected(h:() => Unit) =
    bluetoothSCOHeadsetConnectedHandlers -= h

  private val bluetoothSCOHeadsetDisconnectedHandlers = ListBuffer[() => Unit]()

  /**
   * Registers handler to be run if a bluetooth SCO headset disconnects.
  */

  def onBluetoothSCOHeadsetDisconnected(h:() => Unit) =
    bluetoothSCOHeadsetDisconnectedHandlers += h

  /**
   * Run handlers when bluetooth SCO headset disconnects.
  */

  def bluetoothSCOHeadsetDisconnected() = bluetoothSCOHeadsetDisconnectedHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when bluetooth SCO headset disconnects.
  */

  def removeBluetoothSCOHeadsetDisconnected(h:() => Unit) =
    bluetoothSCOHeadsetDisconnectedHandlers -= h

  private val callAnsweredHandlers = ListBuffer[() => Unit]()

  /**
   * Registers a handler to be run when a call is answered.
  */

  def onCallAnswered(h:() => Unit) =
    callAnsweredHandlers += h

  /**
   * Executes registered handlers when a call is answered.
  */

  def callAnswered() = callAnsweredHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when call is answered.
  */

  def removeOnCallAnswered(h:() => Unit) = callAnsweredHandlers -= h

  private val callIdleHandlers = ListBuffer[() => Unit]()

  /**
   * Registers a handler to be run when a call is idle.
  */

  def onCallIdle(h:() => Unit) =
    callIdleHandlers += h

  /**
   * Runs registered handlers when a call is idle.
  */

  def callIdle = callIdleHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when call is idle.
  */

  def removeOnCallIdle(h:() => Unit) = callIdleHandlers -= h

  private val callRingingHandlers = ListBuffer[(String) => Unit]()

  /**
   * Registers handler to be called when an incoming call is ringing.
   *
   * @param number
  */

  def onCallRinging(h:(String) => Unit) =
    callRingingHandlers += h

  /**
   * Runs registered handlers when call is ringing.
   *
   * @param number
  */

  def callRinging(number:String) = callRingingHandlers.foreach { f => f(number) }

  /**
   * Removes handler from list to be run when call is ringing.
  */

  def removeCallRinging(h:(String) => Unit) = callRingingHandlers -= h

  private val messageNoLongerWaitingHandlers = ListBuffer[() => Unit]()

  /**
   * Registers handler to be run when voicemail indicator is canceled.
  */

  def onMessageNoLongerWaiting(h:() => Unit) =
    messageNoLongerWaitingHandlers += h

  /**
   * Runs handlers when voicemail indicator is canceled.
  */

  def messageNoLongerWaiting() = messageNoLongerWaitingHandlers.foreach { f=> f() }

  /**
   * Removes handler from being run when voicemail indicator is canceled.
  */

  def removeMessageNoLongerWaiting(h:() => Unit) =
    messageNoLongerWaitingHandlers -= h

  private val messageWaitingHandlers = ListBuffer[() => Unit]()

  /**
   * Registers handler to be run when voicemail waiting indicator is active.
  */

  def onMessageWaiting(h:() => Unit) =
    messageWaitingHandlers += h

  /**
   * Runs registered handlers when voicemail indicator is active.
  */

  def messageWaiting() = messageWaitingHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when voicemail indicator is active.
  */

  def removeMessageWaiting(h:() => Unit) = messageWaitingHandlers -= h

  private val proximityFarHandlers = ListBuffer[() => Unit]()

  /**
   * Registers handler to be run when proximity sensor registers nothing 
   * nearby. Activates or deactivates sensor listener based on whether or 
   * not any handlers are registered.
  */

  def onProximityFar(h:() => Unit) = {
    proximityFarHandlers += h
    proximitySensorEnabled = true
    h
  }

  /**
   * Runs registered handlers when proximity sensor registers nothing nearby.
  */

  def proximityFar() = proximityFarHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when proximity sensor registers nothing 
   * nearby. Activates or deactivates sensor listener based on whether 
   * handlers are registered.
  */

  def removeProximityFar(h:() => Unit) = {
    proximityFarHandlers -= h
    if(proximityFarHandlers == Nil && proximityNearHandlers == Nil)
      proximitySensorEnabled = false
  }

  private val proximityNearHandlers = ListBuffer[() => Unit]()

  /**
   * Registers handler to be run if the proximity sensor registers anything 
   * nearby. Activates or deactivates sensor listener accordingly.
  */

  def onProximityNear(h:() => Unit) = {
    proximityNearHandlers += h
    proximitySensorEnabled = true
    h
  }

  /**
   * Run handlers when proximity sensor registers something nearby.
  */

  def proximityNear() = proximityNearHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when proximity sensor registers 
   * something nearby. Activates or deactivates sensor listener accordingly.
  */

  def removeProximityNear(h:() => Unit) = {
    proximityNearHandlers -= h
    if(proximityFarHandlers == Nil && proximityNearHandlers == Nil)
      proximitySensorEnabled = false
  }

  private val ringerModeChangedHandlers = ListBuffer[(String) => Unit]()

  /**
   * Registers handler to be run when ringer mode is changed.
   *
   @param mode Either "normal", "silent" or "vibrate"
  */

  def onRingerModeChanged(h:(String) => Unit) =
    ringerModeChangedHandlers += h

  /**
   * Run registered handlers when ringer mode changes.
   *
   * @param mode Either "normal", "silent" or "vibrate"
  */

  def ringerModeChanged(mode:String) = ringerModeChangedHandlers.foreach { f => f(mode) }

  /**
   * Remove handler from being run when ringer mode changes.
  */

  def removeRingerModeChanged(h:(String) => Unit) =
    ringerModeChangedHandlers -= h

  private val headsetStateChangedHandlers = ListBuffer[(Boolean, Boolean) => Unit]()

  /**
   * Registers handler to be run when headset state changes.
  */

  def onHeadsetStateChanged(h:(Boolean, Boolean) => Unit) =
    headsetStateChangedHandlers += h

  /**
   * Run registered handlers when headset state changes.
  */

  def headsetStateChanged(on:Boolean, bluetooth:Boolean) = headsetStateChangedHandlers.foreach { f => f(on, bluetooth) }

  /**
   * Remove handler from being run when headset state changes.
  */

  def removeHeadsetStateChanged(h:(Boolean, Boolean) => Unit) =
    headsetStateChangedHandlers -= h

  private val screenOffHandlers = ListBuffer[() => Unit]()

  /**
   * Register handler to be run when screen is turned off.
  */

  def onScreenOff(h:() => Unit) =
    screenOffHandlers += h

  /**
   * Run registered handlers when screen is turned off.
  */

  def screenOff() = screenOffHandlers.foreach { f => f() }

  /**
   * Remove handler from being run when screen turns off.
  */

  def removeScreenOff(h:() => Unit) = screenOffHandlers -= h

  private val screenOnHandlers = ListBuffer[() => Unit]()

  /**
   * Register handler to be run when screen turns on.
  */

  def onScreenOn(h:() => Unit) =
    screenOnHandlers += h

  /**
   * Run registered handlers when screen turns on.
  */

  def screenOn() = screenOnHandlers.foreach { f => f() }

  /**
   * Remove handler from being run when screen turns off.
  */

  def removeScreenOn(h:() => Unit) = screenOnHandlers -= h

  private val unlockedHandlers = ListBuffer[() => Unit]()

  /**
   * Register handler to be run when device unlocks.
  */

  def onUnlocked(h:() => Unit) =
    unlockedHandlers += h

  /**
   * Run registered handlers when device unlocks.
  */

  def unlocked() = unlockedHandlers.foreach { f => f() }

  /**
   * Remove handler from being run when device unlocks.
  */

  def removeUnlocked(h:() => Unit) = unlockedHandlers -= h

  private val shakingStartedHandlers = ListBuffer[() => Unit]()

  /**
   * Register a handler to be run when device starts shaking. Activates or 
   * deactivates sensor listener accordingly.
  */

  def onShakingStarted(h:() => Unit) = {
    shakingStartedHandlers += h
    shakerEnabled = true
    h
  }

  /**
   * Runs registered handlers when device starts shaking.
  */

  def shakingStarted() = shakingStartedHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when device starts shaking. Activates or 
   * deactivates sensor listener accordingly.
  */

  def removeShakingStarted(h:() => Unit) = {
    shakingStartedHandlers -= h
    if(shakingStartedHandlers == Nil && shakingStoppedHandlers == Nil)
      shakerEnabled = false
  }

  private val shakingStoppedHandlers = ListBuffer[() => Unit]()

  /**
   * Registers handler to be run when device stops shaking. Activates or 
   * deactivates sensor listener accordingly.
  */

  def onShakingStopped(h:() => Unit) = {
    shakingStoppedHandlers += h
    shakerEnabled = true
    h
  }

  /**
   * Runs registered handlers when device stops shaking.
  */

  def shakingStopped() = shakingStoppedHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when device stops shaking. Activates or 
   * deactivates sensor listener accordingly.
  */

  def removeShakingStopped(h:() => Unit) = {
    shakingStoppedHandlers -= h
    if(shakingStartedHandlers == Nil && shakingStoppedHandlers == Nil)
      shakerEnabled = false
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
      shakingStarted()
    }
  }

  private val gap = 150

  private def isNotShaking() {
    val now = System.currentTimeMillis
    if(lastShakeAt != 0 && now-lastShakeAt > gap) {
      lastShakeAt = 0
      shakingStartedAt = 0
      shakingStopped()
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
        proximityNear()
      else
        proximityFar()
      
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

  private var ttsEngineChangedHandlers = List[() => Unit]()

  /**
   * Registers handler to be run if the TTS engine changes.
  */

  def onTTSEngineChanged(h:() => Unit) = {
    ttsEngineChangedHandlers ::= h
    h
  }

  /**
   * Run handlers when TTS engine changes.
  */

  def ttsEngineChanged() = ttsEngineChangedHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when TTS engine changes.
  */

  def removeTTSEngineChanged(h:() => Unit) = {
    ttsEngineChangedHandlers = ttsEngineChangedHandlers.filterNot(_ == h)
  }

  private var ttsRateChangedHandlers = List[() => Unit]()

  /**
   * Registers handler to be run if the TTS rate changes.
  */

  def onTTSRateChanged(h:() => Unit) = {
    ttsRateChangedHandlers ::= h
    h
  }

  /**
   * Run handlers when TTS rate changes.
  */

  def ttsRateChanged() = ttsRateChangedHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when TTS rate changes.
  */

  def removeTTSRateChanged(h:() => Unit) = {
    ttsRateChangedHandlers = ttsRateChangedHandlers.filterNot(_ == h)
  }

  private var ttsPitchChangedHandlers = List[() => Unit]()

  /**
   * Registers handler to be run if the TTS pitch changes.
  */

  def onTTSPitchChanged(h:() => Unit) = {
    ttsPitchChangedHandlers ::= h
    h
  }

  /**
   * Run handlers when TTS pitch changes.
  */

  def ttsPitchChanged() = ttsPitchChangedHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when TTS pitch changes.
  */

  def removeTTSPitchChanged(h:() => Unit) = {
    ttsPitchChangedHandlers = ttsPitchChangedHandlers.filterNot(_ == h)
  }

}
