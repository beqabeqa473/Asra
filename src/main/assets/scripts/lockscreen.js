forPackage("android");
forClass("com.android.internal.policy.impl.LockScreen", {
  onViewFocused: function() {
    return true;
  }
});

forClass("com.android.internal.policy.impl.KeyguardViewManager$KeyguardViewHost", {
  onWindowStateChanged: function() {
    speak("Locked, press menu to unlock.");
    return true;
  }
});
