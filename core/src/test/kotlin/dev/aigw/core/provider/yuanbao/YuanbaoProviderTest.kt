package dev.aigw.core.provider.yuanbao

import dev.aigw.core.provider.AuthKind
import dev.aigw.core.provider.ProviderAccount
import dev.aigw.core.provider.ProviderHooks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YuanbaoProviderTest {

    private val cookie = "hy_token=abc.def.ghi; hy_user=user123; _qimei_uuid42=dev42; other=1"

    // ------------------------------------------------------------------ 凭据

    @Test
    fun `粘贴完整 Cookie 可导入`() {
        val credential = YuanbaoCredential.fromPasted(cookie)!!
        assertEquals("user123", credential.parseUserId())
        assertEquals(cookie, credential.cookie)
    }

    @Test
    fun `缺少会话关键项的 Cookie 被拒绝`() {
        assertNull(YuanbaoCredential.fromPasted("foo=1; bar=2"))
    }

    @Test
    fun `粘贴凭证 JSON 可导入`() {
        val raw = """{"cookie":"hy_token=a; hy_user=u1","deviceId":"dev1","h38":"h38xyz"}"""
        val credential = YuanbaoCredential.fromPasted(raw)!!
        assertEquals("dev1", credential.deviceId)
        assertEquals("h38xyz", credential.h38)
        assertEquals("u1", credential.parseUserId())
    }

    @Test
    fun `凭证 JSON 序列化往返一致`() {
        val original = YuanbaoCredential(
            cookie = "hy_token=a; hy_user=u1",
            deviceId = "d1",
            h38 = "hxyz",
            uskeyServiceUrl = "http://127.0.0.1:1234/uskey",
        )
        val parsed = YuanbaoCredential.parse(original.toJson())!!
        assertEquals(original, parsed)
    }

    @Test
    fun `缺少 USKey 铸造服务时不注入签名头（provider 仍可加载）`() {
        val provider = YuanbaoProvider(host = "http://127.0.0.1:1")
        // 未配置 uskeyServiceUrl：不会去连铸造服务，直接走网络（此处必然失败但不崩）
        val credential = YuanbaoCredential(cookie = cookie)
        val result = provider.openChat(
            ProviderAccount("yuanbao", "u1", "元宝", credential.toJson()),
            """{"model":"deep_seek_v3","stream":true,"messages":[{"role":"user","content":"hi"}]}""",
        )
        assertFalse(result.status in 200..299)
    }

    @Test
    fun `设备画像成对解析（cookie 里的 qimei uuid）`() {
        val credential = YuanbaoCredential.fromPasted(cookie)!!
        // cookie 未显式带 deviceId 时，回退到 _qimei_uuid42
        assertEquals("dev42", credential.resolvedDeviceId())
        // 显式配置优先
        assertEquals("explicit", credential.copy(deviceId = "explicit").resolvedDeviceId())
    }

    @Test
    fun `provider 元数据正确`() {
        val provider = YuanbaoProvider()
        assertEquals("yuanbao", provider.id)
        assertEquals("腾讯元宝", provider.displayName)
        assertEquals(AuthKind.COOKIE_PASTE, provider.authKind)
        assertEquals(listOf("yuanbao.tencent.com", "static.yuanbao.tencent.com"), provider.hosts())
    }

    @Test
    fun `importCredentials 从 Cookie 建立账号`() {
        // host 指向本地不可达端口：连接立即被拒，测试不依赖外网也不挂起
        val provider = YuanbaoProvider(hooks = ProviderHooks(), host = "http://127.0.0.1:1")
        val account = provider.importCredentials(cookie)
        assertNotNull(account)
        assertEquals("yuanbao", account.providerId)
        assertEquals("user123", account.uid)
        // 无法联网时昵称回落默认值
        assertEquals("元宝账号", account.nickname)
        // 凭证可解析回同一 Cookie
        val credential = YuanbaoCredential.parse(account.secret)!!
        assertEquals(cookie, credential.cookie)
    }

    @Test
    fun `无账号时模型目录为空且不报错`() {
        val provider = YuanbaoProvider()
        val view = provider.listModels(null)
        assertTrue(view.models.isEmpty())
        assertEquals("", view.error)
    }

    @Test
    fun `模型列表解析不抛异常`() {
        // 有账号但连不上网：应返回空列表+错误，而不是抛异常
        val provider = YuanbaoProvider(host = "http://127.0.0.1:1")
        val account = ProviderAccount("yuanbao", "u1", "元宝", YuanbaoCredential(cookie).toJson())
        val list = provider.listModels(account)
        assertTrue(list.models.isEmpty())
    }

    @Test
    fun `resolveModel 去掉前缀`() {
        val provider = YuanbaoProvider()
        assertEquals("hunyuan_gpt_175B_0404", provider.resolveModel("yuanbao/hunyuan_gpt_175B_0404"))
        assertEquals("deep_seek_v3", provider.resolveModel("deep_seek_v3"))
        assertEquals("deep_seek_v3", provider.resolveModel(""))
    }

    // ------------------------------------------------------------------ 错误分类

    @Test
    fun `classify 401 判会话失效`() {
        val provider = YuanbaoProvider()
        assertEquals(dev.aigw.core.provider.ErrorKind.SESSION_DEAD, provider.classify(401, "unauthorized").kind)
    }

    @Test
    fun `classify 429 判限流`() {
        val provider = YuanbaoProvider()
        assertEquals(dev.aigw.core.provider.ErrorKind.SOFT_RATE, provider.classify(429, "too many").kind)
    }

    // ------------------------------------------------------------------ SSE 翻译

    @Test
    fun `text 帧翻译为 content delta`() {
        val translator = YuanbaoSseTranslator("id1", "m1", 1700000000)
        val out = translator.translate("data: {\"type\":\"text\",\"msg\":\"你好\"}")
        assertEquals(1, out.size)
        assertTrue(out[0].startsWith("data: "))
        assertTrue(out[0].contains("\"content\":\"你好\""))
    }

    @Test
    fun `status 带 stopReason 输出收尾帧`() {
        val translator = YuanbaoSseTranslator("id1", "m1", 1700000000)
        val out = translator.translate("data: {\"type\":\"status\",\"stopReason\":\"finished\"}")
        assertEquals(2, out.size)
        assertTrue(out[1].contains("[DONE]"))
    }

    @Test
    fun `error 帧输出标准错误格式而非内容`() {
        val translator = YuanbaoSseTranslator("id1", "m1", 1700000000)
        val out = translator.translate("data: {\"type\":\"error\",\"msg\":\"服务繁忙，请稍后再试。\"}")
        assertEquals(2, out.size)
        assertTrue(out[0].contains("\"error\""), "应为 OpenAI 错误帧")
        assertTrue(out[0].contains("服务繁忙"))
        assertFalse(out[0].contains("\"choices\""), "错误帧不应伪装成正常回复")
        assertTrue(out[1].contains("[DONE]"))
    }

    @Test
    fun `非 data 行与未知类型被忽略`() {
        val translator = YuanbaoSseTranslator("id1", "m1", 1700000000)
        assertEquals(emptyList(), translator.translate("event: message"))
        assertEquals(emptyList(), translator.translate("data: {\"type\":\"plugin\",\"msg\":\"search\"}"))
        assertEquals(emptyList(), translator.translate(""))
    }

    @Test
    fun `close 补齐 DONE`() {
        val translator = YuanbaoSseTranslator("id1", "m1", 1700000000)
        val out = translator.close()
        assertEquals(2, out.size)
        assertTrue(out[1].contains("[DONE]"))
        // 重复 close 幂等
        assertEquals(emptyList(), translator.close())
    }

    // ------------------------------------------------------------------ 请求

    @Test
    fun `openChat 无网络时快速失败而不是挂起`() {
        val provider = YuanbaoProvider(host = "http://127.0.0.1:1")
        val credential = YuanbaoCredential(cookie = cookie)
        val result = provider.openChat(
            ProviderAccount("yuanbao", "user123", "元宝", credential.toJson()),
            """{"model":"m1","stream":true,"messages":[{"role":"user","content":"hi"}]}""",
        )
        assertFalse(result.status in 200..299, "不能连网的环境下应快速失败（而不是挂起）")
    }

    // ------------------------------------------------------------------ 工具调用桥接

    @Test
    fun `工具定义注入系统提示`() {
        val tools = com.google.gson.JsonParser.parseString(
            """[{"type":"function","function":{"name":"web_search","description":"联网搜索","parameters":{"type":"object","properties":{"q":{"type":"string"}}}}}]""",
        ).asJsonArray
        val prompt = YuanbaoToolBridge.buildToolSystemPrompt(tools)
        assertTrue(prompt.contains("web_search"))
        assertTrue(prompt.contains("联网搜索"))
        assertTrue(prompt.contains("<|tool_calls_begin|>"))
        assertTrue(prompt.contains("<|tool_calls_end|>"))
    }

    @Test
    fun `消息转 prompt 保留工具调用与执行结果`() {
        val messages = com.google.gson.JsonParser.parseString(
            """[
              {"role":"system","content":"你是助手"},
              {"role":"user","content":"查一下今天天气"},
              {"role":"assistant","tool_calls":[{"id":"c1","type":"function","function":{"name":"web_search","arguments":"{\"q\":\"天气\"}"}}]},
              {"role":"tool","name":"web_search","content":"晴天 25 度"}
            ]""",
        ).asJsonArray
        val prompt = YuanbaoToolBridge.convertMessages(messages, null)
        assertTrue(prompt.contains("[系统提示: 你是助手]"))
        assertTrue(prompt.contains("用户: 查一下今天天气"))
        assertTrue(prompt.contains("调用工具 web_search"))
        assertTrue(prompt.contains("工具 web_search 的执行结果:"))
        assertTrue(prompt.contains("晴天 25 度"))
        assertTrue(prompt.contains("请作为助手继续回复"))
    }

    @Test
    fun `解析 ASCII 与全角标记的工具调用`() {
        val ascii = """好的<|tool_calls_begin|>{"name":"web_search","arguments":{"q":"hi"}}<|tool_calls_end|>"""
        val c1 = YuanbaoToolBridge.parseToolCalls(ascii)
        assertEquals(1, c1.size)
        assertEquals("web_search", c1[0].name)
        assertTrue(c1[0].arguments.contains("\"q\""))

        val unicode = """<｜tool▁calls_begin｜>{"name":"run","arguments":"{}"}<｜tool▁calls_end｜>"""
        val c2 = YuanbaoToolBridge.parseToolCalls(unicode)
        assertEquals(1, c2.size)
        assertEquals("run", c2[0].name)
    }

    @Test
    fun `多个工具调用按顺序解析`() {
        val text = """<|tool_calls_begin|>{"name":"a","arguments":"{}"}<|tool_calls_end|>
            <|tool_calls_begin|>{"name":"b","arguments":"{}"}<|tool_calls_end|>"""
        val calls = YuanbaoToolBridge.parseToolCalls(text)
        assertEquals(listOf("a", "b"), calls.map { it.name })
    }

    @Test
    fun `非法 JSON 不产生工具调用`() {
        val text = "<|tool_calls_begin|>not-json<|tool_calls_end|>"
        assertTrue(YuanbaoToolBridge.parseToolCalls(text).isEmpty())
    }

    @Test
    fun `流式解析跨 chunk 的工具标记`() {
        val parser = YuanbaoToolBridge.StreamParser()
        val events = mutableListOf<YuanbaoToolBridge.StreamParser.Event>()
        events += parser.feed("你好<|tool_calls_be")
        events += parser.feed("gin|>{\"name\":\"web_search\",\"arguments\":\"{}\"}<|tool_calls_end|>")
        events += parser.finish()
        val texts = events.filterIsInstance<YuanbaoToolBridge.StreamParser.Event.Text>()
            .joinToString("") { it.text }
        val calls = events.filterIsInstance<YuanbaoToolBridge.StreamParser.Event.Call>()
        assertTrue(texts.contains("你好"))
        assertEquals(1, calls.size)
        assertEquals("web_search", calls[0].call.name)
    }

    @Test
    fun `未闭合的工具段在流结束时丢弃`() {
        val parser = YuanbaoToolBridge.StreamParser()
        parser.feed("<|tool_calls_begin|>{\"name\":\"x\"")
        val rest = parser.finish().filterIsInstance<YuanbaoToolBridge.StreamParser.Event.Text>()
        assertTrue(rest.isEmpty())
    }

    @Test
    fun `SSE 翻译器输出标准 tool_calls 帧`() {
        val translator = YuanbaoSseTranslator("chatcmpl-abc", "deep_seek_v3", 1700000000)
        val out = translator.translate(
            """data: {"type":"text","msg":"<|tool_calls_begin|>{\"name\":\"web_search\",\"arguments\":\"{}\"}<|tool_calls_end|>"}""",
        )
        assertTrue(out.any { it.contains("\"tool_calls\"") }, "应输出 tool_calls 帧")
        assertTrue(out.any { it.contains("web_search") })
        val tail = translator.close()
        assertTrue(tail.any { it.contains("tool_calls") }, "结束帧 finish_reason 应为 tool_calls")
    }

    @Test
    fun `代码块格式的工具调用也能解析`() {
        val text = "我来查一下\n```json\n{\"name\":\"web_search\",\"arguments\":{\"q\":\"hi\"}}\n```"
        val calls = YuanbaoToolBridge.parseToolCalls(text, setOf("web_search"))
        assertEquals(1, calls.size)
        assertEquals("web_search", calls[0].name)
    }

    @Test
    fun `裸 JSON 仅在工具名匹配时识别`() {
        val text = "调用 {\"name\":\"web_search\",\"arguments\":{\"q\":\"x\"}}"
        // 已知工具 → 识别
        assertEquals(1, YuanbaoToolBridge.parseToolCalls(text, setOf("web_search")).size)
        // 未知工具 → 不识别（避免把普通 JSON 当工具调用）
        assertTrue(YuanbaoToolBridge.parseToolCalls(text, setOf("other_tool")).isEmpty())
        // 无已知工具名单 → 不识别
        assertTrue(YuanbaoToolBridge.parseToolCalls(text).isEmpty())
    }

    @Test
    fun `流式代码块非工具调用时原样输出`() {
        val parser = YuanbaoToolBridge.StreamParser(setOf("web_search"))
        val events = mutableListOf<YuanbaoToolBridge.StreamParser.Event>()
        events += parser.feed("示例代码：```json{\"a\":1}```")
        events += parser.finish()
        val text = events.filterIsInstance<YuanbaoToolBridge.StreamParser.Event.Text>()
            .joinToString("") { it.text }
        assertTrue(text.contains("```json"), "普通代码块必须原样保留")
        assertTrue(events.none { it is YuanbaoToolBridge.StreamParser.Event.Call })
    }

    @Test
    fun `流式代码块工具调用可识别`() {
        val parser = YuanbaoToolBridge.StreamParser(setOf("web_search"))
        val events = mutableListOf<YuanbaoToolBridge.StreamParser.Event>()
        events += parser.feed("```json{\"name\":\"web_search\",\"arguments\":\"{}\"}```")
        events += parser.finish()
        val calls = events.filterIsInstance<YuanbaoToolBridge.StreamParser.Event.Call>()
        assertEquals(1, calls.size)
        assertEquals("web_search", calls[0].call.name)
    }
}
