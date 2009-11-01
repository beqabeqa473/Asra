forPkg("com.android.launcher");
forCls(".HandleView", {
  onViewFocused: function() {
    TTS.speak("drawer handle", false);
    return true;
  }
});
