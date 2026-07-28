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
        versionCode = 1
        versionName = "0.1"
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
    // room-testing y el runner de pruebas instrumentadas se suman al escribir los tests
    // de migración, para no arrastrar dependencias que todavía no se usan.
}
