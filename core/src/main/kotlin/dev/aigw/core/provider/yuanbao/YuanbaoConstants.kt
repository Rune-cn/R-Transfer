package dev.aigw.core.provider.yuanbao

import dev.aigw.core.provider.AuthKind

/** 腾讯元宝网页版接口常量。 */
object YuanbaoConstants {
    const val API_BASE = "https://yuanbao.tencent.com"

    /** 供应商稳定标识（也是模型路由前缀 `yuanbao/模型`）。 */
    const val PROVIDER_ID = "yuanbao"

    /** 前端版本头（随上游更新；不匹配一般不影响对话）。 */
    const val X_COMMIT_TAG = "dfd04310"
    const val X_WEB_VERSION = "2.63.0"

    /** 默认主助手（元宝）agentId。 */
    const val DEFAULT_AGENT_ID = "naQivTmsDa"

    /** 默认模型（元宝内部标识；实际模型由 chatModelId 决定）。 */
    const val DEFAULT_MODEL_ID = "deep_seek_v3"
    const val DEFAULT_BACKEND_MODEL = "gpt_175B_0404"

    /** 网关注册的登录形态：粘贴 Cookie 会话。 */
    val AUTH_KIND: AuthKind = AuthKind.COOKIE_PASTE

    /** 上游域名后缀（代理开关按供应商匹配用）。 */
    val HOSTS = listOf("yuanbao.tencent.com", "static.yuanbao.tencent.com")
}