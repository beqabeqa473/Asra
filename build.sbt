import AndroidKeys._

name := "Spiel"

version := "1.1.3"

scalaVersion := "2.8.1"

platformName in Android := "android-15"

AndroidProject.androidSettings

TypedResources.settings

AndroidMarketPublish.settings

keystorePath in Android := Path.userHome / ".keystore" / "spiel.keystore"

keyalias in Android := "spiel"

libraryDependencies := Seq(
  "rhino" % "js" % "1.7R2" from "http://android-scripting.googlecode.com/hg/rhino/rhino1_7R2.jar",
  "net.databinder" %% "dispatch-lift-json" % "0.7.8",
  "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1"
)

proguardOption in Android := """
  -keep class info.spielproject.spiel.** { *; }
  -keep class org.mozilla.javascript.** { *; }
"""
