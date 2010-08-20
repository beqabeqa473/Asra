package info.spielproject.spiel

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.media.AudioManager

object StateObserver {

  def apply(service:SpielService) {

    def registerReceiver(r:(Context, Intent) => Unit, intents:List[String]) {
      val f = new IntentFilter
      intents.foreach(f.addAction(_))
      service.registerReceiver(new BroadcastReceiver {
        override def onReceive(c:Context, i:Intent) = r(c, i)
      }, f)
    }

    registerReceiver((c, i) => ringerModeChanged, AudioManager.RINGER_MODE_CHANGED_ACTION :: Nil)

    registerReceiver((c, i) => screenOff , Intent.ACTION_SCREEN_OFF :: Nil)

    registerReceiver((c, i) => screenOn, Intent.ACTION_SCREEN_ON :: Nil)

    registerReceiver((c, i) => applicationAdded(i), Intent.ACTION_PACKAGE_ADDED :: Nil)

    registerReceiver((c, i) => applicationRemoved(i), Intent.ACTION_PACKAGE_REMOVED :: Nil)

  }

  private var applicationAddedHandlers = List[(Intent) => Unit]()
  def onApplicationAdded(h:(Intent) => Unit) = applicationAddedHandlers ::= h
  def applicationAdded(i:Intent) = applicationAddedHandlers.foreach { f => f(i) }

  private var applicationRemovedHandlers = List[(Intent) => Unit]()
  def onApplicationRemoved(h:(Intent) => Unit) = applicationRemovedHandlers ::= h
  def applicationRemoved(i:Intent) = applicationRemovedHandlers.foreach { f => f(i) }

  private var callAnsweredHandlers = List[() => Unit]()
  def onCallAnswered(h:() => Unit) = callAnsweredHandlers ::= h
  def callAnswered = callAnsweredHandlers.foreach { f => f() }

  private var callIdleHandlers = List[() => Unit]()
  def onCallIdle(h:() => Unit) = callIdleHandlers ::= h
  def callIdle = callIdleHandlers.foreach { f => f() }

  private var callRingingHandlers = List[(String) => Unit]()
  def onCallRinging(h:(String) => Unit) = callRingingHandlers ::= h
  def callRinging(number:String) = callRingingHandlers.foreach { f => f(number) }

  private var ringerModeChangedHandlers = List[() => Unit]()
  def onRingerModeChanged(h:() => Unit) = ringerModeChangedHandlers ::= h
  def ringerModeChanged = ringerModeChangedHandlers.foreach { f => f() }

  private[spiel] var _ringerOn = true
  def isRingerOn = _ringerOn
  def isRingerOff = !isRingerOn

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
