package com.sensorpark.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Firebase se inicializa solo (plugin google-services + su ContentProvider):
// no hace falta `FirebaseApp.initializeApp()`.
@HiltAndroidApp
class SensorParkApp : Application()
