# 代码地图（ARCHITECTURE）

> 面向维护者 / AI 代理的代码导航。与 `docs/HANDOVER.md` 配套阅读；
> HANDOVER 讲「怎么改」，本文讲「代码在哪、怎么流动」。

## 模块总览

```
:core              纯 Kotlin/JVM 网关内核（无 Android 依赖，可被 :app / :cli / 单测共用）
:cli               桌面命令行工具（bin: aigw），复用 :core 做端到端联调
:app               Android 应用（miuix UI），业务状态在 AppViewModel
:baselineprofile   启动性能基线（模板自带）
build-plugins      rtransfer.* 构建约定插件（AGP 版本、SDK、签名读取）
```

关键约束：`:core` 只用 `HttpURLConnection` + gson + nanohttpd（纯 Java API），
**禁止引入 Android 依赖**，否则 :cli 与本机单测全部失效。

---

## `:core` — 网关内核（`core/src/main/kotlin/dev/aigw/core/`）

| 包 | 职责 |
|---|---|
| `gateway/` | `GatewayEngine`（唯一引擎：端口监听 / 路由 / 流式转发）、`GatewayHttpServer`（nanohttpd）、`GatewaySettings`、`OpenAiApi`（OpenAI 兼容端点）、`BoundedAsyncRunner` / `ActiveRequests`（并发控制）、`EngineWatchdog`、`LoopbackCallbackServer`（登录回调） |
| `provider/` | 供应商适配：`Provider`（接口）、`ProviderRegistry`、`ChatCall`、`LoginSupport`、`ProviderHooks`、`RoutedModel`；具体实现 `chatgpt/` `trae/` `loomy/` `codebuddy/` `antigravity/` `raccoon/` `custom/`（自定义 OpenAI 兼容）、`yuanbao/`（腾讯元宝，粘贴 Cookie 登录，对话需 qimei USKey 签名） |
| `pool/` | `AccountPool`（账号池 / 冷却 / 配额）、`AccountTransfer`（导入导出格式） |
| `failover/` | 容错：`ProviderCircuitBreaker`（熔断）、`ConcurrencyGate`（闸门）、`ModelCooldownTracker`（冷却）、`ProviderMetricsTracker`（指标）、`WeightedOrder`（加权序）、`FailoverConfig/Decision` |
| `security/` | `RequestSanitizer`（脱敏）、`AccountRateLimiter`（账号限速）、`SecuritySettings` |
| `store/` | `KeyValueStore`（接口；Android 侧由 EncryptedKeyValueStore 实现，CLI 侧 FileKeyValueStore） |
| `usage/` | `CallLogStore`（调用记录）、`RequestLog`、`ModelHealthWindow`（健康窗口）、`StorageAudit` |
| `update/` | `ApkDownloader`、`UpdateSource`（更新源解析，:app 侧另有 GitHub Releases 实现） |
| `protocol/` | `ConversationFingerprint`、`OpenAiGemini`（协议形态差异） |
| `util/` | 纯工具：`HttpSupport`、`JsonAccess`、`Ids`、`PortProbe`、`TimeWindow`、`CappedStringBuilder`、`CountFormat`、`ErrorText` |

单元测试：`core/src/test/kotlin/dev/aigw/core/**`，约 80 个用例，直接 `./gradlew :core:test`。

---

## `:app` — Android 应用（`app/src/main/java/app/ai/rune/ziyong/transfer/`）

### 入口与引擎

```
```
App.kt                Application：CrashHandler / HiddenApiBypass / Monet / 通知渠道 / startKoin；持有 gateway
gateway/              网关逻辑（AppViewModel、服务、加密存储、KeepAlive 等）
├── GatewayHolder.kt  引擎持有者 + Context.gatewayEngine 扩展（UI 与服务共用同一引擎）
├── AppViewModel.kt   ★ 全部业务状态的唯一来源（1836 行；含 AppUiState 全字段）
├── EncryptedKeyValueStore.kt   凭证加密存储（EncryptedSharedPreferences）→ KeyValueStore
├── KeepAlive.kt / LanAddresses.kt / Format.kt / ApkInstaller.kt（应用内更新安装）
└── service/          GatewayService（前台服务）/ GatewayTileService（磁贴）/
                      GatewayBootReceiver / GatewayRestartReceiver / LoginWaitService / LiveStatusNotifier / FloatingKeepAlive
```

### UI（`ui/`）

```
ui/activity/SettingsActivity.kt    唯一 Activity
ui/navigation/
├── AppNavContainer.kt   根导航容器（NavDisplay，预测式返回）
├── MiuixNavWrapper.kt   底栏 5 Tab 装配（首页/供应商/模型/调用记录/设置）
├── MiuixSettingsPage.kt 紧凑/宽屏布局 + HorizontalPager 分发 5 个 Tab
├── Routes.kt            Route：Main / About / Theme（type-safe NavKey）
ui/theme/                AppTheme（MiuixTheme）、颜色/形状/系统栏、Backdrop 毛玻璃
ui/page/miuix/settings/  ★ 所有页面（见下）
ui/page/miuix/widgets/   miuix 风格组件（MiuixDialog / MarkdownText / Badge 等）
ui/library/              毛玻璃 / 动效 / 悬浮底栏（模板自带）
ui/icons/                AppIcons / AppMiuixIcons
```

### 页面清单（`ui/page/miuix/settings/`）

| 目录 | 页面 | 说明 |
|---|---|---|
| `home/` | MiuixHomePage | 首页：状态卡 / 网关总开关 / 地址 / 今日遥测 |
| `providers/` | MiuixProvidersPage / MiuixProviderDetailPage / MiuixCustomProviderPage | 供应商列表 / 详情（登录+账号管理）/ 自定义供应商编辑 |
| `models/` | MiuixModelsPage | 模型清单（前缀说明 / 开放名单，预留测速入口） |
| `log/` | MiuixCallLogPage | 调用记录（今日统计 / 列表 / 详情）；MiuixLogPage 为模板遗留，未使用 |
| `preferred/` | MiuixPreferredPage / UpdateSheet / about/ / theme/ | 设置（语言 / 关于 / 更新 / 主题） |
| `endpoints/` | MiuixEndpointsPage | 接入设置（端口 / API Key / 客户端地址） |
| `keepalive/` | MiuixKeepAlivePage | 保活与权限 |
| `transfer/` | MiuixAccountTransferPage | 账号导入导出 |
| `credits/` | MiuixCreditsPage | 额度中心 |
| `security/` | MiuixSecurityPage | 安全防护（脱敏 / 限速） |
| `resilience/` | MiuixResiliencePage | 并发与容错（闸门 / 重试 / 熔断 / 冷却） |
| `common/` | PreferenceCard.kt + ContextExt.kt | ★ 排版约定：PreferenceCard / NavigationRow / SwitchRow / PreferenceHint / PreferenceButtonRow / PreferenceActionRow / PreferenceBlock / findActivity |

### DI（`di/`）

- `init/app_modules.kt` — 总装配顺序：`gatewayModule` → `viewModelModule` → `serializationModule` → `coreModule` → `settingsModule` → `networkModule` → `updateModule` → `initModule`
- `GatewayModule.kt` — 网关链：`single<Application>`（从 applicationContext 取，**不能用 androidApplication()**，否则自引用递归）→ `KeyValueStore` → `GatewayHolder`（回填 App.gateway）→ `GatewayEngine`（同一实例）→ `viewModelOf(::AppViewModel)`

### 数据 / 领域（模板自带，已裁剪）

```
data/settings/    DataStore 偏好（AppDataStore）+ ThemeStateProvider
data/updater/     更新检查：OnlineUpdateRepositoryImpl → 本仓库 Releases；OnlineUpdatePolicy 拦截 debug/UNSTABLE 构建
domain/settings/  偏好模型 / 仓库 / UpdateSettingUseCase
domain/updater/   UpdateInfo / UpdateRepository
core/             通用基础设施（env / i18n / crash / exception / reflection / device）
```

---

## 关键数据流

- **网关状态**：`GatewayService` 通过 `Context.gatewayEngine` 操作引擎 → 状态回流到 `AppViewModel.state`（StateFlow）→ 5 个 Tab 页面统一 `collectAsStateWithLifecycle` 读取 `uiState.xxx`；所有写操作走 `viewModel.xxx()` 方法
- **全局提示**：各页面把结果/错误上报到 `AppViewModel.notice`，`MiuixNavWrapper` 消费并弹 Snackbar
- **主题**：`AppSettingsRepository.preferencesFlow` → `ThemeStateProvider` → `SettingsActivity` → `AppTheme`（MiuixTheme 双路径）
- **更新检查**：`AboutViewModel.checkUpdate` → `UpdateRepository.checkUpdate`（OkHttp 请求 Releases）→ `UpdateInfo` → `UpdateSheet`（MiuixMarkdownText 渲染）→ 下载跳系统浏览器 / 应用内安装（ApkInstaller）

## 资源

- 字符串：`res/values/strings.xml`（en 主，116 键）+ `values-zh-rCN` + `values-zh-rTW`，三包键集合必须一致；改默认串时同步清理全部语言包，防 lintVital `ExtraTranslation`
- 权限（AndroidManifest）：INTERNET / 前台服务（SPECIAL_USE）/ 通知 / 电池优化豁免 / 安装未知来源 / 悬浮窗（SYSTEM_ALERT_WINDOW）/ 开机广播 / 磁贴绑定等
- 服务 / Receiver：GatewayService、LoginWaitService、GatewayTileService、GatewayBootReceiver、GatewayRestartReceiver