import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Read the secret keys from local.properties (this file is not committed to GitHub)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

android {
    namespace = "uk.ac.wlv.journly"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "uk.ac.wlv.journly"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TUMBLR_CLIENT_ID",
            "\"${localProps.getProperty("TUMBLR_CLIENT_ID", "")}\"")
        buildConfigField("String", "TUMBLR_CLIENT_SECRET",
            "\"${localProps.getProperty("TUMBLR_CLIENT_SECRET", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}