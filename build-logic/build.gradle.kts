plugins { `kotlin-dsl` }

group = "com.shasan731.networkinvestigator.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "network.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "network.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}

