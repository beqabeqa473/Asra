package info.spielproject.spiel.telephony

import android.content.{ContentResolver, Context}
import android.net.Uri
import android.os.Build.VERSION
import android.provider.{Contacts, ContactsContract}
import android.telephony.{PhoneStateListener, TelephonyManager}
import android.util.Log

import info.spielproject.spiel._

private abstract class Resolver(service:SpielService) {

  def apply(number:String):String

  def cursorFor(uri:Uri) = service.getContentResolver.query(uri, null, null, null, null)

}

private class ResolverV1(service:SpielService) extends Resolver(service) {
  def apply(number:String) = {
    val uri= Uri.withAppendedPath(Contacts.Phones.CONTENT_FILTER_URL, Uri.encode(number))
    val cursor = cursorFor(uri)
    var name = number
    if(cursor.getCount > 0) {
      while(cursor.moveToNext) {
        name = cursor.getString(cursor.getColumnIndex(Contacts.PeopleColumns.DISPLAY_NAME))
      }
    }
    name
  }
}

private class ResolverV5(service:SpielService) extends Resolver(service) {
  def apply(number:String) = {
    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val cursor = cursorFor(uri)
    var name = number
    if(cursor.getCount > 0) {
      while(cursor.moveToNext) {
        name = cursor.getString(cursor.getColumnIndex(DisplayNameFix.DISPLAY_NAME))
      }
    }
    name
  }
}

private class Listener(service:SpielService) extends PhoneStateListener {

  val resolve = if(Integer.parseInt(VERSION.SDK) >= 5)
    new ResolverV5(service)
  else
    new ResolverV1(service)

  import TelephonyManager._

  override def onCallStateChanged(state:Int, number:String) = state match {
    case CALL_STATE_IDLE => StateReactor.callIdle
    case CALL_STATE_RINGING => StateReactor.callRinging(resolve(number))
    case CALL_STATE_OFFHOOK => StateReactor.callAnswered
  }

  override def onMessageWaitingIndicatorChanged(mwi:Boolean) = mwi match {
    case true => StateReactor.messageWaiting
    case false => 
  }

}

object TelephonyListener {

  def apply(service:SpielService) {
    val manager = service.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
    manager.listen(new Listener(service), PhoneStateListener.LISTEN_CALL_STATE|PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR)

      var repeaterID = ""

      StateReactor.onCallRinging { number =>
        if(Preferences.talkingCallerID_?)
          repeaterID = TTS.speakEvery(3, number)
      }

      StateReactor.onCallAnswered { () =>
        TTS.stop
        TTS.stopRepeatedSpeech(repeaterID)
        repeaterID = ""
      }

      StateReactor.onCallIdle { () =>
        TTS.stopRepeatedSpeech(repeaterID)
        repeaterID = ""
      }
  }

}
