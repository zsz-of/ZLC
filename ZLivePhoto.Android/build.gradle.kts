plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// glasmio-ui / glasmio-glass 用 third_party 下的本地副本替换 Maven 0.1.3（补丁说明见
// third_party/*/build.gradle.kts）。其余 Glasmio 模块（core / glass / icons / scaffold /
// preference）继续用 Maven 产物；Maven 坐标里对这两个模块的依赖统一排除，由本地模块提供
// 同名同包实现，避免重复类。
subprojects {
    configurations.all {
        exclude(group = "io.github.neoruaa.glasmio", module = "glasmio-ui")
        exclude(group = "io.github.neoruaa.glasmio", module = "glasmio-glass")
    }
}
