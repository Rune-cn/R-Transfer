package app.ai.rune.ziyong.transfer

import app.ai.rune.ziyong.transfer.core.i18n.LocaleManager

import android.content.Context

import android.app.Application
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import com.kieronquinn.monetcompat.core.MonetCompat
import app.ai.rune.ziyong.transfer.core.crash.CrashHandler
import app.ai.rune.ziyong.transfer.core.env.AppConfig
import app.ai.rune.ziyong.transfer.gateway.GatewayHolder
import app.ai.rune.ziyong.transfer.di.init.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber

class App : Application() {

    /**
     * 网关引擎的持有者。
     *
     * 由 Koin 在启动时注入（见 GatewayModule），确保 UI、前台服务、磁贴
     * 拿到的是同一个引擎——两套引擎会争抢端口并让账号状态各自漂移。
     *
     * lateinit 而不是 lazy：Koin 在 `onCreate` 里 startKoin 后立即赋值，
     * 任何真正使用引擎的时机（UI 交互 / 服务启动）都晚于它。
     */
    lateinit var gateway: GatewayHolder
        private set

    /** 供 Koin 在容器构造阶段回填。 */
    internal fun attachGateway(holder: GatewayHolder) {
        gateway = holder
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
    override fun onCreate() {
        CrashHandler.init(this)
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            MonetCompat.setup(this)
            MonetCompat.enablePaletteCompat()
            MonetCompat.getInstance().updateMonetColors()
        }

        // Shell: log always enabled so release installs are debuggable too.
        Timber.plant(object : Timber.DebugTree() {
            override fun createStackElementTag(element: StackTraceElement) = super.createStackElementTag(element)
                ?.substringBefore('$')
                ?.take(23)
        })

        // 通知渠道必须在任何服务发出通知前创建，否则 Android 8+ 会直接丢通知
        getSystemService(NotificationManager::class.java)?.apply {
            createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = getString(R.string.channel_desc) },
            )
            createNotificationChannel(
                NotificationChannel(LIVE_CHANNEL_ID, "实时状态", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "仅在网关处理请求时显示，点击回到应用"
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    setShowBadge(false)
                },
            )
        }

        startKoin {
            // Koin Android Logger
            androidLogger()
            // Koin Android Context
            androidContext(this@App)
            // use modules
            modules(appModules)
        }

        // After Koin startup the file-logging tree is planted, so this entry
        // is captured both to logcat and to the on-disk run log.
        Timber.i(
            "App start: v=%s code=%d level=%s pid=%d",
            AppConfig.VERSION_NAME,
            AppConfig.VERSION_CODE,
            AppConfig.LEVEL.name,
            android.os.Process.myPid(),
        )
    }

    companion object {
        /** 常驻前台服务通知渠道。 */
        const val CHANNEL_ID = "aigw"

        /** 实时状态通知渠道（Android 16 Live Updates 走这里）。 */
        const val LIVE_CHANNEL_ID = "aigw_live"
    }
}
