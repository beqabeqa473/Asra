package info.spielproject.spiel.telephony

import android.content.{ContentResolver, Context}
import android.net.Uri
import android.os.Build
import android.provider.{Contacts, ContactsContract}
import android.telephony.{PhoneStateListener, TelephonyManager}
import android.util.Log

import info.spielproject.spiel._
import tts.TTS

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

  val resolve = try {
    new ResolverV5(service)
  } catch {
    case _ => new ResolverV1(service)
  }

  import TelephonyManager._

  private var repeaterID = ""

  override def onCallStateChanged(state:Int, number:String) = state match {
    case CALL_STATE_IDLE =>
      TTS.stopRepeatedSpeech(repeaterID)
      repeaterID = ""
    case CALL_STATE_RINGING =>
      repeaterID = TTS.speakEvery(3, resolve(number))
    case CALL_STATE_OFFHOOK =>
      TTS.stop
      TTS.stopRepeatedSpeech(repeaterID)
      repeaterID = ""
  }

}

object TelephonyListener {

  def apply(service:SpielService) {
    val manager = service.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
    manager.listen(new Listener(service), PhoneStateListener.LISTEN_CALL_STATE)
  }

}
