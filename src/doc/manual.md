Scripting

# Scripting

The greatest challenge in writing a manual about Spiel's scripting system is that there isn't much that is truly unique to document. The language is JavaScript with [convenience features for accessing Java](https://developer.mozilla.org/en/Scripting_Java). The accessibility API used is mostly Android's [AccessibilityEvent](http://developer.android.com/reference/android/view/accessibility/AccessibilityEvent.html), and Spiel's only contribution is a bit of glue to attach functions to specific events. As such, learning how to write scripts isn't a matter of learning a few functions, but rather of understanding the API with which Spiel (and ultimately your scripts) interface.

Android's accessibility APIs are also still under development and are likely to change in the future. Also, there is plenty of documentation describing the JavaScript language and the fundamentals of the Rhino engine. Rather than duplicating this documentation and keeping up-to-date with changes, this manual attempts instead to link to the documentation that already exists while providing a functional introduction to the task of writing scripts for Spiel.

## A Whirlwind Tour of Android Accessibility

The intricacies of the Android accessibility API are described exhaustively elsewhere, and should be consulted for further details. Below are a few fundamental points to know when exploring the external documentation in order to script Spiel.

### Events

Changes in the Android interface are described by a stream of events. When focus changes, an event is sent. When text is inserted into or deleted from a text field, an event is generated. When an incoming SMS arrives, a button is clicked or a selection is scrolled, events are generated.

Designing an Android accessibility solution involves intercepting these events and presenting them accordingly. This is precisely what Spiel does, having hard-coded handlers for a number of fundamental events. It is also what your scripts will do.

Each event has a number of properties associated with it. Not all events use every single property, and just which properties you can expect to have meaningful values depends on the event type received.

### Classes and Packages: Widgets and Apps

Two properties common to all event types are the _className_ and _packageName_. Both are crucial to creating scripts.

Widgets, or "views" in Android parlance, are created in classes. When an event originates from a view--text is added to a field or a button gain focus for instance--then events associated with that widget are also associated with its class. For instance, if an event of _TYPE_VIEW_FOCUSED_ is generated and has a _className_ of _android.widget.Button_, then a button has just gained focus and it might make sense to speak the button's label followed by the word "button."

Android apps are divided into one or more packages. An event's _packageName_ property indicates the package in which the view which generated it was created.

The _packageName_ property is useful in that it allows for scripts to provide different behaviors for views in specific applications that differ from the default. Say, for instance, that Acme Computer, Inc. releases an Android app with lots of unlabeled buttons, placing it in the package _com.acme.android_. By intercepting _TYPE_VIEW_FOCUSED_ events with a _className_ of _android.widget.Button_ and a _packageName_ of _com.acme.android_, scripts can provide spoken feedback for only these buttons, not being called for buttons in any packages other than _com.acme.android_.

There are a variety of event types and properties. Consult the previously-linked AccessibilityEvent documentation for a more indepth discussion of these.
