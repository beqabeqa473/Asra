package info.thewordnerd.spiel.utils

import java.util.ArrayList

import org.jruby.Ruby
import org.jruby.RubyRuntimeAdapter
import org.jruby.javasupport.JavaEmbedUtils

object Scripter {
  def apply() = {
    val interpreter =JavaEmbedUtils.initialize(new ArrayList())
    val evaler = JavaEmbedUtils.newRuntimeAdapter()
    true
  }
}
