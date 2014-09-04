package info.spielproject

import android.content._
import android.util.Log
import android.view.accessibility._

package object spiel {

  implicit def accessibilityEvent2RichEvent(e:AccessibilityEvent) =
    new RichEvent(e)

  implicit def accessibilityNodeInfo2RichNode(n:AccessibilityNodeInfo) =
    RichNode(n)

  implicit def fToBroadcastReceiver(f:(Context, Intent) => Unit) =
    new BroadcastReceiver {
      override def onReceive(c:Context, i:Intent) {
        try {
          f(c, i)
        } catch {
          case e:Throwable =>
            Log.e("spiel", "Error receiving broadcast", e)
        }
      }
    }

  implicit def fToRunnable(f: => Unit) = new Runnable {
    def run() { f }
  }

}
