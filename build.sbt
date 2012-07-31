import AndroidKeys._

AndroidProject.androidSettings

AndroidMarketPublish.settings

name := "Spiel"

version := "3.0.0"

scalaVersion := "2.9.2"

scalacOptions ++= Seq("-deprecation")

platformName in Android := "android-16"

keystorePath in Android := Path.userHome / ".keystore" / "spiel.keystore"

keyalias in Android := "spiel"

libraryDependencies := Seq(
  "rhino" % "js" % "1.7R2" from "http://android-scripting.googlecode.com/hg/rhino/rhino1_7R2.jar",
  "net.databinder" % "dispatch-lift-json_2.9.1" % "0.7.8",
  "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1"
)

proguardOption in Android := """
  -keep class info.spielproject.spiel.** { *; }
  -keep class org.mozilla.javascript.** { *; }
"""
