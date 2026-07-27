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

rootProject.name = "ReposteriaSandy"

// Módulo Kotlin puro (JVM): las fórmulas de la sección 6.5 de arquitectura.md.
// Es un módulo aparte a propósito -- así el compilador impide meterle un import de
// Android, que es lo que garantiza que se pueda probar con JUnit sin emulador.
include(":logica")

// El módulo :app (Android + Room + Compose) se agrega en el siguiente paso.
// Requiere el Android SDK instalado, así que solo compila desde Android Studio.
// include(":app")
