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
  Scripter.registerHandlerFor(cls, scr);
}

// TODO: I don't know JavaScript well or don't know how it interfaces to 
// Java. This can probably be optimized.
function setString(name, arg1, arg2, arg3, arg4) {
  if(arg4 != undefined)
    return Scripter.setString(name, arg1, arg2, arg3, arg4);
  else if(arg3 != undefined)
    return Scripter.setString(name, arg1, arg2, arg3);
  else if(arg2 != undefined)
    return Scripter.setString(name, arg1, arg2);
  else
    Scripter.setString(name, arg1);
}

function getString(name) {
  return Scripter.getString(__pkg__, name);
}

function log(msg) {
  Scripter.log(__pkg__, msg)
}
