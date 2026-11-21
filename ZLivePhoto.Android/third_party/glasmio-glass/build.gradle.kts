// Glasmio 0.1.3 glasmio-glass 的本地副本（Apache-2.0，见 THIRD_PARTY_NOTICES）。
// 只用于一处补丁：菜单形变结束时 contentProgress 的弹簧会在 1f 附近来回振荡，
// 导致阴影在「独立阴影组」与「材质内阴影」两条路径间反复切换（展开后阴影闪一下）。
// 其余代码与 Maven 0.1.3 完全一致。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.neoruaa.glasmio.glass"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api("androidx.compose.foundation:foundation:1.12.1")
    api("androidx.compose.material3:material3:1.4.0")
    api(libs.glasmio.core)
    api(libs.glasmio.icons)
    api(libs.kyant.shapes)
}
