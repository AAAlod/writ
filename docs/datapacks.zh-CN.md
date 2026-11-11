[English](datapacks.md) | [简体中文](datapacks.zh-CN.md)

# 商契数据包参考

本参考面向数据包作者和整合包作者。它说明商契读取的每一种数据文件、所有受支持的字段、文件的校验方式，以及契约掉落时如何抽取。同一套机制在玩家眼中的表现，请参阅[玩法指南](gameplay.zh-CN.md)。

## 目录

- [基础知识](#基础知识)
- [池](#池)
- [预设](#预设)
- [变体](#变体)
- [获取来源](#获取来源)
- [混沌配置](#混沌配置)
- [服务器配置](#服务器配置)
- [完整示例](#完整示例)
- [重载、校验与排错](#重载校验与排错)

## 基础知识

### 数据包可以定制什么

商契的内容完全由数据驱动。数据包可以：

- 添加、修改或停用 **预设**，即交易契约记载的交易；
- 添加、修改或禁用 **池**，即预设所属的档位和其他分组；
- 添加或修改 **箱子来源** 和 **首领来源**，即契约从哪里掉落、使用哪些池；
- 限制 **混沌契约** 可以抽到哪些物品。

以下内容是固定的，不能通过数据修改：

- 契约只能用于原版 `minecraft:villager`，村民的签约资格规则无法修改。
- 每位村民的上限、获取模式和混沌交易参数属于[服务器配置](#服务器配置)，不属于数据包数据。
- 箱子来源每次生成战利品表时总是尝试两次；首领来源每次死亡总是尝试一次。
- 混沌交易总是支付一个物品、换得一个物品。混沌契约的硬性禁止列表无法解除。
- 只有五个内置档位（`writ:n`、`writ:r`、`writ:sr`、`writ:ssr`、`writ:ur`）有档位颜色，其他池一律显示为灰色。

### 目录结构

商契读取每个已启用数据包中 `data/<namespace>/writs/` 下的 JSON 文件：

```text
data/<namespace>/writs/
├── presets/          交易预设，每个文件一个（可使用子文件夹）
├── loot/
│   ├── pools/        池（档位和用途分组）
│   ├── sources/      箱子来源
│   └── bosses/       首领来源
└── chaos/
    └── config.json   混沌配置（只读取 writ 命名空间中的这个文件）
```

`writs/` 下其他位置的文件会被忽略。

文件路径决定了各对象的 ID：

| 文件 | ID |
|---|---|
| `data/example/writs/presets/honey_supply.json` | 预设 `example:honey_supply` |
| `data/example/writs/presets/food/bread.json` | 预设 `example:food/bread` |
| `data/example/writs/loot/pools/mythic.json` | 池 `example:mythic` |
| `data/example/writs/loot/sources/plains_houses.json` | 来源键 `example:plains_houses` |
| `data/example/writs/loot/bosses/evoker.json` | 来源键 `example:evoker` |

在 JSON 中，凡是不带命名空间的池 ID 或来源键，都表示 `writ` 命名空间。例如 `"r"` 表示 `writ:r`，`"simple_dungeon"` 表示 `writ:simple_dungeon`。物品、实体、职业和战利品表 ID 不带命名空间时，照常表示 `minecraft`。

### pack.mcmeta

Minecraft 1.20.1 使用数据包格式 `15`：

```json
{
  "pack": {
    "pack_format": 15,
    "description": "My Writ content"
  }
}
```

### 数据包优先级与覆盖

商契遵循 Minecraft 的常规数据包规则：

- 两个数据包中有相同路径的文件时，只使用优先级较高的数据包中的文件。文件整体替换，不会合并。
- 商契的内置数据位于 `writ` 命名空间，例如 `data/writ/writs/presets/glass.json`。要修改内置文件，把它复制到自己数据包中的相同路径再编辑。存档 `datapacks` 文件夹中的数据包通常比模组数据优先级更高；可以用 `/datapack list` 和 `/datapack enable` 查看和调整顺序。
- 添加新内容时，请使用自己的命名空间。

内置文件无法删除，但可以让它失效：

| 目标 | 覆盖方式 |
|---|---|
| 让某个内置预设不再掉落 | 把它的 `weight` 设为 `0`。 |
| 关闭某个内置箱子来源或首领来源 | 把它的 `contract_drop_chance` 设为 `0`。 |
| 关闭整个档位 | 在池文件中加入 `"disabled": true`。 |

### 重载行为

商契在加载存档时以及每次执行 `/reload` 时重建全部数据。重载会产生以下效果：

- 池、预设、来源和混沌配置一起替换。
- **已有的交易契约会立即使用新定义。** 村民得到的交易始终按当前预设解析。
- 已有契约的提示框 **不会** 变化，它显示的是契约生成时的交易。
- 已经追加到村民身上的交易永远不会改变。
- 已经生成过战利品的箱子不受影响。
- 获取模式 **不会** 重新读取，只有重启服务器时才会改变。

## 池

池把预设分组，用于自然掉落。商契定义了五个档位和一个用途池：

| 池 | `weight` | `kind` | 说明 |
|---|---|---|---|
| `writ:n` | 45 | `rarity` | N 档 |
| `writ:r` | 35 | `rarity` | R 档 |
| `writ:sr` | 14 | `rarity` | SR 档 |
| `writ:ssr` | 5 | `rarity` | SSR 档 |
| `writ:ur` | 1 | `rarity` | UR 档 |
| `writ:unlock` | 0 | `purpose` | 存放混沌契约解锁预设 |

所有内置来源都自行设置池权重，不继承这些全局权重。全局权重只对继承它们的来源起作用（见[来源中的池权重](#来源中的池权重)）。

### 池字段

文件：`data/<namespace>/writs/loot/pools/<name>.json`

| 字段 | 类型 | 必需 | 默认值 | 说明 |
|---|---|---|---|---|
| `weight` | 整数 ≥ 0 | 是 | — | 全局权重，供继承全局权重的来源使用。必须写成普通整数：`5` 有效，`5.0` 无效。 |
| `kind` | `"rarity"` 或 `"purpose"` | 是 | — | 决定提示框的显示方式。`rarity` 显示为档位行；`purpose` 显示为“用途：*label*”。 |
| `label` | 非空字符串 | 是 | — | 显示文本；客户端缺少 `translation_key` 时也用它作为后备。 |
| `translation_key` | 字符串 | 否 | 无 | 显示文本的语言键。 |
| `disabled` | 布尔值 | 否 | `false` | 为 `true` 时，此池中的预设不会自然掉落。 |

```json
{
  "weight": 2,
  "kind": "rarity",
  "label": "Mythic",
  "translation_key": "example.pool.mythic"
}
```

### 校验与禁用的池

- 无效的池文件会被丢弃并报告。
- 指定了不存在的池的预设仍会加载，但不参与自然掉落，并记录警告。
- 来源的 `pool_weights` 中引用了不存在的池时，该来源会被禁用。禁用的池仍算已知的池，因此给它设置权重的来源仍保持启用；只是这个池永远不会被抽中，并且当有正权重指向它时会记录警告。
- 如果一个有效的池都没有，自然掉落会完全关闭。

## 预设

一个预设定义一项可由交易契约记载的交易。

文件：`data/<namespace>/writs/presets/<path>.json`，ID 为 `<namespace>:<path>`

### 预设字段

| 字段 | 类型 | 必需 | 默认值 | 说明 |
|---|---|---|---|---|
| `pool` | 池 ID | 是 | — | 预设所属的池（档位）。不带命名空间时表示 `writ` 命名空间。 |
| `weight` | 整数 ≥ 0 | 是 | — | 在池内的权重。`0` 表示永远不会自然掉落。 |
| `trade` | 对象 | 是 | — | 交易内容，见[交易对象](#交易对象)。 |
| `allowed_professions` | 职业 ID 数组 | 否 | 不限职业 | 可以签订此契约的职业。每个 ID 都必须是已注册的村民职业。空数组同样表示不限职业。 |
| `sources` | 来源键数组 | 否 | 任意来源 | 允许掉落此预设的来源，见[来源限制](#来源限制)。如果提供，数组必须非空、只含字符串且没有重复项。 |
| `loot_modes` | `"TRADE"` / `"CRAFT_9"` 组成的数组 | 否 | 两者 | 在哪些获取模式下自然掉落。空数组表示永远不会自然掉落，并记录警告。 |
| `display_name` | 非空字符串 | 否 | 无 | 交易的纯文本名称。 |
| `display_translation_key` | 字符串 | 否 | 无 | 交易名称的语言键。 |
| `default_variant_label` | 非空字符串 | 否 | 无 | 默认选项的纯文本名称，仅在预设有变体时显示。 |
| `default_variant_translation_key` | 非空字符串 | 否 | 无 | 默认选项的语言键。 |
| `variants` | 数组 | 否 | 无 | 有序的备选交易，见[变体](#变体)。 |

### 交易对象

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `cost_a` | 物品堆叠 | 是 | 第一支付物。 |
| `cost_b` | 物品堆叠或 `null` | 否 | 第二支付物。省略或设为 `null` 表示没有。 |
| `result` | 物品堆叠 | 是 | 村民出售的物品。 |
| `max_uses` | 整数 > 0 | 是 | 售罄前的可用次数，村民补货后恢复。 |
| `villager_xp` | 整数 ≥ 0 | 是 | 每次交易村民获得的经验。 |
| `price_multiplier` | 数字 ≥ 0 | 是 | 原版价格乘数，决定需求和声望对价格的影响程度。原版通常使用 `0.05`，工具和盔甲使用 `0.2`。必须是有限数。 |

### 物品堆叠

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `item` | 物品 ID | 是 | 已注册的物品，不能是空气。 |
| `count` | 整数 | 是 | 从 1 到该物品的最大堆叠数。例如镐只能是 1，末影珍珠最多 16。 |
| `nbt` | SNBT 字符串或 JSON 对象 | 否 | 物品 NBT。 |

`nbt` 的规则：

- 必须是复合标签。
- 推荐使用 SNBT 字符串，因为它能保留 `1b`、`5s` 这类精确的 NBT 类型。使用 JSON 对象形式时，数字不带显式类型。
- 不能包含 `preset_id`、`contract_version` 或 `display_snapshot` 键。
- 序列化后的大小不能超过 65,536 字节。

```json
{
  "item": "minecraft:diamond_pickaxe",
  "count": 1,
  "nbt": "{Enchantments:[{id:\"minecraft:efficiency\",lvl:5s},{id:\"minecraft:silk_touch\",lvl:1s}]}"
}
```

交易中任一物品带有 NBT 时，契约会为提示框保存完整物品。玩家可以按住 Shift 查看附魔、药水效果或容器内容物。

### 容器支付

要出售装满物品的容器，可以像内置物资箱那样，把空潜影盒设为 `cost_b`，把装满物品的潜影盒设为 `result`。原版交易匹配会忽略支付物的 NBT，所以玩家可能用仍装有物品的潜影盒支付。商契会把这类已消耗支付物中的内容物退还给玩家。这适用于所有村民交易，只有支付物本身就规定了容器内容物（`BlockEntityTag.Items`）的交易除外。

### 显示名称

契约名称是“交易契约：”加上交易名称。交易名称按以下顺序确定：

1. `display_translation_key`。客户端没有该键时，依次使用 `display_name`、预设 ID。
2. `display_name`。
3. 语言键 `<namespace>.preset.<path>`。客户端没有该键时，使用原始预设 ID。

数据包无法附带语言文件。请始终提供 `display_name` 作为可读的后备名称；需要翻译时，另行提供资源包。名称和提示框内容在契约生成时保存在契约上，因此之后的修改只影响新生成的契约。

### 自然掉落资格

预设只有同时满足以下条件，才能自然掉落：

- 它的池存在且未被禁用；
- 它的 `weight` 大于 0；
- 它的 `loot_modes` 包含服务器当前的获取模式；
- 服务器处于 `CRAFT_9` 模式时，产出不能是 `writ:chaos_writ`。在 `CRAFT_9` 模式下，出售混沌契约的预设永远不会掉落。

不满足这些条件的预设属于 *仅手动* 预设。它仍会加载，任何记载其 ID 的交易契约也照常可用（见[测试预设](#测试预设)）。

### 无效预设

预设的任何字段无效，包括其变体中的任何字段，整个预设都会被丢弃，并记录错误。记载了被丢弃预设 ID 的契约会被拒绝，提示“此契约记载的交易在服务器上配置有误”。记载了根本不存在的 ID 的契约会被拒绝，提示“服务器上没有此契约记载的交易”。

## 变体

变体把一个预设变成一组有序的选项，玩家可以在契约上循环切换。预设本身的交易始终是第一个选项，已启用的变体按数组顺序排在后面。

### 变体字段

| 字段 | 类型 | 必需 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | 字符串 | 是 | — | 只能使用小写字母、数字和 `_`，最多 128 个字符。不能是 `default`，并且在预设内（包括已停用的变体）必须唯一。 |
| `label` | 非空字符串 | 否 | 无 | 选项的纯文本名称。 |
| `translation_key` | 非空字符串 | 否 | 无 | 选项的语言键。客户端没有该键时，依次使用 `label`、`id`。 |
| `enabled` | 布尔值 | 否 | `true` | 为 `false` 时隐藏此变体，但仍会校验。 |
| `max_uses` | 整数 > 0 | 否 | 预设的值 | 覆盖 `max_uses`。 |
| `trade` | 对象 | 否 | 预设的值 | 只能包含 `cost_a`、`cost_b` 和 `result`。 |

其他规则：

- 每个预设最多 64 个变体条目，已停用的也计算在内。
- 变体及其 `trade` 对象中出现上表以外的字段都会报错。
- 变体中的物品堆叠必须显式写出整数 `count`。
- 产出为 `writ:chaos_writ` 的预设不能有变体。
- 选项既没有语言键也没有标签时，以其产出物品命名。

### 继承

| 属性 | 变体的行为 |
|---|---|
| `cost_a`、`result` | 未在 `trade` 中给出时继承。 |
| `cost_b` | 未在 `trade` 中给出时继承。`"cost_b": null` 会移除第二支付物。 |
| `max_uses` | 未给出时继承。 |
| `villager_xp`、`price_multiplier` | 始终使用预设的值。 |
| `pool`、`weight`、`sources`、`allowed_professions`、`loot_modes`、显示名称 | 始终使用预设的值。变体不会改变掉落位置，也不会改变谁能签约。 |

### 示例

```json
{
  "pool": "r",
  "weight": 2,
  "sources": ["desert_pyramid", "simple_dungeon"],
  "allowed_professions": ["minecraft:mason"],
  "display_name": "玻璃供货",
  "default_variant_label": "直接购买",
  "trade": {
    "cost_a": { "item": "minecraft:emerald", "count": 5 },
    "result": { "item": "minecraft:glass", "count": 16 },
    "max_uses": 6,
    "villager_xp": 2,
    "price_multiplier": 0.05
  },
  "variants": [
    {
      "id": "smelt_sand",
      "label": "自备沙子烧制",
      "max_uses": 24,
      "trade": {
        "cost_a": { "item": "minecraft:emerald", "count": 1 },
        "cost_b": { "item": "minecraft:sand", "count": 16 },
        "result": { "item": "minecraft:glass", "count": 16 }
      }
    }
  ]
}
```

### 之后修改变体

契约通过变体的 `id` 记住所选选项：

- **调整变体顺序** 是安全的。
- **删除、停用或重命名** 变体，会使选中它的契约失效。对村民使用这类契约时会被拒绝，提示“此契约的选择已失效”；玩家切换这张契约时，它会重置为默认选项。
- 如果预设的变体全部被移除，切换一张仍带有旧选项的契约会清除该选项。
- 新生成的契约总是选中默认选项。

## 获取来源

### 契约如何抽取

每次箱子尝试和每次首领尝试都按相同的步骤进行：

1. **概率。** 尝试以 `contract_drop_chance` 的概率成功；失败时不掉落任何东西。
2. **候选。** 收集[有自然掉落资格](#自然掉落资格)且允许在此来源掉落的预设（见[来源限制](#来源限制)）。
3. **池。** 按[各池在此来源的有效权重](#来源中的池权重)抽取一个池。只有有效权重为正 **并且** 至少有一个候选的池才参与，因此没有候选的池不会稀释抽取。
4. **预设。** 在该池的候选中按预设 `weight` 抽取一个预设。
5. 生成契约，默认选中默认选项。如果第 3 步没有符合条件的池，就不掉落任何东西。

### 来源中的池权重

一个池在某个来源中的有效权重按以下方式确定：

- 如果该池列在来源的 `pool_weights` 中，使用列出的值。
- 否则，如果 `inherit_global_weights` 为 `true`（默认），使用池自身的全局 `weight`。
- 否则有效权重为 0。

希望某个来源只抽取它列出的池时，把 `inherit_global_weights` 设为 `false`。所有内置来源都是这样设置的。

### 来源限制

预设的 `sources` 列出允许它掉落的来源键。来源键是来源文件的 ID 去掉 `loot/sources/` 或 `loot/bosses/` 文件夹后的部分：

| 来源文件 | 键 |
|---|---|
| `data/writ/writs/loot/sources/nether_bridge.json` | `writ:nether_bridge` |
| `data/writ/writs/loot/bosses/warden.json` | `writ:warden` |
| `data/example/writs/loot/sources/plains_houses.json` | `example:plains_houses` |

- 省略 `sources` 时，**所有** 给该预设所在池正权重的来源都可能掉落它，内置来源也包括在内。请使用 `sources` 防止自定义预设出现在内置结构中。
- 引用了不存在或未启用来源的键只会产生警告，只是该预设永远不会在那里掉落。
- 箱子来源和首领来源共用同一套键。同一命名空间中，不要让箱子来源文件和首领来源文件同名。

内置来源键：`writ:abandoned_mineshaft`、`writ:ancient_city`、`writ:bastion`、`writ:bastion_treasure`、`writ:buried_treasure`、`writ:desert_pyramid`、`writ:end_city`、`writ:nether_bridge`、`writ:pillager_outpost`、`writ:shipwreck`、`writ:simple_dungeon`、`writ:stronghold`、`writ:underwater_ruin`、`writ:woodland_mansion`，以及首领 `writ:elder_guardian`、`writ:ender_dragon`、`writ:warden`、`writ:wither`。

### 箱子来源

文件：`data/<namespace>/writs/loot/sources/<name>.json`

| 字段 | 类型 | 必需 | 默认值 | 说明 |
|---|---|---|---|---|
| `loot_table` | 战利品表 ID | 二选一 | — | 单个战利品表。 |
| `loot_tables` | 战利品表 ID 数组 | 二选一 | — | 共用本文件设置的多个战利品表。数组必须非空且没有重复项。 |
| `contract_drop_chance` | 0 到 1 之间的数字 | 是 | — | 每次尝试的概率。 |
| `pool_weights` | 对象：池 ID → 整数 ≥ 0 | 否 | `{}` | 此来源中各池的权重。键可以省略 `writ` 命名空间，每个键都必须是已存在的池。 |
| `inherit_global_weights` | 布尔值 | 否 | `true` | 未列出的池是否使用其全局权重。 |

`loot_table` 和 `loot_tables` 必须有且只有一个。

```json
{
  "loot_tables": [
    "minecraft:chests/stronghold_library",
    "minecraft:chests/stronghold_corridor"
  ],
  "contract_drop_chance": 0.1,
  "inherit_global_weights": false,
  "pool_weights": { "r": 40, "sr": 40, "ssr": 20 }
}
```

#### 两次尝试

列出的战利品表每次生成战利品时，商契都会进行 **两次独立的尝试**，每次尝试分别进行概率判定、池抽取和预设抽取。因此一次生成可能产出 0、1 或 2 张契约，两张还可能完全相同。设概率为 `p`：

- 至少得到一张契约的概率为 `1 − (1 − p)²`，例如 0.39 → 约 63%；
- 期望得到的契约数为 `2p`。

只有实际被生成的那个战利品表才算数。其他战利品表通过 `minecraft:loot_table` 条目引用的表不会单独触发尝试。

#### 校验

来源文件中的每个战利品表单独校验：

- 战利品表必须以 JSON 文件的形式存在于已加载的数据中（`data/<ns>/loot_tables/<path>.json`）。未知的表会被禁用并报告。
- **一个战利品表只能被一个来源文件认领。** 如果两个或更多文件列出了同一个表，该表在所有这些文件中都会被禁用。要修改内置结构，请在原路径覆盖内置文件，不要为同一个表再添加第二个文件。
- `contract_drop_chance` 无效、`pool_weights` 中有未知的池或权重为负，都会禁用该文件的战利品表。
- 文件完全无法解析时，它仍会认领能读出的战利品表，这些表都会被禁用。

#### 自定义和其他模组的战利品表

任何以数据文件形式存在的战利品表都可以列出，包括你自己数据包中的表和其他模组的表，例如 `somemod:chests/tower`。商契在战利品表原有掉落之外追加契约，不会修改战利品表本身。来源是为箱子战利品表设计的。不以数据文件形式存在的战利品表无法被识别，会被禁用。会替换容器战利品生成方式的模组尚未验证。

### 首领来源

文件：`data/<namespace>/writs/loot/bosses/<name>.json`

| 字段 | 类型 | 必需 | 默认值 | 说明 |
|---|---|---|---|---|
| `entity` | 实体类型 ID | 是 | — | 已注册的实体类型，每个文件只能有一个实体。不支持 `entities` 字段，写了会报错。 |
| `contract_drop_chance` | 0 到 1 之间的数字 | 是 | — | 每次死亡的概率。 |
| `pool_weights` | 对象：池 ID → 整数 ≥ 0 | 否 | `{}` | 同箱子来源。 |
| `inherit_global_weights` | 布尔值 | 否 | `true` | 同箱子来源。 |

```json
{
  "entity": "minecraft:evoker",
  "contract_drop_chance": 0.1,
  "inherit_global_weights": false,
  "pool_weights": { "sr": 1 }
}
```

行为：

- 虽然文件夹名为 bosses，但可以使用任何生物实体类型。
- **每次死亡只尝试一次**，每个实体最多给出一张契约。
- 契约交给获得击杀归属的玩家：放入其物品栏；物品栏已满时掉落在其脚下。如果获得击杀归属的不是真实在线的玩家（例如模组的假玩家），或者 `doMobLoot` 游戏规则为 `false`，则不给出契约。
- 校验规则与箱子来源相同。未注册的实体会被禁用；多个首领文件指定同一实体时，该实体在所有这些文件中都会被禁用。

## 混沌配置

文件：`data/writ/writs/chaos/config.json`

只读取 `writ` 命名空间中的这个路径。其他命名空间中的 `chaos/config.json` 会被忽略，并记录警告。要修改配置，请在数据包中覆盖这个文件。内置文件不排除任何物品：

```json
{
  "exclude": []
}
```

### 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `exclude` | 条目数组 | `[]` | 永远不会被抽到，无论作为支付物还是产出。 |
| `payment_allow` | 条目数组 | 全部物品 | 提供时，支付物只从这些条目中抽取。 |
| `result_allow` | 条目数组 | 全部物品 | 提供时，产出只从这些条目中抽取。 |

每个条目都是一个字符串：物品 ID（如 `"minecraft:bedrock"`），或以 `#` 开头的物品标签（如 `"#minecraft:logs"`）。标签按本次重载加载的物品标签展开。

### 列表的构建方式

支付物列表和产出列表分别按以下三步构建：

1. 有允许列表时从允许列表开始；否则从所有已注册物品开始，包括其他模组的物品。
2. 移除固定的 **硬性禁止** 物品。这些物品总是被移除，无法重新允许：`minecraft:air`、`minecraft:command_block`、`minecraft:chain_command_block`、`minecraft:repeating_command_block`、`minecraft:command_block_minecart`、`writ:trade_writ` 和 `writ:chaos_writ`。
3. 移除 `exclude` 中的所有条目。

之后，每项混沌交易独立、均匀地抽取一个支付物和一个产出。

### 校验

- 未知的物品或标签只会产生警告，该条目被跳过。
- 文件格式错误时（例如某个字段不是数组，或某个条目不是字符串），整个配置被拒绝，**两个列表都变为空**。
- 任一列表为空时，混沌契约会被拒绝，提示“当前没有可用的混沌交易”。因此 `"payment_allow": []` 这样的空允许列表可以用来关闭混沌交易。

### 示例

```json
{
  "exclude": [
    "minecraft:bedrock",
    "minecraft:barrier",
    "minecraft:structure_block",
    "minecraft:structure_void",
    "minecraft:jigsaw",
    "minecraft:light",
    "minecraft:debug_stick",
    "minecraft:knowledge_book",
    "minecraft:spawner",
    "minecraft:end_portal_frame",
    "minecraft:reinforced_deepslate",
    "minecraft:petrified_oak_slab"
  ],
  "payment_allow": ["#minecraft:logs", "#minecraft:wool", "minecraft:emerald"]
}
```

在这个示例中，支付物总是原木、羊毛或绿宝石；产出可以是除被排除物品和硬性禁止物品之外的任何物品。

## 服务器配置

服务器设置位于每个存档文件夹内的 `serverconfig/writ-server.toml`。Forge 在存档首次加载时创建此文件，也会把游戏目录中的 `defaultconfigs/writ-server.toml` 复制到新存档中。

该文件控制数据包无法控制的行为：

- 三项每位村民的契约上限；
- 混沌契约的获取模式（`TRADE` 或 `CRAFT_9`）；
- 混沌交易的可用次数、村民经验和价格乘数。

该文件中的注释是各选项、默认值、取值范围和生效时机的权威说明。获取模式通过两种方式与数据包数据相互作用：

- 它通过各预设的 `loot_modes` 以及[自然掉落资格](#自然掉落资格)中关于混沌契约的规则，决定哪些预设会自然掉落。
- 它决定合成配方 `writ:chaos_writ` 是否存在。该配方是普通的配方文件（`data/writ/recipes/chaos_writ.json`），数据包可以覆盖它，但只要模式不是 `CRAFT_9`，商契就会移除它。

## 完整示例

这个数据包添加一项带两个选项的农民交易，并让它从平原和热带草原村庄的房屋箱子中掉落。

```text
honey_writs/
├── pack.mcmeta
└── data/
    └── example/
        └── writs/
            ├── presets/
            │   └── honey_supply.json
            └── loot/
                └── sources/
                    └── village_houses.json
```

**`pack.mcmeta`**

```json
{
  "pack": {
    "pack_format": 15,
    "description": "Honey trade writs"
  }
}
```

**`data/example/writs/presets/honey_supply.json`** 定义预设 `example:honey_supply`：

```json
{
  "pool": "r",
  "weight": 1,
  "sources": ["example:village_houses"],
  "allowed_professions": ["minecraft:farmer"],
  "display_name": "蜂蜜供货",
  "default_variant_label": "蜂蜜瓶",
  "trade": {
    "cost_a": { "item": "minecraft:emerald", "count": 3 },
    "cost_b": { "item": "minecraft:glass_bottle", "count": 4 },
    "result": { "item": "minecraft:honey_bottle", "count": 4 },
    "max_uses": 8,
    "villager_xp": 2,
    "price_multiplier": 0.05
  },
  "variants": [
    {
      "id": "honeycomb",
      "label": "蜜脾",
      "trade": {
        "cost_b": null,
        "result": { "item": "minecraft:honeycomb", "count": 8 }
      }
    }
  ]
}
```

**`data/example/writs/loot/sources/village_houses.json`** 定义来源 `example:village_houses`：

```json
{
  "loot_tables": [
    "minecraft:chests/village/village_plains_house",
    "minecraft:chests/village/village_savanna_house"
  ],
  "contract_drop_chance": 0.25,
  "inherit_global_weights": false,
  "pool_weights": { "r": 1 }
}
```

各部分如何配合：

- 该预设属于 R 档（`"r"` → `writ:r`），只有农民可以签订。
- 默认选项用 3 个绿宝石和 4 个玻璃瓶换取 4 瓶蜂蜜。`honeycomb` 变体去掉了玻璃瓶（`"cost_b": null`），改为出售 8 个蜜脾，价格和 `max_uses` 沿用预设的值。
- `"sources": ["example:village_houses"]` 让这个预设不会出现在同样设置了 R 档权重的内置结构中。
- 该来源认领了两个没有被任何内置来源使用的村庄房屋战利品表，因此不会冲突。每个房屋箱子生成时尝试两次，每次 25%。
- `inherit_global_weights: false` 且只列出 `r`，意味着这个来源只抽取 R 档。由于没有内置预设在 `sources` 中列出 `example:village_houses`，它只会产出这个预设，以及其他数据包中省略了 `sources` 的 R 档预设。

把这个文件夹（或其 zip 压缩包）放进存档的 `datapacks` 文件夹，然后执行 `/reload`。

## 重载、校验与排错

### 查看日志

每次重载后，商契都会在服务器日志中写入一条摘要，类似这样：

```text
Contract reload: pools=6 (disabled=0), presets=… (natural=…, manual-only=…), sources=14/14 enabled, bosses=4/4 enabled, trade-chain=reachable
```

问题会记录在以下标题下：

| 日志标题 | 含义 |
|---|---|
| `Discarded contract presets` | 无效的预设及各自的原因。 |
| `Contract preset diagnostics` | 已加载但无法自然掉落的预设（未知的池、空的 `loot_modes`）。 |
| `Contract pool diagnostics` | 无效的池文件。 |
| `Natural contract loot disabled: no valid pools` | 没有加载任何池，因此什么都不会掉落。 |
| `Contract loot source diagnostics` | 被禁用的箱子来源：未知或冲突的战利品表、无效的概率或权重。 |
| `Contract boss diagnostics` | 被禁用的首领来源。 |
| `Positive weights reference disabled pools` | 某个来源给已禁用的池设置了权重。 |
| `Contract sources with no positive-weight candidates` | 已启用但永远无法产出契约的来源。 |
| `Preset sources reference no enabled source` | 预设列出的来源键不存在或未启用。 |
| `Natural contract presets unreachable from every enabled source` | 有自然掉落资格、但没有任何来源能产出的预设。 |
| `TRADE acquisition chain unreachable` | 在 `TRADE` 模式下，没有任何来源能掉落出售混沌契约的预设。 |
| `Chaos config diagnostics` | 未知的混沌条目、被忽略的其他命名空间配置，或被拒绝的配置。 |
| `Chaos candidate pool is empty; offers disabled` | 混沌契约无法使用。 |

### 测试预设

任何 `preset_id` 标签指向某个预设的交易契约都可以使用，因此无需寻找战利品即可测试：

```mcfunction
/give @s writ:trade_writ{preset_id:"example:honey_supply"}
```

这样创建的契约没有保存预览，提示框显示“无法预览此交易”，名称按[显示名称](#显示名称)中的顺序后备。对于有变体的预设，切换一次选项就会保存预览。设置同一标签的战利品函数也同样有效。

### 排错

| 现象 | 检查项 |
|---|---|
| 新预设从不掉落 | 日志中它是否被列为已丢弃或无法获得？是否有允许它掉落的来源给它的池设置了权重？`weight` 是否大于 0？`loot_modes` 是否包含当前模式？ |
| 自定义预设出现在原版结构中 | 它省略了 `sources`，因此所有给它的池设置了权重的来源都能掉落它。请添加 `sources`。 |
| 某个内置结构不再掉落契约 | 另一个来源文件列出了同一个战利品表，导致两者都被禁用。请覆盖内置文件，而不是另加一个文件。 |
| 来源报告“unknown loot table” | 检查拼写和命名空间，战利品表必须以数据文件形式存在。 |
| 契约被拒绝，提示“配置有误” | 预设未通过校验，见 `Discarded contract presets`。 |
| 契约被拒绝，提示“没有此契约记载的交易” | 没有加载该 ID 的预设。检查路径、命名空间以及数据包是否已启用。 |
| 提示框显示旧数值 | 提示框内容在契约生成时保存。村民得到的仍是当前定义。 |
| 混沌契约被拒绝 | 查看 `Chaos config diagnostics`。格式错误的配置或空的允许列表会使列表为空。 |
| 修改获取模式没有效果 | 模式只在服务器启动时读取，请重启服务器。 |
