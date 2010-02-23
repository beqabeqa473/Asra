var pkg = "";

function forPackage(pk) {
  pkg = pk;
}

function speak(str, shouldInterrupt) {
  if(shouldInterrupt != undefined)
    Handler.speak(str, shouldInterrupt);
  else
    Handler.speak(str);
}

function nextShouldNotInterrupt() {
  Handler.nextShouldNotInterrupt();
}

function forClass(cls, scr) {
  if(cls[0] == ".")
    cls = pkg+cls;
  Scripter.registerHandlerFor(pkg, cls, scr);
}
