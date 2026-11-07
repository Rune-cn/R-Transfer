# 贡献指南

欢迎为 **R 中转（R-Transfer）** 做贡献——把 Android 手机变成私有的 AI 模型聚合服务器。

## 报告问题

- 先查阅 [docs/HANDOVER.md](docs/HANDOVER.md) 与 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- 使用 GitHub Issues 报告可复现的问题；请包含：设备/系统版本、应用版本与构建渠道、运行日志（应用内「调用记录」或日志页可复制）

## 提交代码

- 遵循 Conventional Commits（`fix:` / `feat:` / `refactor:` / `docs:` / `i18n:` / `chore:`）
- **文档同步铁律：每次修改都要更新文档**——任何代码改动必须同步更新 `AGENTS.md`、根 `README.md`、
  `docs/HANDOVER.md`、`docs/ARCHITECTURE.md`、`docs/NEXT_SESSION_CHECKLIST.md` 对应内容（见 [AGENTS.md](AGENTS.md) 铁律 #1）
- `:core` 是纯 JVM 模块，禁止引入 Android 依赖
- UI 排版一律使用 `ui/page/miuix/settings/common/PreferenceCard.kt` 组件族，不直接往 `Card` 塞裸内容
- 中英（en / zh-rCN）文案成对更新；从默认语言删除字符串时同步清理所有语言包（zh-rTW）

## 构建

```bash
./gradlew :core:test                 # 内核单测
./gradlew :app:assembleStableDebug   # debug APK
./gradlew :app:assembleStableRelease # release APK（需要签名 Secrets）
```

- 需要 JDK 25 toolchain、Android SDK；push `main` 后由 GitHub Actions 自动构建并上传产物
- debug 与 release 必须使用同一份密钥库（见 [docs/HANDOVER.md](docs/HANDOVER.md) §6），否则无法覆盖安装