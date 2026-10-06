// Le bouclier anti-arnaques de Sama (innovation 2) : ce qui reconnaît un faux SMS d'opérateur, un appel
// suspect, une page qui demande un code secret. Partagé par Messages, Téléphone, Griot et les Réglages ;
// tout se décide sur le téléphone, rien n'est envoyé.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "africa.samaos.bouclier"
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
