package info.spielproject.spiel

import org.acra._
import annotation._

import events._

@ReportsCrashes(
  formKey = "",
  mailTo = "spiel@thewordnerd.info",
  customReportContent = Array(ReportField.APP_VERSION_NAME, ReportField.ANDROID_VERSION, ReportField.PHONE_MODEL, ReportField.CUSTOM_DATA, ReportField.STACK_TRACE, ReportField.LOGCAT)
)
class Application extends android.app.Application {
  override def onCreate() {
    ACRA.init(this)
    UnhandledException += { t:Throwable => ErrorReporter.getInstance.handleException(t, false) }
    super.onCreate()
  }
}
