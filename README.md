# Build Instructions

Building Spiel requires [SBT](http://scala-sbt.org/). Start by [downloading the latest release](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html).

Once SBT is successfully installed, you can work with the code in a number of ways. SBT can either be used as a normal command line tool by invoking it like so ("$ " indicates an OS shell):

$ sbt android:package release
* Builds a release APK and signs it with a release key if configured

$ sbt android:package debug
* Builds a debug APK and signs it using the debug key

$ sbt android:install
* Install the application to device

You can run SBT without command line arguments and are then dropped into an SBT shell which supports all the commands directly, without needing a prefix. You can also precede any SBT commands with a "~" to rerun the specified action when any changes are made to the source. For instance, Running the following ("> " indicates an SBT shell):

> ~android:package debug

will automatically and incrementally recompile the app upon source changes. With the basics of SBT introduced, here is how to build the app:

First, set the ANDROID_SDK_HOME environment variable to the root directory of your Android SDK installation. Note that, as of this writing, the Android 4.3 SDK is needed to build Spiel. Once set, run the following command:

$ sbt android:package debug for debug builds or $ sbt android:package release for release builds

This leaves a target/spiel*.apk file ready to be installed to your device or emulator.

If you have a device plugged in and recognized by _adb_, you can also run:

$ sbt android:install

to build the app and to install the package directly. If Spiel is already running, it will be restarted automatically.
