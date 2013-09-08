package info.spielproject.spiel

import org.acra._
import annotation._
import org.scaloid.common._

import events._

@ReportsCrashes(
  formKey = "",
  mailTo = "spiel@thewordnerd.info",
  customReportContent = Array(ReportField.APP_VERSION_NAME, ReportField.ANDROID_VERSION, ReportField.PHONE_MODEL, ReportField.CUSTOM_DATA, ReportField.STACK_TRACE, ReportField.LOGCAT)
)
class Application extends android.app.Application with Helpers {
  override def onCreate() {
    ACRA.init(this)
    UnhandledException += { t:Throwable =>
      toast(t.getMessage)(this)
      if(Preferences.sendBacktraces)
        ACRA.getErrorReporter.handleException(t, false)
    }
    super.onCreate()
  }
}
