package info.spielproject.spiel

import actors.Actor._

import android.bluetooth._
import android.content._
import android.media._
import android.util.Log

import events._

object Bluetooth extends BluetoothProfile.ServiceListener {

  private lazy val audioManager:AudioManager = SpielService.context.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]

  private var a2dp:Option[BluetoothProfile] = None

  def apply() {
    Option(BluetoothAdapter.getDefaultAdapter).foreach(_.getProfileProxy(SpielService.context, this, BluetoothProfile.A2DP))
    BluetoothConnected on android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED
    BluetoothDisconnected on android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED
  }

  BluetoothConnected += { i:Intent =>
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
  }

  BluetoothDisconnected += { i:Intent =>
    val device = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE).asInstanceOf[BluetoothDevice]
    device.getBluetoothClass.getDeviceClass match {
      case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET if(audioManager.isBluetoothScoOn()) => BluetoothSCOHeadsetDisconnected()
      case _ =>
    }
  }

  private var usingSco = false

  private class BTReceiver extends BroadcastReceiver {

    private var wasConnected = false

    private var musicVolume:Option[Int] = None
    private var voiceVolume:Option[Int] = None

    connect()

    def connect() {
      cleanupState()
      if(audioManager.isBluetoothScoAvailableOffCall) {
        //Log.d("spielcheck", "Connecting")
        val f = new IntentFilter
        f.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        SpielService.context.registerReceiver(this, f)
        audioManager.startBluetoothSco()
      }
    }

    private def cleanupState() {
      //Log.d("spielcheck", "Cleaning up state.")
      usingSco = false
      wasConnected = false
      if(!Telephony.inCall_?) audioManager.setMode(AudioManager.MODE_NORMAL)
      musicVolume.foreach(
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, _, 0)
      )
      musicVolume = None
      voiceVolume.foreach(
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, _, 0)
      )
      voiceVolume = None
    }

    private def cleanup() {
      //Log.d("spielcheck", "Cleaning up.")
      audioManager.stopBluetoothSco()
      cleanupState()
      try {
        SpielService.context.unregisterReceiver(this)
      } catch {
        case _ =>
      }
    }

    override def onReceive(c:Context, i:Intent) {
      val state = i.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
      //Log.d("spielcheck", "Got "+i+", "+state)
      if(state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
        //Log.d("spielcheck", "here1")
        usingSco = true
        musicVolume = Option(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        voiceVolume = Option(audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL))
        audioManager.setMode(AudioManager.MODE_IN_CALL)
        wasConnected = true
      } else if(state == AudioManager.SCO_AUDIO_STATE_ERROR) {
        //Log.d("spielcheck", "here2")
        cleanupState()
      } else if(usingSco && wasConnected && state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
        //Log.d("spielcheck", "here3")
        cleanupState()
        audioManager.startBluetoothSco()
      } else if(wasConnected) {
        //Log.d("spielcheck", "here4")
        cleanup()
      }
    }

    def disconnect() {
      //Log.d("spielcheck", "Disconnecting")
      audioManager.stopBluetoothSco()
      cleanup()
    }

  }

  private var btReceiver:Option[BTReceiver] = None

  private def startBluetoothSCO() {
    //Log.d("spielcheck", "startBluetoothSCO()")
    if(Preferences.useBluetoothSCO) {
      val r = new BTReceiver()
      r.connect()
      btReceiver = Some(r)
    }
  }

  private def stopBluetoothSCO() {
    btReceiver.foreach { r =>
      r.disconnect()
      btReceiver = None
    }
  }

  BluetoothSCOHeadsetConnected +=startBluetoothSCO()

  BluetoothSCOHeadsetDisconnected += stopBluetoothSCO()

  def reconnectSCOIfNecessary() {
    if(usingSco) {
      actor {
        // Wait until dialer sets audio mode so we can alter it for SCO reconnection.
        Thread.sleep(1000)
        btReceiver.foreach(_.connect())
      }
    }
  }

  def onServiceConnected(profile:Int, proxy:BluetoothProfile) {
    a2dp = Some(proxy)
  }

  def onServiceDisconnected(profile:Int) {
    a2dp = None
  }

}
