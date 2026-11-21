pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "ZLC"
include(":app")
// Glasmio glasmio-ui / glasmio-glass 的本地副本（打补丁用），依赖坐标仍写 Maven 的 0.1.3，
// 由根构建脚本把 Maven 坐标排除并替换成这两个模块
include(":glasmio-ui-fork")
project(":glasmio-ui-fork").projectDir = file("third_party/glasmio-ui")
include(":glasmio-glass-fork")
project(":glasmio-glass-fork").projectDir = file("third_party/glasmio-glass")
