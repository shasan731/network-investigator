pluginManagement {
    includeBuild("build-logic")
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
    }
}

rootProject.name = "network-investigator-android"

include(":app")
include(":core:common", ":core:model", ":core:database", ":core:datastore")
include(":core:network", ":core:diagnostics", ":core:reporting", ":core:security", ":core:ui")
include(":feature:dashboard", ":feature:investigate", ":feature:target-intelligence")
include(":feature:network-tools", ":feature:website-investigator", ":feature:dns-detective")
include(":feature:lan-explorer", ":feature:wifi-diagnostics", ":feature:route-investigator")
include(":feature:tls-investigator", ":feature:port-inspector", ":feature:connectivity-recorder")
include(":feature:network-compare", ":feature:evidence-collector")

