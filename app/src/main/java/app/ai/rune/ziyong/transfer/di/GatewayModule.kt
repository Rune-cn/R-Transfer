// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.di

import app.ai.rune.ziyong.transfer.App
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.gateway.EncryptedKeyValueStore
import app.ai.rune.ziyong.transfer.gateway.GatewayHolder
import dev.aigw.core.gateway.GatewayEngine
import dev.aigw.core.store.KeyValueStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import android.app.Application

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
     * 把 Application 登记进容器。
     *
     * **必须显式声明类型为 Application**：`single { androidApplication() as App }`
     * 会让 Koin 把 bean 的类型推断成 `App`，而 `AppViewModel` 的构造参数是
     * `Application`——两者在 Koin 的类型索引里不是同一个 key，
     * 注入会直接失败（页面整个渲染不出来，表现为「点了没反应」）。
     *
     * ⚠ 绝对不能用 `androidApplication()` 来定义它：
     * Koin 的 `androidApplication()` 内部就是 `get<Application>()`，
     * 用它定义 `single<Application>` 等于「解析自己」，会无限递归直到 StackOverflowError。
     * 必须从 Context 直接取 applicationContext。
     */
    single<Application> { androidContext().applicationContext as Application }

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
        val app = androidContext().applicationContext as App
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

    /**
     * 内置元宝 USKey 铸造器（WebView 加载元宝页面 + qimei 现场签名）。
     *
     * 懒启动：元宝账号存在时才创建 WebView；服务地址由 AppViewModel 自动写入账号凭证。
     */
    single<UskeyMinterManager> { UskeyMinterManager(androidContext()) }

    // AppViewModel 需要 Application（用于启动服务/装 APK）与已有的唯一引擎实例；
    // 两者都已在容器里，Koin 按构造参数自动注入
    viewModelOf(::AppViewModel)
}
