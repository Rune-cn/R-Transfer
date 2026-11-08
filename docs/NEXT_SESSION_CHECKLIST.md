# 一次性交接清单（给下一个会话）

> 让下一个会话在**无本会话记忆**的情况下直接接手。
> 开始前先读：`AGENTS.md`、`docs/HANDOVER.md`、`docs/ARCHITECTURE.md`，再按本清单执行。

## 0. 环境与访问

- **沙箱**：Ubuntu 24.04 rootfs，工作目录 `/workspace`；`/upload` 只读
- **网络**：本沙箱能直连 api.github.com；**git 协议（git push / ls-remote）经常超时**
  ——提交一律走 GitHub Git Trees/Blobs API（见 `docs/HANDOVER.md` §10）
- **仓库**：`Rune-cn/R-Transfer`（**公开**），main 直接推送（历史惯例）
- **凭据**：GitHub PAT（classic：`repo`）——**由用户在会话中提供，严禁写入仓库/文档**；正文字符串也勿回显
- **构建**：一般不做本地构建（无 JDK25/SDK）。工作流 = 静态引用扫描 → 提交推送 → 等 CI（5–15 分钟）→ 产物下载链接发给用户
- **每次构建完成必须给下载链接**（运行页 + 两个 artifact）
- **CRITICAL**：CI 需要 Secrets 签名才能出可覆盖安装的包；debug/release 同签名是硬要求

## 1. 项目一句话速览

**R 中转**：把 Android 手机变成私有 AI 模型聚合服务器（Trae / Loomy / WorkBuddy / Antigravity / ChatGPT / 小浣熊 / 自定义 OpenAI 兼容上游 → 统一 OpenAI 兼容端点）。
包名 `app.ai.rune.ziyong.transfer`；5 个模块（`:core` 纯 JVM / `:cli` / `:app` / `:baselineprofile` / `build-plugins`）；
miuix UI 底部 5 Tab（首页 / 供应商 / 模型 / 调用记录 / 设置）；`AppViewModel` 是唯一业务状态源，引擎单例。

## 2. ⚠️ 未闭环、需先与用户确认的问题（最高优先）

| # | 事项 | 状态 | 下一步 |
|---|---|---|---|
| **A** | 更新检查：`OnlineUpdatePolicy.canCheckUpdates` 拦截 debug/UNSTABLE 构建（CI 产出 Stable **debug** 包）→ 用户装的 debug 包永远「已是最新版本」；失败与「无更新」提示未区分 | 待定 | 与用户确认是否放开 debug 检查 / 区分三态提示（有更新 / 无更新 / 检查失败） |
| **B** | Release 尚未创建，产物只有 Actions artifact（debug 保留 14 天 / release 30 天） | 待定 | 确认后创建 GitHub Release 传 APK，避免过期 |
| **C** | 未接线能力（`:core` 已有，UI 未暴露）：供应商专属设置（ProviderUi 7 个上游实现未移植）、签到/任务中心入口、模型测速按钮 | 待定 | 按用户优先级逐个接线 |

## 3. 待办（功能/质量，按建议顺序）

1. **模型测速**：`testModel()` 已在 `AppViewModel`，给模型页接「测速」按钮（最轻量、收益直接）
2. **签到 / 任务中心**：`checkinAll()` / `runAllTasks()` 可用，首页加入口页
3. **供应商专属设置**：移植上游 `ProviderUi` 实现（Trae 设备指纹 / WorkBuddy 成长中心 / Loomy 新手任务 / 小浣熊积分兑换），账号管理页按供应商类型走专属路径
4. **元宝对话：网页版直连 + 内置 USKey 铸造器（已自动接线）**——用户只需在内置浏览器登录；
   账号写入后自动启动铸造器并把本机地址写进凭证，对话前自动取新鲜 USKey。
   协议对照 `10020099/yuanbao2api`；**USKey 为实测必需**（缺它上游返回「无法回答」）。
5. **更新检查三态**：区分「有更新 / 无更新 / 检查失败」；debug 构建放开或明确告知
6. **Release 固定下载**：创建 GitHub Release 传 APK，解决 artifact 过期问题
7. **更新下载应用内化**（可选）：应用内下载 + 进度 + 安装（`ApkInstaller` 已有，缺 UI 进度）
8. **日志持久化**（可选）：`cacheDir` 会被系统清缓存 → 评估迁 `filesDir`（`util/timber/FileLoggingTree.kt`、`LogViewModel.kt`、`core/crash/CrashHandler.kt`）

9. **Grok / 千问网页版**：已完整调研但未接入（协议、阻断点、参考实现、落地步骤见
   [UPSTREAM_RESEARCH.md](UPSTREAM_RESEARCH.md)）。若将来要接，优先千问（纯 HTTP+SSE，无 WebSocket），
   并必须走「内置 WebView 登录」过 WAF

## 4. 铁律（仓库强制，见 AGENTS.md）

1. **文档同步铁律（每次修改都要更新文档）**——任何代码/资源/构建/配置改动，必须在本提交内同步更新：
   `AGENTS.md`、根 `README.md`、`docs/HANDOVER.md`、`docs/ARCHITECTURE.md`、本清单、`CONTRIBUTING.md`；禁止「只改代码不动文档」；README 仅中文
2. `:core` 纯 JVM，禁止 Android 依赖（否则 :cli / 单测全废）
3. 引擎单例：不新增第二个 ViewModel / 不 new 第二个引擎
4. UI 排版一律走 `common/PreferenceCard.kt` 组件族（PreferenceCard / NavigationRow / SwitchRow / ...），不直接往 `Card` 塞裸内容
5. Compose 纪律：`remember` 不进 LazyListScope；`stringResource` 只在 @Composable 内；MiuixIcons 扩展须显式 import
6. 字符串三包同步：删除默认语言键必须同删 en / zh-rCN / zh-rTW，否则 lintVital `ExtraTranslation` 致命
7. 提交 Conventional Commits；主体 ≤72 字符；不加 AI 署名；诚实汇报验证（跑过什么、是否通过、没跑什么）
8. 每次构建完成给下载链接

## 5. 会话开始检查清单

- [ ] 向用户索要 GitHub PAT（勿写入仓库；勿在回复中大段回显）
- [ ] 读完 4 份文档（AGENTS、HANDOVER、ARCHITECTURE、本清单）
- [ ] 拉取最新 main（git 不通就走 API 取 head），确认工作区干净
- [ ] 静态扫描确认无旧文档残留引用（自查时排除本清单——里面可能写有被禁词）
- [ ] 先把第 2 节 A–C 反馈给用户确认，再开新活儿
- [ ] 每轮改动遵守第 4 节铁律；收尾给下载链接

## 6. GitHub 操作速查

```bash
# 最新 CI 运行
curl -s -H "Authorization: Bearer $TOKEN" "https://api.github.com/repos/Rune-cn/R-Transfer/actions/runs?per_page=1"
# 产物
curl -s -H "Authorization: Bearer $TOKEN" "https://api.github.com/repos/Rune-cn/R-Transfer/actions/runs/$RUN_ID/artifacts"
# git 不通时用 Git Trees/Blobs API 提交（流程见 docs/HANDOVER.md §10）
```

## 7. 2026-11-07 变更记录（供追溯）

- **docs 全面对齐**：AGENTS.md / 根 README.md / HANDOVER.md / ARCHITECTURE.md / 本清单全部重写为 R-Transfer 现状；
  删除冗余的 `docs/HANDOVER_CN.md`、`docs/README_CN.md`（与 HANDOVER.md / 根 README 同语言重复，是漂移根源；
  单一事实源原则）；CONTRIBUTING.md 引用同步修正
- **新增铁律 #1**：「每次修改都要更新文档」（写进 AGENTS.md 铁律、HANDOVER §11 自检清单、本清单 §4）
- 上一版本（miuix-example 时代）的 A–D 未确认项（关于页卡片透明度、设备模糊、语言+主题卡合并、更新 Sheet 按钮色）
  已随 HANDOVER_CN.md 删除一并归档，不再跟踪