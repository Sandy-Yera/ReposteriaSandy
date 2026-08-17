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

// La app Android: Room, pantallas con Compose y respaldo a Drive.
// Necesita el Android SDK instalado (ver docs/entorno.md, nivel 2).
include(":app")
