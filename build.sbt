import AndroidKeys._

AndroidProject.androidSettings

AndroidMarketPublish.settings

site.settings

site.sphinxSupport()

name := "Spiel"

version := "3.0.0"

scalaVersion := "2.9.2"

scalacOptions ++= Seq("-deprecation")

platformName in Android := "android-17"

keystorePath in Android := Path.userHome / ".keystore" / "spiel.keystore"

keyalias in Android := "spiel"

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

libraryDependencies := Seq(
  "rhino" % "js" % "1.7R2" from "http://android-scripting.googlecode.com/hg/rhino/rhino1_7R2.jar",
  "com.ning" % "async-http-client" % "1.7.11-SNAPSHOT" force(),
  "net.databinder.dispatch" % "json4s-native_2.9.1" % "0.9.5",
  "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1"
)

proguardOption in Android := """
  -keep class scala.collection.SeqLike { public protected *; }
  -keep class info.spielproject.spiel.** { *; }
  -keep class org.mozilla.javascript.* { *; }
  -keep class org.mozilla.javascript.ast.* { *; }
  -keep class org.mozilla.javascript.json.* { *; }
  -keep class org.mozilla.javascript.jdk15.* { *; }
  -keep class org.mozilla.javascript.regexp.* { *; }
  -keep class org.mozilla.javascript.resources.* { *; }
"""
