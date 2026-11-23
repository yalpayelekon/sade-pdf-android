plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.yalpayelekon.sadepdf"
    compileSdk = 36
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "io.github.yalpayelekon.sadepdf"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.pdf:pdf-viewer-fragment:1.0.0-beta01")
    implementation("com.google.android.material:material:1.11.0")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
