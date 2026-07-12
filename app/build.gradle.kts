plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.shasan731.networkinvestigator"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shasan731.networkinvestigator"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = providers.gradleProperty("NETWORK_INVESTIGATOR_STORE_FILE").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("NETWORK_INVESTIGATOR_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("NETWORK_INVESTIGATOR_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("NETWORK_INVESTIGATOR_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (providers.gradleProperty("NETWORK_INVESTIGATOR_STORE_FILE").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging.resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
    lint { abortOnError = true; checkReleaseBuilds = true }
}

dependencies {
    implementation(project(":core:model")); implementation(project(":core:common")); implementation(project(":core:database"))
    implementation(project(":core:datastore")); implementation(project(":core:network"))
    implementation(project(":core:diagnostics")); implementation(project(":core:reporting"))
    implementation(project(":core:security")); implementation(project(":core:ui"))
    implementation(project(":feature:dashboard")); implementation(project(":feature:investigate"))
    implementation(project(":feature:target-intelligence")); implementation(project(":feature:network-tools"))
    implementation(project(":feature:website-investigator")); implementation(project(":feature:dns-detective"))
    implementation(project(":feature:lan-explorer")); implementation(project(":feature:wifi-diagnostics"))
    implementation(project(":feature:route-investigator")); implementation(project(":feature:tls-investigator"))
    implementation(project(":feature:port-inspector")); implementation(project(":feature:connectivity-recorder"))
    implementation(project(":feature:network-compare")); implementation(project(":feature:evidence-collector"))
    implementation(libs.core.ktx); implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview); implementation(libs.compose.material3)
    implementation(libs.compose.icons); implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime); implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android); implementation(libs.hilt.navigation.compose)
    implementation(libs.biometric)
    implementation(libs.work.runtime); implementation(libs.hilt.work)
    implementation(libs.coroutines.android); implementation(libs.serialization.json)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit); testImplementation(libs.mockk); testImplementation(libs.turbine)
    debugImplementation(libs.compose.ui.tooling)
}
