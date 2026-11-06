pluginManagement {
    includeBuild("build-plugins")
    repositories {
        // maven { setUrl("https://maven.aliyun.com/repository/public/") }
        // maven { setUrl("https://repo.huaweicloud.com/repository/maven/") }
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        // maven { setUrl("https://maven.scijava.org/content/repositories/public/") }
        mavenLocal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

/*includeBuild("D:/code/miuix") {
    dependencySubstitution {
        substitute(module("top.yukonga.miuix.kmp:miuix-core-android")).using(project(":miuix-core"))
        substitute(module("top.yukonga.miuix.kmp:miuix-ui-android")).using(project(":miuix-ui"))
        substitute(module("top.yukonga.miuix.kmp:miuix-shader-android")).using(project(":miuix-shader"))
        substitute(module("top.yukonga.miuix.kmp:miuix-blur-android")).using(project(":miuix-blur"))
        substitute(module("top.yukonga.miuix.kmp:miuix-preference-android")).using(project(":miuix-preference"))
        substitute(module("top.yukonga.miuix.kmp:miuix-icons-android")).using(project(":miuix-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-navigation3-ui-android")).using(project(":miuix-nav"))
    }
}*/

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // maven { setUrl("https://maven.aliyun.com/repository/public/") }
        // maven { setUrl("https://repo.huaweicloud.com/repository/maven/") }
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        // maven { setUrl("https://maven.scijava.org/content/repositories/public/") }
        // miuix 发布于 Maven Central（0.9.4 起为稳定版），无需私有仓库认证。
        // 早期若要用 GitHub Packages 的 SNAPSHOT，需提供 GITHUB_ACTOR / GITHUB_TOKEN，
        // 那会让 CI 在没有 secrets 时直接失败，故此处不保留。

        mavenLocal()
    }
}

rootProject.name = "R-Transfer"
include(
    ":app",
)
include(":baselineprofile")

// 网关内核：纯 JVM（协议 / 供应商适配 / 存储 / 容错），可直接跑单元测试，也原样打进 APK
include(":core")
// 桌面命令行工具，复用同一份内核
include(":cli")
