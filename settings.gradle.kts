pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal() // Обязательно для Chaquopy
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MetadonMusic"
include(":app")
