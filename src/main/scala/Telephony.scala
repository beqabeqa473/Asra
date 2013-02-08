package info.spielproject.spiel

import android.content._
import android.net.Uri
import android.provider.ContactsContract
import android.telephony._
import TelephonyManager._
import android.util.Log

import events._

/**
 * Singleton that listens to telephony state, calling relevant handlers.
*/

object Telephony extends PhoneStateListener {

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
    Option(number).filter(!_.isEmpty).map { number =>
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
      if(name == number)
        name = PhoneNumberUtils.formatNumber(number)
      name
    }.getOrElse(context.getString(R.string.unknown))
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

  // Manage speaking of occasional voicemail notification.

  private var voicemailIndicator:Option[String] = None

  MessageWaiting += startVoicemailAlerts()

  def startVoicemailAlerts() {
    if(Preferences.voicemailAlerts)
      voicemailIndicator.getOrElse {
        voicemailIndicator = Some(TTS.speakEvery(180, context.getString(R.string.newVoicemail)))
      }
  }

  MessageNoLongerWaiting += stopVoicemailAlerts()

  def stopVoicemailAlerts() {
    voicemailIndicator.foreach { i => TTS.stopRepeatedSpeech(i) }
    voicemailIndicator = None
  }

  // Manage repeating of caller ID information, stopping when appropriate.

  var callerIDRepeaterID = ""

  CallRinging += { number:String =>
    if(Preferences.talkingCallerID)
      callerIDRepeaterID = TTS.speakEvery(3, number)
  }

  private var _inCall = false

  /**
   * Returns <code>true</code> if in call, <code>false</code> otherwise.
  */

  def inCall_? = _inCall

  CallAnswered += {
    _inCall = true
    TTS.stop
    TTS.stopRepeatedSpeech(callerIDRepeaterID)
    callerIDRepeaterID = ""
  }

  CallIdle += {
    _inCall = false
    TTS.stopRepeatedSpeech(callerIDRepeaterID)
    callerIDRepeaterID = ""
    Bluetooth.reconnectSCOIfNecessary()
  }

}
