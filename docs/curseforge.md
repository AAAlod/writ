**Writ** turns villager trading into something you discover. Trade writs appear in loot containers and as boss drops. Each one carries a trade, such as a smelting service, a stack of rare materials or a piece of named enchanted gear. Hand the writ to a villager, the villager signs, and the trade becomes a permanent part of its offers.

Instead of breaking and replacing job site blocks until the right book shows up, you explore, collect writs and decide which villager learns what.

## Features

- **Trades worth exploring for.** Thirteen kinds of structures hide their own themed writs, from dungeons and shipwrecks to bastions and End cities. Supported Overworld loot containers can also contain general writs. The Elder Guardian, Warden, Ender Dragon and Wither drop exclusive ones.
- **Five tiers, from everyday to legendary.** A Common writ might restock building stone; a Legendary one might sell a Nether Star or a Totem of Undying. Each tier has its own name color.
- **Something new for every profession.** Masons cure concrete, toolsmiths smelt ore in bulk, librarians sell bottles o' enchanting, fletchers pack an expedition kit.
- **Choose before you sign.** Search and preview the choices on a writ before you sign it. Try different terracotta colors or choose logs and saplings from item tags.
- **Random writs.** Craft four trade writs into one random writ. Use it on a villager to draw a fitting trade. A draw with multiple choices opens the selector before signing.
- **Chaos writs for the brave.** A random one-for-one trade drawn from every item in the game, modded items included. It might be a bargain. It might be nonsense.

## How it works

1. Check Overworld loot chests, barrels and chest minecarts for general writs, or explore structures and defeat bosses for themed writs.
2. Read its tooltip: tier, the professions that can sign it, and the exact trade. Hold Shift for item details.
3. If the writ offers choices, use it while looking at empty space. Pick a trade in the selector and confirm.
4. Use it on an adult villager with the right profession that you have traded with at least once.
5. The trade is added to the end of the villager's list. A refused writ is never consumed, and the message tells you why.

By default each villager accepts up to 8 trade and random writs, 3 chaos writs and 10 writs in total.

## Other ways to get writs

- **Random writs:** craft four trade writs together in any arrangement.
- **Chaos writs:** depending on the server setting, either dungeons hide a *Chaos Writ Unlock* writ that teaches a villager to sell chaos writs, or nine trade writs craft one.
- **Hero of the Village:** a villager's thank-you gift is occasionally a random writ or a chaos writ (8% and 2% by default).

## Requirements

Minecraft 1.20.1 with Forge 47.4.10 or newer. Install on **both client and server**.

## Configuration and datapacks

Limits, the general loot chance, the chaos writ mode, chaos trade parameters and hero gift chances are set in `config/writ-common.toml`, which is shared by all worlds and read at startup. Upgrading from 1.0.0: the old `serverconfig/writ-server.toml` is no longer read, so copy your settings over. See [Server settings](https://github.com/AAAlod/writ/blob/main/docs/gameplay.md#server-settings).

Everything else is data-driven. A datapack can add or change trades, create tiers, add chest or mob sources (including other mods' loot tables), define explicit or tag-based choices, opt presets into general loot and tune item exclusions. The [Datapack Reference](https://github.com/AAAlod/writ/blob/main/docs/datapacks.md) covers every field and includes a complete example pack.

## AI usage disclosure

Writ is an AI-assisted project. AI coding assistants, including Claude Code, OpenAI Codex and DeepSeek Harness, were used for code generation and refactoring, drafting tests and documentation, and debugging.

## Links

- [Gameplay Guide](https://github.com/AAAlod/writ/blob/main/docs/gameplay.md)
- [Datapack Reference](https://github.com/AAAlod/writ/blob/main/docs/datapacks.md)
- [Source code](https://github.com/AAAlod/writ)
- [Bug reports and suggestions](https://github.com/AAAlod/writ/issues)

*简体中文介绍见下方。*

---

# 商契（Writ）

**商契**让村民的新交易要靠探索来获得。交易契约可从战利品容器和首领掉落中获得，每张都写着一项具体交易：帮你熔炼矿石、出售一批稀有材料、卖一件命名附魔装备……把契约交给村民签下，这项交易就会永久加入它的交易列表。

出门探索、收集契约，再决定让哪位村民学会什么交易。

## 特色

- **交易藏在战利品里**：地牢、废弃矿井、沙漠神殿、沉船、海底废墟、埋藏的宝藏、掠夺者前哨站、要塞、林地府邸、下界要塞、堡垒遗迹、远古城市和末地城，各有一套主题交易。主世界可识别的战利品容器还可能开出通用契约。远古守卫者、监守者、末影龙和凋灵还会掉落专属契约。
- **五个档位**：N 普通、R 稀有、SR 精良、SSR 珍稀、UR 传说，名称颜色各不相同。普通契约可能是建筑石材供货，传说契约可能直接卖下界之星或不死图腾。
- **每种职业都有新活**：石匠能固化混凝土，工具匠能批量熔炼矿石，图书管理员能卖附魔之瓶，制箭师能备好一整箱远征物资。
- **签约前先挑选**：签约前可在选择器里搜索和预览交易。陶瓦可选颜色，原木和树苗可从物品标签提供的选项中挑选。
- **随机契约**：4 张交易契约合成 1 张随机契约。对村民使用时按其职业抽取交易，抽到多个选项时先打开选择器。
- **混沌契约**：随机一种物品换随机一种物品，范围是游戏里的全部物品，模组物品也算在内。可能捡到宝，也可能完全用不上。

## 怎么用

1. 在主世界的战利品箱、木桶和箱子矿车里找通用契约，也可以探索结构或击败首领来获得主题契约。
2. 看提示框：档位、适用职业、具体交易都写在上面，按住 Shift 查看物品详情。
3. 有选项时，看向空处使用契约，在选择器中挑选交易并确认。
4. 对村民使用契约。村民要已成年、职业对得上，而且你和它交易过至少一次。
5. 新交易加在交易列表最后。被拒绝时契约不会消耗，提示会说明原因。

默认每位村民最多签 8 张交易契约和随机契约、3 张混沌契约，总共 10 张。

## 其他获取途径

- **随机契约**：4 张交易契约随意摆放即可合成。
- **混沌契约**：取决于服务器设置，要么在地牢里找“混沌契约解锁”，让村民学会出售混沌契约；要么用 9 张交易契约合成。
- **村庄英雄**：村民送的礼物偶尔会变成随机契约或混沌契约（默认概率分别为 8% 和 2%）。

## 运行环境

Minecraft 1.20.1，Forge 47.4.10 或更高。**客户端和服务器都要安装。**

## 配置与数据包

签约上限、通用掉落概率、混沌契约获取模式、混沌交易参数和村庄英雄礼物概率都在 `config/writ-common.toml` 中设置，所有存档共用，启动时读取。从 1.0.0 升级时，旧的 `serverconfig/writ-server.toml` 不再读取，需要把设置手动抄过去。详见[服务器设置](https://github.com/AAAlod/writ/blob/main/docs/gameplay.zh-CN.md#服务器设置)。

其余内容都由数据包定义：添加或修改交易、新建档位、添加箱子或生物来源（包括其他模组的战利品表）、为交易添加显式或基于物品标签的选项、让预置加入通用掉落池、配置物品排除名单。[数据包参考](https://github.com/AAAlod/writ/blob/main/docs/datapacks.zh-CN.md)列出了全部字段，并附有完整的示例数据包。

## AI 使用说明

商契在开发中使用了 AI 辅助，包括 Claude Code、OpenAI Codex 和 DeepSeek Harness，用于代码生成与重构、编写测试和文档草稿，以及调试。

## 链接

- [玩法指南](https://github.com/AAAlod/writ/blob/main/docs/gameplay.zh-CN.md)
- [数据包参考](https://github.com/AAAlod/writ/blob/main/docs/datapacks.zh-CN.md)
- [源代码](https://github.com/AAAlod/writ)
- [问题反馈与建议](https://github.com/AAAlod/writ/issues)
