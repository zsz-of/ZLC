plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.qmdeve.liquidglass"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.annotation.jvm)
    implementation(libs.dynamicanimation)
}
