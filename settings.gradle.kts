rootProject.name = "inglenook"

pluginManagement {
    repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
    includeBuild("kiteui")
}

includeBuild("kiteui"){
    dependencySubstitution {
        // This tells Gradle: "When the app asks for the remote 'library', use the local ':library' project"
        substitute(module("com.lightningkite.kiteui:library")).using(project(":library"))

        // This maps the lottie library.
        // NOTE: If the folder inside the kiteui repo is actually just called 'lottie',
        // change `project(":library-lottie")` to `project(":lottie")` below!
        substitute(module("com.lightningkite.kiteui:library-lottie")).using(project(":library-lottie"))
    }
}

include(":apps")
include(":server")
include(":shared")
