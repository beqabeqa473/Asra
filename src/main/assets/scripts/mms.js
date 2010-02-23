forPackage("com.android.mms");
forClass("android.app.Notification", {
  onNotificationStateChanged: function() {
    speak("Incoming SMS");
    nextShouldNotInterrupt();
    return false;
  }
});
