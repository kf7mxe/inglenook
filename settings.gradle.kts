rootProject.name = "inglenook"

pluginManagement {
    includeBuild("kiteui-kf7mxe-experimental")
    repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
}


include(":apps")
include(":server")
include(":shared")
