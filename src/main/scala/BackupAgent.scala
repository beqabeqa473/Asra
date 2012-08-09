package info.spielproject.spiel

import android.app.backup.{BackupAgentHelper, SharedPreferencesBackupHelper}

class BackupAgent extends BackupAgentHelper {
  override def onCreate() {
    addHelper(
      "prefs",
      new SharedPreferencesBackupHelper(this, "info.spielproject.spiel_preferences")
    )
  }
}
