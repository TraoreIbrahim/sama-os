// Les soldes et forfaits de Sama (innovation 1) : crédit, data et date de fin lus dans les SMS officiels des
// opérateurs (et les réponses USSD), sans rien envoyer. Partagé par l'Accueil (Pouls), Messages et les Réglages.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "africa.samaos.soldes"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":bouclier"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
