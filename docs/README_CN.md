# miuix example — 项目说明

一个基于 **Jetpack Compose + Miuix** 的 **MIUI 风格设置演示应用**。

由一款开源 Android 安装工具的源码**去功能化改造**而来，安装/卸载/特权相关代码已全部移除；本项目仅保留现代 UI 基建与设置演示。

## 核心内容

- 纯 **miuix** 风格：主页 / 设置 / 运行日志 / 主题，自适应布局、深色模式、动态取色、模糊与毛玻璃
- **无安装器代码**——无安装引擎、无会话、无特权后端、无包解析器
- **更新检查**：指向本仓库 Releases，底部 Sheet 展示（内建 Markdown 更新日志渲染），下载转跳浏览器
- **运行日志 + 崩溃日志**：Timber 落盘于 `cacheDir/logs`，崩溃独立落盘于 `cacheDir/crashes`；应用内日志页可查看 / 复制 / 清空

## 应用标识

- applicationId / namespace：`com.miui.example`
- 应用名：`miuix example`

## 相关文档

- [HANDOVER_CN.md](HANDOVER_CN.md) — 交接文档
- [ARCHITECTURE.md](ARCHITECTURE.md) — 代码地图
- [../README.md](../README.md) — 仓库根 README

## 构建

```bash
./gradlew :app:assembleStableDebug
```

需 GitHub Packages 凭据解析 miuix snapshot 依赖（`~/.gradle/gradle.properties` 中的 `gpr.user` / `gpr.key`，或 CI 中的 `GITHUB_ACTOR` / `GITHUB_TOKEN`）。

## 许可

GPL-3.0。