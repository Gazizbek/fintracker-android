package kz.alem.corporation.fintracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kz.alem.corporation.fintracker.ui.FinTrackerRoot
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.theme.FinTrackerTheme
import kz.alem.corporation.fintracker.widget.WidgetLaunch

// AppCompatActivity is needed for per-app languages on older Android versions.
class MainActivity : AppCompatActivity() {

    /** Pending widget tap; the UI navigates and clears it. */
    private val widgetLaunch = MutableStateFlow<WidgetLaunch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity has already handled its launch intent.
        if (savedInstanceState == null) widgetLaunch.value = WidgetLaunch.from(intent)
        val container = (application as FinTrackerApplication).container
        setContent {
            val theme by container.prefs.theme.collectAsState()
            CompositionLocalProvider(LocalAppContainer provides container) {
                FinTrackerTheme(theme) { FinTrackerRoot(widgetLaunch) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        WidgetLaunch.from(intent)?.let { widgetLaunch.value = it }
    }
}
