package info.spielproject.spiel

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.hardware.{Sensor, SensorEvent, SensorEventListener, SensorManager}
import android.media.AudioManager
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

  private var sensorManager:SensorManager = null

  /**
   * Initialize this <code>StateReactor</code> based off of the specified <code>SpielService</code>.
  */

  def apply(service:SpielService) {

    def registerReceiver(r:(Context, Intent) => Unit, intents:List[String], dataScheme:Option[String] = None) {
      val f = new IntentFilter
      intents.foreach(f.addAction(_))
      dataScheme.foreach(f.addDataScheme(_))
      service.registerReceiver(new BroadcastReceiver {
        override def onReceive(c:Context, i:Intent) = r(c, i)
      }, f)
    }

    registerReceiver((c, i) => screenOff , Intent.ACTION_SCREEN_OFF :: Nil)

    registerReceiver((c, i) => screenOn, Intent.ACTION_SCREEN_ON :: Nil)

    registerReceiver((c, i) => applicationAdded(i), Intent.ACTION_PACKAGE_ADDED :: Nil, Some("package"))

    registerReceiver((c, i) => applicationRemoved(i), Intent.ACTION_PACKAGE_REMOVED :: Nil, Some("package"))

    registerReceiver({ (c, i) =>
      val extra = i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
      val mode = extra match {
        case AudioManager.RINGER_MODE_NORMAL => "normal"
        case AudioManager.RINGER_MODE_SILENT => "silent"
        case AudioManager.RINGER_MODE_VIBRATE => "vibrate"
      }
      ringerModeChanged(mode)
    }, AudioManager.RINGER_MODE_CHANGED_ACTION :: Nil)

    registerReceiver((c, i) => screenOff() , Intent.ACTION_SCREEN_OFF :: Nil)

    registerReceiver((c, i) => screenOn(), Intent.ACTION_SCREEN_ON :: Nil)

    sensorManager = service.getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager]
  }

  private var applicationAddedHandlers = List[(Intent) => Unit]()

  def onApplicationAdded(h:(Intent) => Unit) = applicationAddedHandlers ::= h

  def applicationAdded(i:Intent) = applicationAddedHandlers.foreach { f => f(i) }

  private var applicationRemovedHandlers = List[(Intent) => Unit]()

  def onApplicationRemoved(h:(Intent) => Unit) = applicationRemovedHandlers ::= h

  def applicationRemoved(i:Intent) = applicationRemovedHandlers.foreach { f => f(i) }

  private var callAnsweredHandlers = List[() => Unit]()

  /**
   * Registers a handler to be run when a call is answered.
  */

  def onCallAnswered(h:() => Unit) = {
    callAnsweredHandlers ::= h
    h
  }

  /**
   * Executes registered handlers when a call is answered.
  */

  def callAnswered() = callAnsweredHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when call is answered.
  */

  def removeOnCallAnswered(h:() => Unit) = callAnsweredHandlers = callAnsweredHandlers.filterNot(_ == h)

  private var callIdleHandlers = List[() => Unit]()

  /**
   * Registers a handler to be run when a call is idle.
  */

  def onCallIdle(h:() => Unit) = {
    callIdleHandlers ::= h
    h
  }

  /**
   * Runs registered handlers when a call is idle.
  */

  def callIdle = callIdleHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when call is idle.
  */

  def removeOnCallIdle(h:() => Unit) = callIdleHandlers = callIdleHandlers.filterNot(_ == h)

  private var callRingingHandlers = List[(String) => Unit]()

  /**
   * Registers handler to be called when an incoming call is ringing.
   *
   * @param number
  */

  def onCallRinging(h:(String) => Unit) = {
    callRingingHandlers ::= h
    h
  }

  /**
   * Runs registered handlers when call is ringing.
   *
   * @param number
  */

  def callRinging(number:String) = callRingingHandlers.foreach { f => f(number) }

  /**
   * Removes handler from list to be run when call is ringing.
  */

  def removeCallRinging(h:(String) => Unit) = callRingingHandlers = callRingingHandlers.filterNot(_ == h)

  private var messageNoLongerWaitingHandlers = List[() => Unit]()

  /**
   * Registers handler to be run when voicemail indicator is canceled.
  */

  def onMessageNoLongerWaiting(h:() => Unit) = {
    messageNoLongerWaitingHandlers ::= h
    h
  }

  /**
   * Runs handlers when voicemail indicator is canceled.
  */

  def messageNoLongerWaiting() = messageNoLongerWaitingHandlers.foreach { f=> f() }

  /**
   * Removes handler from being run when voicemail indicator is canceled.
  */

  def removeMessageNoLongerWaiting(h:() => Unit) = messageNoLongerWaitingHandlers = messageNoLongerWaitingHandlers.filterNot(_ == h)

  private var messageWaitingHandlers = List[() => Unit]()

  /**
   * Registers handler to be run when voicemail waiting indicator is active.
  */

  def onMessageWaiting(h:() => Unit) = {
    messageWaitingHandlers ::= h
  }

  /**
   * Runs registered handlers when voicemail indicator is active.
  */

  def messageWaiting() = messageWaitingHandlers.foreach { f => f() }

  /**
   * Removes handler from being run when voicemail indicator is active.
  */

  def removeMessageWaiting(h:() => Unit) = messageWaitingHandlers = messageWaitingHandlers.filterNot(_ == h)

  private var proximityFarHandlers = List[() => Unit]()

  /**
   * Registers handler to be run when proximity sensor registers nothing 
   * nearby. Activates or deactivates sensor listener based on whether or 
   * not any handlers are registered.
  */

  def onProximityFar(h:() => Unit) = {
    proximityFarHandlers ::= h
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
    proximityFarHandlers = proximityFarHandlers.filterNot(_ == h)
    if(proximityFarHandlers == Nil && proximityNearHandlers == Nil)
      proximitySensorEnabled = false
  }

  private var proximityNearHandlers = List[() => Unit]()

  /**
   * Registers handler to be run if the proximity sensor registers anything 
   * nearby. Activates or deactivates sensor listener accordingly.
  */

  def onProximityNear(h:() => Unit) = {
    proximityNearHandlers ::= h
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
    proximityNearHandlers = proximityNearHandlers.filterNot(_ == h)
    if(proximityFarHandlers == Nil && proximityNearHandlers == Nil)
      proximitySensorEnabled = false
  }

  private var ringerModeChangedHandlers = List[(String) => Unit]()

  /**
   * Registers handler to be run when ringer mode is changed.
   *
   @param mode Either "normal", "silent" or "vibrate"
  */

  def onRingerModeChanged(h:(String) => Unit) = {
    ringerModeChangedHandlers ::= h
    h
  }

  /**
   * Run registered handlers when ringer mode changes.
   *
   * @param mode Either "normal", "silent" or "vibrate"
  */

  def ringerModeChanged(mode:String) = ringerModeChangedHandlers.foreach { f => f(mode) }

  /**
   * Remove handler from being run when ringer mode changes.
  */

  def removeRingerModeChanged(h:(String) => Unit) = ringerModeChangedHandlers = ringerModeChangedHandlers.filterNot(_ == h)

  private var screenOffHandlers = List[() => Unit]()

  /**
   * Register handler to be run when screen is turned off.
  */

  def onScreenOff(h:() => Unit) = {
    screenOffHandlers ::= h
    h
  }

  /**
   * Run registered handlers when screen is turned off.
  */

  def screenOff() = screenOffHandlers.foreach { f => f() }

  /**
   * Remove handler from being run when screen turns off.
  */

  def removeScreenOff(h:() => Unit) = screenOffHandlers = screenOffHandlers.filterNot(_ == h)

  private var screenOnHandlers = List[() => Unit]()

  /**
   * Register handler to be run when screen turns on.
  */

  def onScreenOn(h:() => Unit) = {
    screenOnHandlers ::= h
    h
  }

  /**
   * Run registered handlers when screen turns on.
  */

  def screenOn() = screenOnHandlers.foreach { f => f() }

  /**
   * Remove handler from being run when screen turns off.
  */

  def removeScreenOn(h:() => Unit) = screenOnHandlers = screenOnHandlers.filterNot(_ == h)

  private var shakingStartedHandlers = List[() => Unit]()

  /**
   * Register a handler to be run when device starts shaking. Activates or 
   * deactivates sensor listener accordingly.
  */

  def onShakingStarted(h:() => Unit) = {
    shakingStartedHandlers ::= h
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
    shakingStartedHandlers = shakingStartedHandlers.filterNot(_ == h)
    if(shakingStartedHandlers == Nil && shakingStoppedHandlers == Nil)
      shakerEnabled = false
  }

  private var shakingStoppedHandlers = List[() => Unit]()

  /**
   * Registers handler to be run when device stops shaking. Activates or 
   * deactivates sensor listener accordingly.
  */

  def onShakingStopped(h:() => Unit) = {
    shakingStoppedHandlers ::= h
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
    shakingStoppedHandlers = shakingStoppedHandlers.filterNot(_ == h)
    if(shakingStartedHandlers == Nil && shakingStoppedHandlers == Nil)
      shakerEnabled = false
  }

  // Make enabling/disabling sensors look like setting boolean values.

  private var _shakerEnabled = false

  def shakerEnabled = _shakerEnabled

  // This may need tweaking, but this seems like a good value for an intentional shake.
  private val shakerThreshold = 3d*SensorManager.GRAVITY_EARTH*SensorManager.GRAVITY_EARTH

  private val shaker = new SensorEventListener {

    def onSensorChanged(e:SensorEvent) {
      val netForce = e.values(0)*e.values(0)+e.values(1)*e.values(1)+e.values(2)*e.values(2)
      if(shakerThreshold < netForce)
        StateObserver.isShaking()
      else
        StateObserver.isNotShaking()
    }

    def onAccuracyChanged(sensor:Sensor, accuracy:Int) { }

  }

  private var lastShakeAt = 0l

  private def isShaking() {
    val now = System.currentTimeMillis
    if(lastShakeAt == 0)
      shakingStarted()
    lastShakeAt = now
  }

  private val gap = 500

  private def isNotShaking() {
    val now = System.currentTimeMillis
    if(lastShakeAt != 0 && now-lastShakeAt > gap) {
      lastShakeAt = 0
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

}
