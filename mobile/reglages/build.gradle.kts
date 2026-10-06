// Les Réglages de Sama : ils remplacent les Paramètres d'Android (maquettes du lot 3).
// Signés avec la clé de la plateforme de l'émulateur (clés de test publiques d'AOSP) : ils ont les droits
// du système pour régler le Wi-Fi, le Bluetooth, les SIM… comme les Réglages du vrai Sama OS.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val cles = rootProject.file("systeme/cles")
if (!file("$cles/plateforme.p12").exists()) {
    exec { commandLine("sh", "$cles/fabriquer.sh") }
}

android {
    namespace = "africa.samaos.reglages"
    compileSdk = 36

    defaultConfig {
        applicationId = "africa.samaos.reglages"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        create("plateforme") {
            storeFile = file("$cles/plateforme.p12")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "plateforme"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("plateforme")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("plateforme")
        }
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
    implementation(project(":banco"))
    implementation(project(":bouclier"))
    implementation(project(":soldes"))
    implementation(project(":proches"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    // Les codes QR (partager un Wi-Fi).
    implementation("com.google.zxing:core:3.5.3")
}
