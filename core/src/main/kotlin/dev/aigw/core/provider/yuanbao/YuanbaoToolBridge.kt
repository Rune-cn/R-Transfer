package dev.aigw.core.provider.yuanbao

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.core.util.arrayOrNull
import dev.aigw.core.util.objOrNull
import dev.aigw.core.util.stringOrNull

/**
 * 元宝网页版的「工具调用」桥接层。
 *
 * 元宝 `/api/chat` 不支持 OpenAI 原生 function calling（只暴露联网搜索开关）。
 * 这里采用业界通行的**提示词模拟**方案（与开源实现 `yuanbao2api` 一致）：
 *
 * 1. **注入**：把 `tools` 定义写进 prompt 的「可用工具」段，并约定调用格式：
 *    ```
 *    <|tool_calls_begin|>
 *    {"name": "函数名", "arguments": {"参数名": "参数值"}}
 *    <|tool_calls_end|>
 *    ```
 * 2. **解析**：从模型输出里识别上述标记（ASCII 与全角 Unicode 两种变体），
 *    解析出 `name` / `arguments`，再由 provider 转成标准 OpenAI `tool_calls`。
 * 3. **回填**：客户端执行工具后把 `role: "tool"` 的结果发回来，这里再拼回 prompt。
 *
 * 注意：模型可能不按格式输出——此时**降级为纯文本**，不报错。
 */
object YuanbaoToolBridge {

    /** 工具调用标记（ASCII 与全角 Unicode 两种，前端/模型都可能输出）。 */
    private val START_MARKS = listOf("<|tool_calls_begin|>", "<｜tool▁calls_begin｜>")
    private val END_MARKS = listOf("<|tool_calls_end|>", "<｜tool▁calls_end｜>")

    /** 单个工具结果的截断长度（避免 prompt 爆炸）。 */
    private const val MAX_TOOL_RESULT = 3000

    /** 一次工具调用。 */
    data class ToolCall(val name: String, val arguments: String)

    // ------------------------------------------------------------------ 注入

    /** 由 `tools`（OpenAI 定义）生成系统提示段。 */
    fun buildToolSystemPrompt(tools: JsonArray?): String {
        if (tools == null || tools.size() == 0) return ""
        val descriptions = StringBuilder()
        for (element in tools) {
            val tool = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val fn = tool.objOrNull("function") ?: continue
            val name = fn.stringOrNull("name").orEmpty()
            if (name.isEmpty()) continue
            val desc = fn.stringOrNull("description").orEmpty()
            val params = fn.get("parameters")?.toString() ?: "{}"
            descriptions.append("### ").append(name).append('\n')
            if (desc.isNotEmpty()) descriptions.append(desc).append('\n')
            descriptions.append("参数:\n```json\n").append(params).append("\n```\n\n")
        }
        if (descriptions.isEmpty()) return ""
        return buildString {
            append("\n# 可用工具\n")
            append("你可以调用以下工具来完成任务。\n\n")
            append("**重要：当你需要调用工具时，必须严格按照以下格式输出：**\n\n")
            append("<|tool_calls_begin|>\n")
            append("{\"name\": \"函数名\", \"arguments\": {\"参数名\": \"参数值\"}}\n")
            append("<|tool_calls_end|>\n\n")
            append("注意事项：\n")
            append("1. 工具调用必须用上述标记包裹，不要遗漏标记\n")
            append("2. 标记内只能包含 JSON 格式的工具调用，不要添加其他文字\n")
            append("3. 你可以同时调用多个工具，每个工具调用使用单独的标记对\n")
            append("4. 如果不需要调用工具，直接回复用户即可，不要输出标记\n\n")
            append("可用工具列表：\n")
            append(descriptions)
            append('\n')
        }
    }

    /**
     * 把 OpenAI `messages` 转成元宝 prompt（含工具调用历史与工具结果）。
     *
     * 与纯文本路径的区别：这里会保留 assistant 的 tool_calls 与 tool 角色的执行结果，
     * 让模型在下一轮能「看到」工具已返回了什么。
     */
    fun convertMessages(messages: JsonArray, tools: JsonArray?): String {
        val toolSystemPrompt = buildToolSystemPrompt(tools)
        val sb = StringBuilder()
        var systemInjected = false

        for (element in messages) {
            val message = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val role = message.stringOrNull("role").orEmpty()
            val content = contentText(message)
            when (role) {
                "system" -> {
                    sb.append("[系统提示: ").append(content)
                    if (toolSystemPrompt.isNotEmpty() && !content.contains("[系统提示:")) {
                        sb.append(toolSystemPrompt)
                    }
                    sb.append("]\n\n")
                    systemInjected = true
                }

                "user" -> sb.append("用户: ").append(content).append('\n')

                "assistant" -> {
                    val calls = message.arrayOrNull("tool_calls")
                    if (calls != null && calls.size() > 0) {
                        sb.append("助手: 我需要调用工具来完成任务。\n")
                        for (c in calls) {
                            val fn = c.takeIf { it.isJsonObject }?.asJsonObject?.objOrNull("function") ?: continue
                            sb.append("调用工具 ")
                                .append(fn.stringOrNull("name").orEmpty())
                                .append("，参数: ")
                                .append(fn.stringOrNull("arguments").orEmpty())
                                .append('\n')
                        }
                    } else if (content.isNotEmpty()) {
                        sb.append("助手: ").append(content).append('\n')
                    }
                }

                "tool" -> {
                    val toolName = message.stringOrNull("name").orEmpty().ifEmpty { "unknown" }
                    val truncated = content.take(MAX_TOOL_RESULT)
                    sb.append("工具 ").append(toolName).append(" 的执行结果:\n")
                        .append(truncated).append("\n\n")
                }
            }
        }

        sb.append("\n请作为助手继续回复：")
        if (toolSystemPrompt.isNotEmpty() && !systemInjected) {
            sb.append("\n\n").append(toolSystemPrompt)
        }
        return sb.toString()
    }

    /** 取消息正文：字符串直接取；数组（多模态）取其中的 text 片段。 */
    private fun contentText(message: JsonObject): String {
        val raw = message.get("content") ?: return ""
        if (raw.isJsonPrimitive) return runCatching { raw.asString }.getOrDefault("")
        if (raw.isJsonArray) {
            val parts = StringBuilder()
            for (part in raw.asJsonArray) {
                val obj = part.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                obj.stringOrNull("text")?.let { parts.append(it) }
            }
            return parts.toString()
        }
        return ""
    }

    // ------------------------------------------------------------------ 解析

    /** 从完整文本里解析全部工具调用（非流式路径）。 */
    fun parseToolCalls(text: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        var from = 0
        while (from < text.length) {
            val start = findMark(text, START_MARKS, from) ?: break
            val end = findMark(text, END_MARKS, start.index + start.mark.length) ?: break
            val json = text.substring(start.index + start.mark.length, end.index).trim()
            parseOne(json)?.let { calls += it }
            from = end.index + end.mark.length
        }
        return calls
    }

    private data class MarkHit(val index: Int, val mark: String)

    private fun findMark(text: String, marks: List<String>, from: Int): MarkHit? {
        var best: MarkHit? = null
        for (mark in marks) {
            val idx = text.indexOf(mark, from)
            if (idx >= 0 && (best == null || idx < best!!.index)) {
                best = MarkHit(idx, mark)
            }
        }
        return best
    }

    /** 解析单个 `{"name":..,"arguments":..}`；失败返回 null（降级为文本）。 */
    private fun parseOne(json: String): ToolCall? {
        val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        val name = obj.stringOrNull("name").orEmpty()
        if (name.isEmpty()) return null
        val argRaw = obj.get("arguments")
        val arguments = when {
            argRaw == null || argRaw.isJsonNull -> ""
            argRaw.isJsonPrimitive -> runCatching { argRaw.asString }.getOrDefault("")
            else -> argRaw.toString()
        }
        return ToolCall(name, arguments)
    }

    // ------------------------------------------------------------------ 流式解析

    /**
     * 流式增量解析器：元宝 SSE 的 `text` 事件是**文本增量**，
     * 工具调用标记可能跨多个 chunk。这里做状态机缓冲：
     * - 标记外的文本 → [Event.Text]（正常流式输出）
     * - 完整的一对标记 → [Event.Call]（转成 tool_calls delta）
     * - 未闭合的标记（流结束时仍在内）→ 丢弃，避免把半截 JSON 当正文
     */
    class StreamParser {
        sealed interface Event {
            data class Text(val text: String) : Event
            data class Call(val call: ToolCall) : Event

            data object ToolMode : Event
        }

        private val pending = StringBuilder()
        private var inTool = false

        fun feed(chunk: String): List<Event> {
            if (chunk.isEmpty()) return emptyList()
            pending.append(chunk)
            val events = mutableListOf<Event>()
            while (true) {
                if (!inTool) {
                    val hit = findMark(pending.toString(), START_MARKS, 0)
                    if (hit != null) {
                        if (hit.index > 0) {
                            events += Event.Text(pending.substring(0, hit.index))
                            pending.delete(0, hit.index)
                        }
                        pending.delete(0, hit.mark.length)
                        inTool = true
                        events += Event.ToolMode
                    } else {
                        // 无标记：输出安全前缀（保留可能是「开始标记前半段」的尾巴）
                        val text = pending.toString()
                        val keep = partialMarkSuffix(text, START_MARKS)
                        val safeLen = text.length - keep
                        if (safeLen > 0) {
                            events += Event.Text(text.substring(0, safeLen))
                            pending.delete(0, safeLen)
                        }
                        break
                    }
                } else {
                    val hit = findMark(pending.toString(), END_MARKS, 0)
                    if (hit != null) {
                        val json = pending.substring(0, hit.index).trim()
                        pending.delete(0, hit.index + hit.mark.length)
                        inTool = false
                        parseOne(json)?.let { events += Event.Call(it) }
                    } else {
                        break // 等待更多增量
                    }
                }
            }
            return events
        }

        /** 流结束：未闭合的工具段丢弃；返回剩余可输出的文本。 */
        fun finish(): List<Event> {
            if (inTool) {
                pending.clear()
                inTool = false
                return emptyList()
            }
            if (pending.isEmpty()) return emptyList()
            val text = pending.toString()
            pending.clear()
            return listOf(Event.Text(text))
        }

        /** 文本末尾与任一标记前缀重合的长度（这部分先不输出，等下一个 chunk）。 */
        private fun partialMarkSuffix(text: String, marks: List<String>): Int {
            var best = 0
            for (mark in marks) {
                val max = minOf(mark.length - 1, text.length)
                for (len in 1..max) {
                    if (text.endsWith(mark.substring(0, len))) best = maxOf(best, len)
                }
            }
            return best
        }
    }
}