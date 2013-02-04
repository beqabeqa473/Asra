package info.spielproject.spiel

import android.content.{ContentResolver, Context}
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.{PhoneStateListener, TelephonyManager}
import TelephonyManager._
import android.util.Log

import events._

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
    if(number != null && number != "") {
      val uri= Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
      val cursor = context.getContentResolver.query(uri, null, null, null, null)
      var name = number
      if(cursor.getCount > 0) {
        while(cursor.moveToNext) {
          // TODO: Using "display_name" rather than the actual value because 
          // Scala doesn't seem to like class constants, and the interface 
          // that declares this one is marked protected.
          name = cursor.getString(cursor.getColumnIndex("display_name"))
        }
      }
      name
    } else
      context.getString(R.string.unknown)
  }

  override def onCallStateChanged(state:Int, number:String) = state match {
    case CALL_STATE_IDLE => CallIdle()
    case CALL_STATE_RINGING => CallRinging(resolve(number))
    case CALL_STATE_OFFHOOK => CallAnswered()
  }

  override def onMessageWaitingIndicatorChanged(mwi:Boolean) = mwi match {
    case true => MessageWaiting()
    case false => MessageNoLongerWaiting()
  }

}
