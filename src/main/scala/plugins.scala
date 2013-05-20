package info.spielproject.spiel
package plugins

import dalvik.system._

import java.io.File
import java.net.URL
import java.util.Collections

import collection.JavaConversions._
import collection.mutable.ListBuffer
import reflect._

import android.content.Context
import android.util.Log

class Plugin

object PluginManager {

  private val plugins = ListBuffer[Plugin]()

  def apply(context:Context) {
    val foundPlugins:List[Class[_]] = try {
      val classLoader = context.getClassLoader
      val findResources = classOf[BaseDexClassLoader].getDeclaredMethod("findResources", classOf[String])
      findResources.setAccessible(true)
      val dexs = Collections.list[URL](findResources.invoke(classLoader, "classes.dex").asInstanceOf[java.util.Enumeration[URL]])
      dexs.map { dex =>
        val apk = dex.getFile.split(":").last.split("!").head
        val dexFile = new DexFile(apk)
        val entries = Collections.list[String](dexFile.entries)
        entries.map(utils.classForName(_)).flatten
        .filter { c =>
          c != classOf[Plugin] && classOf[Plugin].isAssignableFrom(c)
        }.toList
      }.toList.flatten
    } catch {
      case e:Throwable =>
        Log.d("spielcheck", "Automatic plugin loading failed", e)
        Nil
    }
    plugins ++= foundPlugins.map(_.newInstance().asInstanceOf[Plugin])
  }

  def plugin[T <: Plugin:ClassTag]:List[T] =
    plugins.filter(classTag[T].runtimeClass.isInstance(_)).toList.asInstanceOf[List[T]]

}
