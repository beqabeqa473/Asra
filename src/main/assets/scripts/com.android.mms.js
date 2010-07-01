forClass("android.app.Notification", {
  onNotificationStateChanged: function(e) {
    if(e.text.isEmpty())
      return true;
    speak("Incoming SMS");
    nextShouldNotInterrupt();
    return false;
  }
});
