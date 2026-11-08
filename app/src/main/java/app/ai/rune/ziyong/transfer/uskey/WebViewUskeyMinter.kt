// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.uskey

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * 用系统 WebView（Chromium 内核）铸造元宝 qimei USKey。
 *
 * 原理与逆向包 `uskey_browser.py` 一致：加载真实元宝页面（qimei SDK 随 webpack 就绪），
 * 通过 `webpackChunk_N_E.push(...)` 拿到 require → 模块 94482（QimeiWeb）→
 * `getUSKeySync("7800385", h38, params)` 现场签名。
 *
 * 与 headless Chromium 的关系：Android 系统 WebView 即 Chromium，行为同源；
 * 因此 uskey_browser.py 的注入脚本可直接移植，无需内置 Chromium（省约 300MB）。
 *
 * 线程模型（WebView 的铁律）：
 * - WebView 只能在主线程创建与调用 → 所有操作经 [handler] post 到主线程；
 * - `evaluateJavascript` 回调也在主线程 → 用 [CompletableFuture] 把结果桥回调用线程；
 * - [mint] 是同步阻塞 API（供本地 HTTP 服务线程调用），内部等待 future。
 *
 * 生命周期：随宿主进程常驻。App 重启后 WebView 重建，localStorage（h38）由
 * WebView 私有目录持久化，同一设备保持同一设备指纹。
 */
class WebViewUskeyMinter(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    /** 主线程可达；null = 尚未创建。 */
    @Volatile
    private var webView: WebView? = null

    /** 已注入过的 Cookie（避免重复注入使页面重载）。 */
    @Volatile
    private var injectedCookie: String? = null

    /** 一次页面加载是否最终成功（onPageFinished + h38 就绪）。 */
    @Volatile
    private var pageReady = false

    /**
     * 页面是否已加载完成（onPageFinished）。
     *
     * 必须与 h38 一起判定：localStorage 是 WebView 共享的，登录时写入的 h38 在页面
     * 还没加载完时就能读到——只查 h38 会在 webpack 依赖未就绪时过早铸造
     * （表现为 `Cannot read properties of undefined (reading 'call')`）。
     */
    @Volatile
    private var pageFinished = false

    /** 并发铸造互斥：同一时间只能有一个 mint 在途。 */
    private val mintLock = Any()

    /** 最近一次铸造失败原因（供 UI 展示）。 */
    @Volatile
    var lastError: String = ""
        private set

    /**
     * 同步铸造一个 USKey。
     *
     * @param cookie 元宝账号的 Cookie 串；传入有效值可让页面以登录态加载（避免被重定向到登录页）。
     * @param timeoutMs 总超时（含首次页面加载；页面已就绪时通常 <1.5s）。
     * @return 铸造结果；[MintResult.ok] 为 true 时携带签名四件套。
     */
    fun mint(cookie: String, timeoutMs: Long = 30_000): MintResult {
        synchronized(mintLock) {
            // 页面尚未就绪：确保 WebView 存在并加载页面（阻塞等待）
            if (!pageReady) {
                val loaded = ensureLoaded(cookie, timeoutMs)
                if (!loaded) return MintResult.failure(lastError.ifEmpty { "页面加载超时" })
            }
            // 铸造必须回到主线程执行 evaluateJavascript
            val deadline = System.currentTimeMillis() + timeoutMs
            val future = CompletableFuture<MintResult>()
            handler.post {
                try {
                    val view = webView
                    if (view == null) {
                        future.complete(MintResult.failure("WebView 未创建"))
                        return@post
                    }
                    // 兼容 h38 在 localStorage 但 Cookie 已经变化的情况：先保证 Cookie 一致
                    val current = injectedCookie
                    if (current != cookie && cookie.isNotEmpty()) {
                        injectCookie(cookie, view)
                    }
                    // 先触发铸造（异步写入 window.__uskayMintResult），再轮询读取
                    view.evaluateJavascript(MINT_TRIGGER_SCRIPT) { _ ->
                        mintPoll(view, future, System.currentTimeMillis(), deadline)
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "铸造异常"
                    future.complete(MintResult.failure(lastError))
                }
            }
            return try {
                future.get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                lastError = "铸造超时：" + (e.message ?: "未知错误")
                MintResult.failure(lastError)
            }
        }
    }

    /** 轮询读取铸造结果（webpack push 回调可能异步执行）。 */
    private fun mintPoll(view: WebView, future: CompletableFuture<MintResult>, start: Long, deadline: Long) {
        if (future.isDone) return
        try {
            view.evaluateJavascript(READ_RESULT_SCRIPT) { raw ->
                val result = parseResult(raw)
                if (result.ok || result.reason != "pending") {
                    future.complete(result)
                } else if (System.currentTimeMillis() >= deadline) {
                    lastError = "铸造超时（结果未在 ${deadline - start}ms 内返回）"
                    future.complete(MintResult.failure(lastError))
                } else {
                    handler.postDelayed({ mintPoll(view, future, start, deadline) }, 400)
                }
            }
        } catch (e: Exception) {
            if (System.currentTimeMillis() >= deadline) {
                lastError = "铸造轮询异常：" + (e.message ?: "未知错误")
                future.complete(MintResult.failure(lastError))
            } else {
                handler.postDelayed({ mintPoll(view, future, start, deadline) }, 400)
            }
        }
    }

    /** 释放 WebView（仅供彻底清理/调试；正常情况常驻）。 */
    fun destroy() {
        synchronized(mintLock) {
            handler.post {
                webView?.let { view ->
                    view.stopLoading()
                    // 先断开回调引用再 destroy，避免 onPageFinished 回调进死对象
                    view.webViewClient = WebViewClient()
                    view.settings.javaScriptEnabled = false
                    view.destroy()
                }
                webView = null
                pageReady = false
                injectedCookie = null
            }
        }
    }

    // ------------------------------------------------------------------ 私有

    /** 创建 WebView（主线程）并加载元宝页面，等待页面就绪（h38 可用）。 */
    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureLoaded(cookie: String, timeoutMs: Long): Boolean {
        val ready = CompletableFuture<Boolean>()
        handler.post {
            try {
                val view = webView ?: createWebView()
                webView = view
                if (!pageReady) {
                    injectCookie(cookie, view)
                    // 页面加载失败/超时也放行到 evaluateJavascript：由后续轮询决定成败
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            // 页面完成不代表 qimei 就绪；两者都满足才算就绪（见 readyCheck）
                            pageFinished = true
                        }
                    }
                    pageFinished = false
                    view.loadUrl(CHAT_PAGE_URL)
                }
                readyCheck(view, ready, System.currentTimeMillis() + timeoutMs)
            } catch (e: Exception) {
                lastError = "WebView 初始化失败：" + (e.message ?: "未知错误")
                ready.complete(false)
            }
        }
        return try {
            ready.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            lastError = "等待页面就绪超时（${timeoutMs}ms）"
            false
        }
    }

    private fun createWebView(): WebView {
        // 部分 OEM（华为/部分老 ROM）对非 Activity Context 创建 WebView 要求有主题；
        // 包一个隐式主题的 ContextThemeWrapper 兼容。
        val themed = ContextThemeWrapper(appContext, android.R.style.Theme_DeviceDefault)
        return WebView(themed).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true // localStorage（h38 存这里）必须开
                databaseEnabled = true
                allowFileAccess = false
                loadsImagesAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                userAgentString = CHROME_DESKTOP_UA // 与逆向 headless 浏览器一致，降低风控差异
            }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    private fun injectCookie(cookie: String, view: WebView) {
        if (cookie.isEmpty() || injectedCookie == cookie) return
        runCatching {
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                removeAllCookie() // 只保留本次会话，避免旧 Cookie 串台
            }
            for (part in cookie.split(";")) {
                val idx = part.indexOf('=')
                if (idx <= 0) continue
                val name = part.substring(0, idx).trim()
                if (name.isEmpty()) continue
                val value = part.substring(idx + 1).trim()
                CookieManager.getInstance().setCookie(
                    "https://yuanbao.tencent.com",
                    "$name=$value; domain=.yuanbao.tencent.com; path=/",
                )
            }
            // 同步到 WebView 的 CookieStore
            android.webkit.CookieManager.getInstance().flush()
        }.onFailure { e ->
            Timber.w("USKey 铸造器注入 Cookie 失败：${e.message}")
        }
        injectedCookie = cookie
    }

    /**
     * 轮询等待 qimei 就绪（localStorage._qimei_h38 出现）。
     * uskey_browser.py 的做法是 sleep 重试；这里改成主线程延时轮询（不阻塞 UI）。
     */
    private fun readyCheck(view: WebView, ready: CompletableFuture<Boolean>, deadline: Long) {
        if (ready.isDone) return
        val now = System.currentTimeMillis()
        if (now >= deadline) {
            lastError = if (!pageFinished) {
                "元宝页面加载超时（可能网络不通或 WebView 被系统限制）"
            } else {
                "页面已加载但 qimei 未就绪（h38 不可读）"
            }
            ready.complete(false)
            return
        }
        if (!pageFinished) {
            // 页面还没加载完：不要提前铸造（webpack 依赖未就绪会报 reading 'call'）
            handler.postDelayed({ readyCheck(view, ready, deadline) }, 500)
            return
        }
        try {
            view.evaluateJavascript(H38_READY_SCRIPT) { raw ->
                val has = raw != "null" && raw.isNotBlank() && raw != "false" &&
                    raw.removeSurrounding("\"").isNotEmpty()
                if (has && raw != "undefined") {
                    pageReady = true
                    lastError = ""
                    ready.complete(true)
                } else {
                    handler.postDelayed({ readyCheck(view, ready, deadline) }, 800)
                }
            }
        } catch (e: Exception) {
            handler.postDelayed({ readyCheck(view, ready, deadline) }, 800)
        }
    }

    /**
     * 解析 evaluateJavascript 回传的结果。
     *
     * evaluateJavascript 对「对象返回值」回传的是 JSON 文本（`{...}`）；
     * 个别 WebView 版本可能包一层字符串（`"{...}"`），这里两层都兼容。
     */
    private fun parseResult(raw: String?): MintResult {
        if (raw.isNullOrBlank()) return MintResult.failure("铸造脚本无返回")
        var text = raw.trim()
        // 若被包成字符串（首尾是引号），剥掉引号
        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            lastError = "铸造结果非 JSON：$text"
            return MintResult.failure(lastError)
        }
        if (!json.optBoolean("ok", false)) {
            val reason = json.optString("reason", "未知原因")
            // 「pending」不是最终失败：调用方继续轮询
            if (reason == "pending") return MintResult(ok = false, reason = "pending")
            lastError = "铸造失败：$reason"
            return MintResult.failure(lastError)
        }
        val uskey = json.optString("uskey", "")
        if (uskey.isEmpty()) {
            lastError = "铸造结果缺少 uskey"
            return MintResult.failure(lastError)
        }
        lastError = ""
        return MintResult(
            ok = true,
            uskey = uskey,
            h38 = json.optString("h38", ""),
            timestamp = json.optLong("ts", 0L),
            params = json.optString("params", ""),
        )
    }

    companion object {
        /** 与逆向包 uskey_browser.py 相同的页面。 */
        private const val CHAT_PAGE_URL = "https://yuanbao.tencent.com/chat/naQivTmsDa"

        /** 与逆向 headless Chromium 一致的桌面 UA（元宝按 web 端处理）。 */
        private const val CHROME_DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /** 判断 qimei 是否就绪：localStorage 里出现 h38（剥引号后非空）。 */
        private const val H38_READY_SCRIPT =
            "String(localStorage.getItem('_qimei_h38')||'')"

        /**
         * 触发铸造（写入全局 `window.__uskayMintResult`）。
         *
         * webpack 的 `push` 在页面运行时已就绪时**同步**执行回调；但为兼容
         * 未就绪/异步场景，结果不依赖本脚本的返回值——由 [READ_RESULT_SCRIPT]
         * 轮询读取全局，直到变为终态（ok=true 或 reason 非 pending）。
         */
        private val MINT_TRIGGER_SCRIPT = """
            (function(){
              window.__uskayMintResult = {ok:false,reason:'pending'};
              var h38 = String(localStorage.getItem('_qimei_h38')||'');
              if(!h38){ window.__uskayMintResult = {ok:false,reason:'no-h38'}; return; }
              var attempts = 0, lastErr = '';
              function fail(reason){
                window.__uskayMintResult = {ok:false, reason: reason + (lastErr ? ('; last=' + lastErr) : '')};
              }
              function useModule(M, ts, params){
                var QW = (M && M.QimeiWeb) || M;
                if (typeof QW !== 'function') return false;
                try {
                  var inst = new QW({appKey:'', disableDebugger:true});
                  var s = inst.getUSKeySync('7800385', h38, params);
                  if (!s) { lastErr = 'empty-uskey'; return false; }
                  window.__uskayMintResult = {ok:true, h38:h38, ts:ts, params:params, uskey:encodeURIComponent(s)};
                  return true;
                } catch(e) { lastErr = 'sig:' + (e && e.message || e); return false; }
              }
              function tryOnce(){
                attempts++;
                var ts = Date.now();
                var params = 'h38=' + h38 + '&timestamp=' + ts + '&platform=web';
                var done = false;
                try {
                  window.webpackChunk_N_E.push([[Math.random()], {}, function(req){
                    if (done) return;
                    // 1) 先试结论里的模块 id
                    try {
                      var M = req(94482);
                      if (useModule(M, ts, params)) { done = true; return; }
                    } catch(e) { lastErr = 'mod94482:' + (e && e.message || e); }
                    // 2) 兜底：扫描模块表找导出 QimeiWeb 的模块（前端改版后 id 可能变化）
                    try {
                      var mods = req.m || {};
                      var ids = Object.keys(mods);
                      for (var i = 0; i < ids.length && i < 2000; i++) {
                        var id = ids[i];
                        if (String(id) === '94482') continue;
                        try {
                          var MM = req(id);
                          if (MM && typeof MM.QimeiWeb === 'function') {
                            if (useModule(MM, ts, params)) { done = true; return; }
                          }
                        } catch(e2) { /* 单个模块执行失败属正常，继续 */ }
                      }
                    } catch(e3) { lastErr = 'scan:' + (e3 && e3.message || e3); }
                  }]);
                } catch(e4) { lastErr = 'push:' + (e4 && e4.message || e4); }
                if (!done) {
                  if (attempts >= 6) fail('mint-failed');
                  else setTimeout(tryOnce, 800);
                }
              }
              // h38 出现得比 webpack 依赖就绪更早：先等页面 JS 稳定再铸造
              setTimeout(tryOnce, 1500);
            })()
        """.trimIndent()

        /** 读取铸造全局；返回对象（evaluateJavascript 自动序列化为 JSON）。 */
        private const val READ_RESULT_SCRIPT =
            "window.__uskayMintResult || {ok:false,reason:'pending'}"
    }
}

/** 一次 USKey 铸造的结果，与本地/外部服务契约一致。 */
data class MintResult(
    val ok: Boolean,
    val uskey: String = "",
    val h38: String = "",
    val timestamp: Long = 0L,
    val params: String = "",
    val reason: String = "",
) {
    companion object {
        fun failure(reason: String): MintResult =
            MintResult(ok = false, reason = reason)
    }
}