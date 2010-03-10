var Log = Packages.android.util.Log;

forPackage("com.android.contacts");
forClass("android.widget.EditText", {

  // Fix issue where inserted hyphens interrupt speech.
  onViewTextChanged: function(e) {
    if(this.lastText == e.text.get(0).toString().replace("-", ""))
      return true;
    this.lastText = e.text.get(0).toString().replace("-", "");
    return false;
  }

});

