package info.spielproject.spiel.telephony

import android.content.{ContentResolver, Context}
import android.net.Uri
import android.os.Build
import android.provider.{Contacts, ContactsContract}
import android.telephony.{PhoneStateListener, TelephonyManager}
import android.util.Log

import info.spielproject.spiel._
import tts.TTS

private class Listener(service:SpielService) extends PhoneStateListener {

  import TelephonyManager._

  private var repeaterID = ""

  override def onCallStateChanged(state:Int, number:String) = state match {
    case CALL_STATE_IDLE =>
      TTS.stopRepeatedSpeech(repeaterID)
      repeaterID = ""
    case CALL_STATE_RINGING =>
      val sdkVersion = Integer.parseInt(Build.VERSION.SDK)
      val uri = if(sdkVersion >= Build.VERSION_CODES.ECLAIR)
        Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
      else
        Uri.withAppendedPath(Contacts.Phones.CONTENT_FILTER_URL, Uri.encode(number))
      var name = number
      val cursor = service.getContentResolver.query(uri, null, null, null, null)
      if(cursor.getCount > 0) {
        while(cursor.moveToNext) {
          val column = if(sdkVersion >= Build.VERSION_CODES.ECLAIR)
            DisplayNameFix.DISPLAY_NAME
          else
            Contacts.PeopleColumns.DISPLAY_NAME
          name = cursor.getString(cursor.getColumnIndex(column))
        }
      }
      repeaterID = TTS.speakEvery(3, name)
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
