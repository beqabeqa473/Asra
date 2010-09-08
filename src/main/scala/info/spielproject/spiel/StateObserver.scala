package info.spielproject.spiel

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.hardware.{Sensor, SensorEvent, SensorEventListener, SensorManager}
import android.media.AudioManager

object StateObserver {

  private var sensorManager:SensorManager = null

  def apply(service:SpielService) {

    def registerReceiver(r:(Context, Intent) => Unit, i:String) {
      service.registerReceiver(new BroadcastReceiver {
        override def onReceive(c:Context, i:Intent) = r(c, i)
      }, new IntentFilter(i))
    }

    registerReceiver({ (c, i) =>
      val extra = i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
      val mode = extra match {
        case AudioManager.RINGER_MODE_NORMAL => "normal"
        case AudioManager.RINGER_MODE_SILENT => "silent"
        case AudioManager.RINGER_MODE_VIBRATE => "vibrate"
      }
      ringerModeChanged(mode)
    }, AudioManager.RINGER_MODE_CHANGED_ACTION)

    registerReceiver((c, i) => screenOff , Intent.ACTION_SCREEN_OFF)

    registerReceiver((c, i) => screenOn, Intent.ACTION_SCREEN_ON)

    sensorManager = service.getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager]
    shakerEnabled = true
    proximitySensorEnabled = true

  }

  private var callAnsweredHandlers = List[() => Unit]()
  def onCallAnswered(h:() => Unit) = callAnsweredHandlers ::= h
  def callAnswered = callAnsweredHandlers.foreach { f => f() }

  private var callIdleHandlers = List[() => Unit]()
  def onCallIdle(h:() => Unit) = callIdleHandlers ::= h
  def callIdle = callIdleHandlers.foreach { f => f() }

  private var callRingingHandlers = List[(String) => Unit]()
  def onCallRinging(h:(String) => Unit) = callRingingHandlers ::= h
  def callRinging(number:String) = callRingingHandlers.foreach { f => f(number) }

  private var proximityFarHandlers = List[() => Unit]()
  def onProximityFar(h:() => Unit) = proximityFarHandlers ::= h
  def proximityFar() = proximityFarHandlers.foreach { f => f() }

  private var proximityNearHandlers = List[() => Unit]()
  def onProximityNear(h:() => Unit) = proximityNearHandlers ::= h
  def proximityNear() = proximityNearHandlers.foreach { f => f() }

  private var ringerModeChangedHandlers = List[(String) => Unit]()
  def onRingerModeChanged(h:(String) => Unit) = ringerModeChangedHandlers ::= h
  def ringerModeChanged(mode:String) = ringerModeChangedHandlers.foreach { f => f(mode) }

  private var screenOffHandlers = List[() => Unit]()
  def onScreenOff(h:() => Unit) = screenOffHandlers ::= h
  def screenOff = screenOffHandlers.foreach { f => f() }

  private var screenOnHandlers = List[() => Unit]()
  def onScreenOn(h:() => Unit) = screenOnHandlers ::= h
  def screenOn = screenOnHandlers.foreach { f => f() }

  private var shakingStartedHandlers = List[() => Unit]()
  def onShakingStarted(h:() => Unit) = shakingStartedHandlers ::= h
  def shakingStarted() = shakingStartedHandlers.foreach { f => f() }

  private var shakingStoppedHandlers = List[() => Unit]()
  def onShakingStopped(h:() => Unit) = shakingStoppedHandlers ::= h
  def shakingStopped() = shakingStoppedHandlers.foreach { f => f() }

  private var messageNoLongerWaitingHandlers = List[() => Unit]()
  def onMessageNoLongerWaiting(h:() => Unit) = messageNoLongerWaitingHandlers ::= h
  def messageNoLongerWaiting = messageNoLongerWaitingHandlers.foreach { f => f() }

  private var messageWaitingHandlers = List[() => Unit]()
  def onMessageWaiting(h:() => Unit) = messageWaitingHandlers ::= h
  def messageWaiting = messageWaitingHandlers.foreach { f => f() }

  private var _shakerEnabled = false

  def shakerEnabled = _shakerEnabled

  private val shakerThreshold = 2.6d*SensorManager.GRAVITY_EARTH*SensorManager.GRAVITY_EARTH

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
