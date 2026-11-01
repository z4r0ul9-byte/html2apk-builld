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
        // Diperlukan untuk mengambil ARSCLib via JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "html2apk-builder"
include(":app")