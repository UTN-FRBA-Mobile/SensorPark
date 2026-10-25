import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Con AGP 9 Kotlin viene integrado: no se aplica org.jetbrains.kotlin.android.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// local.properties (gitignoreado): la key de Maps y los datos de la firma.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

/** Un dato de la firma: de local.properties o, si no está (por ejemplo en CI), de una variable de entorno. */
fun signingValue(name: String): String? = localProperties.getProperty(name) ?: System.getenv(name)

android {
    namespace = "com.sensorpark.app"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "com.sensorpark.app"
        minSdk = 26
        targetSdk = 36
        // T25: la primera versión que se entrega. Cada versión nueva sube versionCode.
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Key de Maps desde local.properties (gitignoreado). El default vacío es para que un
        // clone sin key compile: sin él falla el merger de manifiestos (el mapa sale gris).
        manifestPlaceholders["mapsApiKey"] = localProperties.getProperty("MAPS_API_KEY").orEmpty()
    }

    // La firma de la versión que se entrega (T25). El keystore y sus contraseñas no van al repo:
    // se guardan con respaldo fuera de él y se apuntan desde local.properties o variables de entorno.
    // Sin ellos, el release sale sin firmar (sirve para revisar que compile y que R8 no rompa nada).
    val releaseStore = signingValue("SENSORPARK_STORE_FILE")
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = signingValue("SENSORPARK_STORE_PASSWORD")
                keyAlias = signingValue("SENSORPARK_KEY_ALIAS")
                keyPassword = signingValue("SENSORPARK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 achica y ofusca el código y saca los recursos que no se usan.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:map"))
    implementation(project(":feature:parking"))
    implementation(project(":feature:reservations"))
    implementation(project(":feature:checkin"))
    implementation(project(":feature:admin"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Sólo por la pantalla de diagnóstico, que habla con Firebase y Maps directamente.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)
    implementation(libs.coroutines.play.services)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Repositorios falsos y datos de ejemplo (T04).
    testImplementation(project(":core:testing"))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
