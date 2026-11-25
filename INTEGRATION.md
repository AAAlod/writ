# Writ Forge 1.20.1 总库

核心库独立构建交易、签约和契约豁免。法器库独立构建和验证 NBT 效果工具。两个子库的独立 JAR 用于各自验收，集成库按锁定提交重新编译源码，只发行这一份 Writ JAR。

| 路径 | 维护方式 |
| --- | --- |
| `modules/writ-core` | 从核心仓库导入生产源码、资源和回归测试。独立启动入口留在核心库。 |
| `modules/resonance-device` | 从法器仓库导入业务源码和指定资源。独立开发外壳和测试效果留在法器库。 |
| `integration/src` | Writ 正式入口、默认效果接线及跨模块 GameTest。 |
| `modules.lock.json` | 两个子库的 commit、tree 和导入文件哈希。 |

## 更新模块

在子库提交并通过独立构建与测试后，把完整提交 ID 和结果交给集成人。集成人确认总库工作区干净，从正式分支 `integration-forge-1.20.1` 建立 `sync/*` 候选分支，再执行同步。以下为 PowerShell 示例，子库位置可用绝对路径。

```powershell
git status --short
git switch -c sync/resonance-device-<SHORT_SHA> integration-forge-1.20.1
python scripts/sync-modules.py --module resonance-device '<DEVICE_REPO>' <FULL_SHA>
```

首次导入需要两个 `--module` 参数，分别指定 `writ-core` 和 `resonance-device`。更新时只给出有改动的模块，另一个保持锁定。脚本读 Git 提交中的文件，校验现有快照、检查冲突并生成一个本地同步提交。重复同步同一提交时不产生额外提交。模块行为的修改回子库处理。

候选分支必须包含当前本地正式分支的最新提交。遇到未分类的 `src/main/` 文件，同步会列出路径并停止。确认文件是发行资源、独立开发入口还是测试素材后，再更新同步规则。法器的独立入口、开发夹具以及独立构建的元数据有明确的排除规则。

若快照已安装但 Git 提交失败，用 `git status --short`、`git diff` 和 `git diff --cached` 核对新快照与锁文件。修复 Git 身份或钩子问题后，可对本次涉及的模块和锁文件重新暂存，运行 `git diff --cached --check`，再按同步提交格式完成提交。要重新导入时，先确认候选分支只包含本次失败留下的改动，再按 `HEAD` 恢复本次涉及的模块目录与锁文件，检查工作区干净后重跑同步。

已获集成授权时也可以在子库发起同步。例如在核心库根目录执行 `python ../writ-integration/scripts/sync-modules.py --module writ-core . (git rev-parse HEAD)`。总库先建好本次的 `sync/*` 分支。总库的正式分支提交、远端推送、标签和发布都按各自的审核流程执行。

从总库运行 `./scripts/verify-release.ps1` 执行完整发布校验，默认最低要求为 103 项 GameTest。验证两种获取模式时，分别在 `run-gametest/config/writ-common.toml` 设置 `TRADE` 和 `CRAFT_9`。第二次可用 `-SkipBuild` 复用同一份已核实的 JAR。跳过构建或 GameTest 的运行只适用于局部复核，完整校验应从当前源码清理并重新构建。

## 宿主接线与联调

- 子库负责行为、独立默认值和最小接入接口。总库负责面向玩家的入口。法器由总库读取服务端配置并调用 `ResonanceModule.setDefaultCooldownTicks`，通过 Forge 的 `BuildCreativeModeTabContentsEvent` 把已注册物品加入 `writ:writs`。核心库和法器库都可以单独构建。
- 总库的数据包资源维护组合后的默认策略。当前总库提供的混沌配置排除共鸣法器，使用整合包数据包覆盖同一路径即可放行。核心独立构建继续使用自己的配置。
- 冷却由服务器执行，客户端提示使用 Forge 登录时同步的服务端配置。接入影响玩家提示的设置时，同时检查服务端实际行为和客户端所见。修改配置后重启存档或服务器。使用世界时间记录的交互和冷却状态由法器模块在服务器停止时清理，以免影响同一客户端接着打开的其他存档。
- 交付新增生产资源时，同时列出快照导入路径与发布 JAR 预期条目。本轮法器纹理同时进入同步白名单与 JAR 校验。更新默认行为时检查总库原有的测试前置条件。本轮冷却测试从实际配置取值，检查到期前拦截和到期时放行。
- 发布校验脚本可在 Windows PowerShell 5.1 或 PowerShell 7 运行。两个 Gradle 调用都先在 `cmd.exe` 中合并输出，避免 5.1 把 JVM 的启动提示当作终止错误。遇到环境差异时记录外壳版本、复现命令、临时处理和仓库内修复，方便下一位接手人复跑。
