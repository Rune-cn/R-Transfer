# 上游逆向调研记录（未接入）

> 记录**已做过完整技术调研、但决定暂不接入**的网页版上游，避免后人重复踩坑。
> 每一条都写清：协议是什么、卡在哪、要多少工作量、什么条件下才值得做。
>
> 最后更新：2026-11-08

---

## 0. 为什么单独立一份文档

R-Transfer 的供应商分两类：

- **已接入**：Trae / Loomy / WorkBuddy / Antigravity / ChatGPT / 小浣熊 / 腾讯元宝 / 自定义 OpenAI 兼容
- **调研过但未接入**：Grok 网页版、通义千问网页版（本文档）

未接入的不写进 HANDOVER 的功能清单（那会让人误以为能用），但也**不能直接丢掉**——协议细节、阻断点、参考实现都是实打实的成本，重来一遍要再花同样多的时间。

---

## 1. Grok 网页版（grok.com）

**结论：技术上可做，但工程量与风险都偏高，暂不接入。**

### 1.1 协议形态

| 项 | 内容 |
|---|---|
| 对话通道 | **WebSocket**：`wss://grok.com/ws/mgw/?uid=<x-userid>`（不是 HTTP/SSE） |
| 鉴权 | Cookie `sso`（JWT）+ `x-userid`，请求头 `Cookie: sso=<jwt>; x-userid=<uid>` |
| 额外签名 | `x-statsig-id`（见 1.2） |
| 会话 | 服务端管理，`session.create` 后由服务端返回 `session_id` |

### 1.2 statsig 签名（关键门槛）

Grok 对对话请求校验 `x-statsig-id`，它是「用首页 meta 里的校验串 + 请求方法/路径」算出来的：

```
1. GET https://grok.com/index（404 时回退 GET /）
2. 从 HTML 里取 <meta name="grok-site-verification" content="...">
3. POST <签名服务> {method, path, environment:{metaContent}}
   → { "x-statsig-id": "..." }   （解码后固定 70 字节）
```

参考实现默认用公共签名服务 `https://grok.wodf.de/sign`，签名结果缓存 1 小时。

### 1.3 对话消息序列

```
→ session.create        {session:{model, x_grok:{...}}}
← session.created
← conversation.attached  {conversation:{id}}
→ conversation.item.create  {item:{type:message, role:user, x_grok:{input_chunks:[{text:{text:...}}]}}}
→ response.create
← response.chunk ...     （正文增量 / tool_usage_card / render_citation）
← response.done
```

心跳：每 25 秒发 `{"event":{"type":"ping"}}`；`response.done` 或 `error` 结束。

### 1.4 为什么暂不接入

| 问题 | 说明 |
|---|---|
| **`:core` 没有 WebSocket 客户端** | `:core` 是纯 JVM、只用 `HttpURLConnection`。要做就得手写 RFC 6455（握手、帧掩码、分片、ping/pong、关闭帧），约 300 行，且要自己处理 TLS |
| **statsig 签名依赖外部服务** | 官方算法在混淆 JS 里；参考实现直接用第三方签名服务。这等于把可用性绑在一个不受控的外部服务上 |
| **Cloudflare 风控** | 参考实现用 `bogdanfinn/fhttp`（浏览器 TLS 指纹库）才稳定；Android 上 `HttpURLConnection` 的 TLS 指纹与浏览器不同，可能被识别 |
| **参考实现规模** | `gateway.go` 22KB + `chat.go` 90KB + `statsig.go` 15KB + `sso_build.go` 21KB，移植量约 150KB Go |

### 1.5 参考实现

- `chenyme/grok2api`（Go，7888 星，2026-11-08 仍在更新）：支持 Grok Build / **Grok Web** / Grok Console 三通道，OpenAI + Anthropic 兼容
- 关键文件：`backend/internal/infra/provider/web/{gateway,chat,statsig,sso_build,headers}.go`
- 模型目录：`grok-chat-fast`（Basic）/ `grok-chat-auto` / `grok-chat-expert`（Super）/ `grok-chat-heavy`（Heavy），另有 imagine 图片/视频系列

### 1.6 如果将来要接

1. 先实现最小 WebSocket 客户端（只支持 text frame + ping/pong + close），用 `wss://echo.websocket.org` 之类验证
2. 移植 `gatewayEndpoint` / `gatewaySession` / `gatewayTurnEvents` / `parseGatewayEvent` 四段（协议核心，约 400 行 Kotlin）
3. statsig 先支持「手动填 `x-statsig-id`」作为最小可用路径，再考虑签名服务
4. 需要真机验证 Cloudflare 是否放行——这是唯一无法在本地判断的点

---

## 2. 通义千问网页版（chat.qwen.ai）

**结论：协议已完全摸清、请求体已还原，但当前环境被两道墙挡住（Cookie 过期 + 阿里云 WAF），需「内置 WebView 登录」才可行，暂不接入。**

### 2.1 端点（2026-11-08 实测）

| 端点 | 方法 | 说明 | 实测 |
|---|---|---|---|
| `/api/config` | GET | 站点配置（含 `enable_anonymous`、功能开关、语言列表） | ✅ 200 |
| `/api/models` | GET | 模型列表（OpenAI 风格 `{data:[...]}`） | ✅ 200，无需登录 |
| `/api/v1/auths/` | GET | **用 Cookie 换 token** | ❌ 401（Cookie 失效） |
| `/api/v2/chats/new` | POST | 建会话 `{}` → `{data:{id}}` | ❌ Unauthorized / WAF |
| `/api/v2/chat/completions` | POST | **SSE 对话** | ❌ 阿里云 WAF 拦截 |
| `/v1/models`、`/v1/chat/completions` | — | **不存在**（返回 HTML 首页） | — |

模型列表实际返回（供参考）：

```
qwen3.7-plus          Qwen3.7-Plus         上下文 1M，vision/document/video/audio/thinking/search
qwen3.8-max           Qwen3.8-Max          旗舰，上下文 1M
qwen3.8-omni-flash    Qwen3.8-Omni-Flash   原生多模态（文本/图/音/视频），1M 上下文
```

### 2.2 鉴权链路

```
Cookie（含 tongyi_sso_ticket）
  → GET /api/v1/auths/            （带 Cookie）
  → { token: "..." }               （JWT，含 exp）
  → 后续所有请求 Authorization: Bearer <token> + Cookie
```

请求头需接近浏览器（`source: web`、`bx-v`、`sec-ch-ua*`、`sec-fetch-*`、`x-request-id`、`origin`、`referer`）。

### 2.3 对话请求体（已完整还原）

```json
{
  "stream": true,
  "incremental_output": true,
  "chat_id": "<建会话返回的 id>",
  "chat_mode": "normal",
  "model": "qwen3.8-max",
  "parent_id": null,
  "messages": [{
    "fid": "<uuid>",
    "parentId": null,
    "childrenIds": [],
    "role": "user",
    "content": "用户输入",
    "user_action": "chat",
    "files": [],
    "timestamp": 1794117600,
    "models": ["qwen3.8-max"],
    "chat_type": "t2t",
    "feature_config": { "thinking_enabled": false, "output_schema": "phase" },
    "extra": { "meta": { "subChatType": "t2t" } },
    "sub_chat_type": "t2t",
    "parent_id": null
  }],
  "timestamp": 1794117600
}
```

`chat_type` 取值：`t2t`（文本）/ `search` / `t2i`（文生图）/ `image_edit` / `t2v`（文生视频）/ `deep_research`。
`feature_config.thinking_enabled=true` 开启思考模式（返回 `reasoning` 字段）。

### 2.4 SSE 响应格式

```
data: {"choices":[{"delta":{"content":"...","phase":"...","status":"..."},"finish_reason":null}]}
data: {"success":false,"data":{"code":"...","details":"..."}}      ← 业务错误
data: [DONE]
```

- 正文增量：`choices[0].delta.content`
- 图片生成：`delta.phase == "image_gen"` 或 `chat_type in (t2i, image_edit)` 时，content 是 CDN 直链
- 结束：`delta.status == "finished"` 或 `finish_reason == "stop"`

### 2.5 实测阻断点（2026-11-08）

| # | 阻断 | 证据 |
|---|---|---|
| 1 | **Cookie 已过期** | `GET /api/v1/auths/` → 401；`GET /api/v2/user`（ticket 当 token）→ `"Your session has expired, or the token is no longer valid"` |
| 2 | **阿里云 WAF** | `POST /api/v2/chat/completions` → `{"ret":["FAIL_SYS_USER_VALIDATE","RGV587_ERROR::SM::哎哟喂,被挤爆啦,请稍后重试"]}`，附 `_____tmd_____/punish?...&action=captcha`（滑块）；`POST /api/v2/chats/new` 直接返回 WAF 挑战 HTML（`aliyun_waf_aa`） |

WAF 拦截的两个叠加因素：
- **数据中心 IP**（本沙箱）：真实用户手机是住宅 IP，通常不触发
- **无有效会话**：未登录请求更容易被判可疑

### 2.6 可行路径（若将来接入）

与腾讯元宝 USKey 铸造器**完全同构**的方案：

```
App 内置 WebView（真实浏览器指纹 + 住宅 IP）
  → 用户正常登录 chat.qwen.ai（Google / GitHub / 账号密码）
  → WAF 在真实浏览器里自然通过，Cookie 里带上 isg / x5sec 等 WAF 凭证
  → 从 CookieManager 读完整 Cookie
  → 用 Cookie 换 token（/api/v1/auths/）
  → 之后纯 HTTP（HttpURLConnection 即可，无需 WebSocket）
```

与 Grok 相比，千问**没有 WebSocket 门槛**，只要解决登录态就能纯 HTTP 复用——工程量明显更小。

### 2.7 参考实现

| 仓库 | 语言 | 星 | 说明 |
|---|---|---|---|
| `YangYuHou/qwen2API` | JS | 971 | 网页版转 API |
| `Rfym21/Qwen2API` | — | 844 | 同类 |
| `ckcoding/QwenChat2Api` | Node | 68 | 结构最清晰，`lib/{headers,sse,transformers,token-refresh}.js` 可直接对照 |
| `smanx/qwen2api` | JS | 169 | 单文件 `core.js`（90KB），覆盖最全 |

### 2.8 如果将来要接

1. 复用元宝那套 `WebViewUskeyMinter` 的骨架，改成「登录后只取 Cookie」
2. `QwenProvider`：`importCredentials`（Cookie→token）、`listModels`（`/api/models`）、`openChat`（建会话 + SSE）
3. SSE 翻译器：`choices[0].delta.content` → OpenAI chunk，`success:false` → 标准错误帧
4. 图片生成可映射为 content 里的 Markdown 图片链接

---

## 3. 通用经验（对后续任何网页版上游都适用）

### 3.1 先分清「技术阻断」与「环境阻断」

| 类型 | 例子 | 能否在沙箱判断 |
|---|---|---|
| 技术阻断 | 需要 WebSocket、需要动态签名、需要 protobuf | ✅ 能，读参考实现即可 |
| 环境阻断 | 数据中心 IP 被 WAF 拦、Cookie 过期 | ❌ 不能，必须真机住宅网络验证 |

**别把环境阻断误判成「协议不可行」**。千问就是典型：协议完全清楚，只是沙箱 IP + 过期 Cookie 过不了 WAF。

### 3.2 WAF 识别特征

- 响应体含 `aliyun_waf_aa` / `aliyun_waf_bb` / `nc-container` / `punish` / `captcha`
- `FAIL_SYS_USER_VALIDATE` + `RGV587_ERROR`（阿里系）
- HTTP 200 但返回 HTML 而不是 JSON —— 这是最强的信号：**看 content-type，不要只看状态码**

### 3.3 「内置 WebView」是网页版上游的通用钥匙

原因：WebView 天然具备
- 真实浏览器 TLS 指纹与 JS 环境
- 手机住宅 IP
- 能跑完 WAF 的 JS 挑战、拿到 `isg` / `x5sec` 等凭证
- 登录态（Cookie / localStorage）就在本地，可直接读出

代价：WebView 生命周期管理（挂窗口、被回收、超时重建）——这套已在腾讯元宝的 `app/.../uskey/` 里解决，可复用。

### 3.4 判断「值不值得做」

按顺序问：

1. 参考实现有多少星、最近是否还在更新？（7888 星的 grok2api vs 作者自称「随便弄着玩的」项目，可信度差很多）
2. 通道是 HTTP/SSE 还是 WebSocket？后者在 `:core` 里是全新基础设施
3. 鉴权是「长期有效」还是「短期动态签名」？后者要常驻浏览器环境
4. 风控是「协议级」还是「环境级」？环境级在真机上可能根本不是问题

四条里踩中两条以上，就要认真评估是否值得。

---

## 4. 相关文档

- [HANDOVER.md](HANDOVER.md) — 已接入供应商的完整说明
- [ARCHITECTURE.md](ARCHITECTURE.md) — 代码地图
- [NEXT_SESSION_CHECKLIST.md](NEXT_SESSION_CHECKLIST.md) — 会话开始清单与待办