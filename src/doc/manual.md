Scripting

# Scripting

The greatest challenge in writing a manual about Spiel's scripting system is that there isn't much that is truly unique to document. The language is JavaScript with [convenience features for accessing Java](https://developer.mozilla.org/en/Scripting_Java). The accessibility API used is mostly Android's [AccessibilityEvent](http://developer.android.com/reference/android/view/accessibility/AccessibilityEvent.html), and Spiel's only contribution is a bit of glue to attach functions to specific events. As such, learning how to write scripts isn't a matter of learning a few functions, but rather of understanding the API with which Spiel (and ultimately your scripts) interface.

Android's accessibility APIs are still under development and are likely to change in the future. Also, there is plenty of documentation describing the JavaScript language and the fundamentals of the Rhino engine. Rather than duplicating this documentation and keeping up-to-date with changes, this manual attempts instead to link to the documentation that already exists while providing a functional introduction to the task of writing scripts for Spiel.

## A Whirlwind Tour of Android Accessibility

The intricacies of the Android accessibility API are described exhaustively elsewhere, and should be consulted for further details. Below are a few fundamental points to know when exploring the external documentation in order to script Spiel.

### Events

Changes in the Android interface are described by a stream of events. When focus changes, an event is sent. When text is inserted into or deleted from a text field, an event is generated. When an incoming SMS arrives, a button is clicked or a selection is scrolled, events are generated.

Designing an Android accessibility solution involves intercepting these events and presenting them accordingly. This is precisely what Spiel does, having hard-coded handlers for a number of fundamental events. It is also what your scripts will do.

Each event has a number of properties associated with it. Not all events use every single property, and just which properties you can expect to have meaningful values depends on the event type received.

When reading the Android accessibility documentation, it isn't necessary to memorize exact event names, as these are made somewhat friendlier in Spiel scripts. Simply be aware of the following:

 * Changes in the UI generate a stream of events which are intercepted by access technologies and, quite possibly, by Spiel scripts.
 * Events have different properties based on their type.

### Classes and Packages: Widgets and Apps

Two properties common to all event types are the _className_ and _packageName_. Both are crucial to creating scripts.

Widgets, or "views" in Android parlance, are created in classes. When an event originates from a class of a specified type, the class should be used to determine what type of feedback to provide for the given event.

For instance, the class _android.view.Button_ describes a button. If you receive a focus event with this as the class name, then it might make sense to speak the button's associated text or content description followed by the word "button". This is, in fact, Spiel's own hard-coded behavior for any events originating from _android.view.Button_.

Android apps are contained in a single package. An event's _packageName_ property indicates the package in which the view which generated it was created.

The _packageName_ property is useful in that it allows for scripts to provide different behaviors for views in specific applications that differ from the default. Say, for instance, that Acme Computer, Inc. releases an Android app with lots of unlabeled buttons, placing it in the package _com.acme.android_. By intercepting _TYPE_VIEW_FOCUSED_ events with a _className_ of _android.widget.Button_ and a _packageName_ of _com.acme.android_, scripts can provide spoken feedback for only these buttons, not being called for buttons in any packages other than _com.acme.android_.

There are a variety of event types and properties. Consult the previously-linked AccessibilityEvent documentation for a more indepth discussion of these.

## Spiel Scripting Concepts

Now that Android accessibility has been briefly explained, we'll move on to how these concepts are integrated into Spiel's scripting system. Spiel takes various measures to simplify the accessibility API for end users, and to secure it against malicious use or interference.

### Division of Labor

For purposes of simplicity and security, scripts are divided on package boundaries. That is, individual scripts can only handle events from a specific package. Since apps are contained in single packages, this makes it easier to limit the reach of individual scripts, as well as to identify which script is introducing any given behavior.

Scripts indicate what package they handle via their filename followed by a _.js_ extension. For instance, a script that handles events in the _com.acme.android_ package would be named _com.acme.android.js_.

### Handlers

As previously stated, all events have a class and package name with which they are associated. Events are associated with handlers, which run specific code when various criteria are matched. To attach a handler to events of a given class, the _forClass_ method is called with the class name as its first argument and a Javascript object as its second.

For instance, if the app in the package _com.acme.android_ contains a number of unlabeled buttons, then you may wish to write a script which speaks various text when said buttons receive focus. We'll discuss the contents of such a script more indepth as this manual progresses, but such a script might look like this:

<pre>
forClass("android.widget.Button" {
  ...
});
</pre>

Placing this code in the file _com.acme.android.js_ causes it to be triggered whenever any buttons in the _com.acme.android_ app are encountered.

### Events

It is also necessary to specify which events from a given class and package are of interest before a script is functional. Android accessibility currently defines several event types which the API generates. Please consult its own documentation to learn when these events are generated and which properties are associated with each. As previously stated, however, the names Spiel associates with these events are slightly more human-friendly. The following event types are supported, and should map quite obviously to their Android counterparts:

 * onNotificationStateChanged
 * onViewClicked
 * onViewFocused
 * onViewLongClicked
 * onViewSelected
 * onViewTextChanged
 * onWindowStateChanged

Each of these events is the name of a Javascript function which accepts the event as its argument. Since arguments are optional in Javascript, if the event is not needed in the script which handles it, then the function can be declared with an empty argument list. These functions are declared in the Javascript object passed to the _forClass_ function demonstrated above.

Multiple handlers can be associated with a single package and/or class. As such, these functions need to indicate if they provide all handling necessary for a given event, or if processing should continue further. They do so by returning _true_ to indicate that handling should stop, or _false_ if it should continue.

How handlers are matched is a somewhat complex subject. As a script developer, all you need know is that the handlers created in your scripts will be matched before the more generic handlers defined in Spiel. As such, in most instances you'll wish to return _false_ from your handler functions, letting default handlers run in addition to your own.

Building on our previous example where we wish to provide speech feedback for unlabeled buttons, we are interested in the _onViewFocused_ event for buttons in the app's package. Since the inaccessible behavior results from focus moving to buttons with no accessible text, it makes sense that we wish to modify how such buttons are presented when they receive focus. We'll discuss how to modify the presentation later, but the below code intercepts the focus events and simply passes them on unchanged.

<pre>
forClass("android.widget.Button" {
  onViewFocused: function(event) {
    return false;
  }
});
</pre>

### Handling Events

All of the power of Javascript is available to you when handling events. However, Spiel exposes a few convenience functions for speaking and interrupting text, documented below.

#### speak()

This is the most commonly-called function used, as its name implies, for speaking text. It accepts the following arguments:

 * Text to be spoken
 * Optional boolean, set to _true_ if the text should interrupt

In general, you shouldn't explicitly use the second argument, as Spiel's event handling intelligently interrupts based on a number of complex factors. The option to use it is available for advanced use, in instances where it is necessary.

#### speakNotification()

Speaking of notifications is more complex than is simply speaking strings. The screen or ringer may be off, and the user may have enabled the preference to not speak notifications in those particular situations. This function honors these preferences, and should be used whenever your script needs to speak something incidental to its operation not in response to a user's direct action. For instance, if your script speaks an incoming SMS, then it should use this method as to not do so when the user prefers not to hear it. The function accepts a single argument:

 * Notification text to be spoken

#### nextShouldNotInterrupt()

As previously mentioned, the method by which Spiel determines whether text should interrupt is somewhat complex. It generally makes more sense to tell Spiel that a given utterance should _not_ interrupt speech rather than that it should.

For instance, say you wish to speak a piece of text, then let event delegation continue on to core handlers. If you simply speak a string and return, Spiel will likely interrupt whatever you've spoken with its own handling of the event. If you wish to indicate that any speech generated by future handlers should not interrupt your own, however, calling _nextShouldNotInterrupt()_ will achieve this in almost every case.

This is why scripts generally shouldn't explicitly interrupt speech without a good reason. While it is guaranteed that scripted handlers will be run before application handlers, the order in which they are run is not guaranteed. Explicitly interrupting speech disregards the needs of previously-run scripts.

### Event Properties

As the Android documentation demonstrates, each event has a number of properties. Each event includes a unique combination of properties relevant to its type, and just which types include which properties will not be duplicated here.

Spiel's scripting layer defines somewhat more convenient access to these properties, however. For instance, while Java-based APIs must access an event's _text_ property by calling its _getText()_ method, Spiel scripts need only refer to the _text_ property to access the same information.

Completing the above example, let's say that the events for the unlabeled buttons in _com.acme.android_ have _currentItemIndex_ properties of 4, 5 and 6. Building on the functions described in the previous sections, we might complete our script as follows:

<pre>
forClass("android.widget.Button" {
  onViewFocused: function(event) {
    if(event.currentItemIndex == 4)
      speak("New account");
    else if(event.currentItemIndex == 5)
      speak("Existing account");
    else if(event.currentItemIndex == 6)
      speak("Support");
    nextShouldNotInterrupt();
    return false;
  }
});
</pre>

Here we do several things. As previously described, we intercept events of the given type from the package specified by the filename and the class specified in the script. In the function, we check the _currentItemIndex_ property of the buttons, speaking a string for each which assumedly corresponds to some inaccessible label text. We then specify that the next handler should not interrupt speech and return false, stating that processing should continue. Processing then continues, eventually reaching the default button handler which speaks the string "Button". Because we have explicitly asked that the next handler not interrupt speech, our existing speech is allowed to continue and "Button" is queued. Had we not called _nextShouldNotInterrupt()_, the string "Button" would have interrupted our attempts to speak more accessible text, and our script would seem to have no effect.

### Viewing Events

While knowing how to script is one thing, knowing which events to intercept (or indeed, if any are available to intercept) is another matter entirely. Fortunately, Spiel offers several convenience features to make scripting easier. To enable these, navigate to Spiel's preferences, select _Scripting and debugging_, then enable _Display most recent accessibility events_.

When this is done, a tab labeled _Events_ starts populating with the 50 most recently-generated accessibility events. This makes it fairly easy to determine whethera given action is generating an event and, if so, whether that event includes properties that might be used to script it.

Events are also sent to the device log. By using the Android SDK and running the following command:

<pre>
adb logcat
</pre>

you'll receive a great deal of information on the events that apps are generating.

(Note: Soon there will be a feature that produces a script template for the most recent accessibility event, but such a feature doesn't exist yet.)

### Script Locations

Spiel checks for scripts in a number of locations, each with a very specific purpose. If a script read later in the loading process defines a handler with the same package and class name as one encountered earlier, that handler will override the one defined earlier. Handlers are read from the following locations:

 * The assets directory in the Spiel package. This is only used to define a few core scripts whose contents should never be changed or overridden.
 * Spiel's internal scripts directory. Here reside various scripts received from others via the Spiel Bazaar.
 * The Spiel scripts directory on your SD card, typically /spiel/scripts. Here is where your own scripts should be developed before being shared with others via the Spiel Bazaar.

The main point to keep in mind is that scripts you develop or copy will always override those provided by others, or by Spiel itself. This provides you as the user with a great deal of power, but as any comic book fan knows, with great power comes great responsibility. Be aware of the scripts located on your SD card, and be sure that they don't redefine core functions, or other handlers you may not wish to be replaced.
