package dev.aigw.core.provider.yuanbao

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 元宝账号凭证 = Cookie 会话 + 可选的 USKey 签名配置。
 *
 * 与其它供应商的 OAuth token 不同，元宝的登录态是一组 Cookie（`hy_token` + `hy_user`
 * 为主），从浏览器登录后复制得到。读取用户信息、模型、额度这些查询接口只需要 Cookie；
 * 发起对话还需要 qimei USKey 签名（见 [UskeyConfig]）。
 */
data class YuanbaoCredential(
    /** 完整 Cookie 串（浏览器里复制，含 hy_token/hy_user）。 */
    val cookie: String,
    /** 浏览器设备标识（X-device-id；cookie `_qimei_uuid42`，可为空，签名会补充）。 */
    val deviceId: String = "",
    /** 可选：USKey 签名配置。为空表示未配置（查询可用、对话会明确报错）。 */
    val uskey: UskeyConfig? = null,
) {

    /** 登录用户 id（cookie hy_user）；为空时由初始化流程通过 /api/getuserinfo 补齐。 */
    fun parseUserId(): String {
        val token = cookieValue("hy_user")
        if (token.isNotEmpty()) return token
        // 兜底：cookie 里的 _qimei_uuid42 也能当账号标识（同一浏览器同一设备）
        return cookieValue("_qimei_uuid42")
    }

    private fun cookieValue(name: String): String {
        for (part in cookie.split(";")) {
            val idx = part.indexOf('=')
            if (idx < 0) continue
            val key = part.substring(0, idx).trim()
            if (key.equals(name, ignoreCase = true)) {
                return part.substring(idx + 1).trim()
            }
        }
        return ""
    }

    fun toJson(): String = JsonObject().apply {
        addProperty("cookie", cookie)
        addProperty("deviceId", deviceId)
        uskey?.let { add("uskey", it.toJson()) }
    }.toString()

    companion object {
        fun parse(raw: String): YuanbaoCredential? {
            val obj = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
                ?: return null
            val cookie = obj.get("cookie")?.asString.orEmpty()
            if (cookie.isEmpty()) return null
            return YuanbaoCredential(
                cookie = cookie,
                deviceId = obj.get("deviceId")?.asString.orEmpty(),
                uskey = obj.get("uskey")?.asJsonObject?.let { UskeyConfig.parse(it) },
            )
        }

        /**
         * 直接解析粘贴的 Cookie 串（浏览器 DevTools → Network → 复制请求头里的 Cookie）。
         * 支持整串或 JSON（含 deviceId / uskey 字段），尽量宽松。
         */
        fun fromPasted(raw: String): YuanbaoCredential? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            // 已是凭证 JSON：原样解析
            if (trimmed.startsWith("{")) {
                return parse(trimmed)
            }
            // Cookie 串必须包含会话关键项，否则大概率贴错（比如贴了 UA/其它站点的 Cookie）
            val lower = trimmed.lowercase()
            if (!lower.contains("hy_token") && !lower.contains("hy_user")) {
                return null
            }
            return YuanbaoCredential(cookie = trimmed)
        }
    }
}

/**
 * qimei USKey 签名配置（对话必需）。
 *
 * 两个来源二选一：
 * - [serviceUrl]：本机/局域网运行的 USKey 铸造服务（如 uskey_browser.py --serve 8787），
 *   每次对话前调用 `GET <url>` 拿新鲜签名（返回 `{ok,h38,ts,params,uskey}`）。
 * - 静态签名：[uskey]/[h38]/[timestamp]/[params]（浏览器抓包填回，短暂有效）。
 */
data class UskeyConfig(
    val serviceUrl: String = "",
    val uskey: String = "",
    val h38: String = "",
    val timestamp: Long = 0L,
    val params: String = "",
) {
    fun hasStatic(): Boolean = uskey.isNotEmpty() && h38.isNotEmpty()

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("serviceUrl", serviceUrl)
        addProperty("uskey", uskey)
        addProperty("h38", h38)
        addProperty("timestamp", timestamp)
        addProperty("params", params)
    }

    companion object {
        fun parse(obj: JsonObject): UskeyConfig = UskeyConfig(
            serviceUrl = obj.get("serviceUrl")?.asString.orEmpty(),
            uskey = obj.get("uskey")?.asString.orEmpty(),
            h38 = obj.get("h38")?.asString.orEmpty(),
            timestamp = runCatching { obj.get("timestamp")?.asLong ?: 0L }.getOrDefault(0L),
            params = obj.get("params")?.asString.orEmpty(),
        )
    }
}