package com.sensorpark.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    // Base predeterminada `(default)`: no hay que nombrarla acá, ni en firebase.json, ni en los triggers.
    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    // Tiene que ser la misma región que setGlobalOptions en functions/index.js.
    @Provides
    @Singleton
    fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance("southamerica-east1")

    @Provides
    @Singleton
    fun messaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
