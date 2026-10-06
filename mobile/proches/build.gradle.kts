// Proche en proche (innovation 6) : les paquets signés par Sama et Sugu, vérifiés avant toute installation,
// et leur échange sans data avec les points Sama et les contacts proches.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "africa.samaos.proches"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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
    implementation(project(":bouclier"))
    // L'état des réceptions est observé par les écrans (Compose) de Sugu et des Réglages.
    api("androidx.compose.runtime:runtime:1.9.0")
}
