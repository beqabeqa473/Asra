package info.spielproject.spiel

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}

object StateObserver {

  def apply(service:SpielService) {

    service.registerReceiver(new BroadcastReceiver {
      override def onReceive(context:Context, intent:Intent) = StateReactor.screenOff
    }, new IntentFilter(Intent.ACTION_SCREEN_OFF))

    service.registerReceiver(new BroadcastReceiver {
      override def onReceive(context:Context, intent:Intent) = StateReactor.screenOn
    }, new IntentFilter(Intent.ACTION_SCREEN_ON))

  }

}
