package com.kj2431.pielauncher

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kj2431.pielauncher.ui.ThemeHelper
import androidx.appcompat.widget.Toolbar
import com.kj2431.pielauncher.service.OverlaySettings
import com.kj2431.pielauncher.ui.EdgeToEdge

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyActivityTheme(this)
        setContentView(R.layout.activity_settings)
        val tb = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(tb)
        tb.setNavigationOnClickListener { finish() }
        title = getString(R.string.settings)
        EdgeToEdge.setup(this, findViewById(R.id.root))
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, SettingsFragment())
                .commit()
        }
    }
    // Show the activation strips while Settings is visible so position/size
    // edits can be seen live; the running service rebuilds them on change.
    override fun onStart() { super.onStart(); OverlaySettings.preview = true }
    override fun onStop() { OverlaySettings.preview = false; super.onStop() }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
