package info.spielproject.spiel.activities

import android.app.TabActivity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater

class Main extends TabActivity {

  override protected def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    val tabHost = getTabHost()
    LayoutInflater.from(this).inflate(
      R.layout.main, tabHost.getTabContentView(), true
    )

    tabHost.addTab(tabHost.newTabSpec("tab1"
      ).setIndicator("Scripting"
      ).setContent(new Intent(this, classOf[Main]))
    )

    tabHost.addTab(tabHost.newTabSpec("About"
      ).setIndicator("About"
      ).setContent(R.id.about)
    )
  }
}
