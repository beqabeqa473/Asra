var pkg = null;

function forPkg(pk) {
  pkg = pk;
}

function forCls(cls, scr) {
  if(cls[0] == ".")
    cls = pkg+cls;
  for(e in scr) {
    if(e == "onNotificationStateChanged")
      NotificationStateChanged.registerHandler(pkg, cls, scr[e]);
    else if(e == "onViewClicked")
      ViewClicked.registerHandler(pkg, cls, scr[e]);
    else if(e == "onViewFocused")
      ViewFocused.registerHandler(pkg, cls, scr[e]);
    else if(e == "onViewSelected")
      ViewSelected.registerHandler(pkg, cls, scr[e]);
    else if(e == "onViewTextChanged")
      ViewTextChanged.registerHandler(pkg, cls, scr[e]);
    else if(e == "onWindowStateChanged")
      WindowStateChanged.registerHandler(pkg, cls, scr[e]);
    else
      print("Invalid event: ",e);
  }
}
