// La raíz no construye nada: solo deja los plugins en el classpath con su versión, para
// que cada módulo los aplique sin volver a declararla.
//
// Tienen que estar TODOS acá, incluidos los que solo usa un módulo. kotlin.jvm (que usa
// :logica) y kotlin.android (que usa :app) son en el fondo el mismo plugin, así que si
// uno queda en el classpath por su módulo, el otro ya no puede pedir su versión y la
// compilación falla con "the plugin is already on the classpath with an unknown version".
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
