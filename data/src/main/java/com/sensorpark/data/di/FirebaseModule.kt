package com.sensorpark.data.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.sensorpark.data.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase se inicializa solo (plugin google-services + su ContentProvider); acá sólo se expone.
 * Si el build trae FIREBASE_EMULATOR_HOST (ver data/build.gradle.kts), apunta a los emuladores.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    private val emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST.takeIf { it.isNotBlank() }

    // Los puertos son los de firebase.json.
    @Provides
    @Singleton
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance().apply {
        emulatorHost?.let { useEmulator(it, 9099) }
    }

    // Base predeterminada `(default)`: no hay que nombrarla acá, ni en firebase.json, ni en los triggers.
    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        emulatorHost?.let { useEmulator(it, 8080) }
    }

    // Tiene que ser la misma región que setGlobalOptions en functions/index.js.
    @Provides
    @Singleton
    fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance("southamerica-east1").apply {
        emulatorHost?.let { useEmulator(it, 5001) }
    }

    @Provides
    @Singleton
    fun messaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
