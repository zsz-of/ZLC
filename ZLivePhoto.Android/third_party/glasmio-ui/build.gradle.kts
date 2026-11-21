// Glasmio 0.1.3 glasmio-ui 的本地副本（Apache-2.0，见 THIRD_PARTY_NOTICES）。
// 只用于打两处补丁：
//  1. 菜单 backdrop 不再在展开结束时切到 SettledBackdrop（消除阴影闪一帧）；
//  2. 菜单支持按住拖动跨行选择与悬停开合二级菜单。
// 包名 / 类名 / 公开 API 与 Maven 0.1.3 保持一致，其余模块继续走 Maven。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.neoruaa.glasmio.ui"
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

    // vendor 代码：库自身带 lint 配置，但本地副本不参与发布质量门禁
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
    api(project(":glasmio-glass-fork"))
    implementation(libs.activity.compose)
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
}
