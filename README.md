# R 中转

把 **Android 手机变成私有的 AI 模型聚合服务器**：将 Trae / Loomy / WorkBuddy / Antigravity / ChatGPT / 小浣熊 / 任意自定义 OpenAI 兼容上游，
统一翻译成一个 **OpenAI 兼容端点**，供任意客户端调用。

- 包名：`app.ai.rune.ziyong.transfer`
- 应用名：`R 中转`
- 视觉：基于 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)（MIUI / HyperOS 风格），不使用 Material3 组件

## 这是什么

- **本地私有无需上云**：所有账号与流量只存在你的手机里
- **统一端点**：`http://<手机局域网地址>:<端口>/v1/chat/completions`，兼容 OpenAI 协议
- **多供应商聚合**：账号池 + 智能路由，同一模型按供应商/账号自动分配
- **完整桌面级能力**：额度中心、调用记录、安全脱敏、并发容错（熔断/重试/冷却）、保活、导入导出、应用内更新

## 功能

| 模块 | 说明 |
|---|---|
| 首页 | 网关总开关、运行状态、局域网地址、今日遥测 |
| 供应商 | 列表 / 详情（登录 + 账号管理）/ 自定义供应商新建编辑 |
| 模型 | 模型清单（前缀说明 / 开放名单） |
| 调用记录 | 今日统计 / 请求列表 / 详情，含脱敏展示 |
| 设置 | 接入设置（端口 / API Key / 地址）、保活与权限、账号导入导出、额度中心、安全防护、并发与容错；主题 / 语言 / 更新 / 关于（模板自带） |

## 架构

```
:core   纯 Kotlin/JVM 网关内核（协议转换、供应商适配、账号池、存储、容错、统计），无 Android 依赖
:cli    桌面命令行工具（aigw），复用同一份内核，用于本机端到端联调
:app    Android 应用（miuix UI）
:baselineprofile  启动性能基线
build-plugins    构建约定插件（rtransfer.*）
```

详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 构建

```bash
./gradlew :core:test                 # 内核单测
./gradlew :app:assembleStableDebug   # debug APK
./gradlew :app:assembleStableRelease # release APK（需要签名 Secrets）
```

- CI（GitHub Actions）在 push `main` 时自动构建 Stable debug + release 并上传产物
- 需要在 `keystore.properties` 或环境变量/Secrets 中配置签名（debug 与 release 必须同签名，否则无法覆盖安装）

## 文档

- [docs/HANDOVER.md](docs/HANDOVER.md) — 交接文档（架构、约定、已知问题、后续建议，先读）
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 代码地图
- [docs/NEXT_SESSION_CHECKLIST.md](docs/NEXT_SESSION_CHECKLIST.md) — 一次性交接清单
- [AGENTS.md](AGENTS.md) — 面向 AI 代理的开发指引
- [CONTRIBUTING.md](CONTRIBUTING.md) — 贡献指南

## 许可

GPL-3.0（见 [LICENSE](LICENSE)）。