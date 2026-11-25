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

已获集成授权时也可以在子库发起同步。例如在核心库根目录执行 `python ../writ-integration/scripts/sync-modules.py --module writ-core . (git rev-parse HEAD)`。总库先建好本次的 `sync/*` 分支。总库的正式分支提交、远端推送、标签和发布都按各自的审核流程执行。

从总库运行 `scripts/verify-release.ps1 -MinGameTests 100` 执行当前发布校验。需要两种获取模式的验收时，分别在 `run-gametest/config/writ-common.toml` 设置 `TRADE` 和 `CRAFT_9` 运行 GameTest。两种模式共用同一份生产 JAR。
