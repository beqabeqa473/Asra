Scripting

# Scripting

## Fundamentals

Before you can begin writing scripts for Spiel, you must first understand a few core concepts of the Android accessibility API. Spiel's scripting system is simply a nicer layer over the lower-level API. As such, you are urged to familiarize yourself with the API documentation linked to in the below sections.

### Events

Most state changes produce an [AccessibilityEvent](http://developer.android.com/reference/android/view/accessibility/AccessibilityEvent.html). For instance, arrowing around the screen produces VIEW_FOCUSED events each time a new view (Android's term for widget) gains focus. Clicking on something produces VIEW_CLICKED or VIEW_LONG_CLICKED depending on its duration, while editing or deleting text in edit areas generates TEXT_CHANGED events.

All AccessibilityEvents have a number of attached properties, but each event only uses a subset of these. Consult the previously-linked documentation for a discussion of these, and of which event types include which properties. There are two properties which all events carry, however.

### Event Classes

Android apps are comprised of many views, each of which is indicated by a class name. For instance, buttons are typically subclasses of android.widget.Button, checkboxes descend from android.widget.CheckBox, etc.

All AccessibilityEvents feature a className string property indicating the class which generated them. For instance, a VIEW_FOCUSED event with a className of android.widget.Button would indicate that a button has just gained focus, for which an appropriate response might be to speak the view's text followed by "button."

### Event Packages

Android apps are written in JVM languages, a consequence of which is that classes are generally categorized in packages. In fact, Android uses packages internally to deliniate applications.

All events contain a packageName property which, like className, is a string. However, this property contains the package of the app responsible for its generation.

Let's say I'm writing an app in the package com.mycompany.myapp. A class in this app creates a button view descending from android.view.Button. When that button gains focus, the resulting event would be of type VIEW_FOCUSED with a className of android.view.Button and a packageName of com.mycompany.myapp.

### Putting it All Together

This model gives us a great deal of flexibility when scripting app presentation. For instance, we can specify behavior for all views of a given type by presenting all events with a className equal to the desired widget. If, on the other hand, only the views in a specific application are behaving oddly then we can intercept events originating from the given package and view without modifying how others are presented, or even adding to their presentation should we wish.

The previous discussion is highly abstract, as we haven't even begun to explore how this interacts with Spiel. Hopefully, however, you are beginning to get a sense of the possibilities, and future sections will clarify matters further.

## Scripting Language

Spiel scripts are written in JavaScript, using an engine with extensions to hook into Java. While no knowledge of Java is strictly necessary to write scripts, a basic understanding of core concepts is helpful. Since the JavaScript engine can interface with Java, much of the AccessibilityEvent API documentation is directly applicable to Spiel scripting, but with a few conveniences. Here is some of the syntactic sugar supported to make scripting Spiel easier.

### Easier Property Access

Java uses getters and setters for variable access, resulting in names like getText(), setText(str), etc. Spiel's scripting engine simplifies such access by removing the "get" or "set" and by lower-casing the first letter of the function. As such, the previous examples become much more concisely represented as text(), or text(str).

Whenever you see methodYou shouldn't ever need to set a property directly on an AccessibilityEvent. However, property access becomes simplified. The previous example shows how you would access an event's associated text, for instance.

## Handlers

Spiel's behavior is coded in a series of handlers. The actual mechanics of creating these are hidden by the scripting language, but they must still be understood to script effectively.

Each handler is associated with a package and or class. Handlers can leave one or both of these values blank, in which case they are associated with multiple classes or packages. Perhaps some examples will clear matters up.

 * A handler associated with package "com.mycompany.myapp" and class "android.view.Button" is called whenever events from this package and class arrive.
 * A handler associated with package "" and class "android.view.Button" is called whenever events from any button arrive.
 * A handler associated with package "" and class "" is called for all events.

Each handler can respond to one or all AccessibilityEvent types. For instance, the handler associated with only the class android.view.Button speaks the word "button" when receiving events of type VIEW_FOCUSED.

Multiple handlers can be attached to events of similar types, but only one can be attached to the same combination of package and class names. For instance, there may be a handler attached to both com.mycompany.myapp, android.view.Button as well as only to android.view.Button, but there cannot be two handlers attached to the same package and class.

Handlers are looked up from most to least specific. First, the exact package and class name are searched for. Next, handlers matching the event's class are checked. Then, all parent classes for the event's class are checked for a match. For instance, if your event originates not from android.view.Button but instead from one of its descendants then this check will match the event with the default button handler. Finally, the default event catches the event and performs generic catch-all processing. Once a handler is run, it is not run again for the same event, so handlers that result in multiple matches won't trigger multiple times.

Each handler can hault processing for a given event. By returning true, a handler indicates that the event should not be processed further, and matching ends. Returning false continues the matching process, allowing any other handlers access to the event.

## Scripts and Devices


