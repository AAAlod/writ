# Writ (商契)

## Project Introduction

**Writ** turns villager trading into something you discover. Trade writs are scattered through structure chests and dropped by bosses. Each writ carries one specific trade: a smelting service, a stack of rare materials, a piece of named enchanted gear. Hand the writ to a villager, the villager signs, and the trade becomes a permanent part of its offers.

Instead of rerolling job sites until the right book appears, you explore, collect writs and decide which villager learns what. And for those who like a gamble, the **chaos writ** teaches a villager a trade drawn completely at random.

## Core Features

- **Trades worth exploring for.** Dungeons, mineshafts, desert pyramids, shipwrecks, ocean ruins, buried treasure, pillager outposts, strongholds, woodland mansions, Nether fortresses, bastion remnants, ancient cities and End cities each hide their own themed writs. The Elder Guardian, Warden, Ender Dragon and Wither drop exclusive ones.
- **Five tiers, from everyday to legendary.** Common, Rare, Superior, Precious and Legendary writs each have their own name color. A Common writ might restock building stone. A Legendary one might sell a Nether Star or a Totem of Undying.
- **Every profession gets something new.** All 13 vanilla professions have writ trades. Masons can cure concrete, toolsmiths can smelt ore in bulk, librarians can sell Mending, and fletchers can fill an expedition kit.
- **Choose before you sign.** Some writs hold several versions of a trade, such as every color of terracotta or wool. Cycle through the choices in your hand, then apply the one you want.
- **Chaos writs for the brave.** A chaos writ adds a random one-for-one trade drawn from every item in the game, including items from other mods. It might be a bargain. It might be nonsense.

## Gameplay

**Finding writs.** When a structure chest generates its loot, it gets two independent chances to contain a writ. Bosses have a chance to award a writ directly to the player who gets credit for the kill.

**Reading a writ.** A trade writ is named after its trade and colored by its tier. The tooltip shows which professions can sign it and exactly what the trade costs and gives. Hold Shift to see enchantments, potion effects or the contents of a supply box.

**Applying a writ.** Use the writ on a villager. The villager must be an adult with a profession (not a nitwit), and you must have traded with it at least once. The new trade is added to the end of its list and one writ is used up. If a villager refuses, the writ is never lost, and the message tells you why: wrong profession, too young, or no room for more writs.

**Cycling choices.** If a writ offers choices, look at empty space and use it to cycle forward, or sneak and use it to cycle backward. The choice applies to the whole stack in your hand.

**Getting chaos writs.** Depending on the server's setting, either dungeons hide a *Chaos Writ Unlock* writ that lets a villager sell chaos writs, or chaos writs are crafted from nine trade writs.

**Limits.** By default, each villager accepts up to 8 trade writs, 3 chaos writs and 10 writs in total. Server owners can change these numbers.

## Content Overview

- **Kinds of trades:** bulk supplies; processing services (bring raw materials, get the finished product); supply kits delivered in a shulker box (pay with an empty one); enchanted books and named enchanted gear; rare materials such as Echo Shards, Hearts of the Sea and Sniffer Eggs.
- **Tiers:** N Common, R Rare, SR Superior, SSR Precious, UR Legendary. Higher tiers are rarer, come from more dangerous places and often allow fewer uses before restocking.
- **Professions:** armorer, butcher, cartographer, cleric, farmer, fisherman, fletcher, leatherworker, librarian, mason, shepherd, toolsmith and weaponsmith.
- **Structures:** 13 kinds of structures, each with a themed set of trades and its own tier mix.
- **Bosses:** the Elder Guardian, Warden, Ender Dragon and Wither, each with exclusive trades found nowhere else.

## Customization and Extension

**Server settings.** Each world's `serverconfig/writ-server.toml` sets the three per-villager limits, the chaos writ obtain mode (`TRADE` or `CRAFT_9`) and the parameters of chaos trades.

**Datapacks.** All content is data-driven. With a datapack you can add or change trades, create tiers, add new chest or mob sources (including loot tables from other mods), add ordered choices to any trade, and exclude items from chaos trades. The [Datapack Reference](https://github.com/AAAlod/writ/blob/main/docs/datapacks.md) documents every field and includes a complete example pack.

## AI Usage Disclosure

Writ is an AI-assisted project. AI coding assistants, including Claude Code, OpenAI Codex, and DeepSeek Harness, were used for code generation and refactoring, drafting tests and documentation, and debugging.

## Documentation and Feedback

- Source code: [github.com/AAAlod/writ](https://github.com/AAAlod/writ)
- [Gameplay Guide](https://github.com/AAAlod/writ/blob/main/docs/gameplay.md)
- [Datapack Reference](https://github.com/AAAlod/writ/blob/main/docs/datapacks.md)
- Bug reports and suggestions: [GitHub Issues](https://github.com/AAAlod/writ/issues)

---

# 商契（Writ）

## 项目简介

**商契** 让村民交易变成一种需要探索发现的东西。交易契约散落在各种结构的箱子里，也会由首领掉落。每张契约记载着一项具体的交易：一项熔炼服务、一批稀有材料，或是一件命名的附魔装备。把契约交给村民，村民签约后，这项交易就会永久成为它的交易之一。

你不必再反复更换工作站点方块，等着想要的附魔书刷出来。你去探索、收集契约，再决定让哪位村民学会什么交易。喜欢赌一把的玩家还可以使用 **混沌契约**，让村民学会一项完全随机抽取的交易。

## 核心特色

- **值得探索的交易。** 地牢、废弃矿井、沙漠神殿、沉船、海底废墟、埋藏的宝藏、掠夺者前哨站、要塞、林地府邸、下界要塞、堡垒遗迹、远古城市和末地城，各自藏着主题不同的契约。远古守卫者、监守者、末影龙和凋灵还会掉落专属契约。
- **五个档位，从日常到传说。** 普通、稀有、精良、珍稀、传说五个档位的契约各有专属的名称颜色。普通契约可能提供建筑石材补给，传说契约则可能出售下界之星或不死图腾。
- **每种职业都有新交易。** 原版全部 13 种职业都有契约交易。石匠可以固化混凝土，工具匠可以批量熔炼矿石，图书管理员可以出售经验修补，制箭师可以备好远征物资箱。
- **签约前先选择。** 部分契约包含同一交易的多个版本，例如所有颜色的陶瓦或羊毛。在手中切换选项，再使用你想要的那一个。
- **为勇者准备的混沌契约。** 混沌契约会添加一项随机的一换一交易，物品从游戏中的全部物品里抽取，包括其他模组的物品。可能捡到大便宜，也可能毫无意义。

## 玩法

**寻找契约。** 结构箱子生成战利品时，有两次独立的机会出现契约。首领被击败时，有一定概率直接把契约交给获得击杀归属的玩家。

**查看契约。** 交易契约以其交易命名，名称颜色表示档位。提示框会显示可以签订它的职业，以及这项交易的确切花费和产出。按住 Shift 可以查看附魔、药水效果或物资箱的内容物。

**使用契约。** 对村民使用契约。村民必须已成年、有职业（傻子不行），并且你至少和它交易过一次。新交易会追加到它交易列表的末尾，同时消耗一张契约。村民拒绝时，契约绝不会丢失，提示消息会告诉你原因：职业不符、年纪太小，或已经签满。

**切换选项。** 如果契约提供选项，看向空处使用它可以切换到下一个选项，潜行并使用则切换到上一个。选项作用于手中的整组契约。

**获取混沌契约。** 取决于服务器设置：要么地牢中会出现 *混沌契约解锁*，让村民出售混沌契约；要么用九张交易契约合成混沌契约。

**上限。** 默认情况下，每位村民最多接受 8 张交易契约、3 张混沌契约，总计 10 张。服务器管理员可以调整这些数值。

## 内容概览

- **交易类型：** 大宗供货；加工服务（自备原材料，换取成品）；装在潜影盒里的物资箱（用空潜影盒支付）；附魔书和命名附魔装备；回响碎片、海洋之心、嗅探兽蛋等稀有材料。
- **档位：** N 普通、R 稀有、SR 精良、SSR 珍稀、UR 传说。档位越高越稀有，来自越危险的地方，补货前的可用次数往往也越少。
- **职业：** 盔甲匠、屠夫、制图师、牧师、农民、渔夫、制箭师、皮匠、图书管理员、石匠、牧羊人、工具匠和武器匠。
- **结构：** 13 类结构，每类都有主题交易和各自的档位分布。
- **首领：** 远古守卫者、监守者、末影龙和凋灵，各有别处找不到的专属交易。

## 定制与扩展

**服务器设置。** 每个存档的 `serverconfig/writ-server.toml` 用于设置三项村民上限、混沌契约的获取模式（`TRADE` 或 `CRAFT_9`）以及混沌交易参数。

**数据包。** 全部内容都由数据驱动。通过数据包可以添加或修改交易、创建新档位、添加新的箱子或生物来源（包括其他模组的战利品表）、为任何交易添加有序选项，以及从混沌交易中排除物品。[数据包参考](https://github.com/AAAlod/writ/blob/main/docs/datapacks.zh-CN.md)说明了每个字段，并附有完整的示例数据包。

## AI 使用说明

Writ 是一个使用 AI 辅助开发的项目。开发中使用了 AI 编程助手，包括 Claude Code、OpenAI Codex 和 DeepSeek Harness，主要用于代码生成与重构、测试与文档草稿，以及调试。

## 文档与反馈

- 源代码：[github.com/AAAlod/writ](https://github.com/AAAlod/writ)
- [玩法指南](https://github.com/AAAlod/writ/blob/main/docs/gameplay.zh-CN.md)
- [数据包参考](https://github.com/AAAlod/writ/blob/main/docs/datapacks.zh-CN.md)
- 问题反馈与建议：[GitHub Issues](https://github.com/AAAlod/writ/issues)
