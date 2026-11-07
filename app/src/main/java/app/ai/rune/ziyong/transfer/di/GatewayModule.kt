// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.di

import app.ai.rune.ziyong.transfer.App
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
import org.koin.core.module.dsl.onKoinStarted

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

    /**
     * 把 Application 本身登记进容器。
     *
     * Koin 的 androidContext() 提供的是 Context，而 AppViewModel 需要 Application
     * （用于 startService / 安装 APK）。显式绑定可以避免各处再写 `as Application` 强转。
     */
    single { androidApplication() as App }

    /**
     * 凭证加密存储。
     *
     * 必须由这里创建并传给引擎：`GatewayEngine` 的构造参数就要一个 KeyValueStore，
     * 反向「从引擎取 store」会形成循环依赖。
     */
    single<KeyValueStore> { EncryptedKeyValueStore(androidContext()) }

    /**
     * Application 侧的持有者（唯一引擎的真正归属地）。
     *
     * 由容器构造并回填给 App：这样 `Context.gatewayEngine`（服务/磁贴走这条路）
     * 与容器里的 `GatewayEngine`（UI 走这条路）才是同一个实例。
     */
    single<GatewayHolder> {
        val app = androidApplication() as App
        GatewayHolder(app, get<KeyValueStore>()).also { app.attachGateway(it) }
    }

    /**
     * 网关引擎：**必须与前台服务拿到的是同一个实例**。
     *
     * 这里不能自己 new 一个 GatewayEngine：
     * 前台服务通过 `Context.gatewayEngine` → `App.gateway.engine` 取引擎并让它监听端口，
     * 若容器里再新建一个，UI 检查的是 A 引擎、服务启动的是 B 引擎，
     * 结果就是「点了开关没反应」——UI 永远看到 isRunning=false。
     */
    single<GatewayEngine> { get<GatewayHolder>().engine }

    // AppViewModel 需要 Application（用于启动服务/装 APK）与已有的唯一引擎实例；
    // 两者都已在容器里，Koin 按构造参数自动注入
    viewModelOf(::AppViewModel)
}

/**
 * 容器启动后立刻构造 [GatewayHolder] 并回填给 App。
 *
 * 为什么不能只靠 `single { }` 的懒加载：`App.gateway` 是 lateinit，
 * 前台服务可能在「还没有任何 UI 请求过引擎」时就通过 `Context.gatewayEngine` 访问它，
 * 此时若 Holder 尚未创建就会抛 UninitializedPropertyAccessException。
 * 在 onKoinStarted 里主动 get 一次，把它提前实例化。
 */
val gatewayStartupModule = module {
    onKoinStarted { get<GatewayHolder>() }
}
