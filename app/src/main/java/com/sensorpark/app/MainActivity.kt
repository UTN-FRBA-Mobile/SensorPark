package com.sensorpark.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.sensorpark.app.navigation.AppNavigation
import com.sensorpark.core.ui.theme.SensorParkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
            SensorParkTheme {
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
