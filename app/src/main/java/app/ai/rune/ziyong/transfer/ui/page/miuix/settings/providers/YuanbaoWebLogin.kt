// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.providers

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 元宝「内置浏览器登录」。
 *
 * 解决用户手动 F12 复制 Cookie / localStorage 的麻烦：
 * 1. 全屏 WebView 打开元宝首页，用户正常登录（扫码 / 手机号 / 微信等）；
 * 2. 点「完成」时，App 自动读取 CookieManager 的 Cookie 与页面的 `_qimei_h38`；
 * 3. 两者一起交给账号导入流程——用户全程不需要开发者工具。
 *
 * 与网关请求的画像一致性：
 * - Cookie 与 h38 都产自这个 WebView（同一浏览器会话），因此天然配对；
 * - WebView 使用桌面 UA，让元宝渲染完整网页版（移动版页面可能隐藏部分登录入口）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YuanbaoWebLoginDialog(
    onDismiss: () -> Unit,
    onDone: (cookie: String, h38: String, webView: WebView) -> Unit,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("请先在上方页面登录元宝") }
    var reading by remember { mutableStateOf(false) }
    // 登录成功后 WebView 被交给铸造器接管，不能再销毁（否则铸造器没页面可用）
    var handedOff by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true // h38 存在 localStorage，必须开
                databaseEnabled = true
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                userAgentString = DESKTOP_UA
                // 元宝登录可能走 target=_blank：关闭多窗口让它在本 WebView 打开
                setSupportMultipleWindows(false)
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    // 页面每次加载完检查一次登录态，给用户即时反馈
                    val cookie = CookieManager.getInstance().getCookie(HOME_URL).orEmpty()
                    if (readCookieToken(cookie).isNotEmpty()) {
                        status = "已检测到登录，点右上角「完成」"
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        webView.loadUrl(HOME_URL)
        onDispose {
            // 已交接给铸造器：保留 WebView（它常驻后台铸造 USKey）
            if (!handedOff) {
                runCatching {
                    webView.stopLoading()
                    webView.webViewClient = WebViewClient()
                    webView.destroy()
                }
            }
        }
    }

    fun finish() {
        if (reading) return
        val cookie = CookieManager.getInstance().getCookie(HOME_URL).orEmpty()
        if (cookie.isEmpty() || readCookieToken(cookie).isEmpty()) {
            status = "还没有检测到登录：请先在上方页面完成登录"
            return
        }
        reading = true
        status = "正在读取设备标识…"
        webView.evaluateJavascript("String(localStorage.getItem('_qimei_h38')||'')") { raw ->
            val h38 = raw.orEmpty().trim().removeSurrounding("\"").takeIf {
                it.isNotEmpty() && it != "null"
            }.orEmpty()
            reading = false
            handedOff = true
            onDone(cookie, h38, webView)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部工具栏：标题 + 状态 + 完成/取消
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "登录腾讯元宝",
                            style = MiuixTheme.textStyles.title3,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = status,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    TextButton(text = "取消", onClick = onDismiss)
                    TextButton(
                        text = if (reading) "读取中…" else "完成",
                        enabled = !reading,
                        onClick = { finish() },
                    )
                }
                // WebView 主体
                AndroidView(
                    factory = { webView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

/** 从 Cookie 串里取会话令牌（hy_token）；没有表示尚未登录。 */
private fun readCookieToken(cookie: String): String {
    for (part in cookie.split(";")) {
        val idx = part.indexOf('=')
        if (idx <= 0) continue
        if (part.substring(0, idx).trim().equals("hy_token", ignoreCase = true)) {
            return part.substring(idx + 1).trim()
        }
    }
    return ""
}

private const val HOME_URL = "https://yuanbao.tencent.com"

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"