plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing.
//
// There is deliberately no default password: a real signing credential must never
// live in the repository. Put these in ~/.gradle/gradle.properties (never in the
// project, and never on the command line, where it lands in shell history):
//
//   MCFX_STORE_FILE=/abs/path/release.jks   (optional; defaults to keystore/mcfx-release.jks)
//   MCFX_STORE_PASSWORD=...
//   MCFX_KEY_ALIAS=...
//   MCFX_KEY_PASSWORD=...
//
// No keystore -> unsigned release APK (a fresh clone still builds).
// Keystore present but credentials missing -> fail loudly rather than ship unsigned.
val keystorePath = providers.gradleProperty("MCFX_STORE_FILE").getOrElse("keystore/mcfx-release.jks")
val keystoreFile = rootProject.file(keystorePath)
val keystorePassword = providers.gradleProperty("MCFX_STORE_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val keystoreAlias = providers.gradleProperty("MCFX_KEY_ALIAS").orNull?.takeIf { it.isNotBlank() }
val keystoreKeyPassword = providers.gradleProperty("MCFX_KEY_PASSWORD").orNull?.takeIf { it.isNotBlank() }

val hasKeystore = keystoreFile.isFile
val hasSigningCredentials = keystorePassword != null && keystoreAlias != null && keystoreKeyPassword != null

if (hasKeystore && !hasSigningCredentials) {
    throw GradleException(
        "Found a release keystore at '$keystorePath' but signing credentials are incomplete. " +
            "Add MCFX_STORE_PASSWORD, MCFX_KEY_ALIAS and MCFX_KEY_PASSWORD to " +
            "~/.gradle/gradle.properties, or remove the keystore to build an unsigned release APK.",
    )
}

android {
    namespace = "com.vibecoding.mcfx"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vibecoding.mcfx"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "1.5.2"
    }

    androidResources {
        localeFilters += listOf("zh", "en")
    }

    signingConfigs {
        if (hasKeystore && hasSigningCredentials) {
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
            // null when there is no keystore -> Gradle emits an unsigned release APK.
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
