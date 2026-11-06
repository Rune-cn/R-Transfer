import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import java.util.Properties

plugins {
    alias(libs.plugins.rtransfer.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.ai.rune.ziyong.transfer"

    val properties = Properties()
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        try {
            properties.load(keystorePropertiesFile.inputStream())
        } catch (e: Exception) {
            println("Warning: Could not load keystore.properties file: ${e.message}")
        }
    }
    val storeFile = properties.getProperty("storeFile") ?: System.getenv("KEYSTORE_FILE")
    val storePassword = properties.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
    val keyAlias = properties.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS")
    val keyPassword = properties.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
    val hasCustomSigning = storeFile != null && storePassword != null && keyAlias != null && keyPassword != null

    defaultConfig {
        // If you use miuix-example source code, package it into apk or other installation package format
        // Please change the applicationId to one that does not conflict with any official release.
        applicationId = "app.ai.rune.ziyong.transfer"

        // Version control retrieved from git, with a build-plugin fallback when git is unavailable.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCustomSigning) {
            register("releaseCustom") {
                this.storeFile = rootProject.file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = if (hasCustomSigning) {
                println("Applying custom signing to debug build.")
                signingConfigs.getByName("releaseCustom")
            } else {
                println("No custom signing info. Debug build will use the default debug keystore.")
                signingConfigs.getByName("debug")
            }
            optimization.enable = false
        }
        getByName("release") {
            signingConfig = if (hasCustomSigning) {
                println("Applying custom signing to release build.")
                signingConfigs.getByName("releaseCustom")
            } else {
                println("No custom signing info. Release build will use the default debug keystore.")
                signingConfigs.getByName("debug")
            }
            vcsInfo.include = false
            optimization.enable = true
        }
        create("nonMinifiedRelease") {
            signingConfig = if (hasCustomSigning) {
                signingConfigs.getByName("releaseCustom")
            } else {
                signingConfigs.getByName("debug")
            }
        }

        create("benchmarkRelease") {
            isDebuggable = true
            signingConfig = if (hasCustomSigning) {
                signingConfigs.getByName("releaseCustom")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    flavorDimensions.add("level")

    productFlavors {
        create("Unstable") {
            dimension = "level"
            isDefault = true
            versionNameSuffix = ".${project.getGitHash()}"
            buildConfigField("int", "BUILD_LEVEL", "0")
        }

        create("Preview") {
            dimension = "level"
            versionNameSuffix = ".${project.getGitHash()}"
            buildConfigField("int", "BUILD_LEVEL", "1")
        }

        create("Stable") {
            dimension = "level"
            buildConfigField("int", "BUILD_LEVEL", "2")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        aidl = true
    }

    packaging {
        jniLibs {
            // Module-specific exclusions
            excludes += setOf(
                "lib/*/libandroidx.graphics.path.so",
                "lib/*/libdatastore_shared_counter.so",
            )
        }
    }

    androidResources {
        generateLocaleConfig = true
    }
}

/*configurations.all {
    exclude(group = "androidx.navigationevent", module = "navigationevent-compose")
}*/

aboutLibraries {
    library {
        // Enable the duplication mode, allows to merge, or link dependencies which relate
        duplicationMode = DuplicateMode.MERGE
        // Configure the duplication rule, to match "duplicates" with
        duplicationRule = DuplicateRule.SIMPLE
    }
}

dependencies {
    // 网关内核：协议转换、供应商适配、账号池、存储与容错
    implementation(project(":core"))
    // :core 以 implementation 引 gson，不传递；UI 层需要直接构造 JsonObject
    implementation("com.google.code.gson:gson:2.11.0")
    // 凭证加密存储
    implementation("androidx.security:security-crypto:1.1.0")
    // 登录走 Custom Tab：裸 ACTION_VIEW 会让进程被系统判 idle 冻结
    implementation("androidx.browser:browser:1.10.0")

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.splashscreen)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // implementation(libs.androidx.navigation3.runtime)
    /*    implementation(libs.androidx.navigationevent) {
            exclude(group = "androidx.navigation", module = "navigationevent-compose")
        }*/

    implementation(libs.compose.materialIcons)
    // Preview support only for debug builds
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.ktx.serializationJson)

    implementation(libs.hiddenapibypass)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    // aboutlibraries
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    // log
    implementation(libs.timber)

    // miuix
    implementation(libs.miuix.core)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.navigation)

    // okhttp
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    // monetcompat
    implementation(libs.monetcompat)
    implementation(libs.androidx.palette)

    implementation(libs.focus.api)

    implementation(libs.materialKolor)
    implementation(libs.materialColorUtilities)
}
