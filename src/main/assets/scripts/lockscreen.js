forPkg("android");
forCls("com.android.internal.policy.impl.LockScreen", {
  onViewFocused: function() {
    return true;
  }
});

forCls("com.android.internal.policy.impl.KeyguardViewManager$KeyguardViewHost", {
  onWindowStateChanged: function() {
    TTS.speak("Locked, press menu to unlock.", true);
    return true;
  }
});
