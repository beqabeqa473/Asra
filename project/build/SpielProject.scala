import sbt._
import Process._
import java.io.File

import org.clapper.sbtplugins.MarkdownPlugin

class SpielProject(info: ProjectInfo) extends AndroidProject(info) with MarkdownPlugin {

  val scanDirectories = mainAssetsPath/"scripts" :: Nil

  override def androidPlatformName = "android-8"

  val ttsVersion = "3.0_rc02"
  val tts = "google" % "tts" % ttsVersion from "http://eyes-free.googlecode.com/files/TTS_library_stub_"+ttsVersion+".jar"

  val stacktrace = "com.nullwire" % "trace" % "latest" from "http://android-remote-stacktrace.googlecode.com/files/trace.jar"

  val rhino = "rhino" % "js" % "1.7R2" from "http://spielproject.info/attachments/download/3/js.jar"

  override def proguardOption = """
    -keep class com.google.tts.** { *; }
    -keep class info.spielproject.spiel.scripting.Scripter {
      public void registerHandlerFor(java.lang.String, java.lang.String, java.lang.Object);
    }
    -keep class info.spielproject.spiel.handlers.** { *; }
  """

  val rhinoPath = Path.fromFile("lib_managed/scala_2.8.0.Beta1/compile/js-1.7R2.jar")
  override def proguardExclude = super.proguardExclude+++rhinoPath

  override def dxTask = execTask {<x> {dxPath.absolutePath} --dex --output={classesDexPath.absolutePath} {classesMinJarPath.absolutePath} {rhinoPath.absolutePath}</x> }

  override def aaptPackageTask = task {
    super.aaptPackageTask.run
    FileUtilities.unzip(rhinoPath, outputDirectoryName, GlobFilter("org/mozilla/javascript/resources/*.properties"), log)
    for(p <- (outputDirectoryName/"org"**"*.properties").get) {
      (
        (new java.lang.ProcessBuilder(
          aaptPath.absolutePath,
          "add",
          resourcesApkPath.absolutePath,
          p.toString.replace("./"+outputDirectoryName+"/", "")
        ))
        directory outputDirectoryName.asFile
      ) ! log
    }
    FileUtilities.clean(outputDirectoryName/"org", log)
    None
  }

  override def cleanLibAction = super.cleanAction dependsOn(markdownCleanLibAction)
  override def updateAction = super.updateAction dependsOn(markdownUpdateAction)

  val manualMD = "src" / "doc" / "manual.md"
  val manualHTML = "target" / "manual.html"
  lazy val htmlDocs = fileTask(manualMD from manualHTML) {
    markdown(manualMD, manualHTML, log)
    Some("")
  }

}
