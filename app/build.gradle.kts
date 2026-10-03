plugins {
    id("com.android.application")
}

android {
    namespace = "app.aquawiznotifier"
    compileSdk = 36

    val releaseKeystorePath = System.getenv("AQUAWIZ_KEYSTORE_PATH")
    signingConfigs {
        if (!releaseKeystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("AQUAWIZ_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AQUAWIZ_KEY_ALIAS")
                keyPassword = System.getenv("AQUAWIZ_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "app.aquawiznotifier"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "0.8.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.work:work-runtime:2.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("org.json:json:20240303")
}
