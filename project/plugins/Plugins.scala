import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val android = "org.scala-tools.sbt" % "android-plugin" % "0.4"

  val orgClapperMavenRepo = "clapper.org Maven Repo" at "http://maven.clapper.org/"
  val markdown = "org.clapper" % "sbt-markdown-plugin" % "0.1.1"
}
