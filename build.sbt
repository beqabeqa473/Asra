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
  "jcenter" at "http://jcenter.bintray.com",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

libraryDependencies := Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
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
"""
