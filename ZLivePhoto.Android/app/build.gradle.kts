import java.util.Properties
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zsz.zlivephoto"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zsz.zlivephoto"
        targetSdk = 37
        versionCode = 61
        versionName = "4.0.1"

        // 官方版本校验用的签名证书 SHA-256：构建时从本地 keystore 读出后写入 BuildConfig。
        // keystore 与 signing.properties 都不入库，因此其他证书 / debug 包会自动判定为非官方版本，
        // 赞助入口只提示、不展示收款码。
        buildConfigField("String", "SPONSOR_CERT_SHA256", "\"${releaseCertSha256(rootProject.file("signing.properties"))}\"")
    }

    flavorDimensions += "edition"
    productFlavors {
        // normal：完整版（Android 12+），液态玻璃 UI（Glasmio），内置 ffmpeg 转码器
        create("normal") {
            dimension = "edition"
            applicationId = "com.zsz.zlivephoto"
            minSdk = 31
            // 内置 ffmpeg 仅有 arm64-v8a 二进制；限定单 ABI，避免为不存在的 ABI 打包
            ndk { abiFilters += "arm64-v8a" }
        }
        // go：轻量版（Android 6+，Material 3 UI、单线程、不含 ffmpeg 转码器）。
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
    // iOS 风格液态玻璃（边缘折射 / 模糊 / 高光 / 阴影）
    implementation(libs.backdrop)
    debugImplementation(libs.compose.ui.tooling)

    // Glasmio 液态玻璃 UI（仅 normal flavor / Android 12+ 打包）
    add("normalImplementation", libs.glasmio.core)
    // glasmio-glass / glasmio-ui 使用 third_party 下的本地副本（已打阴影/拖选补丁），Maven 坐标已在根构建排除
    add("normalImplementation", project(":glasmio-glass-fork"))
    add("normalImplementation", project(":glasmio-ui-fork"))
    add("normalImplementation", libs.glasmio.scaffold)
    add("normalImplementation", libs.glasmio.preference)
    add("normalImplementation", libs.glasmio.icons)

    // JVM 单元测试（AndroidLogicGpsTest：验证转换不破坏 GPS）
    testImplementation("junit:junit:4.13.2")

    // 赞助二维码生成（纯离线，无网络/相机依赖）
    implementation("com.google.zxing:core:3.5.3")
}

/**
 * 读取本地 keystore 证书的 SHA-256（大写十六进制）。
 * signing.properties 缺失或读取失败时返回空串 —— 运行时据此判定「非官方版本」。
 */
fun releaseCertSha256(propsFile: File): String {
    if (!propsFile.exists()) return ""
    return runCatching {
        val props = Properties().apply { propsFile.inputStream().use { load(it) } }
        val store = props.getProperty("storeFile") ?: return ""
        val password = props.getProperty("storePassword") ?: return ""
        val alias = props.getProperty("keyAlias") ?: return ""
        val keystoreFile = File(store).let { if (it.isAbsolute) it else propsFile.parentFile.resolve(store) }
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        FileInputStream(keystoreFile).use { ks.load(it, password.toCharArray()) }
        val cert = ks.getCertificate(alias) ?: return ""
        MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02X".format(it) }
    }.getOrDefault("")
}
