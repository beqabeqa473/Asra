import sbt._
import java.io.File

class SpielProject(info: ProjectInfo) extends AndroidProject(info) {
  def androidSdkPath = Path.fromFile(new File("/home/nolan/lib/android-sdk-linux_x86-1.6_r1"))
  override def androidPlatformName = "android-1.6"
}
