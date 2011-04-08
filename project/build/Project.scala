import sbt._
import Process._
import java.io.File

class SpielProject(info: ProjectInfo) extends AndroidProject(info) with MarketPublish {

  val scanDirectories = mainAssetsPath/"scripts" :: Nil

  override def androidPlatformName = "android-10"

  val databinder = "databinder" at "http://databinder.net/repo/"

  override val libraryDependencies = Set(
    "com.nullwire" % "trace" % "latest" from "http://android-remote-stacktrace.googlecode.com/files/trace.jar",
    "rhino" % "js" % "1.7R2" from "http://spielproject.info/files/js-1.7R2.jar",
    "net.databinder" %% "dispatch-lift-json" % "0.7.8"
  )++super.libraryDependencies

  override def proguardOption = """
    -keep class info.spielproject.spiel.** { *; }
  """

  val rhinoPath = Path.fromFile("lib_managed/scala_"+buildScalaVersion+"/compile/js-1.7R2.jar")
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

  val keyalias = "spiel"
  override def keystorePath = Path.userHome / ".keystore" / "spiel.keystore"

}
