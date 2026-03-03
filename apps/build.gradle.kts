import com.lightningkite.kiteui.KiteUiPluginExtension
import java.nio.file.Files
import java.util.*
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
    alias(libs.plugins.androidApp)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
//    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.kiteui)
    alias(libs.plugins.kjsplain)
    alias(libs.plugins.kfc)
}

group = "com.kf7mxe.inglenook"
version = "1.0-SNAPSHOT"

repositories {
    maven("https://jitpack.io")
}

kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
//    iosX64()
//    iosArm64()
//    iosSimulatorArm64()
    js {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kiteui)
                api(libs.csvDurable)
                api(libs.lightningServer.client)
                api(project(":shared"))
                // Readable library for Constant
                api("com.lightningkite:readable:2.0.0")
                api(libs.lottie)
                // Ktor client for Jellyfin API
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-android:3.0.3")
                // Media3 for audio playback
                implementation("androidx.media3:media3-exoplayer:1.5.1")
                implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
                implementation("androidx.media3:media3-session:1.5.1")
                implementation("androidx.media3:media3-ui:1.5.1")
                implementation("com.vanniktech:blurhash:0.4.0-SNAPSHOT")
                // Readium for ebook reading
                implementation(libs.readium.shared)
                implementation(libs.readium.streamer)
                implementation(libs.readium.navigator)
                // AppCompat for ReaderActivity
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("androidx.constraintlayout:constraintlayout:2.2.1")
                implementation("com.google.android.material:material:1.12.0")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
                implementation("androidx.fragment:fragment-ktx:1.8.7")
            }
        }
//        val iosMain by getting {
//            dependencies {
//                implementation("io.ktor:ktor-client-darwin:3.0.3")
//            }
//        }
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:3.0.3")
                implementation(libs.indexddb)
                // epub.js for EPUB parsing and rendering on web
                implementation(npm("jszip", "3.1.5"))
                implementation(npm("epubjs", "0.3.93"))
            }
        }


        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }

//    cocoapods {
//        // Required properties
//        // Specify the required Pod version here. Otherwise, the Gradle project version is used.
//        version = "1.0"
//        summary = "Some description for a Kotlin/Native module"
//        homepage = "Link to a Kotlin/Native module homepage"
//        ios.deploymentTarget = "14.0"
//
//        // Optional properties
//        // Configure the Pod name here instead of changing the Gradle project name
//        name = "apps"
//
//        framework {
//            baseName = "apps"
//            export(project(":shared"))
//            export(libs.kiteui)
//            export(libs.lightningServer.client)
////            podfile = project.file("../example-app-ios/Podfile")
//        }
//    }
//    compilerOptions {
//        optIn.add("kotlin.time.ExperimentalTime")
//        optIn.add("kotlin.uuid.ExperimentalUuidApi")
//        freeCompilerArgs.add("-Xcontext-parameters")
//    }
}

android {
    namespace = "com.kf7mxe.inglenook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kf7mxe.inglenook"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources.excludes.add("com/lightningkite/lightningserver/lightningdb.txt")
        resources.excludes.add("com/lightningkite/lightningserver/lightningdb-log.txt")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    val props = project.rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { stream ->
        Properties().apply { load(stream) }
    }
    if (props != null && props.getProperty("signingKeystore") != null) {
        signingConfigs {
            this.create("release") {
                storeFile = project.rootProject.file(props.getProperty("signingKeystore"))
                storePassword = props.getProperty("signingPassword")
                keyAlias = props.getProperty("signingAlias")
                keyPassword = props.getProperty("signingAliasPassword")
            }
        }
        buildTypes {
            this.getByName("release") {
                this.isMinifyEnabled = false
                this.proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
                this.signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    dependencies {
        coreLibraryDesugaring(libs.desugarJdkLibs)
    }
}

// Exclude standalone PhotoView — Readium navigator bundles it in its AAR
configurations.all {
    exclude(group = "com.github.chrisbanes", module = "PhotoView")
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<YarnRootExtension>().yarnLockMismatchReport =
        YarnLockMismatchReport.WARNING
    rootProject.the<YarnRootExtension>().reportNewYarnLock = true
    rootProject.the<YarnRootExtension>().yarnLockAutoReplace = true
}

configure<KiteUiPluginExtension> {
    this.packageName = "com.kf7mxe.inglenook"
    this.iosProjectRoot = project.file("./ios/app")
}


configurations.all {
    resolutionStrategy {
//        force("0.3.13-traxexperimental-199")
        force("com.lightningkite.kiteui:library:00.3.13-kf7mxeexperimental-200-local")

        // If you also need to force lottie or other modules, do it like this:
        force("com.lightningkite.kiteui:library-lottie:00.3.13-kf7mxeexperimental-200-local")        // If there are other modules like library-jvmssr, force those too:
    }
}


// Create symlink for Kotlin/JS source maps to resolve correctly
// Source maps reference paths like ../../../../../../src/jsMain/kotlin/... which resolve to build/js/packages/src/...
// This symlink redirects those requests to the actual source location
tasks.register("setupSourceMapSymlink") {
    val packagesDir = rootProject.file("build/js/packages")
    val symlinkPath = packagesDir.resolve("src")
    val targetPath = rootProject.file("apps/src")

    doLast {
        if (!packagesDir.exists()) {
            packagesDir.mkdirs()
        }
        if (symlinkPath.exists()) {
            if (Files.isSymbolicLink(symlinkPath.toPath())) {
                return@doLast // Already set up
            }
            symlinkPath.delete()
        }
        Files.createSymbolicLink(
            symlinkPath.toPath(),
            symlinkPath.parentFile.toPath().relativize(targetPath.toPath())
        )
        println("Created source map symlink: $symlinkPath -> $targetPath")
    }
}

tasks.matching { it.name == "jsViteDev" || it.name == "jsBrowserDevelopmentRun" }.configureEach {
    dependsOn("setupSourceMapSymlink")
}

fun env(name: String, profile: String) {
    tasks.create("deployWeb${name}Init", Exec::class.java) {
        group = "deploy"
        this.dependsOn("viteBuild")
        this.environment("AWS_PROFILE", profile)
        val props = Properties()
        props.entries.forEach { environment(it.key.toString().trim('"', ' '), it.value.toString().trim('"', ' ')) }
        this.executable = "terraform"
        this.args("init")
        this.workingDir = file("terraform/$name")
    }
    tasks.create("deployWeb${name}", Exec::class.java) {
        group = "deploy"
        this.dependsOn("deployWeb${name}Init")
        this.environment("AWS_PROFILE", profile)
        val props = Properties()
        props.entries.forEach { environment(it.key.toString().trim('"', ' '), it.value.toString().trim('"', ' ')) }
        this.executable = "terraform"
        this.args("apply", "-auto-approve")
        this.workingDir = file("terraform/$name")
    }
}

env("default", "default")