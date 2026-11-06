# 交接文档

> 面向下一任维护者 / AI 代理。说明本项目现状、架构、约定与已知问题，避免重复踩坑。
> **接手先读 `NEXT_SESSION_CHECKLIST.md`**（一次性交接清单，含未闭环问题与待办）。

## 1. 项目概况

`miuix-example` 是一个 **纯 Miuix 风格的设置演示壳应用**：

- 由一款开源 Android 安装工具的源码**去功能化改造**而来，安装器相关代码与**命名**均已移除：
  - 构建插件统一为 `miuixexample.*`（原 `installerx.*` 已重命名）
  - 主题统一为 `Theme.MiuixExample`（原 `Theme.Installer` 已重命名）
  - `app/src/main/keepRules/apksig.keep`（apksig 签名残留）已删除；`android-components.keep` / `app-process.keep` / `ignore-warnings.keep` 为通用 R8 规则，**必须保留**（R8 release 依赖其中的 `-dontwarn`，如 MonetCompat 的 DynamicColors）
- 主页状态卡已还原并改为**演示壳「运行状态」语义**（运行正常 / 异常，非安装器激活状态，见 `HomePageViewState.isRunningNormally`）；安装器清理范围为**根目录除 `app/` 外的文件夹**（`build-plugins/`、`gradle/`、`.github/` 等）及 `app/` 内的命名级残留（插件名 / 主题名 / apksig.keep / 字符串键）
  - 主页「激活状态卡」等安装器语义 UI 与文案已删除
- applicationId / namespace：`com.miui.example`，应用名 `miuix example`
- Gradle 模块：`:app` 与 `:baselineprofile`
- 语言：仅保留默认 `values/`（en）+ `values-zh-rCN` + `values-zh-rTW`，**其余语言包已删除**

## 2. 功能清单（当前）

| 模块 | 说明 |
|---|---|
| 主页 | 运行状态卡（演示壳「运行正常 / 异常」语义）、设备信息（机型 / 系统版本）、项目链接 |
| 设置 | 语言（跟随系统 / 简体中文 / English）、主题入口、关于入口（一张卡内） |
| 运行日志 | Timber 运行日志（`cacheDir/logs`）+ 崩溃日志（`cacheDir/crashes`），5s 自动刷新，复制 / 清空 |
| 主题设置 | 主题模式 / 动态取色 / 调色板 / MIUIX 特性 / 预测式返回 / 毛玻璃 |
| 关于 | 图标 / 版本、更新检查（启动可选自动 + 手动）、崩溃日志摘要、源码链接 |

## 3. 架构要点

- **UI 家族**：只保留 **miuix**（`ui/page/miuix/**`）；共享 ViewModel 放在 `ui/page/main/settings/**`（历史命名，不涉及安装功能）
- **导航**：`ui/navigation/AppNavContainer.kt`（底部 4 个 Tab：主页 / 示例 1 占位 / 运行日志 / 设置，Pager 驱动）；`Route` 仅有 Main / About / Theme
- **DI**：Koin，模块见 `di/init/app_modules.kt`
- **设置持久化**：DataStore（`data/settings/local/datastore/AppDataStore.kt`），`AppSettingsRepository(Impl)` 提供 typed 读写
- **更新检查**：`data/updater/OnlineUpdateRepositoryImpl` → 本仓库 Releases；`UpdateInfo.updateLog` 携带发布说明；UI 用 `MiuixMarkdownText`（零依赖）渲染
- **日志体系**：`App` 植入 DebugTree（logcat）+ `LogController` 植入 `FileLoggingTree`（文件，**不受构建类型门控**）；`CrashHandler` 崩溃时写独立 dump
- **主题**：`ui/theme/AppTheme.kt` + miuix 双路径；blur 辅助在 `ui/theme/Backdrop.kt`

## 4. 构建与验证

```bash
./gradlew :app:assembleStableDebug       # 本地冒烟
./gradlew :app:assembleStableRelease
```

- CI（`.github/workflows/build.yml`）在 main 推送/PR 时构建 **Stable** debug + release，产物上传 artifact；release 会跑 `lintVital`
- **lintVital 陷阱**：字符串若从默认语言删除键，必须同时从**全部语言包**（en / zh-rCN / zh-rTW）删除，否则 `ExtraTranslation` 致命错误（历史教训：13748 个错误）
- 本地无 JDK25+SDK 时，以「静态引用扫描 + 推送后 CI 验证」循环迭代

## 5. 约定（提交 / 代理）

- ⛔ **铁律：每次代码修改都必须同步更新代码 MD 文档的对应内容**（本文件、`ARCHITECTURE.md`、`AGENTS.md`、根 `README.md`、`docs/README_CN.md`），禁止「只改代码不动文档」的提交
- 提交遵循 Conventional Commits（`fix:` / `feat:` / `refactor:` / `docs:` / `i18n:` / `chore:`），主体 ≤72 字符，不做 AI 署名
- 成对更新：默认语言（en）+ zh-rCN 文案同步；zh-rTW 由译者负责（改动后同步）
- 诚实汇报验证：跑过什么、是否通过、没跑什么

## 6. 已知问题 / 注意事项

1. **关于页卡片透明度**：与上游一致——模糊开启时卡片容器透明（背景由合成层提供），关闭时回退不透明。若设备模糊不生效会表现为「文字可见、卡片容器不可见」，属预期（可调 `useBlur`）。**更新卡与「查看源代码」「调试」卡使用相同的 `textureBlur` 毛玻璃修饰与 `BLUR_CARD_ALPHA` 半透明色值**，整页卡片外观一致；「获取更新」为带右箭头的 `MiuixNavigationItemWidget`（与其余条目同款）
2. **日志路径**：`cacheDir` 属可清缓存，系统清缓存后日志会清空
3. **更新检查**：仓库为私有且无 Release 时 `checkUpdate` 会记录失败日志，UI 统一提示「已是最新版本」（失败与无更新暂未区分，属已知待办，见 `NEXT_SESSION_CHECKLIST.md` §3.5）；发布 Releases 后即生效。`autoCheckUpdateOnStart` 每次进程仅检查一次；当前 `canCheckUpdates` 会拦截 debug 构建（CI 产物即 debug）
4. **多语言**：仅 en / zh-rCN / zh-rTW，其余语言包已删除（2026-11-01）；新增或删除字符串时保持三包键集合一致
5. **README 仅中文**：根 `README.md` 与 `docs/README_CN.md`；英文版 README 已删除

## 7. 建议的后续工作

- 「示例 1」Tab 换成真正的演示页（或删除只剩 3 个 Tab）
- 更新检查下载改为应用内下载 + 进度（当前跳浏览器）
- 日志持久化：评估从 `cacheDir` 迁到 `filesDir`
- 死代码清扫：`ui/icons/AppIcons.kt` 未引用图标、`enableFileLogging` 领域层简化
- 发布固定下载：创建 GitHub Release 传 APK，避免 artifact 90 天过期