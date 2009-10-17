import sbt._
import Process._
import java.io.File

class SpielProject(info: ProjectInfo) extends AndroidProject(info) {
  override def androidPlatformName = "android-1.6"

  val rhino = "rhino" % "js" % "1.7R2"

  override def proguardOption = """
    -adaptresourcefilenames **.properties
    -keep class * extends org.mozilla.javascript.VMBridge
    -keep public class org.mozilla.javascript.Token
  """

  override def packageTask(signPackage: Boolean) = task {
    super.packageTask(signPackage)
    FileUtilities.unzip(classesMinJarPath, outputDirectoryName, GlobFilter("org/mozilla/javascript/resources/*.properties"), log)
    for(p <- (outputDirectoryName/"org"**"*.properties").get) {
      (
        (new java.lang.ProcessBuilder(
          aaptPath.absolutePath,
          "add",
          packageApkPath.absolutePath,
          p.toString.replace(outputDirectoryName+"/", "")
        ))
        directory outputDirectoryName.asFile
      ) ! log
    }
    FileUtilities.clean(outputDirectoryName/"org", log)
    None
  }
}
