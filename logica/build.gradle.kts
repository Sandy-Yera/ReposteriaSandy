import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Kotlin puro: sin Android, sin Room, sin coroutines. Si alguna vez hace falta agregar
// una dependencia de Android acá, es señal de que esa función no pertenece a este módulo
// -- ver la regla de ubicación en la sección 6.5 de arquitectura.md.
dependencies {
    testImplementation(libs.junit)
}

// Bytecode 17 porque es lo que espera el módulo :app de Android, pero sin exigir un
// toolchain 17 instalado: compila con el JDK que haya (17 o superior).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
