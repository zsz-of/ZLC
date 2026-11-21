# Compose
-dontwarn androidx.compose.**

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# 保留核心转换逻辑
-keep class com.zsz.zlivephoto.core.** { *; }

# Glasmio 液态玻璃 UI：库内大量使用运行时着色器/JNI（libglasmio_liquid_menu_sdf.so）
# 与反射式资源访问，强制保留以避免 R8 裁剪后启动即崩溃
-keep class io.github.neoruaa.glasmio.** { *; }
-dontwarn io.github.neoruaa.glasmio.**

# kyant backdrop（自研玻璃组件的底层实现）：同样依赖运行时着色器与合成层
-keep class com.kyant.backdrop.** { *; }
-dontwarn com.kyant.backdrop.**
