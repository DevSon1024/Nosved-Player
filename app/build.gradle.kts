import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?: project.file("keystore.properties").takeIf { it.exists() }
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile != null && keystorePropertiesFile.canRead()) {
        keystorePropertiesFile.inputStream().use { stream ->
            load(stream)
        }
    }
}
val splitApks = !project.hasProperty("noSplits") && !gradle.startParameter.taskNames.any {
    it.contains("debug", ignoreCase = true)
}

android {
    namespace = "com.devson.nvplayer"
    compileSdk {
        version = release(36)
    }

    androidResources {
        ignoreAssetsPatterns.add("native-v9a")
        ignoreAssetsPatterns.add("py.arm64-v8a")
    }

    defaultConfig {
        applicationId = "com.devson.nvplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 131
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        externalNativeBuild {
            cmake {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
                arguments += listOf(
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
                cFlags += "-Wl,-z,max-page-size=16384"
                cppFlags += "-Wl,-z,max-page-size=16384"
            }
        }
    }
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile != null) {
                val storeFilePath = keystoreProperties.getProperty("storeFile")
                if (!storeFilePath.isNullOrBlank()) {
                    val candidateAppFile = file(storeFilePath)
                    val candidateRootFile = rootProject.file(storeFilePath)
                    storeFile = when {
                        candidateAppFile.exists() -> candidateAppFile
                        candidateRootFile.exists() -> candidateRootFile
                        else -> candidateRootFile
                    }
                }
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storePassword = keystoreProperties.getProperty("storePassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "NPlay Beta")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "app_name", "Nosved Player")
            if (keystorePropertiesFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                debugSymbolLevel = "none"
            }
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abiName = outputImpl.filters.find { it.filterType == "ABI" }?.identifier ?: "universal"
            outputFileName = "NosvedPlayer-v${variant.versionName}-${abiName}.apk"
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
        viewBinding = true
        resValues = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "28.2.13676358"

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "assets/native-v9a/**"
            excludes += "assets/py.*/**"
        }
        jniLibs {
            useLegacyPackaging = true // false when build the aab file and true when required small size compressed apk file 
            pickFirsts += "**/libc++_shared.so"
            excludes += "**/libpython_bin.so"
        }
    }
}

dependencies {
    implementation("com.github.marlboro-advance:mediainfoAndroid:v1.0.0-fix")
    implementation(files("libs/mpvlib.aar"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)

    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-video:3.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation(libs.sora.editor.core)
    implementation(libs.sora.editor.textmate)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Biometric Authentication & Encrypted SharedPreferences / Tink
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}

androidComponents {
    val abiCodes = mapOf(
        "armeabi-v7a" to 1,
        "arm64-v8a" to 2
    )

    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier

            // Multiplies original version code by 10 and adds the ABI digit
            output.versionCode.set(
                (output.versionCode.orNull ?: 0) * 10 + (abiCodes[abi] ?: 0)
            )
        }
    }
}

composeCompiler {
    stabilityConfigurationFile.set(rootProject.file("compose_stability.conf"))
}

tasks.register("stripPythonStdlib") {
    group = "optimization"
    description = "Strips unused desktop, testing, and legacy modules from python313.zip"
    doLast {
        val zipFile = file("src/main/assets/ytdl/python313.zip")
        if (!zipFile.exists()) {
            logger.warn("python313.zip not found at ${zipFile.absolutePath}")
            return@doLast
        }
        val removablePatterns = listOf(
            Regex("""^asyncio/windows_.*"""),
            Regex("""^_pyrepl/windows_.*"""),
            Regex("""^_pyrepl/(curses|_minimal_curses|trace)\.pyc$"""),
            Regex("""^curses/.*"""),
            Regex("""^wsgiref/.*"""),
            Regex("""^(turtle|antigravity|pydoc|bdb|doctest|_osx_support|_aix_support)\.pyc$"""),
            Regex("""^ctypes/_aix\.pyc$"""),
            Regex("""^(imaplib|poplib|smtplib|mailbox|ftplib)\.pyc$"""),
            Regex("""^(tabnanny|wave|colorsys|timeit|cProfile|profile|pstats|trace)\.pyc$"""),
            Regex("""^(tkinter|unittest|idlelib)/.*""")
        )
        val tempZip = file("src/main/assets/ytdl/python313_stripped.zip")
        var removedCount = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            ZipOutputStream(tempZip.outputStream().buffered()).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val shouldRemove = removablePatterns.any { it.matches(name) }
                    if (shouldRemove) {
                        removedCount++
                    } else {
                        zos.putNextEntry(ZipEntry(name))
                        zis.copyTo(zos)
                        zos.closeEntry()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        val originalSize = zipFile.length()
        val newSize = tempZip.length()
        zipFile.delete()
        tempZip.renameTo(zipFile)
        logger.lifecycle("stripPythonStdlib: removed $removedCount unused modules. ($originalSize bytes -> $newSize bytes)")
    }
}

tasks.register("verify16kbAlignment") {
    group = "verification"
    description = "Audits and verifies 16KB ELF page size alignment on 64-bit native libraries"
    doLast {
        fun readShortLE(raf: RandomAccessFile): Short {
            val b0 = raf.read() and 0xFF
            val b1 = raf.read() and 0xFF
            return ((b1 shl 8) or b0).toShort()
        }

        fun readIntLE(raf: RandomAccessFile): Int {
            val b0 = raf.read() and 0xFF
            val b1 = raf.read() and 0xFF
            val b2 = raf.read() and 0xFF
            val b3 = raf.read() and 0xFF
            return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        }

        fun readLongLE(raf: RandomAccessFile): Long {
            val b0 = raf.read().toLong() and 0xFF
            val b1 = raf.read().toLong() and 0xFF
            val b2 = raf.read().toLong() and 0xFF
            val b3 = raf.read().toLong() and 0xFF
            val b4 = raf.read().toLong() and 0xFF
            val b5 = raf.read().toLong() and 0xFF
            val b6 = raf.read().toLong() and 0xFF
            val b7 = raf.read().toLong() and 0xFF
            return (b7 shl 56) or (b6 shl 48) or (b5 shl 40) or (b4 shl 32) or
                   (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        }

        fun readElfAlignment(file: File): Long {
            var result = 4096L
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic[0] != 0x7F.toByte() || magic[1] != 'E'.code.toByte() || magic[2] != 'L'.code.toByte() || magic[3] != 'F'.code.toByte()) {
                    result = -1L
                    return@use
                }
                val elfClass = raf.readByte().toInt()
                if (elfClass != 2) {
                    result = 4096L
                    return@use
                }

                raf.seek(32)
                val phOff = readLongLE(raf)
                raf.seek(54)
                val phEntSize = readShortLE(raf).toInt()
                val phNum = readShortLE(raf).toInt()

                var minAlign = Long.MAX_VALUE
                for (i in 0 until phNum) {
                    val entryOffset = phOff + (i * phEntSize)
                    raf.seek(entryOffset)
                    val pType = readIntLE(raf)
                    if (pType == 1) { // PT_LOAD
                        raf.seek(entryOffset + 48)
                        val pAlign = readLongLE(raf)
                        if (pAlign in 1 until minAlign) {
                            minAlign = pAlign
                        }
                    }
                }
                result = if (minAlign == Long.MAX_VALUE) 4096L else minAlign
            }
            return result
        }

        val candidateDirs = listOf(
            file("build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib"),
            file("build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib"),
            file("src/main/jniLibs")
        )
        val targetDirs = candidateDirs.filter { it.exists() }
        if (targetDirs.isEmpty()) {
            logger.warn("No native library directories found to verify. Run assembleDebug or mergeDebugNativeLibs first.")
            return@doLast
        }

        var failCount = 0
        var totalAudited = 0

        targetDirs.forEach { baseDir ->
            baseDir.walkTopDown().filter { it.isFile && it.extension == "so" }.forEach fileLoop@{ soFile ->
                val abiDir = soFile.parentFile?.name ?: ""
                val is64Bit = abiDir.contains("64") || soFile.absolutePath.contains("arm64") || soFile.absolutePath.contains("x86_64")
                if (!is64Bit) return@fileLoop

                totalAudited++
                val alignment = readElfAlignment(soFile)
                val isAligned = alignment >= 16384L
                val hexAlign = "0x" + alignment.toString(16)
                val status = if (isAligned) "PASS (16KB+)" else "FAIL (4KB)"
                logger.lifecycle("${soFile.name.padEnd(30)} [$abiDir] -> Align: $hexAlign ($status)")
                if (!isAligned) {
                    failCount++
                }
            }
        }

        if (failCount > 0) {
            throw GradleException("16KB Page Alignment Verification FAILED: $failCount library/libraries have 4KB alignment.")
        } else {
            logger.lifecycle("16KB Page Alignment Verification PASSED for all $totalAudited audited 64-bit native libraries.")
        }
    }
}
