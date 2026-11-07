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
import dev.aigw.core.provider.StreamingChatCall
import dev.aigw.core.provider.UpstreamError
import dev.aigw.core.provider.isStreamingBody
import dev.aigw.core.provider.requestedModelOf
import dev.aigw.core.util.HttpSupport
import dev.aigw.core.util.arrayOrNull
import dev.aigw.core.util.bodySnippet
import dev.aigw.core.util.firstString
import dev.aigw.core.util.newCompletionId
import dev.aigw.core.util.objOrNull
import dev.aigw.core.util.randomHex
import dev.aigw.core.util.stringOrNull
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 腾讯元宝（`yuanbao.tencent.com`）Web 端供应商。
 *
 * 协议来自对前端 JS 的逆向（2026-11-07 实测，详见星球/文档 `yuanbao-re` 包）：
 * - 鉴权：Cookie 会话（`hy_token` + `hy_user`），浏览器登录后复制导入；
 * - 查询接口（用户信息 / 模型 / 额度 / 会话）只需 Cookie；
 * - 对话 `POST /api/chat/{cid}` 是 SSE 流，需要 **qimei USKey 签名三件套**
 *   （`X-Timestamp` / `X-Bus-Params-Md5` / `X-Uskey`，见 [UskeySigner]）。
 *
 * USKey 只能由浏览器环境铸造：「本地 USKey 铸造服务」或浏览器抓包静态签名。
 * 两者都没配时**不发起对话**（上游会在被拒时照样扣配额，必须避免白扣），
 * 返回带明确指引的错误。查询类能力不受影响。
 */
class YuanbaoProvider(
    private val hooks: ProviderHooks = ProviderHooks(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val host: String = YuanbaoConstants.API_BASE,
    /**
     * USKey 铸造服务地址（如 `http://127.0.0.1:8787/uskey`，处理 `{ok,h38,ts,params,uskey}`）。
     * 也可通过账号凭证里的 [UskeyConfig.serviceUrl] 覆盖（换设备/换浏览器时）。
     */
    private val defaultUskeyServiceUrl: String = "",
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
        // 客户端按 prefix/model 传；prefix 由网关切掉，这里可能拿到 `元宝/model` 形式
        val normalized = if (model.startsWith("$id/")) model.removePrefix("$id/") else model
        return normalized.ifEmpty { YuanbaoConstants.DEFAULT_AGENT_ID }
    }

    override fun isInternalModel(id: String): Boolean = false

    // ------------------------------------------------------------------ 对话

    override fun openChat(account: ProviderAccount, openAiBody: String): ChatCall {
        val credential = YuanbaoCredential.parse(account.secret)
            ?: return FailedChatCall(401, "凭证无法解析")
        val streaming = isStreamingBody(openAiBody)
        val agentId = YuanbaoConstants.DEFAULT_AGENT_ID
        val model = resolveModel(requestedModelOf(openAiBody))

        // 创建会话需要 USKey；没有签名配置时直接拒绝，避免上游扣费
        val cid = try {
            createConversation(credential, agentId)
        } catch (e: UskeyRequiredException) {
            return FailedChatCall(0, e.message ?: "缺少 USKey")
        } catch (e: Exception) {
            return FailedChatCall(0, e.message ?: "创建会话失败")
        }

        val conn = try {
            openChatConnection(credential, cid, agentId, model, openAiBody)
        } catch (e: UskeyRequiredException) {
            return FailedChatCall(0, e.message ?: "缺少 USKey")
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

    private class UskeyRequiredException(message: String) : Exception(message)

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

    /** 创建会话。返回 conversationId。 */
    private fun createConversation(credential: YuanbaoCredential, agentId: String): String {
        val body = JsonObject().apply { addProperty("agentId", agentId) }.toString()
        val signed = signedHeaders(credential, "/api/user/agent/conversation/create", body)
        val conn = HttpSupport.open(
            url = "$host/api/user/agent/conversation/create",
            method = "POST",
            headers = baseHeaders(credential) + signed,
            body = body,
            connectTimeoutMs = 15_000,
            readTimeoutMs = 30_000,
        )
        val status = conn.responseCode
        val text = HttpSupport.readBody(conn)
        conn.disconnect()
        if (status !in 200..299) {
            throw IllegalStateException("创建会话失败（HTTP $status）：${text.take(200)}")
        }
        val cid = runCatching {
            val obj = JsonParser.parseString(text).asJsonObject
            obj.stringOrNull("id") ?: obj.objOrNull("data")?.stringOrNull("id").orEmpty()
        }.getOrDefault("")
        if (cid.isEmpty()) throw IllegalStateException("创建会话响应缺少 id：${text.take(200)}")
        return cid
    }

    private fun openChatConnection(
        credential: YuanbaoCredential,
        cid: String,
        agentId: String,
        model: String,
        openAiBody: String,
    ): HttpURLConnection {
        val prompt = extractPrompt(openAiBody)
        val body = buildChatBody(prompt, agentId, model, cid)
        val signed = signedHeaders(credential, "/api/chat/$cid", body)
        val extra = mapOf(
            "Accept" to "text/event-stream",
            "Content-Type" to "text/plain;charset=UTF-8",
            "X-AgentID" to agentId,
            "X-Traceparent" to traceparent(),
            "X-Event-Input-Type" to "11",
            "X-Input-Type" to "text",
        )
        return HttpSupport.open(
            url = "$host/api/chat/$cid",
            method = "POST",
            headers = baseHeaders(credential) + signed + extra,
            body = body,
            connectTimeoutMs = 15_000,
            readTimeoutMs = 120_000,
        )
    }

    private fun buildChatBody(prompt: String, agentId: String, model: String, cid: String): String {
        val ext = JsonObject().apply {
            addProperty("modelId", model)
            addProperty("subModelId", "")
            add("supportFunctions", JsonObject().apply { addProperty("internetSearch", "") })
            addProperty("internetSearch", "autoInternetSearch")
        }
        return JsonObject().apply {
            addProperty("model", "gpt_175B_0404")
            addProperty("prompt", prompt)
            addProperty("plugin", "")
            addProperty("displayPrompt", prompt)
            addProperty("displayPromptType", 1)
            addProperty("agentId", agentId)
            addProperty("isTemporary", false)
            addProperty("projectId", "")
            addProperty("chatModelId", model)
            add("supportFunctions", JsonArray().apply {
                add("openAutoSearchSwitch")
                add("autoInternetSearch")
            })
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
            addProperty("offsetOfHour", 0)
            addProperty("conversationId", cid)
        }.toString()
    }

    /** 浏览器基础请求头（不含签名）。 */
    private fun baseHeaders(credential: YuanbaoCredential): Map<String, String> = mapOf(
        "User-Agent" to userAgent(),
        "Cookie" to credential.cookie,
        "Origin" to host,
        "Referer" to "$host/",
    )

    /**
     * 需要签名的路径补三件套。签名来源优先级：
     * 1. 账号凭证里的静态签名（浏览器抓包）；
     * 2. 铸造服务（账号级 [UskeyConfig.serviceUrl] 或 provider 默认地址）。
     * 都没有 → 抛异常（调用方转 FailedChatCall，不发起请求，避免扣费）。
     */
    private fun signedHeaders(credential: YuanbaoCredential, path: String, body: String): Map<String, String> {
        if (YuanbaoConstants.USKEY_PATHS.none { path.contains(it) }) return emptyMap()
        val config = credential.uskey

        // 1) 静态签名可用且未过期（5 分钟窗口）
        if (config != null && config.hasStatic() &&
            (config.timestamp == 0L || nowMillis() - config.timestamp < 5 * 60 * 1000L)
        ) {
            return staticHeaders(config)
        }

        // 2) 铸造服务
        val service = config?.serviceUrl?.takeIf { it.isNotEmpty() } ?: defaultUskeyServiceUrl
        if (service.isNotEmpty()) {
            val minted = try {
                mintFromService(service)
            } catch (e: Exception) {
                throw UskeyRequiredException("USKey 铸造服务不可用（$service）：${e.message ?: "未知错误"}")
            }
            return minted
        }

        throw UskeyRequiredException(
            "元宝对话需要 qimei USKey（浏览器环境铸造，短暂有效）。请配置本地 USKey 铸造服务" +
                "（uskey_browser.py --serve，地址填到账号设置），或在浏览器抓取 X-Uskey 静态填入。",
        )
    }

    private fun staticHeaders(config: UskeyConfig): Map<String, String> {
        val params = config.params.ifEmpty { "h38=${config.h38}&timestamp=${config.timestamp}&platform=web" }
        return mapOf(
            "X-HY92" to config.h38,
            "X-Timestamp" to (if (config.timestamp > 0) config.timestamp else nowMillis()).toString(),
            "X-Bus-Params-Md5" to md5(params),
            "X-Uskey" to config.uskey,
            "X-HY93" to (config.params.takeIf { it.isNotEmpty() }?.let { "" } ?: "static"),
        ).filterValues { it.isNotEmpty() }
    }

    /** 调用本地铸造服务（与 Python `uskey_browser.py --serve` 的输出契约一致）。 */
    private fun mintFromService(serviceUrl: String): Map<String, String> {
        val conn = HttpSupport.open(
            url = serviceUrl,
            method = "GET",
            headers = mapOf("Accept" to "application/json"),
            connectTimeoutMs = 15_000,
            readTimeoutMs = 90_000,
        )
        val status = conn.responseCode
        val text = HttpSupport.readBody(conn)
        conn.disconnect()
        if (status !in 200..299) throw IllegalStateException("服务返回 HTTP $status")
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: throw IllegalStateException("服务返回非 JSON")
        if (obj.boolOrNull("ok") != true) throw IllegalStateException("服务返回 ok=false：${text.take(200)}")
        val h38 = obj.stringOrNull("h38").orEmpty()
        val ts = obj.longOrNull("ts") ?: nowMillis()
        val params = obj.stringOrNull("params").orEmpty()
            .ifEmpty { "h38=$h38&timestamp=$ts&platform=web" }
        val uskey = obj.stringOrNull("uskey").orEmpty()
        if (uskey.isEmpty() || h38.isEmpty()) throw IllegalStateException("服务响应缺少 uskey/h38")
        return mapOf(
            "X-HY92" to h38,
            "X-HY93" to (obj.stringOrNull("h93") ?: ""),
            "X-Timestamp" to ts.toString(),
            "X-Bus-Params-Md5" to md5(params),
            "X-Uskey" to uskey,
        ).filterValues { it.isNotEmpty() }
    }

    /**
     * X-Traceparent：`md5(randhex + instanceId + uuid4)`（前端模块 48376 规则）。
     * 每请求唯一——同一 traceparent 复用会导致 21007「Answer fetch failed, retrying」。
     */
    private fun traceparent(): String {
        val instanceId = "5"
        return md5("${randomHex(7)}$instanceId${randomHex(16)}")
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun userAgent(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 从 OpenAI 请求体提取用户 prompt（首条 user 消息）。 */
    private fun extractPrompt(openAiBody: String): String {
        val obj = runCatching { JsonParser.parseString(openAiBody).asJsonObject }.getOrNull()
            ?: return openAiBody
        val messages = obj.arrayOrNull("messages") ?: return openAiBody
        for (element in messages) {
            val message = element.asObjectOrNull() ?: continue
            if (message.stringOrNull("role") == "user") {
                return message.stringOrNull("content").orEmpty()
            }
        }
        return openAiBody
    }

    private fun fetchModels(credential: YuanbaoCredential, agentId: String): List<ProviderModel> {
        // 最新模型列表接口（含 subModelList / supportFunctions）；旧 /api/models 作兜底
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
                // 界面展示名；能力标签从 extra 传出（前端各字段口径不齐，尽力而为）
                val name = item.firstString("modelName", "name", "title", "chatModelName").ifEmpty { id }
                val backendModel = item.firstString("model", "backendModel").ifEmpty { id }
                val extra = buildMap {
                    if (backendModel.isNotEmpty() && backendModel != id) put("backend", backendModel)
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

    private fun JsonObject.boolOrNull(name: String): Boolean? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean

    private fun JsonElement?.asObjectOrNull(): JsonObject? =
        this?.takeIf { it.isJsonObject }?.asJsonObject
}

/** gson 的 JsonElement 等别名的简单扩展，避免与 util 命名冲突。 */
private typealias JsonElement = com.google.gson.JsonElement