forClass("android.app.Notification", {
  onNotificationStateChanged: function(e) {
    if(e.text.isEmpty())
      return true;
    speakNotification("SMS");
    nextShouldNotInterrupt();
    return false;
  }
});
