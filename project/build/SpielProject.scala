import sbt._
import Process._
import java.io.File

class SpielProject(info: ProjectInfo) extends AndroidProject(info) {
  override def androidPlatformName = "android-1.6"
  override def androidSdkPath = Path.fromFile("/home/nolan/lib/android")

  val rhino = "rhino" % "js" % "1.7R2"

  override def proguardOption = """
    -keep class info.spielproject.spiel.presenters.Presenter
    -keepclassmembers class info.spielproject.spiel.presenters.Presenter {
      public void registerHandler(java.lang.String, java.lang.String, org.mozilla.javascript.Function);
    }
  """

  val rhinoPath = Path.fromFile("lib_managed/compile/js-1.7R2.jar")
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
