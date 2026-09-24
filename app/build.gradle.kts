import java.util.Properties
import org.gradle.internal.os.OperatingSystem

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val skipRust = project.hasProperty("skipRust")
val ndkVersion = "28.2.13676358"

android {
    namespace = "erl.webdavtoon"
    compileSdk = 37
    ndkVersion = ndkVersion

    defaultConfig {
        applicationId = "erl.webdavtoon"
        minSdk = 24
        targetSdk = 36
        versionCode = 23
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
    }

    signingConfigs {
        val keystorePropertiesFile = rootProject.file("keystore/signing.properties")
        if (keystorePropertiesFile.exists()) {
            val properties = Properties()
            properties.load(keystorePropertiesFile.inputStream())

            create("release") {
                storeFile = rootProject.file("keystore/release.jks")
                storePassword = properties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = properties.getProperty("KEY_ALIAS")
                keyPassword = properties.getProperty("KEY_PASSWORD")
            }
        } else if (System.getenv("SIGNING_KEY") != null) {
            // Support for GitHub Actions or other CI environments
            create("release") {
                storeFile = file("release.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: System.getenv("KEY_STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug")

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Safely assign signing config
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(file("src/main/java"), file("build/generated/source/uniffi/java"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.4.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.4.10")
    }
}

tasks.matching { it.name.startsWith("check") && it.name.endsWith("AarMetadata") }.configureEach {
    enabled = false
}

val rustCoreDir = rootProject.layout.projectDirectory.dir("rust-core")
val rustJniLibDir = layout.buildDirectory.dir("rustJniLibs/android")
val rustTarget = "aarch64-linux-android"
val rustAbi = "arm64-v8a"
val rustLibName = "librust_core.so"

// Replaces the unmaintained org.mozilla.rust-android-gradle plugin, which still reads the
// removed AGP `AppExtension` and therefore cannot run on AGP 9. This project only targets arm64.
val cargoBuild = tasks.register<Exec>("cargoBuild") {
    group = "rust"
    description = "Cross-compile rust-core for $rustTarget (release)"

    val isWindows = OperatingSystem.current().isWindows
    val sdkDir = run {
        val localPropertiesFile = rootProject.file("local.properties")
        val fromProperties = if (localPropertiesFile.exists()) {
            Properties().apply { localPropertiesFile.inputStream().use { load(it) } }.getProperty("sdk.dir")
        } else {
            null
        }
        fromProperties ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: error("Android SDK location not found (local.properties sdk.dir / ANDROID_HOME)")
    }
    val ndkDir = File(sdkDir, "ndk/$ndkVersion")
    val hostTag = when {
        isWindows -> "windows-x86_64"
        OperatingSystem.current().isMacOsX -> "darwin-x86_64"
        else -> "linux-x86_64"
    }
    val exeSuffix = if (isWindows) ".exe" else ""
    val cmdSuffix = if (isWindows) ".cmd" else ""
    val toolchainBin = File(ndkDir, "toolchains/llvm/prebuilt/$hostTag/bin")
    val clang = File(toolchainBin, "aarch64-linux-android24-clang$cmdSuffix")
    val clangxx = File(toolchainBin, "aarch64-linux-android24-clang++$cmdSuffix")
    val llvmAr = File(toolchainBin, "llvm-ar$exeSuffix")

    val cargoHome = System.getenv("CARGO_HOME")?.let(::file)
        ?: file("${System.getProperty("user.home")}/.cargo")
    val cargoExe = File(cargoHome, "bin/cargo$exeSuffix")

    workingDir = rustCoreDir.asFile
    commandLine(cargoExe.absolutePath, "build", "--target", rustTarget, "--release")

    environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", clang.absolutePath)
    environment("CC_$rustTarget", clang.absolutePath)
    environment("CXX_$rustTarget", clangxx.absolutePath)
    environment("AR_$rustTarget", llvmAr.absolutePath)
    environment("CLANG_PATH", clang.absolutePath)

    inputs.dir(rustCoreDir.dir("src"))
    inputs.file(rustCoreDir.file("Cargo.toml"))
    inputs.files(rustCoreDir.file("Cargo.lock")).optional()
    outputs.file(rustCoreDir.file("target/$rustTarget/release/$rustLibName"))
}

val copyRustJniLibs = tasks.register<Copy>("copyRustJniLibs") {
    group = "rust"
    description = "Copy $rustLibName into the app's jniLibs"
    dependsOn(cargoBuild)
    from(rustCoreDir.file("target/$rustTarget/release/$rustLibName"))
    into(rustJniLibDir.map { it.dir(rustAbi) })
}

if (!skipRust) {
    android.sourceSets.getByName("main").jniLibs.srcDir(rustJniLibDir.get().asFile)
    tasks.named("preBuild").configure {
        dependsOn(copyRustJniLibs)
    }
    tasks.matching { task ->
        task.name.startsWith("merge") && task.name.endsWith("JniLibFolders")
    }.configureEach {
        dependsOn(copyRustJniLibs)
    }
}

fun registerRootApkExportTask(
    taskName: String,
    sourceRelativePath: String,
    exportedFileName: String
) = tasks.register(taskName) {
    val sourceApk = layout.buildDirectory.file(sourceRelativePath)
    val exportedApk = rootProject.layout.projectDirectory.file(exportedFileName)
    inputs.file(sourceApk)
    outputs.file(exportedApk)
    doNotTrackState("Copies the built APK into the project root for user download.")

    doLast {
        copy {
            from(sourceApk)
            into(rootProject.layout.projectDirectory)
            rename { exportedFileName }
        }
    }
}

val exportDebugApkToRoot = registerRootApkExportTask(
    taskName = "exportDebugApkToRoot",
    sourceRelativePath = "outputs/apk/debug/app-debug.apk",
    exportedFileName = "webdavtoon-debug.apk"
)

val exportReleaseApkToRoot = registerRootApkExportTask(
    taskName = "exportReleaseApkToRoot",
    sourceRelativePath = "outputs/apk/release/app-release.apk",
    exportedFileName = "webdavtoon-release.apk"
)

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(exportDebugApkToRoot)
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(exportReleaseApkToRoot)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // COUI (ColorOS-style UI, fork of Miuix)
    implementation("io.github.suqi8.coui.kmp:coui-ui:1.1.0")
    implementation("io.github.suqi8.coui.kmp:coui-icons:1.1.0")
    implementation("io.github.suqi8.coui.kmp:coui-preference:1.1.0")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")


    implementation("androidx.core:core-ktx:1.12.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation("androidx.biometric:biometric:1.1.0")
    // BiometricPrompt needs a FragmentActivity host; appcompat used to supply it transitively.
    implementation("androidx.fragment:fragment:1.8.9")

    implementation("androidx.datastore:datastore-preferences:1.1.3")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    // Glide's annotation processor is replaced by a hand-written
    // app/src/main/java/com/bumptech/glide/GeneratedAppGlideModuleImpl.java:
    // the 4.16.0 KSP processor crashes on the okhttp3-integration indexer, and kapt is
    // unavailable under AGP 9's built-in Kotlin.

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Single combined AAR (one namespace) from Maven Central. The split -core/-native
    // artifacts share namespace "wseemann.media", which AGP 9 rejects in the manifest merger.
    implementation("com.github.wseemann:FFmpegMediaMetadataRetriever:1.0.14")

    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.belerweb:pinyin4j:2.5.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

