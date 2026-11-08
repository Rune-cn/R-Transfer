package dev.aigw.core.provider.yuanbao

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.core.util.stringOrNull

/**
 * 元宝 SSE → OpenAI `chat.completion.chunk` 逐行翻译。
 *
 * 元宝的流式事件（`POST /api/chat/{cid}`）每帧 `data` 是一个 JSON，用 `type` 区分：
 * `header`（会话开始）/ `text`（正文增量，`msg` 字段）/ `status`（含 `stopReason`）/
 * `error`（业务错误）/ `plugin` / `msgIdx` / `speechType` / `extra` / `delete` / `end`。
 *
 * 翻译规则：
 * - `text` 且 `msg` 非空 → 若含工具调用标记，转成标准 `tool_calls` 增量；
 *   否则输出 content delta（标记跨 chunk 由 [YuanbaoToolBridge.StreamParser] 缓冲）；
 * - `status` 携带 `stopReason` → 输出收尾帧（有工具调用时 `finish_reason=tool_calls`）；
 * - `error` / `event: error` → 输出标准错误帧（`data: {"error":{...}}`），
 *   网关据此把本次调用标为失败（不再伪装成模型回复）；
 * - 其它帧忽略；流结束时由 [close] 补终止帧与 [DONE]。
 */
class YuanbaoSseTranslator(
    private val completionId: String,
    private val model: String,
    private val createdSeconds: Long = System.currentTimeMillis() / 1000,
    knownTools: Set<String> = emptySet(),
) {
    private var lastEvent: String? = null
    private var finished = false
    private var hasToolCalls = false
    private var toolCallIndex = 0
    private val toolParser = YuanbaoToolBridge.StreamParser(knownTools)

    fun translate(line: String): List<String> {
        if (finished) return emptyList()
        if (line.startsWith("event:")) {
            lastEvent = line.removePrefix("event:").trim()
            return emptyList()
        }
        if (!line.startsWith("data:")) return emptyList()
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return emptyList()
        val event = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: return emptyList()
        val type = event.stringOrNull("type") ?: lastEvent.orEmpty()

        return when (type) {
            "text" -> {
                val msg = event.stringOrNull("msg").orEmpty()
                if (msg.isEmpty()) emptyList() else translateText(msg)
            }

            "status" -> {
                val stopReason = event.stringOrNull("stopReason").orEmpty()
                if (stopReason.isNotEmpty()) onTerminal() else emptyList()
            }

            "error" -> onFailed(event)

            "end" -> onTerminal()

            else -> emptyList()
        }
    }

    /** 流结束时补帧：上游没发终止也要让客户端收到结束块与 [DONE]。 */
    fun close(): List<String> {
        if (finished) return emptyList()
        val out = mutableListOf<String>()
        // 冲掉解析器里剩余的纯文本（未闭合的工具段会被丢弃）
        for (event in toolParser.finish()) {
            if (event is YuanbaoToolBridge.StreamParser.Event.Text) {
                out += chunk(textDelta(event.text))
            }
        }
        out += onTerminal()
        finished = true
        return out
    }

    /** 把一段正文增量翻译成 content / tool_calls 帧。 */
    private fun translateText(msg: String): List<String> {
        val out = mutableListOf<String>()
        for (event in toolParser.feed(msg)) {
            when (event) {
                is YuanbaoToolBridge.StreamParser.Event.Text ->
                    if (event.text.isNotEmpty()) out += chunk(textDelta(event.text))

                is YuanbaoToolBridge.StreamParser.Event.Call -> {
                    hasToolCalls = true
                    out += chunk(toolCallDelta(event.call))
                }

                YuanbaoToolBridge.StreamParser.Event.ToolMode -> Unit
            }
        }
        return out
    }

    private fun onTerminal(): List<String> {
        if (finished) return emptyList()
        finished = true
        val reason = if (hasToolCalls) "tool_calls" else "stop"
        return listOf(chunk(JsonObject(), reason), DONE)
    }

    /**
     * 错误帧 → 标准 OpenAI 错误帧（`data: {"error":{...}}`）。
     *
     * 不再把错误文案当成 content 输出：那样客户端会把它当作模型回复
     * （例如「服务繁忙，请稍后再试」看起来像模型在说话），
     * 网关也无法把这次调用标为失败。
     */
    private fun onFailed(event: JsonObject): List<String> {
        if (finished) return emptyList()
        finished = true
        val message = event.stringOrNull("msg").orEmpty().ifEmpty { "上游错误" }
        val errorFrame = JsonObject().apply {
            add("error", JsonObject().apply {
                addProperty("message", message)
                addProperty("type", "upstream_error")
            })
        }
        return listOf("data: $errorFrame\n\n", DONE)
    }

    private fun toolCallDelta(call: YuanbaoToolBridge.ToolCall): JsonObject {
        val index = toolCallIndex++
        return JsonObject().apply {
            add("tool_calls", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("index", index)
                    addProperty("id", "call_" + completionId.removePrefix("chatcmpl-") + "_$index")
                    addProperty("type", "function")
                    add("function", JsonObject().apply {
                        addProperty("name", call.name)
                        addProperty("arguments", call.arguments)
                    })
                })
            })
        }
    }

    private fun chunk(delta: JsonObject, finishReason: String? = null): String {
        val obj = JsonObject().apply {
            addProperty("id", completionId)
            addProperty("object", "chat.completion.chunk")
            addProperty("created", createdSeconds)
            addProperty("model", model)
            add("choices", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("index", 0)
                    add("delta", delta)
                    if (finishReason == null) {
                        add("finish_reason", JsonNull.INSTANCE)
                    } else {
                        addProperty("finish_reason", finishReason)
                    }
                })
            })
        }
        return "data: $obj\n\n"
    }

    private fun textDelta(content: String): JsonObject =
        JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", content)
        }

    companion object {
        const val DONE = "data: [DONE]\n\n"
    }
}