import android.Keys._

name := "Spiel"

version := "3.0.0-SNAPSHOT"

versionCode := Some(13)

scalaVersion := "2.11.2"

scalacOptions ++= Seq("-deprecation", "-feature", "-language:existentials,implicitConversions,postfixOps", "-target:jvm-1.6")

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

//keystorePath in Release := file(".") / "spiel.keystore"

//keyalias := "spiel"

//PasswordManager.settings

//cachePasswords := true

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

libraryDependencies := Seq(
  "rhino" % "js" % "1.7R2" from "https://github.com/damonkohler/sl4a/raw/master/rhino/rhino1_7R2.jar",
  //"net.databinder.dispatch" %% "dispatch-json4s-native" % "0.10.0",
  "org.scaloid" %% "scaloid" % "3.4-10",
  "org.macroid" %% "macroid" % "2.0.0-M3",
  "com.android.support" % "support-v4" % "20.0.0",
  "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1",
  "ch.acra" % "acra" % "4.5.0"
)

proguardOptions in Android += """
  -ignorewarnings
  -keep class scala.collection.SeqLike { public protected *; }
  -keep class info.spielproject.spiel.** { *; }
  -keep class org.mozilla.javascript.* { *; }
  -keep class org.mozilla.javascript.ast.* { *; }
  -keep class org.mozilla.javascript.json.* { *; }
  -keep class org.mozilla.javascript.jdk15.* { *; }
  -keep class org.mozilla.javascript.regexp.* { *; }
  -keep class org.mozilla.javascript.resources.* { *; }
"""
