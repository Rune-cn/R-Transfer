# R 中转 · 交接文档

> 面向下一任维护者 / AI 代理。读完这份文档，应当能在不看历史对话的前提下继续开发。
>
> 最后更新：2026-11-07（文档与代码同步维护，改动代码必须同步更新本文，见 §5）

---

## 1. 这是什么

把 Android 手机变成**私有的 AI 模型聚合服务器**：将 Trae / Loomy / WorkBuddy / Antigravity / ChatGPT / 小浣熊 / 任意自定义 OpenAI 兼容上游，
统一翻译成一个 OpenAI 兼容端点，供任意客户端调用。

- 仓库：`Rune-cn/R-Transfer`
- 包名：`app.ai.rune.ziyong.transfer`
- 应用名：`R 中转`
- 视觉：整套基于 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)（MIUI / HyperOS 风格），**不使用 Material3 组件**

### 与上游的关系

基底是 [`Rune-cn/miuix-example`](https://github.com/Rune-cn/miuix-example)（miuix 官方示例的定制版），
网关逻辑来自 [`xibai6668/Android-AI-gateway`](https://github.com/xibai6668/Android-AI-gateway) 的 `:core`。

**注意方向**：不是「给网关换皮」，而是「以 miuix-example 为底座，把网关逻辑搬进去」。
所以 UI 层大量保留了模板自带的 Koin / Navigation / 主题 / 偏好设置体系。

---

## 2. 模块结构

```
:core    纯 Kotlin/JVM，与 UI 无关。协议转换、供应商适配、账号池、存储、容错、统计
:cli     桌面命令行工具，复用 :core
:app     Android 应用（miuix UI）
:baselineprofile  启动性能基线（模板自带）
build-plugins     构建约定插件（rtransfer.android.application 等）
```

### `:core` 为什么是纯 JVM

它同时被三个消费者使用：Android 应用、桌面 CLI、单元测试。
一旦引入 Android API 就无法在 JVM 上直接跑测试，因此它只用 `HttpURLConnection` + 纯 Java 库
（gson、nanohttpd）。**不要往 :core 里加 Android 依赖。**

---

## 3. 代码地图（`:app`）

```
app/src/main/java/app/ai/rune/ziyong/transfer/
├── App.kt                       Application：Koin 启动、通知渠道、持有 GatewayHolder
├── gateway/                     ← 从上游搬来的网关逻辑，包名统一到 gateway 下
│   ├── GatewayHolder.kt         引擎持有者 + Context.gatewayEngine 扩展
│   ├── AppViewModel.kt          全部业务状态的唯一来源（1836 行，见 §4）
│   ├── EncryptedKeyValueStore.kt  凭证加密存储（EncryptedSharedPreferences）
│   ├── KeepAlive.kt             保活状态检测 + 系统设置 Intent
│   ├── ApkInstaller.kt          应用内更新安装
│   ├── LanAddresses.kt          局域网地址枚举
│   ├── Format.kt                数字/时长/字节格式化
│   └── service/                 GatewayService（前台服务）、磁贴、开机恢复、悬浮窗、通知
├── ui/
│   ├── activity/SettingsActivity.kt   唯一 Activity
│   ├── navigation/                    AppNavContainer、MiuixNavWrapper（底栏 5 Tab）、Navigator
│   ├── theme/                         AppTheme（MiuixTheme）、颜色、形状、系统栏
│   ├── page/miuix/settings/           ← 所有页面
│   │   ├── MiuixSettingsPage.kt       底栏容器 + 5 个 Tab 的分发
│   │   ├── home/                     首页（状态卡 + 总开关）
│   │   ├── providers/                供应商列表 / 详情 / 自定义供应商编辑
│   │   ├── models/                   模型清单
│   │   ├── log/                      调用记录（另有模板遗留的 MiuixLogPage，未被使用）
│   │   ├── preferred/                设置（模板自带 + 网关分组）
│   │   ├── endpoints/                接入设置
│   │   ├── keepalive/                保活与权限
│   │   ├── transfer/                 账号导入导出
│   │   ├── credits/                  额度中心
│   │   ├── security/                 安全防护
│   │   ├── resilience/               并发与容错
│   │   └── common/                   ★ 排版约定（PreferenceCard 体系 + findActivity，见 §5）
│   ├── library/                      模板自带的毛玻璃、动效组件
│   └── icons/                        图标集
├── di/                          Koin 模块（gatewayModule 是本项目新增的）
└── domain/ data/ core/ util/    模板自带（主题、DataStore、更新检查、日志）
```

---

## 4. `AppViewModel` 是唯一的业务状态源

所有页面都通过 `koinViewModel()` 拿到**同一个** `AppViewModel`（Koin 里声明为 `viewModelOf`）。

- 状态：`val state: StateFlow<AppUiState>`，1836 行里包含 `AppUiState` 的全部字段定义
- 页面只读 `uiState.xxx`，所有写操作走 `viewModel.xxx()` 方法
- **不要新增第二个 ViewModel 去管网关状态**：引擎是单例，两套状态会各自漂移

### 引擎注入

```kotlin
// di/GatewayModule.kt（真实代码）
single<Application> { androidContext().applicationContext as Application }  // ⚠ 不能用 androidApplication()，会自引用递归
single<KeyValueStore> { EncryptedKeyValueStore(androidContext()) }
single<GatewayHolder> {
    val app = androidContext().applicationContext as App
    GatewayHolder(app, get<KeyValueStore>()).also { app.attachGateway(it) }
}
single<GatewayEngine> { get<GatewayHolder>().engine }   // 与服务层同一个实例
viewModelOf(::AppViewModel)
```

`App.gateway` 是 `lateinit`，由 Koin 在 `onCreate` 里 `startKoin` 后立即通过 `attachGateway` 回填，
随后 `GlobalContext.get().get<GatewayHolder>()` 主动提前实例化，保证前台服务先启动时引擎已就绪。

> ⚠ 两个历史坑：`single<Application>` 必须显式声明为 `Application` 类型（否则 Koin 按 `App`
> 建 key，注入到 `AppViewModel(Application)` 会失败）；且**不能用 `androidApplication()` 定义它**
> （内部就是 `get<Application>()`，等于解析自己 → StackOverflowError，commit `3d0e54a`）。

**服务层通过 `Context.gatewayEngine`（→ `App.gateway.engine`）取同一个实例**，
保证 UI 与服务不会各持一套账号池。

---

## 5. UI 排版约定（★ 最容易踩的坑）

### 5.1 miuix 的 `Card` 默认内边距是 0

`CardDefaults.InsideMargin = PaddingValues(0.dp)`——它假设**子项自带 padding**。
`BasicComponent` 自带 16dp，所以「Card 里只放 BasicComponent」没问题；
但**直接往 Card 里塞 `Text` / `Row` 而不补 padding，文字就会贴边**。

**正确做法：一律用 `common/PreferenceCard.kt` 里的 `PreferenceCard`**，它统一给：
水平 12dp 外边距 + 12dp 下边距，与模板「关于」「个性化」两页完全一致。
内部可以混放 `NavigationRow` / `SwitchRow` / `BasicComponent` 等**自带 padding** 的组件。

```kotlin
PreferenceCard {
    NavigationRow(title = "标题", summary = "说明", onClick = ...)  // = ArrowPreference，带右箭头
    SwitchRow(title = "开关", summary = "说明", checked = ..., onCheckedChange = ...)
    PreferenceHint("自定义说明文字")   // 卡片内解释性文字，统一次级色
}
```

> 之前踩过的坑：卡片不带 `padding(horizontal = 12.dp)`、可点击行手写 `BasicComponent` 加 onClick
> （没有箭头，用户不知道可点）、开关行手写 `Row + Switch`（点按区域只剩开关本身）。
> 这三类问题在 `cc6bc00` 起的模板体系重写中已全部收敛到上面的组件。

### 5.2 并排按钮用 `PreferenceButtonRow` + 原生 `TextButton`

`TextButton` 自带 16dp 水平内边距 + `minWidth = 58dp`。两个各占 `weight(1f)` 时，
内边距会吃掉大半宽度，看起来「太挤」。

```kotlin
PreferenceButtonRow {
    TextButton(text = "取消", onClick = ...)
    TextButton(text = "保存", onClick = ...)
    TextButton(text = "删除", onClick = ...)
}
```

整行主操作（保存 / 添加账号）用 `PreferenceActionRow(text, onClick)`；
自定义内容块用 `PreferenceBlock`。**不要改 `insideMargin` / `minWidth` 等样式参数**，
那会让高度与圆角偏离其余按钮。

### 5.3 `remember` 不能写在 `LazyListScope` 里

`LazyColumn { ... }` 的 lambda 不是 `@Composable` 上下文。分组、排序等计算
必须在 `Scaffold` 之前（Composable 作用域）算好再交给 LazyListScope。

同理，`remember { }` 的 lambda 里**不能读 Composable 状态**（如 `MiuixTheme.colorScheme`），
要先读进局部变量再 remember。

### 5.4 `MiuixIcons.Regular.X` 是扩展属性

必须 `import top.yukonga.miuix.kmp.icon.extended.X`，写全限定路径无法解析。

### 5.5 条件项出现/收起必须带过渡动画

基准「个性化」页的所有条件展开项都用：

```kotlin
AnimatedVisibility(
    visible = ...,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
) { ... }
```

- **凡是条件渲染（开关联动出现、列表展开/收起、错误提示、加载状态）都要用它**，
  不要用裸 `if` 硬切（2026-11-07 已把这些改齐：模型折叠、导入预览/结果、导出树、
  悬浮窗权限入口、端口提示、短名校验、启动失败提示等）。
- **`visible` 必须绑定真实状态**；不要写成 `AnimatedVisibility(visible = true)` 包 `if`
  ——那等于没动画，退场动画永远不会播放。
- 卡片内的说明文字一律用 `PreferenceHint`（自带 16dp 水平内边距），
  不要直接往 `PreferenceCard` 里塞裸 `Text`（会贴边）。

---

## 6. 签名与发版（★ 关系到用户能否覆盖安装）

### 规则

**debug 与 release 必须用同一份密钥库。** 否则：

- 两者互相装不上（签名不一致）
- 前后两次构建也无法覆盖安装，用户只能卸载——**卸载会清掉全部账号与设置**

### CI 实现

`.github/workflows/build.yml` 里「还原签名密钥库」步骤**排在任何构建之前**，
从 Secrets 解出密钥库到 `$RUNNER_TEMP/release.jks`，并导出：

| Secret | 作用 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | 密钥库文件的 base64 |
| `RELEASE_STORE_PASSWORD` | 密钥库口令 |
| `RELEASE_KEY_ALIAS` | 密钥别名 |
| `RELEASE_KEY_PASSWORD` | 密钥口令 |

`app/build.gradle.kts` 的签名读取顺序：`keystore.properties`（本地）→ 环境变量（CI）。
两者都没有时用 AGP 默认 debug 密钥（**此时产物无法覆盖安装**，workflow 会给 warning）。

### 当前密钥库

自签名证书（`CN=R Transfer`，有效期 30 年），**不能上架应用商店**。
换正式证书只需替换上述 4 个 Secret，代码无需改动。

---

## 7. 构建

**不做本地构建**，全部由 GitHub Actions 完成。

- push `main` → 构建 `StableDebug` + `StableRelease`，上传 Actions 产物
- PR → 只跑 `:core` 单测 + debug
- 手动触发 → 可选是否跑单测

产物路径有 flavor 层级：`app/build/outputs/apk/Stable/debug/*.apk`
（AGP 的目录顺序是 `apk/<flavor>/<buildType>/`，flavor 在前）。

> 只构建 Stable：`Unstable` / `Preview` 是模板遗留的版本梯度标记，对本应用无意义，
> 三个全构建会产出三份几乎相同的 APK（约 78MB）。

### 版本基线

| 组件 | 版本 |
|---|---|
| AGP | 9.4.1 |
| Kotlin | 2.4.21 |
| Gradle | 9.8.1（wrapper） |
| JDK | 25（toolchain；CI 用 21 运行 Gradle） |
| Compose BOM | 2026.09.00 |
| miuix | 0.9.4（Maven Central 稳定版） |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |

### 两个已知的依赖坑

1. **miuix 不能用 SNAPSHOT**：模板原依赖 `0.9.4-xxx-SNAPSHOT`，只发布在 GitHub Packages
   且需认证，CI 无凭证会 401。已改为 Maven Central 的 `0.9.4` 并移除私有仓库配置。

2. **`material-color-utilities` 的跨版本包路径冲突**：
   miuix 0.9.4 编译时同时引用 `com.materialkolor.scheme.*`（5.x）与
   `com.materialkolor.dynamiccolor.*`（6.x），而该库任一版本只含其一，
   release 混淆必然报 Missing class。解法是 `app/proguard-rules.pro` 里的 `-dontwarn`，
   并把该文件挂到 `optimization` 上（模板原本没配 proguardFiles）。

---

## 8. 功能完成度

| 页面 | 状态 |
|---|---|
| 首页（状态卡 / 总开关 / 地址 / 今日遥测） | ✅ |
| 供应商列表 → 详情（登录 + 账号管理） | ✅ |
| 自定义供应商 新建 / 编辑 / 删除 | ✅ |
| **腾讯元宝（yuanbao）**：粘贴 Cookie 登录、模型、额度；对话需 qimei USKey | ✅（USKey 见下） |
| 模型清单（含前缀说明 / 开放名单） | ✅ |
| 调用记录（今日统计 / 列表 / 详情） | ✅ |
| 设置：接入设置 | ✅ |
| 设置：保活与权限 | ✅ |
| 设置：账号导入导出 | ✅ |
| 设置：额度中心 | ✅ |
| 设置：安全防护 | ✅ |
| 设置：并发与容错 | ✅ |
| 模板自带：主题 / 语言 / 在线更新 / 关于 | ✅ |

### 未接线的能力（`:core` 已有，UI 未暴露）

- **供应商专属设置**（`ProviderUiRegistry` / `ProviderUi` 接口）：Trae 的设备指纹、
  WorkBuddy 的成长中心、Loomy 的新手任务、小浣熊的积分兑换等。
  上游有 7 个 `ProviderUi` 实现，本项目**尚未移植**——目前账号管理走通用路径。
- **签到 / 批量任务**：`checkinAll()` / `runAllTasks()` 在 ViewModel 里可用，但首页没有入口
  （上游有「任务中心」页）。
- **模型测速**：`testModel()` 可用，模型页未接按钮。

### 腾讯元宝（yuanbao）协议说明
元宝**网页版只需要 Cookie**（`hy_token` + `hy_user`），浏览器登录后复制导入即可。
账号详情页提供两种导入方式：
- **内置浏览器登录（推荐）**：全屏 WebView 打开元宝首页，用户正常登录（扫码/手机号/微信），
  点「完成」后 App 自动读取 CookieManager 的 Cookie 与页面 `localStorage._qimei_h38`
  —— 全程不需要开发者工具；**登录用过的 WebView 会被接管为 USKey 铸造器**
  （页面/登录态/qimei 环境已就绪，无需重新加载，也避免被重定向到登录页导致找不到 qimei 模块）；
- **手动粘贴 Cookie**：备选（老浏览器/无 WebView 环境）。

对话 `POST /api/chat/{conversationId}` 是 SSE 流，要点（对照实测可用的开源实现
`10020099/yuanbao2api`）：
- **`conversationId` 本地生成 UUID**，请求体 `isTemporary: true` —— 不调用「创建会话」接口，
  也不会在元宝界面留下记录；
- **不需要 qimei USKey**（早期逆向文档的结论有误：那条路依赖浏览器环境、不稳定）；
- 只需基础浏览器头 + 关键头 `x-agentid: {agentId}/{conversationId}`、`x-requested-with: XMLHttpRequest`；
- **USKey 动态签名（对话必需，已自动接线）**：实测表明缺 USKey 时上游一律返回
  「抱歉，我无法回答这个问题，让我们换个话题再聊聊吧。」。App 内置铸造器
  （`app/.../uskey/`）会把本机回环地址自动写入账号凭证的 `uskeyServiceUrl`，
  provider 每次对话前取新鲜签名三件套（`X-Uskey` / `X-Bus-Params-Md5` / `X-Timestamp`）
  与**同源**的 `X-HY92`（用铸造器返回的 h38，避免「画像不一致」）。
  **用户只需在内置浏览器里登录**——登录/导入成功后自动启动铸造器，无额外操作；
  也可填外部 `uskey_browser.py --serve` 的地址替代内置铸造器。
  > 铸造器 WebView 必须**挂在 Activity 窗口上（1×1 不可见）**：一旦从窗口树 detach
  > （如登录 Dialog 关闭），部分 ROM 会挂起 JS，表现为「铸造超时」。
  > 另有两层容错：**USKey 4 分钟内复用**（上游约 5 分钟有效，连续对话不再重复铸造）；
  > 超时后销毁 WebView 并在下次调用重建（渲染进程可能已被系统回收）。
- **设备标识 `h38`（`X-HY92`）**：未接铸造器时用账号里的 h38（F12 →
  Application → Local Storage → `_qimei_h38`），与 qimei uuid（Cookie 的 `_qimei_uuid42`）
  成对下发；接了铸造器则由铸造器提供同源 h38。
- 头细节与 `yuanbao2api` 保持一致：截断 UA（不含 Chrome 版本段）、`accept-charset: utf-8`、
  不额外传 `x-commit-tag`；另按逆向报告补齐 `x-instance-id: 5` 与
  **`x-traceparent`（每请求唯一，`md5(randhex+5+uuid4)`）**——报告指出复用 traceparent 会触发
  21007「Answer fetch failed, retrying」；
- **上游错误提示**（如「服务繁忙，请稍后再试。」「抱歉，我无法回答…」）会以标准
  OpenAI 错误帧（`data: {"error":{...}}`）透传——不再伪装成模型回复，
  这样客户端能正确识别失败，调用记录也能标为失败（区别于以前的「看起来像模型在说服务繁忙」）；
- 多轮对话：元宝只收纯文本 `prompt`，网关把 OpenAI `messages`（含 system 与历史）拼接后发送；
- **不支持 OpenAI function calling**：客户端带 `tools` 时明确拒绝（CLIENT 错误），
  元宝的联网搜索由上游自行执行。

> 「服务繁忙，请稍后再试。」属上游**临时限流**：稍后重试即可；若频繁出现，
> 说明该账号被限流，可在账号池加号（网关的失败转移会换号重试非流式请求）。

> 历史：曾实现过「qimei USKey 铸造器」（含 WebView 版），因该结论不成立已整体移除，
> 代码回到「Cookie + 直连」的简单形态。

---

## 9. 已知问题

1. **`git push` 在部分环境走不通**（`Failed to connect to github.com port 443`），
   但 GitHub API 正常。可用 Git Trees/Blobs API 提交（见 §10）。

2. **`EncryptedSharedPreferences` 已废弃**：`androidx.security:security-crypto` 标记为
   deprecated，但仍是官方可用的加密 SharedPreferences 实现。迁移到 DataStore 会改变
   凭证磁盘格式，需要「解密-迁移-重写」流程与回滚路径，属于独立变更，**不要顺手改**。

3. **通知权限被永久拒绝时**，`launch()` 不再弹窗、回调也不触发，开关看起来点了没反应。
   已用 `shouldShowRequestPermissionRationale` 区分「还能问」与「永久拒绝」，
   后者会提示去设置页并仍然尝试启动。

---

## 10. 用 API 提交（git 不通时）

```python
# 1. 取 head 与 base tree
ref  = GET /repos/{repo}/git/ref/heads/main          # -> object.sha
base = GET /repos/{repo}/git/commits/{head}          # -> tree.sha

# 2. 每个文件建 blob
blob = POST /repos/{repo}/git/blobs
       {"content": base64(content), "encoding": "base64"}

# 3. 建 tree（base_tree 保证只改这几个文件）
tree = POST /repos/{repo}/git/trees
       {"base_tree": base, "tree": [{"path":..., "mode":"100644", "type":"blob", "sha":...}]}

# 4. 建 commit
commit = POST /repos/{repo}/git/commits
         {"message": ..., "tree": tree.sha, "parents": [head]}

# 5. 移动 ref
PATCH /repos/{repo}/git/refs/heads/main  {"sha": commit.sha}
```

---

## 11. 改动时的自检清单

推之前逐项确认（这些都是踩过的）：

- [x] 新页面用 `PreferenceCard`，没有直接往 `Card` 塞裸 `Text`/`Row`
- [ ] 并排按钮用 `PreferenceButtonRow` + 原生 `TextButton`；整行主操作用 `PreferenceActionRow`
- [ ] 条件项用 `AnimatedVisibility(fadeIn+expandVertically / fadeOut+shrinkVertically)`，visible 绑定真实状态
- [ ] 卡片内说明文字用 `PreferenceHint`，没有裸 `Text` 贴边
- [ ] `remember` 不在 `LazyListScope` / 非 Composable lambda 里
- [ ] `MiuixIcons.Regular.X` 有对应 import
- [ ] 没有 `app.ai.rune...` 的全限定写法（会与 `AppViewModel` 的 `app` 参数冲突）
- [ ] 新增二级页后，`BackHandler` 覆盖了它的返回路径
- [ ] 数值型设置在解析失败时回落原值，而不是静默变 0
- [ ] 改动后确认 CI 通过（本地不构建）
- [ ] **同步更新了全部相关文档**（`AGENTS.md`、根 `README.md`、`docs/HANDOVER.md`、`docs/ARCHITECTURE.md`、`docs/NEXT_SESSION_CHECKLIST.md`、`CONTRIBUTING.md`）——铁律，见 AGENTS.md 铁律 #1

---

## 12. 关键设计取舍（避免被"优化"掉）

| 取舍 | 原因 |
|---|---|
| `:core` 保持纯 JVM | 三个消费者共用，且要能跑单测 |
| 引擎单例 + `App.gateway` lateinit（Koin 启动即回填并提前实例化） | 两套引擎会争抢端口、账号状态漂移；服务先启动时引擎必须已就绪 |
| 单账号并发默认 1 | 上游按账号限流并发流，多条会被挂住只回空 chunk |
| 额度 `limit == 0` 显示「剩余 N」而非 0% | 把"不知道"画成 0% 会误导用户以为额度用尽 |
| 模型专属配额单独标注 | 否则「某模型用完」会被误判为「账号耗尽」 |
| 短名建后不可改 | 已写进模型路由标识 `custom:<短名>/模型` |
| 删除账号先关弹窗再执行 | 列表重建后弹窗会引用已失效的账号 |
| 限速默认 0 | 它是发往上游前阻塞等待，会让首字变慢 |
| debug/release 同签名 | 否则用户每次更新都要卸载，丢全部账号 |
