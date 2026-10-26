plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sensorpark.core.ui"
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
    // Para los textos y componentes que muestran modelos de domain (tipos de lugar, errores).
    implementation(project(":domain"))

    // `api`: los módulos que usan core:ui reciben Compose y Material 3 sin volver a declararlos.
    api(platform(libs.compose.bom))
    api(libs.ui)
    api(libs.ui.graphics)
    api(libs.ui.tooling.preview)
    api(libs.material3)
    api(libs.material.icons.extended)

    debugImplementation(libs.ui.tooling)
    testImplementation(libs.junit)
}
