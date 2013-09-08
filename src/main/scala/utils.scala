package info.spielproject.spiel

import concurrent._
import ExecutionContext.Implicits.global
import duration._

import android.content._
import android.util.Log

package object utils {

  def instantiateAllMembers(cls:Class[_]) {
    val c = cls.newInstance().asInstanceOf[AnyRef]
    cls.getDeclaredClasses.foreach { cls =>
      try {
        Option(cls.getConstructor(c.getClass)).foreach(_.newInstance(c))
      } catch { case _:Throwable => }
    }
  }

  def ancestors(cls:Class[_]):List[Class[_]] = {
    def iterate(start:Class[_], classes:List[Class[_]] = Nil):List[Class[_]] = start.getSuperclass match {
      case null => classes
      case v => iterate(v, v :: classes)
    }
    iterate(cls).reverse
  }

  private var classes:Map[(String, String), Class[_]] = Map.empty

  def classForName(cls:String, pkg:String = ""):Option[Class[_]] = {
    classes.get((cls, pkg)).orElse {
      val context = SpielService.context
      try {
        val rv = context.getClassLoader.loadClass(cls)
        classes += (cls, pkg) -> rv
        Some(rv)
      } catch {
        case _ if(pkg != "") => try {
          val pc = context.createPackageContext(pkg, Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY)
          val f = future(Class.forName(cls, true, pc.getClassLoader))
          val rv = Await.result(f, 100 milliseconds)
          classes += (cls, pkg) -> rv
          Some(rv)
        } catch {
          case _:Throwable => None
        }
        case _:Throwable => None
      }
    }
  }

  events.ApplicationRemoved += { i:Intent =>
    val packageName = i
    .getData().getSchemeSpecificPart
    classes = classes.filter(_._1._2 != packageName)
  }

  import xml.XML
  import java.io.{ByteArrayInputStream, ByteArrayOutputStream, OutputStreamWriter}
  import org.ccil.cowan.tagsoup._
  import org.xml.sax.InputSource

  def htmlToXml(html:String) = {
    val parser = new Parser()
    parser.setProperty(Parser.schemaProperty, new HTMLSchema())
    val input = new InputSource(new ByteArrayInputStream(html.getBytes))
    val output = new ByteArrayOutputStream()
    val writer = new OutputStreamWriter(output)
    val xmlWriter = new XMLWriter(writer)
    xmlWriter.setOutputProperty(XMLWriter.OMIT_XML_DECLARATION, "yes")
    parser.setContentHandler(xmlWriter)
    parser.parse(input)
    val x = XML.loadString(new String(output.toByteArray))
    x
  }

  def installedPackages =
    SpielService.context.getPackageManager.getInstalledPackages(0)

}
