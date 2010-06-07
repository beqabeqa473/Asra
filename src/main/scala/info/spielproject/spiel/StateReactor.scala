package info.spielproject.spiel

object StateReactor {

  private var callAnsweredHandlers = List[() => Unit]()
  def onCallAnswered(h:() => Unit) = callAnsweredHandlers ::= h
  def callAnswered = callAnsweredHandlers.foreach { f => f() }

  private var callIdleHandlers = List[() => Unit]()
  def onCallIdle(h:() => Unit) = callIdleHandlers ::= h
  def callIdle = callIdleHandlers.foreach { f => f() }

  private var callRingingHandlers = List[(String) => Unit]()
  def onCallRinging(h:(String) => Unit) = callRingingHandlers ::= h
  def callRinging(number:String) = callRingingHandlers.foreach { f => f(number) }

  private var screenOffHandlers = List[() => Unit]()
  def onScreenOff(h:() => Unit) = screenOffHandlers ::= h
  def screenOff = screenOffHandlers.foreach { f => f() }

  private var screenOnHandlers = List[() => Unit]()
  def onScreenOn(h:() => Unit) = screenOnHandlers ::= h
  def screenOn = screenOnHandlers.foreach { f => f() }


}
