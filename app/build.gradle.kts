plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sandyyera.reposteria"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sandyyera.reposteria"
        minSdk = 26          // Android 8.0, ver sección 2 de arquitectura.md
        targetSdk = 35
        // La versión es `0.<fase>.<actualización>` (ver arquitectura.md, sección 15):
        //
        // - El **0** de adelante dice que la app todavía no está terminada. Cambia a 1 cuando
        //   estén todas las fases, no antes.
        // - El del medio es **la fase en curso**, así que el número dice en qué parte del plan
        //   estamos sin tener que ir a buscarlo.
        // - El último cuenta las actualizaciones **dentro** de esa fase, y vuelve a 00 al
        //   cambiar de fase: 0.10.01 → 0.10.02 → … → 0.11.00.
        //
        // `versionCode` tiene que ser un entero que **solo suba** —Android se niega a instalar
        // encima algo con un número menor—, así que se deriva del nombre y no se lleva aparte:
        // fase × 1000 + actualización. Dos números que se escriben a mano se separan.
        versionCode = 12_008
        versionName = "0.12.08"
        // Necesario para `./gradlew :app:connectedAndroidTest` (la prueba de migración).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Room guarda acá el esquema de cada versión. Estos archivos SÍ se versionan:
    // son el registro de las migraciones y perderlos arriesga los datos del celular.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // ⚠️ FIRMA PROVISORIA — hay que cambiarla en la Fase 15, antes de repartir el APK.
            //
            // Existe porque `:app:installRelease` **no existía**: sin firma, Gradle ni siquiera
            // crea esa tarea (deja solo `uninstallRelease`, que es lo que confundió al buscarla).
            // Y esa tarea hace falta para medir: comparar el arranque con y sin depuración es la
            // mitad de la respuesta a "por qué se siente lenta" (6.7), porque la app de
            // depuración va sin optimizar y sin compilar de antemano.
            //
            // Firmar con la llave de depuración sirve para instalar en el propio celular y para
            // nada más: Google Play la rechaza, que es la buena noticia — el error aparece al
            // publicar y no después. Lo que sí puede pasar es repartir por WhatsApp un APK
            // firmado con una llave que es pública y la misma para todo el mundo. Antes de
            // repartirlo hay que crear una llave propia (`.jks`, ya ignorado por `.gitignore`,
            // y `keystore.properties` también) y apuntar esto ahí.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Genera `BuildConfig`, de donde el menú lee la versión. Sin esto habría que escribir
        // el número una segunda vez en el código, y dos copias de una versión se separan a la
        // primera actualización que alguien apure.
        buildConfig = true
    }

    // `MigrationTestHelper` lee los esquemas exportados **en el celular**, así que tienen
    // que viajar dentro del APK de pruebas. Sin esta línea la prueba de migración falla
    // con "Cannot find the schema file in the assets folder", que no dice que falte esto.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

dependencies {
    // Las fórmulas puras. La flecha va en un solo sentido: :app usa :logica, nunca al revés.
    implementation(project(":logica"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Da `collectAsStateWithLifecycle`, que deja de leer el estado cuando la pantalla no
    // se ve. Con `collectAsState` a secas la app seguiría consultando la base con la
    // pantalla en segundo plano, gastando batería sin que nadie lo mire.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    // Deja adelantar el tiempo y controlar en qué hilo corren las corrutinas. Hace falta
    // para probar un ViewModel: `viewModelScope` usa el hilo principal de Android, que en
    // una prueba de escritorio no existe y hay que reemplazar por uno de mentira.
    testImplementation(libs.kotlinx.coroutines.test)

    // Solo para `connectedAndroidTest`: la prueba de migración (5.5), que es la única que
    // necesita SQLite de verdad. No entra al APK de la app.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
