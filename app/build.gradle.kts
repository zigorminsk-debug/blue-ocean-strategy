import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Постоянный ключ подписи.
 *
 * Файл ключа лежит в репозитории (keystore/release.p12), а пароли передаются
 * через переменные окружения (в GitHub Actions — secrets, локально — gradle.properties
 * или переменные среды). Один и тот же ключ используется для всех версий, поэтому
 * новые APK всегда устанавливаются поверх старых без удаления приложения.
 */
val keystoreFile = rootProject.file("keystore/release.p12")
val localProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
fun secret(envName: String, propName: String, fallback: String): String =
    System.getenv(envName) ?: localProps.getProperty(propName) ?: fallback

val storePasswordValue = secret("KEYSTORE_PASSWORD", "storePassword", "BlueOcean2026Strong!")
val keyAliasValue = secret("KEY_ALIAS", "keyAlias", "blueocean")
val keyPasswordValue = secret("KEY_PASSWORD", "keyPassword", "BlueOcean2026Strong!")

// versionCode: берём из переменной окружения (в CI = номер сборки), иначе из версии
val versionCodeValue = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val versionNameValue = System.getenv("VERSION_NAME") ?: "1.0.0"

android {
    namespace = "by.blueocean.news"
    compileSdk = 34

    defaultConfig {
        applicationId = "by.blueocean.news"
        minSdk = 26
        targetSdk = 34
        versionCode = versionCodeValue
        versionName = versionNameValue
        resourceConfigurations += listOf("ru", "en")
    }

    signingConfigs {
        create("releasePersistent") {
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storeType = "PKCS12"
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (keystoreFile.exists()) signingConfigs.getByName("releasePersistent") else null
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
