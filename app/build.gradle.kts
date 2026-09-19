plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The release keystore is deliberately NOT part of the repository (see .gitignore):
// publishing a signing key would let anyone produce an APK that Android treats as an
// update to an installed copy. A fresh clone still builds -- `assembleRelease` then
// emits an unsigned APK, and the debug build is unaffected.
val keystoreFile = rootProject.file("keystore/mcfx-release.jks")
val keystorePassword = providers.gradleProperty("MCFX_STORE_PASSWORD").getOrElse("mcfx123456")
val keystoreAlias = providers.gradleProperty("MCFX_KEY_ALIAS").getOrElse("mcfx")
val keystoreKeyPassword = providers.gradleProperty("MCFX_KEY_PASSWORD").getOrElse("mcfx123456")

android {
    namespace = "com.vibecoding.mcfx"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vibecoding.mcfx"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "1.4.0"
    }

    androidResources {
        localeFilters += listOf("zh", "en")
    }

    signingConfigs {
        if (keystoreFile.isFile) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // v1: keep minification off so the artifact is guaranteed to build and debuggable.
            isMinifyEnabled = false
            isShrinkResources = false
            // null when the keystore is absent -> Gradle emits an unsigned release APK.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // The app uses Android's built-in org.json; unit tests run on the JVM and need a real one.
    testImplementation("org.json:json:20240303")
}
