forClass("com.android.internal.policy.impl.LockScreen", {
  onViewFocused: function() {
    return true;
  }
});

forClass("com.android.internal.policy.impl.KeyguardViewManager$KeyguardViewHost", {
  onWindowStateChanged: function() {
    return true;
  }
});

forClass("com.android.internal.policy.impl.LockPatternKeyguardView", {
  onViewFocused: function() {
    return true;
  }
});
