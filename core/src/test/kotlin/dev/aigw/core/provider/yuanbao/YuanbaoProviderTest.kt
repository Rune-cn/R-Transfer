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
        val original = YuanbaoCredential(cookie = "hy_token=a; hy_user=u1", deviceId = "d1", h38 = "hxyz")
        val parsed = YuanbaoCredential.parse(original.toJson())!!
        assertEquals(original, parsed)
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
    fun `error 帧输出提示并收尾`() {
        val translator = YuanbaoSseTranslator("id1", "m1", 1700000000)
        val out = translator.translate("data: {\"type\":\"error\",\"msg\":\"抱歉，我无法回答\"}")
        assertEquals(2, out.size)
        assertTrue(out[0].contains("抱歉"))
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

    @Test
    fun `请求带 tools 时明确拒绝`() {
        // tools 检查在发请求之前：不依赖网络即可断言拒绝语义
        val provider = YuanbaoProvider(hooks = ProviderHooks(), host = "http://127.0.0.1:1")
        val credential = YuanbaoCredential(cookie = cookie)
        val result = provider.openChat(
            ProviderAccount("yuanbao", "user123", "元宝", credential.toJson()),
            """{"model":"m1","stream":false,"tools":[{"type":"function","function":{"name":"search"}}],"messages":[{"role":"user","content":"hi"}]}""",
        )
        assertEquals(0, result.status)
        val failure = result.failure
        assertNotNull(failure)
        assertEquals(dev.aigw.core.provider.ErrorKind.CLIENT, failure.kind)
        assertTrue(failure.message.contains("不支持工具调用"))
    }

    @Test
    fun `tool_choice 单独出现也视为工具请求`() {
        val provider = YuanbaoProvider(hooks = ProviderHooks(), host = "http://127.0.0.1:1")
        val credential = YuanbaoCredential(cookie = cookie)
        val result = provider.openChat(
            ProviderAccount("yuanbao", "user123", "元宝", credential.toJson()),
            """{"model":"m1","stream":false,"tool_choice":"auto","messages":[{"role":"user","content":"hi"}]}""",
        )
        assertNotNull(result.failure)
        assertEquals(dev.aigw.core.provider.ErrorKind.CLIENT, result.failure!!.kind)
    }
}