// Banco, le système de design de Sama : couleurs, polices, paysages, icônes et composants communs
// aux surfaces du système (l'Accueil, les Réglages…).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "africa.samaos.banco"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api("androidx.compose.ui:ui:1.9.0")
    api("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")
}
