forClass(".HandleView", {

  onViewClicked: function() {
    speak("expanded");
    return true;
  },

  onViewFocused: function() {
    speak("drawer handle");
    return true;
  }

});
