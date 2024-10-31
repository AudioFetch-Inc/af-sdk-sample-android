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

        maven(url = "https://storage.googleapis.com/download.flutter.io")
        maven(url = "../af-disco-android")
    }

}

rootProject.name = "AF SDK Sample"
include(":app")
 