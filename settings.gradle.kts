pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "light-notifications"

include(":app")
// Not "app": a module literally named `server` is skipped by the Light SDK
// plugin's source scan, manifest check and dependency allowlist, which is the
// only place an AccessibilityService and an overlay permission can be
// declared. Everything privileged lives here; nothing of it sits in :app.
include(":server")

// Consume the Light SDK as an adjacent composite build, matching the other
// tools in this tree. Override with -Pnotifications.sdkPath=/path/to/light-sdk.
val lightSdkPath = providers.gradleProperty("notifications.sdkPath").getOrElse("../light-sdk")

includeBuild(lightSdkPath) {
    dependencySubstitution {
        substitute(module("com.thelightphone:ui")).using(project(":sdk:ui"))
        substitute(module("com.thelightphone:client")).using(project(":sdk:client"))
        substitute(module("com.thelightphone:server")).using(project(":sdk:server"))
        substitute(module("com.thelightphone:shared")).using(project(":sdk:shared"))
    }
}
