var pkg = null;

function forPkg(pk) {
  pkg = pk;
}

function forCls(cls, scr) {
  if(cls[0] == ".")
    cls = pkg+cls;
  for(e in scr) {
    if(e == "onViewClicked")
      spiel.ViewClicked.handlers.put(k, scr[e]);
    else if(e == "onViewFocused")
      ViewFocused.registerHandler(pkg, cls, scr[e]);
    else if(e == "onViewSelected")
      spiel.ViewSelected.handlers.put(k, scr[e]);
    else if(e == "onViewTextChanged")
      spiel.ViewTextChanged.handlers.put(k, scr[e]);
    else if(e == "onWindowStateChanged")
      spiel.WindowStateChanged.handlers.put(k, scr[e]);
    else
      print("Invalid event: ",e);
  }
}
