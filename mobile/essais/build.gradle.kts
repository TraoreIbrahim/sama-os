// Une appli d'essai, pour l'émulateur seulement : elle joue une leçon avec une vraie session média
// et envoie un message auquel on peut répondre, pour essayer le lecteur et la réponse du Pouls.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "africa.samaos.essais"
    compileSdk = 36

    defaultConfig {
        applicationId = "africa.samaos.essais"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
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
