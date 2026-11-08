package dev.aigw.core.provider.yuanbao

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 元宝账号凭证 = 一组 Cookie。
 *
 * 与其它供应商的 OAuth token 不同，元宝的登录态就是浏览器里的 Cookie
 * （`hy_token` + `hy_user` 为主），从浏览器登录后复制得到。
 *
 * 实测（参考 `yuanbao2api` 项目）网页版 `/api/chat` **只需要 Cookie**：
 * 会话 ID 本地生成 UUID + `isTemporary=true`，不需要 USKey 签名，
 * 也不需要调用「创建会话」接口。
 */
data class YuanbaoCredential(
    /** 完整 Cookie 串（浏览器里复制，含 hy_token/hy_user）。 */
    val cookie: String,
    /** 浏览器设备标识（X-device-id；cookie `_qimei_uuid42`，可为空）。 */
    val deviceId: String = "",
    /**
     * qimei 设备标识 h38（浏览器 localStorage `_qimei_h38`，可选）。
     *
     * 上游按设备画像做风控：缺失或与其它头不一致时，对话会被拒
     * （「抱歉，我无法回答这个问题」）。它是**设备指纹、长期有效**，
     * 从浏览器复制一次即可，不像 USKey 会过期。
     * 获取方式：浏览器 F12 → Application → Local Storage → `_qimei_h38`。
     */
    val h38: String = "",
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
        if (h38.isNotEmpty()) addProperty("h38", h38)
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
                h38 = obj.get("h38")?.asString.orEmpty(),
            )
        }

        /**
         * 直接解析粘贴的 Cookie 串（浏览器 DevTools → Network → 复制请求头里的 Cookie）。
         * 支持整串或 JSON（含 deviceId 字段），尽量宽松。
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