forPkg("com.android.mms");
forCls("android.app.Notification", {
  onNotificationStateChanged: function() {
    TTS.speak("Incoming SMS", false);
    return false;
  }
});
