package info.spielproject.spiel

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.os.BatteryManager
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

  def ancestors(cls:Class[_]):List[Class[_]] = {
    def iterate(start:Class[_], classes:List[Class[_]] = Nil):List[Class[_]] = start.getSuperclass match {
      case null => classes
      case v => iterate(v, v :: classes)
    }
    iterate(cls).reverse
  }

  def classForName(cls:String, pkg:String = "") = {
    val context = SpielService.context
    try {
      Some(context.getClassLoader.loadClass(cls))
    } catch {
      case _ if(pkg != "") => try {
        val pc = context.createPackageContext(pkg, Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY)
        Some(Class.forName(cls, true, pc.getClassLoader))
      } catch {
        case _ => None
      }
      case _ => None
    }
  }

  def batteryPercentage(callback:Int => Unit) = {
    val receiver:BroadcastReceiver = new BroadcastReceiver {
      override def onReceive(context:Context, intent:Intent) {
        context.unregisterReceiver(this)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if(level >= 0 && scale > 0)
          callback(level*100/scale)
      }
    }
    SpielService.context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED))
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
