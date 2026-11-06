// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.di

import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.gateway.EncryptedKeyValueStore
import app.ai.rune.ziyong.transfer.gateway.GatewayHolder
import app.ai.rune.ziyong.transfer.gateway.LanAddresses
import dev.aigw.core.gateway.GatewayEngine
import dev.aigw.core.store.KeyValueStore
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * 网关内核的依赖装配。
 *
 * 与模板自带的 settings/theme 模块分开，是为了让「内核」与「应用外观」两条依赖链
 * 互不牵连：换主题系统不需要动网关，反之亦然。
 *
 * 引擎在这里声明为 **single**：整个进程只能有一个 HTTP 监听器与一份账号池，
 * 两个实例会争抢同一端口并让账号状态各自漂移。
 */
val gatewayModule = module {

    /** 凭证加密存储。 */
    single<KeyValueStore> { EncryptedKeyValueStore(androidContext()) }

    /** 网关引擎：唯一实例，延迟到首次注入时构造。 */
    single<GatewayEngine> {
        GatewayEngine(
            store = get<KeyValueStore>(),
            lanAddressProvider = { LanAddresses.current(androidContext()) },
            // 调用记录预载与过期清理走后台，冷启动首帧不等全量解密
            preloadCallLogs = false,
            // 完整原文溢出到缓存目录（系统空间不足时可回收）
            spillDir = java.io.File(androidContext().cacheDir, "call-fulltext"),
        )
    }

    /**
     * Application 侧的持有者。
     *
     * 前台服务（GatewayService 等）是通过 Context 拿引擎的，走的是
     * `Context.gatewayEngine` → `App.gateway` 这条路径；这里把它注册进容器，
     * 让 UI 侧与服务侧拿到的是**同一个**实例，避免出现两套账号池。
     */
    single { (androidApplication() as app.ai.rune.ziyong.transfer.App).gateway }

    // AppViewModel 需要 Application（用于启动服务/装 APK）与已有的唯一引擎实例；
    // 两者都已在容器里，Koin 按构造参数自动注入
    viewModelOf(::AppViewModel)
}
