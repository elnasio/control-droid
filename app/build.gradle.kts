import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}

fun localProperty(name: String): String? = localProperties.getProperty(name)

val signingKeystorePath = localProperty("controlDroid.keystorePath")
    ?: providers.gradleProperty("controlDroid.keystorePath").orNull
    ?: providers.environmentVariable("CONTROL_DROID_KEYSTORE_PATH").orNull
    ?: "/Users/morieshutapea/AndroidStudioProjects/control-droid/control-droid.jks"
val signingStorePassword = localProperty("controlDroid.storePassword")
    ?: providers.gradleProperty("controlDroid.storePassword").orNull
    ?: providers.environmentVariable("CONTROL_DROID_STORE_PASSWORD").orNull
val signingKeyAlias = localProperty("controlDroid.keyAlias")
    ?: providers.gradleProperty("controlDroid.keyAlias").orNull
    ?: providers.environmentVariable("CONTROL_DROID_KEY_ALIAS").orNull
val signingKeyPassword = localProperty("controlDroid.keyPassword")
    ?: providers.gradleProperty("controlDroid.keyPassword").orNull
    ?: providers.environmentVariable("CONTROL_DROID_KEY_PASSWORD").orNull

android {
    namespace = "com.mories.control_droid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mories.control_droid"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(signingKeystorePath)
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

@Suppress("DEPRECATION")
android.applicationVariants.all {
    if (buildType.name == "release") {
        val releaseVersionName = (versionName ?: "unknown")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val releaseVersionCode = versionCode

        outputs.all {
            (this as com.android.build.gradle.api.ApkVariantOutput).outputFileName =
                "control-droid-$releaseVersionName-$releaseVersionCode.apk"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui-components"))

    // Core & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.zxing.android.embedded)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
