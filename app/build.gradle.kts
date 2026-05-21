import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutLibraries)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
val splitApks = !project.hasProperty("noSplits") && !gradle.startParameter.taskNames.any {
    it.contains("debug", ignoreCase = true)
}

val appVersion = "1.4.0"

android {
    namespace = "com.devson.nvplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.devson.nvplayer"
        minSdk = 30
        targetSdk = 37
        versionCode = 140
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
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "NosPlayer Beta")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "app_name", "Nosved Player")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    if (splitApks) {
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                isUniversalApk = false
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
        viewBinding = true
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "**/kotlin/**"
            excludes += "**/*.kotlin_metadata"
            excludes += "**/*.version"
            excludes += "**/kotlin-tooling-metadata.json"
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "**/libavcodec.so",
                "**/libavfilter.so",
                "**/libavformat.so",
                "**/libavutil.so",
                "**/libswresample.so",
                "**/libswscale.so",
                "**/libpostproc.so",
                "**/libc++_shared.so"
            )
        }
    }
    ndkVersion = "27.0.12077973"
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiName = output.filters.find { 
                it.filterType == FilterConfiguration.FilterType.ABI 
            }?.identifier ?: "universal"
            val versionName = output.versionName.get()
            output.outputFileName.set("NosvedPlayer_v${versionName}-${abiName}.apk")
        }
    }
}

dependencies {
    // Project Modules (Missing from current project structure)
    // implementation(project(":core:common"))
    // implementation(project(":core:data"))
    // implementation(project(":core:domain"))
    // implementation(project(":core:media"))
    // implementation(project(":core:model"))
    // implementation(project(":core:ui"))

    // androidx
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewModel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.constraintlayout)

    // Material
    implementation(libs.google.android.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.icons)

    // media3
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.github.peerless2012.ass.media)

    // custom aar for replacing exoplayer
    implementation(files("libs/media3ext-release.aar"))
    implementation(files("libs/mediainfo-release.aar"))

    // FFMPEG kit for Video Utility
    implementation("io.github.jamaismagic.ffmpeg:ffmpeg-kit-main-full-gpl-16kb:6.1.4")
    
    // DataStore for Settings
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    
    // Room for Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)    
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)
    // kspAndroidTest(libs.hilt.compiler) // Requires kspAndroidTest configuration to be available

    // Compose
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.reorderable)

        // coil
    implementation(libs.coil.compose)
    implementation(libs.coilcore)
    implementation(libs.coil.video)
    
    // About Libraries
    implementation("com.mikepenz:aboutlibraries-core:${libs.versions.aboutlibraries.get()}")
    implementation("com.mikepenz:aboutlibraries-compose-m3:${libs.versions.aboutlibraries.get()}")

    // WorkManager for background tasks
    implementation(libs.androidx.work.runtime.ktx)

    // Networking & IO
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.commons.net)
    implementation(libs.smbj)

    // documentfile
    implementation("androidx.documentfile:documentfile:1.1.0")
    
    // Test
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.testManifest)
}