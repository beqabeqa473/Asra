import sbt._
import Process._
import java.io.File

class SpielProject(info: ProjectInfo) extends AndroidProject(info) {

  override def androidPlatformName = "android-2.1"

  val tts = "google" % "tts" % "2.1_rc01" from "http://eyes-free.googlecode.com/files/TTS_library_stub_2.1_rc01.jar"

  val stacktrace = "com.nullwire" % "trace" % "latest" from "http://android-remote-stacktrace.googlecode.com/files/trace.jar"

  val rhino = "rhino" % "js" % "1.7R2" from "http://spielproject.info/attachments/download/3/js.jar"

  override def proguardOption = """
    -keep class info.spielproject.spiel.scripting.Scripter {
      public void registerHandlerFor(java.lang.String, java.lang.String, java.lang.Object);
    }
    -keep class info.spielproject.spiel.handlers.Handler$ { *; }
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
}
