package com.sensorpark.app

import android.app.Application
import com.sensorpark.data.notifications.LocalNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Firebase se inicializa solo (plugin google-services + su ContentProvider):
// no hace falta `FirebaseApp.initializeApp()`.
@HiltAndroidApp
class SensorParkApp : Application() {
    @Inject lateinit var notifier: LocalNotifier

    override fun onCreate() {
        super.onCreate()
        // Así los cuatro canales aparecen en los ajustes del teléfono desde la primera apertura,
        // sin esperar al primer aviso.
        notifier.createChannels()
    }
}
