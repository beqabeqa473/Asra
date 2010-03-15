var pkg = "";

function forPackage(pk) {
  pkg = pk;
}

function speak(str, shouldInterrupt) {
  if(shouldInterrupt != undefined)
    TTS.speak(str, shouldInterrupt);
  else
    TTS.speak(str, !Handler.nextShouldNotInterrupt());
}

function nextShouldNotInterrupt() {
  Handler.nextShouldNotInterrupt();
}

function forClass(cls, scr) {
  if(cls[0] == ".")
    cls = pkg+cls;
  Scripter.registerHandlerFor(pkg, cls, scr);
}
