import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.VariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile= rootProject.file("keystore.properties")!!
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val splitApks = !project.hasProperty("noSplits") && !gradle.startParameter.taskNames.any {
    it.contains("debug", ignoreCase = true)
}

val appVersion = "1.2.0"

android {
    namespace = "com.devson.nosvedplayer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.devson.nosvedplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 120
        versionName = appVersion

        if (!splitApks) {
            // For debug builds - only include device ABI for faster builds
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String?
                keyPassword = keystoreProperties["keyPassword"] as String?
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String?
            }
        }
    }

   buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["appLabel"] = "Nosved Player"
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "NosPlayer Beta"
        }
    }

    if (splitApks) {
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                isUniversalApk = true
            }
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
    ndkVersion = "27.0.12077973"
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiName = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier ?: "Universal"
            val buildType = variant.buildType?.replaceFirstChar { it.uppercase() } ?: "Unknown"
            (output as VariantOutputImpl).outputFileName.set("NosvedPlayer_v${appVersion}_${buildType}_${abiName}.apk")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.nextlib.media3ext)
    implementation(libs.nextlib.mediainfo)

    implementation(files("libs/lib-decoder-ffmpeg-release.aar"))

    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.androidx.work.runtime.ktx)
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}