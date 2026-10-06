// Le Mail de Sama (maquette l2-mail) : vos boîtes (IMAP et SMTP), lire, écrire, pièces jointes à la demande, bouclier anti-arnaques.
// Signé avec la clé de la plateforme de l'émulateur (clés de test publiques d'AOSP), comme les applis du vrai Sama OS.
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
    namespace = "africa.samaos.mail"
    compileSdk = 36

    defaultConfig {
        applicationId = "africa.samaos.mail"
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

    packaging {
        resources {
            // Jakarta Mail : ses textes de licence en double. Ses fournisseurs (META-INF/javamail.*) restent.
            excludes += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt")
        }
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
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    // Jakarta Mail pour Android (EPL 2.0, ou GPL 2.0 avec l'exception Classpath) : IMAP et SMTP éprouvés.
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
}
