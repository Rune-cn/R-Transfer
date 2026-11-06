import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // :core 是与 UI 完全无关的网关内核，保持纯 Kotlin/JVM：
    // 既能在本机直接跑单元测试，也能原样打进 Android 应用
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 仅使用 Android 上同样可用的 API（HttpURLConnection + 纯 Java 库）
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
