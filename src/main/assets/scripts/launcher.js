forPackage("com.android.launcher");
forClass(".HandleView", {
  onViewFocused: function() {
    speak("drawer handle");
    return true;
  }
});
