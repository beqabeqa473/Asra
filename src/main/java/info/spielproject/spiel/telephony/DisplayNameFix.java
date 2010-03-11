package info.spielproject.spiel.telephony;

import android.provider.ContactsContract;

// Needed because Scala can't access protected static variables.
public class DisplayNameFix {
  public static String DISPLAY_NAME = ContactsContract.PhoneLookup.DISPLAY_NAME;
}
