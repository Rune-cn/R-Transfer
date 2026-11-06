package app.ai.rune.ziyong.transfer.gateway

import android.app.Application
import app.ai.rune.ziyong.transfer.App
import android.content.Context
import dev.aigw.core.gateway.GatewayEngine
import dev.aigw.core.store.KeyValueStore
import java.io.File

/**
 * 网关引擎的持有与提供。
 *
 * 为什么不是全局单例对象：引擎构造要用到 Application 的 filesDir / cacheDir，
 * 且必须随进程生命周期存在一次即可。放在 Application 里既拿到了 Context，
 * 也避免静态持有 Context 造成泄漏。
 */
class GatewayHolder(private val app: Application) {

    /** 整个进程共享一个引擎；首次访问时构造。 */
    val engine: GatewayEngine by lazy {
        GatewayEngine(
            store = EncryptedKeyValueStore(app),
            lanAddressProvider = { LanAddresses.current(app) },
            // 调用记录预载与过期清理走后台，冷启动首帧不再等全量解密
            preloadCallLogs = false,
            // 完整原文溢出到缓存目录（系统空间不足时可回收）：
            // 加密存储只留摘要，详情页按记录 id 回读全文
            spillDir = File(app.cacheDir, "call-fulltext"),
        )
    }
}

/**
 * 取得进程内唯一的网关引擎。
 *
 * 早失败（error）比返回 null 后静默出问题更容易定位：调用方必然在 UI 层，
 * 一旦为空说明 Application 初始化链路被改坏了，应当立刻暴露。
 */
val Context.gatewayEngine: GatewayEngine
    get() {
        val application = applicationContext as? App
            ?: error("applicationContext 不是 App：请检查 AndroidManifest 的 android:name")
        return application.gateway.engine
    }

/** 供 Koin 直接注入引擎时使用（避免每次调用都做类型转换）。 */
fun Application.gatewayEngineOrThrow(): GatewayEngine = gatewayEngine

/** 凭证加密存储用的 KeyValueStore 接口别名，方便 Koin 单例声明。 */
typealias GatewayStore = KeyValueStore
