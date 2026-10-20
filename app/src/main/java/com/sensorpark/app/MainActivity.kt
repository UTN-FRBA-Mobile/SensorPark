package com.sensorpark.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import android.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.app.navigation.AppNavigation
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.core.ui.theme.ThemeMode
import com.sensorpark.data.preferences.ThemePreferencesStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferencesStore

    /**
     * Deep link que llegó por onNewIntent y la navegación todavía no abrió. Se guarda porque puede
     * llegar antes de que exista: al tocar un aviso con la app en recientes y el proceso muerto,
     * la Activity se recrea y el link llega mientras todavía se carga la sesión.
     */
    private val pendingDeepLink = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val storedMode by themePreferences.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = when (ThemeMode.from(storedMode)) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }
            SensorParkTheme(dark = darkTheme) {
                AppNavigation(
                    pendingDeepLink = pendingDeepLink.value,
                    onDeepLinkHandled = { pendingDeepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) pendingDeepLink.value = intent
    }
}
