import java.util.Properties
import com.nishtahir.CargoExtension
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Copy

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("org.mozilla.rust-android-gradle.rust-android")
}

extensions.configure<CargoExtension> {
    module = "../rust-core"
    libname = "rust_core"
    targets = listOf("arm64")
    profile = "release"
    val cargoHome = System.getenv("CARGO_HOME")
        ?.let(::file)
        ?: file("${System.getProperty("user.home")}/.cargo")
    val cargoBin = cargoHome.resolve("bin")
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        cargoCommand = cargoBin.resolve("cargo.exe").absolutePath
        rustcCommand = cargoBin.resolve("rustc.exe").absolutePath
    }
    val pythonExecutable = if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        System.getenv("PYTHON") ?: file("${System.getProperty("user.home")}/AppData/Local/Microsoft/WindowsApps/python.exe").absolutePath
    } else {
        System.getenv("PYTHON") ?: "python3"
    }
    pythonCommand = pythonExecutable
    apiLevel = 24
}

android {
    namespace = "erl.webdavtoon"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "erl.webdavtoon"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "1.1.7"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val rustJniLibDir = layout.buildDirectory.dir("rustJniLibs/android")

tasks.named("preBuild").configure {
    dependsOn("cargoBuild")
}

tasks.matching { task ->
    task.name.startsWith("merge") && task.name.endsWith("JniLibFolders")
}.configureEach {
    dependsOn("cargoBuild")
    inputs.dir(rustJniLibDir)
        .withPropertyName("rustJniLibDir")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.upToDateWhen { false }
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    implementation("androidx.datastore:datastore-preferences:1.1.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-core:1.0.21")
    implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-native:1.0.21")

    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.belerweb:pinyin4j:2.5.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

