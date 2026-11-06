# miuix example

一个基于 **Jetpack Compose + Miuix** 的 **MIUI 风格设置演示应用**。

早期由一款开源 Android 安装工具的源码**去功能化改造**而来——安装/卸载/特权相关代码与命名已全部移除，本项目只保留现代 UI 基建与设置演示。

## 这是什么

- **纯 miuix 风格** UI：主页 / 设置 / 运行日志 / 主题，适配手机与平板、深色模式、动态取色、模糊与毛玻璃效果
- **不含任何安装相关代码与命名**：无安装引擎、无会话管理、无特权通道（无 Root/Shizuku/Dhizuku）、无 APK 解析器；构建插件使用 `miuixexample.*` 命名，主题为 `Theme.MiuixExample`
- **更新检查**：指向本仓库 Releases，结果用底部 Sheet 呈现（含 Markdown 更新日志渲染），下载转跳浏览器
- **运行日志 + 崩溃日志**：Timber 落盘（`cacheDir/logs`）、崩溃独立落盘（`cacheDir/crashes`），应用内日志页可查看 / 复制 / 清空
- **语言**：仅保留英文（默认）与简体中文 / 繁体中文，其余语言包已移除

## 应用标识

- applicationId / namespace：`com.miui.example`
- 应用名：`miuix example`

## 功能

| 模块 | 说明 |
|---|---|
| 主页 | 运行状态卡（演示壳「运行正常 / 异常」）、设备信息、项目链接 |
| 设置 | 语言（跟随系统 / 简体中文 / English）、主题设置、关于入口 |
| 运行日志 | Timber 运行日志 + 崩溃日志，5s 自动刷新，复制 / 清空 |
| 主题设置 | 主题模式、动态取色、调色板、MIUIX 特性、预测式返回、毛玻璃 |
| 关于 | 图标 / 版本、更新检查（启动自动 + 手动）、崩溃日志摘要、源码链接 |

## 构建

```bash
./gradlew :app:assembleStableDebug
```

需要 GitHub Packages 凭据以解析 miuix snapshot 依赖（`~/.gradle/gradle.properties` 中 `gpr.user` / `gpr.key`，或 CI 中的 `GITHUB_ACTOR` / `GITHUB_TOKEN`）。

## 文档

- [docs/HANDOVER_CN.md](docs/HANDOVER_CN.md) — 交接文档（架构、约定、已知问题、后续建议）
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 代码地图
- [docs/README_CN.md](docs/README_CN.md) — 项目说明（中文）
- [AGENTS.md](AGENTS.md) — 面向 AI 代理的开发指引

## 许可

GPL-3.0（见 [LICENSE](LICENSE)）。