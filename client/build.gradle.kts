plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.chatclient"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.chatclient"
        minSdk = 25
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
}
