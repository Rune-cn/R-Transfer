package app.ai.rune.ziyong.transfer

import app.ai.rune.ziyong.transfer.core.i18n.LocaleManager

import android.content.Context

import android.app.Application
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
     * 用 by lazy 延迟构造：引擎会读取加密存储（要解密全部 settings 与账号），
     * 冷启动阶段立刻构造会把首帧拖慢；只有真正进入网关界面或服务启动时才初始化。
     */
    val gateway: GatewayHolder by lazy { GatewayHolder(this) }

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
}
