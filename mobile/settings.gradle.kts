// Sama OS mobile — prototype des surfaces système en Kotlin + Jetpack Compose.
// Module « accueil » : l'Accueil, la Natte et la Cour, installés comme écran d'accueil.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sama-mobile"

// Banco, le système de design partagé.
include(":banco")
include(":bouclier")
include(":soldes")
include(":proches")
include(":accueil")
// Les Réglages de Sama, signés avec la clé de la plateforme de l'émulateur.
include(":reglages")
// Les applis natives de Sama (lot 2).
include(":telephone")
include(":contacts")
include(":messages")
include(":horloge")
include(":calculatrice")
include(":notes")
include(":agenda")
include(":fichiers")
include(":photos")
include(":dictaphone")
include(":lecteur")
include(":griot")
include(":appareil")
include(":sugu")
// Appli d'essai pour l'émulateur (lecture, message avec réponse) ; jamais installée ailleurs.
include(":essais")
