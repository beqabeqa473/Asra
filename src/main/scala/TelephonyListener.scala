package info.spielproject.spiel

import android.content.{ContentResolver, Context}
import android.net.Uri
import android.provider.{Contacts, ContactsContract}
import android.telephony.{PhoneStateListener, TelephonyManager}
import TelephonyManager._
import android.util.Log

/**
 * Singleton that listens to telephony state, calling relevant handlers.
*/

object TelephonyListener extends PhoneStateListener {

  private var context:Context = null

  /**
   * Initialize this <code>TelephonyListener</code> based on the specified <code>Context</code>.
  */

  def apply(c:Context) {
    context = c
    val manager = context.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
    manager.listen(this, PhoneStateListener.LISTEN_CALL_STATE|PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR)
  }

  // Resolve the number to a contact where possible.

  private def resolve(number:String) = {
    val uri= Uri.withAppendedPath(Contacts.Phones.CONTENT_FILTER_URL, Uri.encode(number))
    val cursor = context.getContentResolver.query(uri, null, null, null, null)
    var name = number
    if(cursor.getCount > 0) {
      while(cursor.moveToNext) {
        name = cursor.getString(cursor.getColumnIndex(Contacts.PeopleColumns.DISPLAY_NAME))
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
