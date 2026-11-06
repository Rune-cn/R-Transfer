# ============================================================================
# material-color-utilities 跨版本包路径差异（重要）
# ============================================================================
# miuix 0.9.4 的 ThemeController 编译时同时引用了两代包路径：
#   com.materialkolor.scheme.*        （5.x）
#   com.materialkolor.dynamiccolor.*  （6.x）
# 而该库任一发布版本只含其中一套，因此 R8 必然对另一路报 Missing class。
# 我们依赖的是 6.0.0（dynamiccolor 这一路，由 material-kolor-material3 传入），
# 缺失的 scheme.* 只在旧分支上被引用，运行期不会触达 —— 用 -dontwarn 放行即可。
-dontwarn com.materialkolor.scheme.**
-dontwarn com.materialkolor.**

# ============================================================================
# 网关内核（:core）
# ============================================================================
# Trae 的凭证结构由 Gson 反射读写
-keepclassmembers class dev.aigw.core.provider.trae.** { <fields>; }
# 存储层经接口反射调用
-keep class dev.aigw.core.store.** { *; }

# 网关内嵌的 HTTP 服务器（NanoHTTPD）使用了可选的日志/压缩类
-dontwarn org.nanohttpd.**

# Tink（androidx.security.crypto 的依赖）引用了仅编译期存在的注解
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
