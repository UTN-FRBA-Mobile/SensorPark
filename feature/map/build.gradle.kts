plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sensorpark.feature.map"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":domain"))
    // Trae Compose y Material 3.
    implementation(project(":core:ui"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    // El pedido del permiso de ubicación.
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    // El mapa. La key sale de MAPS_API_KEY en local.properties: sin ella compila igual, pero el
    // mapa se ve gris.
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    debugImplementation(libs.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Repositorios falsos y datos de ejemplo (T04).
    testImplementation(project(":core:testing"))

    // Tests de UI: dibujan las pantallas de verdad en un emulador, sin Firebase.
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.ui.test.manifest)
}
