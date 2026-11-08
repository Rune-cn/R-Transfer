package dev.aigw.core.provider.yuanbao

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.core.provider.AggregatedChatCall
import dev.aigw.core.provider.ChatCall
import dev.aigw.core.provider.CreditInfo
import dev.aigw.core.provider.ErrorKind
import dev.aigw.core.provider.FailedChatCall
import dev.aigw.core.provider.LineTransformStream
import dev.aigw.core.provider.OpenAiSseAggregator
import dev.aigw.core.provider.Provider
import dev.aigw.core.provider.ProviderAccount
import dev.aigw.core.provider.ProviderHooks
import dev.aigw.core.provider.ProviderModel
import dev.aigw.core.provider.ProviderModelCatalogView
import dev.aigw.core.provider.StreamFailureChatCall
import dev.aigw.core.provider.StreamingChatCall
import dev.aigw.core.provider.UpstreamError
import dev.aigw.core.provider.isStreamingBody
import dev.aigw.core.provider.requestedModelOf
import dev.aigw.core.util.HttpSupport
import dev.aigw.core.util.arrayOrNull
import dev.aigw.core.util.bodySnippet
import dev.aigw.core.util.firstString
import dev.aigw.core.util.longOrNull
import dev.aigw.core.util.newCompletionId
import dev.aigw.core.util.objOrNull
import dev.aigw.core.util.stringOrNull
import java.net.HttpURLConnection
import java.util.UUID

/**
 * 腾讯元宝（`yuanbao.tencent.com`）网页版供应商。
 *
 * 协议要点（对照实测可用的开源实现 `10020099/yuanbao2api`）：
 * - 鉴权只需要 **Cookie 会话**（`hy_token` + `hy_user`），浏览器登录后复制导入；
 * - 对话 `POST /api/chat/{conversationId}` 是 SSE 流；**`conversationId` 本地生成 UUID**，
 *   请求体带 `isTemporary: true`（临时会话，不在元宝界面留下记录）；
 * - **不需要 qimei USKey 签名**（早期逆向结论有误：那条路依赖浏览器环境且不稳定）。
 *   只需要基础浏览器头 + `x-agentid: {agentId}/{conversationId}`。
 *
 * 不支持 OpenAI function calling：客户端带 `tools` 时明确拒绝（见 [openChat]），
 * 避免客户端收到「没有 tool_calls 的回复」后自行猜测。元宝的联网搜索由上游自行执行。
 */
class YuanbaoProvider(
    private val hooks: ProviderHooks = ProviderHooks(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val host: String = YuanbaoConstants.API_BASE,
) : Provider {

    override val id: String = YuanbaoConstants.PROVIDER_ID
    override val displayName: String = "腾讯元宝"
    override val authKind get() = YuanbaoConstants.AUTH_KIND
    override val capabilities
        get() = setOf(dev.aigw.core.provider.ProviderCapability.CREDIT_REFRESH)

    override fun hosts(): List<String> = YuanbaoConstants.HOSTS

    // ------------------------------------------------------------------ 模型

    override fun listModels(account: ProviderAccount?): ProviderModelCatalogView {
        val credential = account?.let { YuanbaoCredential.parse(it.secret) }
        val agentId = YuanbaoConstants.DEFAULT_AGENT_ID
        return if (credential == null) {
            ProviderModelCatalogView(emptyList(), fromFallback = false, error = "")
        } else {
            try {
                val models = fetchModels(credential, agentId)
                if (models.isEmpty()) {
                    ProviderModelCatalogView(emptyList(), fromFallback = false, error = "上游返回空模型列表")
                } else {
                    ProviderModelCatalogView(models, fromFallback = false, error = "")
                }
            } catch (e: Exception) {
                hooks.onLog("元宝拉取模型目录失败：${e.message ?: "未知错误"}")
                ProviderModelCatalogView(emptyList(), fromFallback = false, error = e.message ?: "拉取模型失败")
            }
        }
    }

    override fun invalidateModelCache() = Unit

    override fun resolveModel(requested: String): String {
        val model = requested.trim()
        val normalized = if (model.startsWith("$id/")) model.removePrefix("$id/") else model
        return normalized.ifEmpty { YuanbaoConstants.DEFAULT_MODEL_ID }
    }

    override fun isInternalModel(id: String): Boolean = false

    // ------------------------------------------------------------------ 对话

    override fun openChat(account: ProviderAccount, openAiBody: String): ChatCall {
        val credential = YuanbaoCredential.parse(account.secret)
            ?: return FailedChatCall(401, "凭证无法解析")
        val streaming = isStreamingBody(openAiBody)
        val agentId = YuanbaoConstants.DEFAULT_AGENT_ID
        val model = resolveModel(requestedModelOf(openAiBody))

        // 元宝网页版不支持 OpenAI function calling。
        if (buildChatUsesTools(openAiBody)) {
            hooks.onLog("元宝：客户端请求包含 tools，但上游不支持 function calling")
            return StreamFailureChatCall(
                0,
                UpstreamError(
                    ErrorKind.CLIENT,
                    "元宝网页版不支持工具调用（OpenAI function calling）：请关闭客户端的工具/MCP/搜索功能后重试，" +
                        "或改用支持工具的模型。元宝内置的联网搜索由上游自动执行，无需客户端工具。",
                ),
            )
        }

        // 会话 ID 本地生成（临时会话），不调用创建会话接口。
        val cid = UUID.randomUUID().toString()
        val conn = try {
            openChatConnection(credential, cid, agentId, model, openAiBody)
        } catch (e: Exception) {
            return FailedChatCall(0, e.message ?: "连接失败")
        }

        val status = try {
            conn.responseCode
        } catch (e: Exception) {
            conn.disconnect()
            return FailedChatCall(0, e.message ?: "连接失败")
        }
        if (status !in 200..299) {
            val errorBody = runCatching { HttpSupport.readLimited(conn.errorStream) }.getOrDefault("")
            conn.disconnect()
            return FailedChatCall(status, errorBody)
        }

        val stream = conn.inputStream
        if (streaming) {
            val translator = YuanbaoSseTranslator(newCompletionId(), model, nowMillis() / 1000)
            val transformed = LineTransformStream(
                source = stream,
                transform = { line -> translator.translate(line) },
                onFinish = { translator.close() },
            )
            return StreamingChatCall(status, transformed) { conn.disconnect() }
        }

        // 非流式：把元宝 SSE 聚合为 OpenAI completion
        val aggregated = try {
            val translator = YuanbaoSseTranslator(newCompletionId(), model, nowMillis() / 1000)
            val transformed = LineTransformStream(
                source = stream,
                transform = { line -> translator.translate(line) },
                onFinish = { translator.close() },
            )
            OpenAiSseAggregator.aggregate(transformed, model)
        } finally {
            conn.disconnect()
        }
        if (OpenAiSseAggregator.isEmptyCompletion(aggregated)) {
            return FailedChatCall(status, "上游返回了空响应")
        }
        return AggregatedChatCall(status, aggregated)
    }

    // ------------------------------------------------------------------ 额度

    override fun creditInfo(account: ProviderAccount): CreditInfo? {
        val credential = YuanbaoCredential.parse(account.secret) ?: return null
        return try {
            val (status, text) = getJson("/api/query/chat/quotainfo", credential)
            if (status !in 200..299) {
                return CreditInfo(0, known = false, detail = "上游 HTTP $status" + bodySnippet(text))
            }
            val obj = JsonParser.parseString(text).asJsonObject
            val remain = obj.longOrNull("remainQuota")
                ?: obj.objOrNull("data")?.longOrNull("remainQuota")
            if (remain == null) {
                CreditInfo(0, known = false, detail = "响应缺少 remainQuota")
            } else {
                CreditInfo(remain, known = true, detail = "剩余额度 $remain")
            }
        } catch (e: Exception) {
            CreditInfo(0, known = false, detail = e.message ?: "查询额度失败")
        }
    }

    override fun classify(status: Int, body: String): UpstreamError {
        val message = extractMessage(body).ifEmpty { "上游 HTTP $status" + bodySnippet(body) }
        val kind = when (status) {
            401, 403 -> ErrorKind.SESSION_DEAD
            429 -> ErrorKind.SOFT_RATE
            404 -> ErrorKind.NOT_FOUND
            in 500..599 -> ErrorKind.SERVER
            else -> ErrorKind.CLIENT
        }
        return UpstreamError(kind, message)
    }

    // ------------------------------------------------------------------ 登录（粘贴 Cookie）

    override fun importCredentials(raw: String): ProviderAccount? {
        val credential = YuanbaoCredential.fromPasted(raw) ?: return null
        var uid = credential.parseUserId().ifEmpty {
            // Cookie 里没有 hy_user 时尝试用用户信息接口补（可能没登录态，失败则退回设备 id）
            runCatching {
                val (status, text) = getJson("/api/getuserinfo", credential)
                if (status in 200..299) {
                    val obj = JsonParser.parseString(text).asJsonObject
                    obj.stringOrNull("userId") ?: obj.objOrNull("data")?.stringOrNull("userId").orEmpty()
                } else ""
            }.getOrDefault("")
        }
        if (uid.isEmpty()) uid = credential.deviceId
        if (uid.isEmpty()) return null

        var nickname = "元宝账号"
        runCatching {
            val (status, text) = getJson("/api/getuserinfo", credential)
            if (status in 200..299) {
                val obj = JsonParser.parseString(text).asJsonObject
                val data = obj.objOrNull("data") ?: obj
                nickname = data.firstString("userName", "name", "nickname", "phoneNumber").ifEmpty { nickname }
            }
        }
        return ProviderAccount(
            providerId = id,
            uid = uid,
            nickname = nickname,
            secret = credential.toJson(),
        )
    }

    override fun migrateAccount(account: ProviderAccount): ProviderAccount? = null

    override fun refreshAccount(account: ProviderAccount, skewSeconds: Long): ProviderAccount? = null

    // ------------------------------------------------------------------ 网络

    /** 查询类请求：只带 Cookie 与浏览器头。 */
    private fun getJson(path: String, credential: YuanbaoCredential): Pair<Int, String> {
        val conn = HttpSupport.open(
            url = "$host$path",
            method = "GET",
            headers = baseHeaders(credential) + mapOf("Accept" to "application/json, text/plain, */*"),
            connectTimeoutMs = 15_000,
            readTimeoutMs = 30_000,
        )
        val status = conn.responseCode
        val body = HttpSupport.readBody(conn)
        conn.disconnect()
        return status to body
    }

    private fun openChatConnection(
        credential: YuanbaoCredential,
        cid: String,
        agentId: String,
        model: String,
        openAiBody: String,
    ): HttpURLConnection {
        val prompt = buildPrompt(openAiBody)
        val body = buildChatBody(prompt, agentId, model)
        val extra = mapOf(
            "Accept" to "text/event-stream, application/json, text/plain, */*",
            "Content-Type" to "text/plain;charset=UTF-8",
            // 关键：x-agentid 必须是 agentId/cid 两段式（对照 yuanbao2api）
            "X-AgentID" to "$agentId/$cid",
            "X-Timestamp" to nowMillis().toString(),
            "X-Requested-With" to "XMLHttpRequest",
            "X-Platform" to "win",
            "X-Source" to "web",
            "Referer" to "$host/chat/$agentId/$cid",
        )
        return HttpSupport.open(
            url = "$host/api/chat/$cid",
            method = "POST",
            headers = baseHeaders(credential) + extra,
            body = body,
            connectTimeoutMs = 15_000,
            readTimeoutMs = 120_000,
        )
    }

    /**
     * 元宝请求体（对照 `yuanbao2api` 实测结构）。
     *
     * 关键字段：`isTemporary=true`（临时会话）、`plugin`、`supportFunctions`、
     * `offsetOfHour=8`（东八区），以及 `chatModelExtInfo`（模型与搜索开关）。
     */
    private fun buildChatBody(prompt: String, agentId: String, model: String): String {
        val internetSearchFn = "openInternetSearch"
        val ext = JsonObject().apply {
            addProperty("modelId", model)
            addProperty("subModelId", "")
            add("supportFunctions", JsonObject().apply { addProperty("internetSearch", internetSearchFn) })
        }
        return JsonObject().apply {
            addProperty("model", "gpt_175B_0404")
            addProperty("prompt", prompt)
            addProperty("plugin", "Adaptive")
            addProperty("displayPrompt", prompt.take(2000))
            addProperty("displayPromptType", 1)
            addProperty("agentId", agentId)
            addProperty("projectId", "")
            addProperty("isTemporary", true)
            addProperty("chatModelId", model)
            add("supportFunctions", JsonArray().apply { add(internetSearchFn) })
            addProperty("docOpenid", "")
            add("options", JsonObject().apply {
                add("imageIntention", JsonObject().apply {
                    addProperty("needIntentionModel", true)
                    addProperty("backendUpdateFlag", 2)
                    addProperty("intentionStatus", true)
                })
            })
            add("multimedia", JsonArray())
            addProperty("supportHint", 1)
            addProperty("chatModelExtInfo", ext.toString())
            add("applicationIdList", JsonArray())
            addProperty("version", "v2")
            add("extReportParams", com.google.gson.JsonNull.INSTANCE)
            addProperty("isAtomInput", false)
            addProperty("offsetOfHour", 8)
            addProperty("offsetOfMinute", 0)
        }.toString()
    }

    /**
     * 浏览器基础请求头（严格对齐实测可用的 `yuanbao2api`）。
     *
     * 注意几个刻意的取舍：
     * - `User-Agent` 用**截断版**（不含 Chrome 版本段），与实测实现一致；
     * - 不传 `X-Instance-ID` / `x-commit-tag`（实测实现不传，多传反而与设备画像不一致）；
     * - `X-device-id` 仅在用户显式提供时传；
     * - `X-HY92`（qimei h38）与 `X-HY93`（qimei uuid）仅在账号里配置了 h38 时传。
     */
    private fun baseHeaders(credential: YuanbaoCredential): Map<String, String> = buildMap {
        put("User-Agent", userAgent())
        put("Cookie", credential.cookie)
        put("Origin", host)
        put("X-Language", "zh-CN")
        put("X-Source", "web")
        put("X-Platform", "win")
        put("X-WebVersion", YuanbaoConstants.X_WEB_VERSION)
        put("Accept-Charset", "utf-8")
        if (credential.deviceId.isNotEmpty()) {
            put("X-device-id", credential.deviceId)
            put("X-HY93", credential.deviceId)
        }
        if (credential.h38.isNotEmpty()) {
            put("X-HY92", credential.h38)
        }
    }

    /** 与实测实现一致的截断 UA（不含 Chrome 版本段）。 */
    private fun userAgent(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    /**
     * 把 OpenAI `messages` 拼成元宝 prompt（元宝只收纯文本 prompt，多轮靠拼接）。
     *
     * 与原实现只取最后一条 user 消息不同：这里保留 system 与历史轮次，
     * 否则多轮对话会丢上下文。
     */
    private fun buildPrompt(openAiBody: String): String {
        val obj = runCatching { JsonParser.parseString(openAiBody).asJsonObject }.getOrNull()
            ?: return openAiBody
        val messages = obj.arrayOrNull("messages") ?: return openAiBody
        val sb = StringBuilder()
        for (element in messages) {
            val message = element.asObjectOrNull() ?: continue
            val role = message.stringOrNull("role").orEmpty()
            val content = message.stringOrNull("content").orEmpty()
            if (content.isEmpty()) continue
            when (role) {
                "system" -> sb.append("系统指示：").append(content).append("\n\n")
                "assistant" -> sb.append("（此前回复）").append(content).append("\n\n")
                else -> sb.append(content).append("\n\n")
            }
        }
        return sb.toString().trim().ifEmpty { openAiBody }
    }

    /** 请求体是否带 tools / tool_choice（元宝网页版不支持，需明确拒绝）。 */
    private fun buildChatUsesTools(openAiBody: String): Boolean {
        val obj = runCatching { JsonParser.parseString(openAiBody).asJsonObject }.getOrNull() ?: return false
        return obj.arrayOrNull("tools")?.size()?.let { it > 0 } == true || obj.has("tool_choice")
    }

    private fun JsonObject.has(name: String): Boolean = get(name) != null && !get(name).isJsonNull

    /** 最新模型列表接口（含 subModelList / supportFunctions）；旧 /api/models 作兜底。 */
    private fun fetchModels(credential: YuanbaoCredential, agentId: String): List<ProviderModel> {
        val (status, text) = getJson("/api/agent/model/list?agentId=$agentId", credential)
        if (status !in 200..299) return emptyList()
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return emptyList()
        val list = obj.arrayOrNull("modelList") ?: obj.objOrNull("data")?.arrayOrNull("modelList")
            ?: return emptyList()
        return buildList {
            for (element in list) {
                val item = element.asObjectOrNull() ?: continue
                val id = item.firstString("chatModelId", "modelId", "id")
                if (id.isEmpty()) continue
                val name = item.firstString("modelName", "name", "title", "chatModelName").ifEmpty { id }
                val extra = buildMap {
                    if (item.objOrNull("supportFunctions")?.has("internetSearch") == true) {
                        put("search", "1")
                    }
                }
                add(ProviderModel(id = id, name = name, contextWindow = 0, extra = extra))
            }
        }
    }

    private fun extractMessage(body: String): String {
        val text = body.trim()
        if (text.isEmpty()) return ""
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return text.take(200)
        return obj.firstString("message", "msg", "error", "errorMessage").ifEmpty { text.take(200) }
    }

    private fun JsonElement?.asObjectOrNull(): JsonObject? =
        this?.takeIf { it.isJsonObject }?.asJsonObject
}

/** gson 的 JsonElement 等别名的简单扩展，避免与 util 命名冲突。 */
private typealias JsonElement = com.google.gson.JsonElement