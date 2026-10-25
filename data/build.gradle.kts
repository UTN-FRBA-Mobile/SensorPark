import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sensorpark.data"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Con FIREBASE_EMULATOR_HOST en local.properties, el build debug usa los emuladores de
        // Firebase en vez del proyecto real. Desde el emulador de Android la PC es 10.0.2.2.
        val emulatorHost = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }
                ?.inputStream()?.use { load(it) }
        }.getProperty("FIREBASE_EMULATOR_HOST").orEmpty()
        debug { buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$emulatorHost\"") }
        release { buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"") }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.work.runtime)

    // Geocercas y FusedLocationProvider.
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    // Tests instrumentados: Room de verdad, en memoria, en el emulador.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.coroutines.test)
}
