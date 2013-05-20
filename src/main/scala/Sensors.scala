package info.spielproject.spiel

import android.content._
import android.hardware._
import android.os.Handler
import android.util.Log

import events._

object Sensors extends SensorEventListener {

  private lazy val sensorManager = service.getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager]

  private lazy val Accelerometer = Option(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER))

  private lazy val Proximity = Option(sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY))

  private var service:SpielService = null

  def apply(_service:SpielService) {
    service = _service
  }

  private var _shakerEnabled = false

  def shakerEnabled = _shakerEnabled

  // This may need tweaking, but this seems like a good value for an intentional shake.
  private val shakerThreshold = 1.2

  def onSensorChanged(e:SensorEvent) = e.sensor match {
    case Accelerometer =>
      val netForce = math.sqrt(math.pow(e.values(0)/SensorManager.GRAVITY_EARTH, 2)
      +math.pow(e.values(1)/SensorManager.GRAVITY_EARTH, 2)
      +math.pow(e.values(2)/SensorManager.GRAVITY_EARTH, 2))
      if(netForce > shakerThreshold)
        isShaking()
      else
        isNotShaking()
    case Proximity =>
      val distance = e.values(0)
      if(distance < maxProximitySensorRange)
        ProximityNear()
      else
        ProximityFar()
      
  }

  def onAccuracyChanged(sensor:Sensor, accuracy:Int) { }

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
    Accelerometer.foreach { a =>
      if(v && !shakerEnabled)
        sensorManager.registerListener(this, a, SensorManager.SENSOR_DELAY_UI)
      else if(!v && _shakerEnabled)
        sensorManager.unregisterListener(this, a)
      _shakerEnabled = v
    }
  }

  private var maxProximitySensorRange = 0f

  private var _proximitySensorEnabled = false
  def proximitySensorEnabled = _proximitySensorEnabled

  def proximitySensorEnabled_=(v:Boolean) {
    Proximity.foreach { p =>
      if(v && !proximitySensorEnabled) {
        maxProximitySensorRange = p.getMaximumRange
        sensorManager.registerListener(this, p, SensorManager.SENSOR_DELAY_UI)
      } else if(!v && proximitySensorEnabled)
        sensorManager.unregisterListener(this, p)
      _proximitySensorEnabled = v
    }
  }

}
