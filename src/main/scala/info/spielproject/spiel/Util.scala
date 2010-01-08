package info.spielproject.spiel

private object Util {
  def toFlatString(ls:java.util.List[CharSequence]) = {
    val str = ls.toString
    str.substring(1, str.length-1)
  }
}
