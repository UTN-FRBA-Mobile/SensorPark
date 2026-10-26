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

    // El mapa. La key va en el manifest de app (MAPS_API_KEY de local.properties).
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    debugImplementation(libs.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Repositorios falsos y datos de ejemplo (T04).
    testImplementation(project(":core:testing"))
}
