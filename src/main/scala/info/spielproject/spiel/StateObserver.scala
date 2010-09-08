package info.spielproject.spiel

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.media.AudioManager

object StateObserver {

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

  private var ringerModeChangedHandlers = List[(String) => Unit]()
  def onRingerModeChanged(h:(String) => Unit) = ringerModeChangedHandlers ::= h
  def ringerModeChanged(mode:String) = ringerModeChangedHandlers.foreach { f => f(mode) }

  private var screenOffHandlers = List[() => Unit]()
  def onScreenOff(h:() => Unit) = screenOffHandlers ::= h
  def screenOff = screenOffHandlers.foreach { f => f() }

  private var screenOnHandlers = List[() => Unit]()
  def onScreenOn(h:() => Unit) = screenOnHandlers ::= h
  def screenOn = screenOnHandlers.foreach { f => f() }

  private var messageNoLongerWaitingHandlers = List[() => Unit]()
  def onMessageNoLongerWaiting(h:() => Unit) = messageNoLongerWaitingHandlers ::= h
  def messageNoLongerWaiting = messageNoLongerWaitingHandlers.foreach { f => f() }

  private var messageWaitingHandlers = List[() => Unit]()
  def onMessageWaiting(h:() => Unit) = messageWaitingHandlers ::= h
  def messageWaiting = messageWaitingHandlers.foreach { f => f() }

}
