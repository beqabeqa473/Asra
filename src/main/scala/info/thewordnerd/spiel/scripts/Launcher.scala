package info.thewordnerd.spiel.scripts

import android.view.accessibility.AccessibilityEvent

object Launcher extends Script("com.android.launcher") {

  on(".HandleView") viewFocused((e:AccessibilityEvent) => {
    tts.speak("Drawer handle", false)
    true
  })

}
