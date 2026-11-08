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
            append("**调用工具时，必须严格按下面两种格式之一输出（不要用其它格式）：**\n\n")
            append("格式一（推荐）：\n")
            append("<|tool_calls_begin|>\n")
            append("{\"name\": \"函数名\", \"arguments\": {\"参数名\": \"参数值\"}}\n")
            append("<|tool_calls_end|>\n\n")
            append("格式二（代码块）：\n")
            append("```json\n")
            append("{\"name\": \"函数名\", \"arguments\": {\"参数名\": \"参数值\"}}\n")
            append("```\n\n")
            append("示例（用户要求搜索时，你必须只输出工具调用，不要解释）：\n")
            append("用户: 搜索今天的天气\n")
            append("助手: <|tool_calls_begin|>\n")
            append("{\"name\": \"web_search\", \"arguments\": {\"query\": \"今天天气\"}}\n")
            append("<|tool_calls_end|>\n\n")
            append("示例（不需要工具时，正常回答，不要输出任何标记）：\n")
            append("用户: 你好\n")
            append("助手: 你好！有什么可以帮你的？\n\n")
            append("注意事项：\n")
            append("1. 工具调用必须用上述标记或代码块包裹，不要遗漏\n")
            append("2. 包裹内只能包含 JSON 格式的工具调用，不要添加其他文字\n")
            append("3. 你可以同时调用多个工具，每个工具使用单独的标记对\n")
            append("4. 如果不需要调用工具，直接回复用户即可，不要输出标记\n")
            append("5. 函数名必须是下方列表里的名字\n\n")
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

    /** 代码块围栏（模型常用 ```json 包裹 JSON）。 */
    private val FENCE_STARTS = listOf("```json", "```JSON", "```Json", "```")
    private const val FENCE_END = "```"

    /**
     * 从完整文本里解析全部工具调用（非流式路径）。
     *
     * 依次尝试三种格式，命中即返回：
     * 1. 专用标记 `<|tool_calls_begin|>…<|tool_calls_end|>`（提示词推荐格式）；
     * 2. 代码块 ```json … ```；
     * 3. 裸 JSON 对象（**仅当 name 在 [knownTools] 里**，避免把普通回答中的 JSON 误判）。
     */
    fun parseToolCalls(text: String, knownTools: Set<String> = emptySet()): List<ToolCall> {
        // 1) 标记
        val marked = mutableListOf<ToolCall>()
        var from = 0
        while (from < text.length) {
            val start = findMark(text, START_MARKS, from) ?: break
            val end = findMark(text, END_MARKS, start.index + start.mark.length) ?: break
            val json = text.substring(start.index + start.mark.length, end.index).trim()
            parseOne(json)?.let { marked += it }
            from = end.index + end.mark.length
        }
        if (marked.isNotEmpty()) return marked

        // 2) 代码块
        val fenced = parseFenced(text, knownTools)
        if (fenced.isNotEmpty()) return fenced

        // 3) 裸 JSON（必须有已知工具名兜底，否则误判率太高）
        return parseBareJson(text, knownTools)
    }

    private fun parseFenced(text: String, knownTools: Set<String>): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        val regex = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        for (match in regex.findAll(text)) {
            val call = parseOne(match.groupValues[1].trim()) ?: continue
            if (knownTools.isEmpty() || call.name in knownTools) calls += call
        }
        return calls
    }

    private fun parseBareJson(text: String, knownTools: Set<String>): List<ToolCall> {
        if (knownTools.isEmpty()) return emptyList()
        val calls = mutableListOf<ToolCall>()
        var i = 0
        while (i < text.length) {
            val start = text.indexOf('{', i)
            if (start < 0) break
            val end = balancedEnd(text, start)
            if (end < 0) break
            val call = parseOne(text.substring(start, end + 1))
            if (call != null && call.name in knownTools) calls += call
            i = end + 1
        }
        return calls
    }

    /** 从 [start]（必须是 `{`）找配对的 `}`；跳过字符串内的花括号与转义。 */
    private fun balancedEnd(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
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
        val argRaw = obj.get("arguments") ?: obj.get("parameters")
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
     * 工具调用标记 / 代码块可能跨多个 chunk。状态机缓冲：
     * - 标记外的文本 → [Event.Text]（正常流式输出）
     * - 完整标记对 → [Event.Call]
     * - 完整代码块：JSON 的 name 命中 [knownTools] → [Event.Call]；
     *   否则**原样输出整段文本**（普通人写的代码块不能被吞掉）
     * - 未闭合的段在流结束时：标记段丢弃；代码块段原样输出
     */
    class StreamParser(knownTools: Set<String> = emptySet()) {
        sealed interface Event {
            data class Text(val text: String) : Event
            data class Call(val call: ToolCall) : Event

            data object ToolMode : Event
        }

        private val tools: Set<String> = knownTools
        private val pending = StringBuilder()
        private val fenceText = StringBuilder()
        private var mode = Mode.Normal

        private enum class Mode { Normal, Marked, Fenced }

        fun feed(chunk: String): List<Event> {
            if (chunk.isEmpty()) return emptyList()
            pending.append(chunk)
            val events = mutableListOf<Event>()
            while (true) {
                when (mode) {
                    Mode.Normal -> {
                        val text = pending.toString()
                        val mark = findMark(text, START_MARKS, 0)
                        val fence = findFenceStart(text)
                        val useFence = fence != null && (mark == null || fence.index < mark.index)
                        val hitIndex = if (useFence) fence!!.index else (mark?.index ?: -1)
                        if (hitIndex < 0) {
                            val keep = maxOf(
                                partialMarkSuffix(text, START_MARKS),
                                partialMarkSuffix(text, FENCE_STARTS),
                            )
                            val safeLen = text.length - keep
                            if (safeLen > 0) {
                                events += Event.Text(text.substring(0, safeLen))
                                pending.delete(0, safeLen)
                            }
                            return events
                        }
                        if (hitIndex > 0) {
                            events += Event.Text(text.substring(0, hitIndex))
                            pending.delete(0, hitIndex)
                        }
                        if (useFence) {
                            val start = fence!!.mark
                            pending.delete(0, start.length)
                            mode = Mode.Fenced
                            fenceText.setLength(0)
                            fenceText.append(start)
                        } else {
                            val start = mark!!.mark
                            pending.delete(0, start.length)
                            mode = Mode.Marked
                            events += Event.ToolMode
                        }
                    }

                    Mode.Marked -> {
                        val text = pending.toString()
                        val hit = findMark(text, END_MARKS, 0) ?: return events
                        val json = text.substring(0, hit.index).trim()
                        pending.delete(0, hit.index + hit.mark.length)
                        mode = Mode.Normal
                        parseOne(json)?.let { events += Event.Call(it) }
                    }

                    Mode.Fenced -> {
                        val text = pending.toString()
                        val end = text.indexOf(FENCE_END)
                        if (end < 0) return events
                        val inner = text.substring(0, end)
                        pending.delete(0, end + FENCE_END.length)
                        mode = Mode.Normal
                        fenceText.append(inner).append(FENCE_END)
                        val call = parseOne(inner.trim())
                        if (call != null && (tools.isEmpty() || call.name in tools)) {
                            events += Event.Call(call)
                        } else {
                            // 不是工具调用：原样输出（普通代码块不能吞）
                            events += Event.Text(fenceText.toString())
                        }
                        fenceText.setLength(0)
                    }
                }
            }
        }

        fun finish(): List<Event> {
            val events = mutableListOf<Event>()
            when (mode) {
                Mode.Marked -> pending.clear()

                Mode.Fenced -> {
                    // 未闭合的代码块：原样输出，避免吞掉普通代码
                    val rest = fenceText.toString() + pending.toString()
                    fenceText.setLength(0)
                    pending.clear()
                    if (rest.isNotEmpty()) events += Event.Text(rest)
                }

                Mode.Normal -> {
                    if (pending.isNotEmpty()) {
                        events += Event.Text(pending.toString())
                        pending.clear()
                    }
                }
            }
            mode = Mode.Normal
            return events
        }

        private fun findFenceStart(text: String): MarkHit? {
            var best: MarkHit? = null
            for (mark in FENCE_STARTS) {
                val idx = text.indexOf(mark)
                if (idx >= 0 && (best == null || idx < best!!.index)) best = MarkHit(idx, mark)
            }
            return best
        }

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
