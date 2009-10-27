var pkg = null;

function forPkg(pk) {
  pkg = pk;
}

function forCls(cls, scr) {
  if(cls[0] == ".")
    cls = pkg+cls;
  key = [pkg, cls];
  var spiel = JavaImporter();
  /*spiel.importPackage(Packages.info.thewordnerd.spiel.scripting);
  spiel.importPackage(Packages.info.thewordnerd.spiel.presenters);
  for(e in scr) {
    h = {run: scr[k]};
    if(e == "onViewClicked")
      ViewClicked.handlers.put(k, new AccessibilityEventHandler(h));
    else if(e == "onViewFocused")
      ViewFocused.handlers.put(k, new AccessibilityEventHandler(h));
    else if(e == "onViewSelected")
      ViewSelected.handlers.put(k, new AccessibilityEventHandler(h));
    else if(e == "onViewTextChanged")
      ViewTextChanged.handlers.put(k, new AccessibilityEventHandler(h));
    else if(e == "onWindowStateChanged")
      WindowStateChanged.handlers.put(k, new AccessibilityEventHandler(h));
    else
      print("Invalid event: ",e);
  }*/
}
