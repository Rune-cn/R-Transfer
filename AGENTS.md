# AGENTS.md

面向 AI 编码代理的仓库指引。

## 用途

本文件定义在 **R 中转（R-Transfer）** 上工作时必须遵守的规则：

- 改动放哪里、哪些约束不能破坏、怎么自证完成。
- 窄任务做最小改动；大功能/跨文件重构先列计划再动手。

## 关键事实

- **R 中转**：把 Android 手机变成私有的 AI 模型聚合服务器——将 Trae / Loomy / WorkBuddy / Antigravity / ChatGPT / 小浣熊 / 腾讯元宝 / 自定义 OpenAI 兼容上游统一翻译成一个 OpenAI 兼容端点（包名 `app.ai.rune.ziyong.transfer`，应用名 `R 中转`）。
- **模块**：`:core`（纯 Kotlin/JVM 网关内核，无 Android 依赖）、`:cli`（命令行工具 `aigw`）、`:app`（Android UI）、`:baselineprofile`、`build-plugins`（`rtransfer.*` 约定插件）。
- **UI**：整套基于 **miuix**（`top.yukonga.miuix.kmp`），**不使用 Material3 组件**；底部 5 个 Tab（首页 / 供应商 / 模型 / 调用记录 / 设置）。
- **状态**：`AppViewModel` 是全部业务状态的唯一来源（Koin `viewModelOf` 单例）；网关引擎是进程级单例（`App.gateway`）。
- **语言包**：仅 `values/`（en）+ `values-zh-rCN` + `values-zh-rTW`，三包键集合必须一致；README 仅中文。
- **服务**：`GatewayService`（前台服务）、`GatewayTileService`（磁贴）、开机/重启恢复 Receiver。

## 项目布局

- `core/src/main/kotlin/dev/aigw/core/` — 网关内核：`gateway`（引擎/HTTP/协议翻译）、`provider/*`（供应商适配）、`pool`（账号池）、`failover`（容错）、`security`、`store`、`usage`、`update`、`util`
- `app/src/main/java/app/ai/rune/ziyong/transfer/` — Android 应用：`gateway/`（AppViewModel、服务、加密存储）、`ui/`（导航、页面、主题、组件）、`di/`（Koin）
- `cli/src/main/kotlin/dev/aigw/cli/Main.kt` — 命令行入口
- `docs/HANDOVER.md` — 交接文档（架构、约定、已知问题，**先读**）
- `docs/ARCHITECTURE.md` — 代码地图
- `docs/NEXT_SESSION_CHECKLIST.md` — 一次性交接清单（会话开始先读）

## 构建与验证

```bash
./gradlew :core:test                 # 内核单测（~80 个用例，JVM 直跑）
./gradlew :app:assembleStableDebug   # 冒烟
./gradlew :app:assembleStableRelease # release（需签名 Secrets）
```

- 需要 JDK 25 toolchain、Android SDK、GitHub Actions Secrets（签名）。本地无 JDK25/SDK 时：以**静态引用扫描 + 推送后 CI 验证**迭代；**如实报告**跑了什么、结果如何。
- CI（`.github/workflows/build.yml`）：push `main` 构建 Stable debug + release（含 `:core:test` 之外的构建），PR 只跑 `:core:test` + debug；产物上传 artifact。
- 本环境 git 协议有时不通（`Failed to connect to github.com port 443`），API 正常——提交方案见 `docs/HANDOVER.md` §10。

## 铁律（易踩坑，按优先级）

1. **文档同步铁律（每次修改都要更新文档）**：任何代码 / 资源 / 构建 / 配置改动，必须在本提交内同步更新对应文档（根 `README.md`、`docs/HANDOVER.md`、`docs/ARCHITECTURE.md`、`docs/NEXT_SESSION_CHECKLIST.md`、`AGENTS.md`、`CONTRIBUTING.md`）。禁止「只改代码不动文档」的提交。新增文件/页面/模块时，把新事实写进 ARCHITECTURE 的代码地图与 HANDOVER 的功能清单。
2. **`:core` 纯 JVM**：禁止向 `:core` 添加任何 Android 依赖（它同时被 `:app`、`:cli`、单测消费）。只用 `HttpURLConnection` + gson + nanohttpd 这类纯 Java 库。
3. **引擎单例**：`GatewayEngine` 只能有一个（`App.gateway` + `Context.gatewayEngine` 同源）。**不要新增第二个 ViewModel 去管网关状态**，也不要 new 第二个引擎（会争抢端口、账号状态漂移）。
4. **UI 排版约定**：一律用 `ui/page/miuix/settings/common/PreferenceCard.kt` 的组件——`PreferenceCard` / `NavigationRow` / `SwitchRow` / `PreferenceHint` / `PreferenceButtonRow` / `PreferenceActionRow` / `PreferenceBlock`；不要直接往 miuix `Card` 塞裸 `Text`/`Row`（默认内边距为 0）。**条件项出现/收起必须用 `AnimatedVisibility(fadeIn+expandVertically / fadeOut+shrinkVertically)`，visible 绑定真实状态**（不要 `visible=true` 包 `if`，那没有退场动画）。
5. **Compose 纪律**：`remember` 不能写在 `LazyListScope` 里；`stringResource` 只能在 `@Composable` 内调用；`MiuixIcons.Regular.X` 是扩展属性必须显式 import；数值型设置解析失败回落原值，不静默变 0。
6. **字符串三包同步**：en / zh-rCN / zh-rTW 键集合必须一致；从默认语言删除键时必须同删全部分支，否则 lintVital 报 `ExtraTranslation` 致命错误。
7. **提交**：Conventional Commits；主体 ≤72 字符；不加 AI 署名/Co-authored-by；成对更新 en + zh-rCN 文案。

## 交接格式

任务收尾汇报：**改了哪些文件 / 行为变化 / 验证（命令+结果）/ 备注（风险、未验证项）**。