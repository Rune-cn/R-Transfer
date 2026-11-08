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
 * - `text` 且 `msg` 非空 → 输出 content delta；
 * - `status` 携带 `stopReason` → 输出收尾帧（finish_reason）；
 * - `error` / `event: error` → 输出错误提示帧并结束（不中断流，客户端能正常收到 [DONE]）；
 * - 其它帧忽略；流结束时由 [close] 补终止帧与 [DONE]。
 */
class YuanbaoSseTranslator(
    private val completionId: String,
    private val model: String,
    private val createdSeconds: Long = System.currentTimeMillis() / 1000,
) {
    private var lastEvent: String? = null
    private var finished = false

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
                if (msg.isEmpty()) emptyList() else listOf(chunk(textDelta(msg)))
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
        finished = true
        return listOf(chunk(JsonObject(), "stop"), DONE)
    }

    private fun onTerminal(): List<String> {
        if (finished) return emptyList()
        finished = true
        return listOf(chunk(JsonObject(), "stop"), DONE)
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