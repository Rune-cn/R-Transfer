package dev.aigw.core.provider.yuanbao

import dev.aigw.core.provider.AuthKind

/** 腾讯元宝 Web 端接口常量（逆向自 https://yuanbao.tencent.com，详见仓库 docs/）。 */
object YuanbaoConstants {
    const val API_BASE = "https://yuanbao.tencent.com"

    /** 供应商稳定标识（也是模型路由前缀 `yuanbao/模型`）。 */
    const val PROVIDER_ID = "yuanbao"

    /** 前端写死的版本头（2026-11-07 从真实浏览器抓取）。 */
    const val X_COMMIT_TAG = "dfd04310"
    const val X_WEB_VERSION = "2.67.1"

    /** 默认主助手（元宝）agentId。 */
    const val DEFAULT_AGENT_ID = "naQivTmsDa"

    /** 需要 USKey 签名三件套的路径子串（前端白名单）。 */
    val USKEY_PATHS = listOf(
        "/user/agent/conversation/create",
        "/chat/",
        "/joint/login",
        "/joint/register",
        "/login/rapidPhoneLogin",
        "/login/getverificationcode",
        "/cauth/login",
        "/anon/login",
        "/accountLogic/login/config",
        "/accountLogic/login/accountList",
    )

    /** 网关注册的登录形态：粘贴 Cookie 会话。 */
    val AUTH_KIND: AuthKind = AuthKind.COOKIE_PASTE

    /** 上游域名后缀（代理开关按供应商匹配用）。 */
    val HOSTS = listOf("yuanbao.tencent.com", "static.yuanbao.tencent.com")
}