package kz.alem.corporation.fintracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kz.alem.corporation.fintracker.ui.FinTrackerRoot
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.theme.FinTrackerTheme

// AppCompatActivity is needed for per-app languages on older Android versions.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FinTrackerApplication).container
        setContent {
            val theme by container.prefs.theme.collectAsState()
            CompositionLocalProvider(LocalAppContainer provides container) {
                FinTrackerTheme(theme) { FinTrackerRoot() }
            }
        }
    }
}
