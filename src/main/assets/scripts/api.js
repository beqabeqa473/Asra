var __pkg__ = "";

function speak(str, shouldInterrupt) {
  if(shouldInterrupt != undefined)
    TTS.speak(str, shouldInterrupt);
  else
    TTS.speak(str, !Handler.nextShouldNotInterrupt());
}

function speakNotification(str) {
  TTS.speakNotification(str)
}

function nextShouldNotInterrupt() {
  Handler.nextShouldNotInterrupt();
}

function forClass(cls, scr) {
  if(cls[0] == ".")
    cls = __pkg__+cls;
  Scripter.registerHandlerFor(__pkg__, cls, scr);
}
