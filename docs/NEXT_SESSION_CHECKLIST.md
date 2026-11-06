# 一次性交接清单（给下一个会话）

> 让下一个会话在**无本会话记忆**的情况下直接接手。
> 开始前先读：`AGENTS.md`、`docs/HANDOVER_CN.md`、`docs/ARCHITECTURE.md`，再按本清单执行。

## 0. 环境与访问

- **沙箱**：Ubuntu 24.04 rootfs，工作目录 `/workspace`；`/upload` 只读
- **网络**：外网可直连 api.github.com（本沙箱）；如受限走本地代理 `http://127.0.0.1:7890`
- **仓库**：`Rune-cn/miuix-example`（私有），main 直接推送（历史惯例）
- **凭据**：GitHub PAT（classic：`repo` + `read:packages`）——**由用户在会话中提供，严禁写入仓库/文档**；正文字符串也勿回显
- **构建**：一般不做本地构建（无 JDK25/SDK）。工作流 = 静态引用扫描 → 提交推送 → 等 CI（5–15 分钟）→ 产物下载链接发给用户
- **每次构建完成必须给下载链接**（运行页 + 两个 artifact）

## 1. 项目一句话速览

Miuix 风格设置演示壳；**无任何安装器代码与命名**（构建插件 `miuixexample.*`、主题 `Theme.MiuixExample`、keepRules 仅含通用 R8 规则、`apksig.keep` 已删）；底部 4 Tab（主页 / 示例 1 / 运行日志 / 设置）；主页含**运行状态卡**（演示壳语义：运行正常/异常，非安装器激活状态）；更新检查跳浏览器；Timber 文件日志（`cacheDir/logs`）+ 崩溃 dump（`cacheDir/crashes`）；主题/语言（en + zh-rCN + zh-rTW）/关于齐全；**README 仅中文**。

## 2. ⚠️ 未闭环、需先与用户确认的问题（最高优先）

| # | 事项 | 状态 | 下一步 |
|---|---|---|---|
| **A** | 关于页三张卡片「模糊时 45% 半透明毛玻璃、关闭时实心」（`BLUR_CARD_ALPHA=0.45f`） | **未获用户确认** | 装最新 debug 包，问用户卡片是否「可见且半透明」。若仍异常转 **B** |
| **B** | 设备模糊渲染：日志页标题栏「不虚化」。2026-11-01 排查结论：**设备完全支持**（sdk=35、RenderEffect/RuntimeShader 均 true、backdrop 全部创建、各页代码一致），疑似设备实际绘制层不输出；**用户决定暂停**，`[blur]` 诊断埋点已移除 | **暂停** | 若重启排查：加埋点复测（`rememberMiuixBlurBackdrop` / 各页 TopAppBar），确认是否所有页面（含主页滚动）都不绘制 |
| **C** | 设置页「语言 + 主题」已合并为一张卡（commit `07935cba`） | **未获用户确认** | 确认中间无空隙、无视觉异常 |
| **D** | 更新 Sheet「下载更新」按钮色已统一为默认（commit `1dcc3f39`） | CI ✅，**未获用户确认** | 确认 Sheet 内两按钮同色 |

## 3. 待办（功能/质量，按建议顺序）

1. **「示例 1」占位 Tab**：改成一个真实演示页（如「关于本项目 / 帮助」），或删除只剩 3 个 Tab（涉及 `ui/navigation/MiuixNavWrapper.kt`、`ui/page/miuix/settings/MiuixSettingsPage.kt`）
2. **更新下载应用内化**（可选）：应用内下载 + 进度 + 安装（`UpdateSheet.kt` 中 `TODO(legado-update-sheet)` 点 5/6）；若不做，清理相关 TODO
3. **日志持久性**：`cacheDir` 会被系统清缓存清掉 → 评估把日志/崩溃迁到 `filesDir`（`util/timber/FileLoggingTree.kt`、`LogViewModel.kt`、`core/crash/CrashHandler.kt`），并清理旧路径残留
4. **死代码清扫**（2026-11-01 已清：注释 `agreement_text`、`keepRules/apksig.keep`、installerx 命名。⚠️ 通用 keep 规则文件勿删，R8 release 依赖）：
   - `ui/icons/AppIcons.kt`：大量未引用图标（保留 Home/Theme/Terminal/DragHandle 等在用项）
   - `enableFileLogging`：官方已无 UI 开关，LogController 直连默认 true 可简化（需保留领域层兼容）
5. **更新检查逻辑**（用户要求暂缓，2026-11-01）：目前 `OnlineUpdatePolicy.canCheckUpdates` 拦截 debug 构建（CI 产出的正是 debug 包）→ 用户安装的 debug 包永远「已是最新版本」；失败与「无更新」未区分提示（均显示「已是最新版本」）；APK 资源选择仍沿用 `online` 命名规则。确认后一起改
6. **Release 固定下载**（可选）：创建 GitHub Release 传 APK，避免 artifact 90 天过期；私有仓库登录后可下载

## 4. 铁律（仓库强制，见 AGENTS.md / HANDOVER_CN.md）

1. 不引入安装器代码/命名（引擎/会话/特权/解析器、`installerx` 等前缀标识符）
2. **每次代码修改必须同步更新对应 MD 文档**（HANDOVER_CN / ARCHITECTURE / AGENTS / README / docs/README_CN / 本清单）；README 仅中文
3. 从默认语言删除字符串键时，必须同步删除**全部语言包**（en / zh-rCN / zh-rTW，其余已删）对应键，否则 lintVital `ExtraTranslation` 致命（历史 13748 错误教训）
4. Compose/代码纪律：`stringResource` 仅在 `@Composable` 内调用；普通函数勿调组合函数；默认参数表达式内不能写 `return`
5. 提交遵循 Conventional Commits；主体 ≤72 字符；不加 AI 署名；**诚实汇报验证**（跑过什么、是否通过、没跑什么）
6. 每次构建完成给下载链接

## 5. 会话开始检查清单

- [ ] 向用户索要 GitHub PAT（勿写入仓库；勿在回复中大段回显）
- [ ] 读完 4 份文档（AGENTS、HANDOVER、ARCHITECTURE、本清单）
- [ ] `git pull` main，`git status` 干净
- [ ] 静态扫描无历史命名残留（自查时排除本清单/docs，因为清单里写有被禁止词）
- [ ] 先把第 2 节 A–D 四项反馈给用户确认，再开新活儿
- [ ] 每轮改动遵守第 4 节铁律；收尾给下载链接

## 6. GitHub 操作速查

```bash
# 最新 CI 运行
curl -s -H "Authorization: token $TOKEN" "https://api.github.com/repos/Rune-cn/miuix-example/actions/runs?per_page=1"
# 产物
curl -s -H "Authorization: token $TOKEN" "https://api.github.com/repos/Rune-cn/miuix-example/actions/runs/$RUN_ID/artifacts"
# 推送
git push origin main
```

## 7. 2026-11-01 变更记录（供追溯）

- `fix(ui)`: 关于页更新卡补齐 `textureBlur` 并恢复「获取更新」箭头行（`af5480f2`）
- `chore(ui)`: blur 诊断埋点（`b8e3270d`）——已在本日随暂停决定移除
- `chore`: 安装器残留清理——`installerx.*` 插件 → `miuixexample.*`；`Theme.Installer` → `Theme.MiuixExample`；删除 `keepRules/apksig.keep`（通用 keep 规则保留）；`home_learn_more_installerx_*` → `home_learn_more_*`；`get_update_directly_desc` 去除「安装」措辞。**主页状态卡按用户要求还原**，改为演示壳「运行状态」语义（`home_status_running` / `home_status_abnormal`）；清理范围确认 = 根目录除 app 外 + `app/` 内命名级残留
- `i18n`: 仅保留 en + zh-rCN + zh-rTW（其余语言包删除）；两份中文翻译补齐并与 en 键集合一致
- `docs`: README 仅保留中文版本；交接文档内容与格式更新