import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Repositorios falsos y datos de ejemplo (T04). Kotlin puro, como `domain`: así lo pueden usar
// tanto los tests de JVM como los de instrumentación de cualquier módulo.
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
    api(project(":domain"))
}
