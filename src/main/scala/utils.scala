package info.spielproject.spiel
package utils


object HtmlParser {

  import xml._
  import factory._
  import parsing._

  import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl

  trait HTMLFactoryAdapter extends FactoryAdapter {

    val emptyElements = Set("area", "base", "br", "col", "hr", "img", "input", "link", "meta", "param")

    def nodeContainsText(localName: String) = !(emptyElements contains localName)
  }

  class TagSoupFactoryAdapter extends HTMLFactoryAdapter with NodeFactory[Elem] {

    protected def create(pre: String, label: String, attrs: MetaData, scpe: NamespaceBinding, children: Seq[Node]): Elem =
      Elem( pre, label, attrs, scpe, children :_* )

    def createNode(pre: String, label: String, attrs: MetaData, scpe: NamespaceBinding, children: List[Node] ): Elem =
      Elem( pre, label, attrs, scpe, children:_* )

    def createText(text: String) = Text(text)

    def createProcInstr(target: String, data: String) = makeProcInstr(target, data)

    def loadXML(source : InputSource) : Node = {
      val reader = getReader()
      reader.setContentHandler(this)
      scopeStack.push(TopScope)
      reader.parse(source)
      scopeStack.pop
      rootElem
    }

    private val parserFactory = new SAXFactoryImpl
    parserFactory.setNamespaceAware(true)
    def getReader() = parserFactory.newSAXParser().getXMLReader()
  }

  def apply(str:String) = new TagSoupFactoryAdapter().loadString(str)

}
