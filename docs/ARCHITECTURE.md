# 代码地图（ARCHITECTURE）

应用主代码位于 `app/src/main/java/com/miui/example/`。

## 分层

| 包 | 职责 |
|---|---|
| `App.kt` | Application 入口：崩溃处理、Hidden API 豁免、Monet、日志树、Koin |
| `core/` | 通用基础设施：`env`（AppConfig/DeviceConfig）、`i18n`（LocaleManager）、`crash`（CrashHandler）、`exception`（AppException）、`reflection` |
| `data/` | 数据层：`settings`（DataStore 持久化 + ThemeStateProvider）、`updater`（GitHub Releases 更新检查） |
| `domain/` | 领域层：`settings`（偏好模型/仓库/用例，已裁剪）、`updater`（UpdateInfo/UpdateRepository）、`device`、`archive`(已删) |
| `di/` | Koin 模块装配（`init/app_modules.kt` 为总装配） |
| `ui/` | Compose UI |
| `util/` | 通用工具：`timber`（FileLoggingTree/LogController）、`ContextUtil`、`LocaleUtil`、`ThrowableUtil` 等 |

## UI 结构

```
ui/
├── activity/SettingsActivity.kt          # 唯一 Activity（入口）
├── navigation/
│   ├── AppNavContainer.kt                # 根导航（Main/About/Theme）
│   ├── MiuixNavWrapper.kt                # 4 Tab 装配（主页/示例1/运行日志/设置）
│   ├── Routes.kt                         # Route（Main/About/Theme）
│   └── MainScreenBackHandler.kt / Navigator.kt / PagerState.kt
├── page/
│   ├── main/settings/                    # 共享 ViewModel/State（历史命名）
│   │   ├── home/                         # 主页 VM（仅设备信息）
│   │   ├── preferred/                    # 设置 VM（语言/关于/更新）
│   │   │   └── about/                    # 关于 VM（更新检查/崩溃摘要）
│   │   ├── theme/                        # 主题 VM
│   │   └── log/                          # 日志 VM（读运行日志+崩溃）
│   └── miuix/
│       ├── settings/MiuixSettingsPage.kt # Pager + 底部导航
│       ├── settings/home/MiuixHomePage.kt
│       ├── settings/preferred/           # MiuixPreferredPage/UpdateSheet/about/theme
│       ├── settings/log/MiuixLogPage.kt
│       └── widgets/                      # Miuix 风格组件库
├── theme/                                # AppTheme、颜色、毛玻璃、系统栏
├── icons/                                # AppIcons / AppMiuixIcons
├── library/                              # 效果库（blend/effect/liquid/FloatingBottomBar）
└── util/                                 # 设备/工具 UI 辅助
```

## 关键数据流

- **主题**：`AppSettingsRepository.preferencesFlow` → `ThemeStateProvider` → `SettingsActivity` → `AppTheme`
- **更新检查**：`AboutViewModel.checkUpdate` → `UpdateRepository.checkUpdate`（OkHttp 请求本仓库 Releases）→ `UpdateInfo(updateLog=Release.body)` → `MiuixUpdateSheet`（MiuixMarkdownText 渲染）→ 下载/打开跳系统浏览器
- **日志**：`Timber.i/d` → `FileLoggingTree`（异步写 `cacheDir/logs/*.log`，轮转 2 文件×4MB）+ `CrashHandler` 写 `cacheDir/crashes/*.txt` → `LogViewModel`（每 5s 刷新）→ `MiuixLogPage`（崩溃区+运行区，复制/清空）

## 资源

- 字符串：`res/values/strings.xml`（en 主）+ `values-zh-rCN`（简中）+ `values-zh-rTW`（繁中），其余语言包已删除；改默认串时必须同步清理全部语言包，防 lintVital `ExtraTranslation`
- 图标：`res/drawable/`（`ic_tool.xml` 为扳手工具图标；`ic_launcher_*` 为浅蓝底白色扳手桌面图标）