# AGENTS.md

面向 AI 编码代理的仓库指引。

## 用途

本文件定义在 **miuix-example**（Miuix 风格设置演示壳应用）上工作时必须遵守的规则：

- 改动放哪里、哪些约束不能破坏、怎么自证完成。
- 窄任务做最小改动；大功能/跨文件重构先列计划再动手。

## 关键事实

- 本项目是**去安装化**的演示壳：**禁止**重新引入任何安装/卸载/特权/解析相关功能或命名（含 `installerx` 等前缀标识符）
- 只有 `:app` 与 `:baselineprofile` 两个 Gradle 模块；包名 `com.miui.example`；构建插件统一为 `miuixexample.*`；主题为 `Theme.MiuixExample`
- 只保留 **miuix** UI 家族；共享 ViewModel 位于 `ui/page/main/settings/**`（历史命名）
- 应用始终可写文件日志（不随构建类型门控）
- 语言包仅保留 `values/`（en）+ `values-zh-rCN` + `values-zh-rTW`；**README 仅中文**（根 `README.md` + `docs/README_CN.md`）

## 项目布局

- `app/src/main/java/com/miui/example/` — 主代码（`core/data/domain/di/ui/util`）
- `app/src/main/java/com/miui/example/ui/page/miuix/` — miuix 页面
- `app/src/main/java/com/miui/example/ui/page/main/` — 共享 VM/State
- `docs/HANDOVER_CN.md` — 交接文档（架构、已知问题，先读）
- `docs/ARCHITECTURE.md` — 代码地图

## 构建与验证

- 使用仓库 wrapper：`./gradlew :app:assembleStableDebug`（冒烟）/ `:app:assembleStableRelease`
- 需要 JDK 25、Android SDK、GitHub Packages 凭据（`gpr.user`/`gpr.key` 或 `GITHUB_ACTOR`/`GITHUB_TOKEN`）
- 若无法本地构建：以静态引用扫描（grep 已删包/符号）+ 推送后 CI 验证迭代；**如实报告**跑了什么、结果如何
- CI 在 main 构建 Stable debug+release；release 跑 `lintVital`

## 铁律（易踩坑）

1. **不要重新引入安装器代码/命名**（引擎、会话、特权、解析器、`installer`/`installerx` 前缀标识符）
2. **文档同步铁律：每次代码修改都必须同步更新代码 MD 文档的对应内容**（`docs/HANDOVER_CN.md`、`docs/ARCHITECTURE.md`、本文件、根 `README.md`、`docs/README_CN.md`）。文档与代码必须一致，禁止"只改代码不动文档"
3. **字符串修改**：若从默认语言（`values/`，en）删除键，必须同时从**全部语言包**（`values-zh-rCN`、`values-zh-rTW`，其余语言包已删除）删除，否则 lintVital 报 `ExtraTranslation` 致命错误
3. **Koin 构造**：VM 注入 `Context` 直接写构造函数参数即可（koin 提供）
4. **Compose**：`stringResource` 只能在 `@Composable` 内调用；局部普通函数不要直接调组合函数
5. **卡片透明**：关于页卡片透明度跟随模糊开关（上游设计），勿改成恒不透明
6. **提交**：Conventional Commits；主体 ≤72 字符；不加 AI 署名/Co-authored-by
7. **成对更新**：en + zh-rCN 文案同步

## 交接格式

任务收尾汇报：**改了哪些文件 / 行为变化 / 验证（命令+结果）/ 备注（风险、未验证项）**。