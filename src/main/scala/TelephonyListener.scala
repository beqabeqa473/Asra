package info.spielproject.spiel

import android.content.{ContentResolver, Context}
import android.net.Uri
import android.os.Build.VERSION
import android.provider.{Contacts, ContactsContract}
import android.telephony.{PhoneStateListener, TelephonyManager}
import TelephonyManager._
import android.util.Log

/**
 * Singleton that listens to telephony state, calling relevant handlers.
*/

object TelephonyListener extends PhoneStateListener {

  private var service:SpielService = null

  /**
   * Initialize this <code>TelephonyListener</code> based on the specified <code>SpielService</code>.
  */

  def apply(s:SpielService) {
    service = s
    val manager = service.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
    manager.listen(this, PhoneStateListener.LISTEN_CALL_STATE|PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR)
  }

  private def cursorFor(uri:Uri) = service.getContentResolver.query(uri, null, null, null, null)

  // Resolve the number to a contact where possible. Handles differing API versions.

  private def resolve(number:String) = {
    if(VERSION.SDK_INT >= 5)
      resolveV5(number)
    else {
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

  private def resolveV5(number:String) = {
    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    var name = ""
    val cursor = if(number != null && number != "") {
      name = number
      Option(cursorFor(uri))
    } else {
      name = "Unknown"
      None
    }
    cursor.foreach { c =>
      if(c.getCount > 0) {
        while(c.moveToNext) {
          name = c.getString(c.getColumnIndex(DisplayNameFix.DISPLAY_NAME))
        }
      }
    }
    name
  }

  override def onCallStateChanged(state:Int, number:String) = state match {
    case CALL_STATE_IDLE => StateObserver.callIdle
    case CALL_STATE_RINGING => StateObserver.callRinging(resolve(number))
    case CALL_STATE_OFFHOOK => StateObserver.callAnswered
  }

  override def onMessageWaitingIndicatorChanged(mwi:Boolean) = mwi match {
    case true => StateObserver.messageWaiting
    case false => StateObserver.messageNoLongerWaiting
  }

}
