# 贡献指南

欢迎为本项目（Miuix 风格设置演示壳）做贡献。

## 报告问题

- 先查阅 [docs/HANDOVER_CN.md](docs/HANDOVER_CN.md) 与 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- 使用 GitHub Issues 报告可复现的问题；请包含：设备/系统版本、应用版本与构建渠道、运行日志（应用内"运行日志"页可复制）

## 提交代码

- 遵循 Conventional Commits（`fix:` / `feat:` / `refactor:` / `docs:` / `i18n:`）
- 不要重新引入安装器相关功能或命名
- 中英文案同步更新
- 涉及字符串删除时，同步清理所有语言包

## 构建

```bash
./gradlew :app:assembleStableDebug
```

需要 JDK 25、Android SDK 与 GitHub Packages 凭据（`gpr.user` / `gpr.key`）。