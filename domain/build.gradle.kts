import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Kotlin puro: este módulo no puede depender de Android ni de Firebase.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    api(libs.coroutines.core)
    // Sólo la anotación @Inject: Hilt arma los casos de uso sin que domain conozca a Hilt.
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
