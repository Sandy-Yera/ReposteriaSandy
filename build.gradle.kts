// Solo declara los plugins para que los módulos los apliquen; la raíz no construye nada.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
