package info.spielproject.spiel

import android.util.Log

package object utils {

  def instantiateAllMembers(cls:Class[_]) {
    val c = cls.newInstance().asInstanceOf[AnyRef]
    cls.getDeclaredClasses.foreach { cls =>
      try {
        Option(cls.getConstructor(c.getClass)).foreach(_.newInstance(c))
      } catch { case _ => }
    }
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
