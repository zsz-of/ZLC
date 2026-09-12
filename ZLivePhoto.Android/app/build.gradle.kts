import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zsz.zlivephoto"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zsz.zlivephoto"
        targetSdk = 36
        versionCode = 29
        versionName = "3.4.4"
    }

    flavorDimensions += "edition"
    productFlavors {
        // normal：完整版（Android 10+），应用名 ZLC，内置 ffmpeg 转码器
        create("normal") {
            dimension = "edition"
            applicationId = "com.zsz.zlivephoto"
            minSdk = 29
            // 内置 ffmpeg 仅有 arm64-v8a 二进制；限定单 ABI，避免为不存在的 ABI 打包
            ndk { abiFilters += "arm64-v8a" }
        }
        // go：轻量版（Android 6+，单线程、无动态取色、不含 ffmpeg 转码器）。
        // 独立 applicationId 确保与 normal 版可同时安装
        create("go") {
            dimension = "edition"
            applicationId = "com.zsz.zlivephoto.go"
            minSdk = 23
        }
    }

    sourceSets {
        // 内置 ffmpeg（libffmpeg.so）仅打进 normal 版；go 轻量版不含该二进制
        getByName("normal") {
            jniLibs.directories.add("src/normal/jniLibs")
        }
    }

    packaging {
        jniLibs {
            // 以 deflate 压缩存储 native 库，安装时由系统解压到 nativeLibraryDir。
            // nativeLibraryDir 是 Android 10+ 唯一可执行二进制的目录（APK 内直接 mmap
            // 的库无法用 ProcessBuilder 调用），同时压缩后 APK 体积显著减小。
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val propsFile = rootProject.file("signing.properties")
            if (propsFile.exists()) {
                val props = Properties()
                props.load(FileInputStream(propsFile))
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(props["storeFile"] as String)
                    storePassword = props["storePassword"] as String
                    keyAlias = props["keyAlias"] as String
                    keyPassword = props["keyPassword"] as String
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    debugImplementation(libs.compose.ui.tooling)

    // JVM 单元测试（AndroidLogicGpsTest：验证转换不破坏 GPS）
    testImplementation("junit:junit:4.13.2")
}
