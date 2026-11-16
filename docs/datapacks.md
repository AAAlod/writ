[English](datapacks.md) | [简体中文](datapacks.zh-CN.md)

# Writ Datapack Reference

This reference is for datapack and modpack authors. It describes every data file Writ reads, every supported field, how the files are validated, and how writs are selected when they drop. For the player's view of the same systems, see the [Gameplay Guide](gameplay.md).

## Contents

- [Fundamentals](#fundamentals)
- [Pools](#pools)
- [Presets](#presets)
- [Variants](#variants)
- [Acquisition sources](#acquisition-sources)
- [Chaos configuration](#chaos-configuration)
- [Server configuration](#server-configuration)
- [Complete example](#complete-example)
- [Reloading, validation and troubleshooting](#reloading-validation-and-troubleshooting)

## Fundamentals

### What a datapack can customize

Writ's content is fully data-driven. A datapack can:

- add, change or retire **presets**: the trades that trade writs carry;
- add, change or disable **pools**: the tiers and other groups that presets belong to;
- add or change **chest sources** and **boss sources**: where writs drop and with which pools;
- restrict which items **chaos writs** can draw.

These things are fixed and cannot be changed by data:

- Writs can only be applied to the vanilla `minecraft:villager`. The villager eligibility rules cannot be changed.
- The per-villager limits, the obtain mode and the parameters of chaos trades are [server configuration](#server-configuration), not datapack data.
- A chest source always makes two attempts per generated loot table. A boss source always makes one attempt per death.
- A chaos trade always costs one item and gives one item. The hard-deny list for chaos writs cannot be lifted.
- Only the five built-in tiers (`writ:n`, `writ:r`, `writ:sr`, `writ:ssr`, `writ:ur`) have tier colors. Every other pool is shown in gray.

### Directory structure

Writ reads JSON files under `data/<namespace>/writs/` in every enabled datapack:

```text
data/<namespace>/writs/
├── presets/          trade presets, one per file (subfolders allowed)
├── loot/
│   ├── pools/        pools (tiers and purpose groups)
│   ├── sources/      chest sources
│   └── bosses/       boss sources
└── chaos/
    └── config.json   chaos configuration (read only from the writ namespace)
```

Files elsewhere under `writs/` are ignored.

The file path determines each object's ID:

| File | ID |
|---|---|
| `data/example/writs/presets/honey_supply.json` | preset `example:honey_supply` |
| `data/example/writs/presets/food/bread.json` | preset `example:food/bread` |
| `data/example/writs/loot/pools/mythic.json` | pool `example:mythic` |
| `data/example/writs/loot/sources/plains_houses.json` | source key `example:plains_houses` |
| `data/example/writs/loot/bosses/evoker.json` | source key `example:evoker` |

Wherever a pool ID or source key is written in JSON without a namespace, it means the `writ` namespace. For example, `"r"` means `writ:r` and `"simple_dungeon"` means `writ:simple_dungeon`. Item, entity, profession and loot table IDs without a namespace mean `minecraft`, as usual.

### pack.mcmeta

Minecraft 1.20.1 uses data pack format `15`:

```json
{
  "pack": {
    "pack_format": 15,
    "description": "My Writ content"
  }
}
```

### Pack priority and overriding

Writ follows the normal Minecraft datapack rules:

- When two packs contain a file at the same path, only the file from the higher-priority pack is used. Files are replaced whole and never merged.
- Writ's built-in data lives in the `writ` namespace, for example `data/writ/writs/presets/glass.json`. To change a built-in file, copy it to the same path in your pack and edit the copy. A pack in the world's `datapacks` folder normally has priority over mod data. You can check and change the order with `/datapack list` and `/datapack enable`.
- To add new content, use your own namespace.

You cannot delete a built-in file, but you can neutralize it:

| Goal | Override |
|---|---|
| Stop a built-in preset from dropping | Set its `weight` to `0`. |
| Turn off a built-in chest or boss source | Set its `contract_drop_chance` to `0`. |
| Turn off a whole tier | Add `"disabled": true` to the pool file. |

### Reload behavior

Writ rebuilds all of its data at world load and on every `/reload`. A reload has these effects:

- Pools, presets, sources and the chaos configuration are replaced together.
- **Existing trade writs immediately use the new definitions.** The trade a villager receives is always resolved from the current preset.
- The tooltip of an existing writ does **not** change. It shows the trade as it was when the writ was generated.
- Trades already added to villagers are never changed.
- Chests whose loot has already been generated are not affected.
- The obtain mode is **not** re-read on `/reload` or when switching worlds. Restart the game or dedicated server process to change it.

## Pools

A pool groups presets for natural drops. Writ defines the five tiers and one purpose pool:

| Pool | `weight` | `kind` | Notes |
|---|---|---|---|
| `writ:n` | 45 | `rarity` | Tier N |
| `writ:r` | 35 | `rarity` | Tier R |
| `writ:sr` | 14 | `rarity` | Tier SR |
| `writ:ssr` | 5 | `rarity` | Tier SSR |
| `writ:ur` | 1 | `rarity` | Tier UR |
| `writ:unlock` | 0 | `purpose` | Holds the Chaos Writ Unlock preset |

Every built-in source sets its own pool weights and does not inherit these global weights (see [Pool weights at a source](#pool-weights-at-a-source)). Random writs also use the global weights, independently of source-specific weights.

### Random writ candidates

Random writs need no extra fields; they draw from existing presets. A preset is a candidate for a villager when all of the following hold:

- it is [naturally eligible](#natural-eligibility);
- its pool is an enabled `rarity` pool with a positive global `weight`;
- `allowed_professions` is empty or contains the villager's profession;
- neither its default result nor any enabled variant result is a trade, random or chaos writ;
- it has enabled general loot with a positive general chance, omits `sources`, or at least one listed **chest** source can reach it (positive `contract_drop_chance` and a positive effective weight for its pool). Boss sources alone do not qualify;
- a dynamic preset has at least one valid choice.

The draw picks a pool by global pool weight (built-in N 45 / R 35 / SR 14 / SSR 5 / UR 1), counting only pools that have candidates, then a preset by `weight` within that pool. Source chances and `pool_weights` only decide reachability; they do not change the odds. A single valid choice signs immediately; multiple choices become a trade writ for the player to select. Choices do not make a preset more likely to be drawn.

### Pool fields

File: `data/<namespace>/writs/loot/pools/<name>.json`

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `weight` | integer ≥ 0 | yes | — | Global weight, used by random writs and sources that inherit global weights. It must be written as a plain integer: `5` is valid, `5.0` is not. |
| `kind` | `"rarity"` or `"purpose"` | yes | — | Controls the tooltip. `rarity` shows the pool as a tier line. `purpose` shows "Purpose: *label*". |
| `label` | non-empty string | yes | — | Display text. It is also the fallback when `translation_key` is missing on the client. |
| `translation_key` | string | no | none | Language key for the display text. |
| `disabled` | boolean | no | `false` | When `true`, presets in this pool never drop naturally. |

```json
{
  "weight": 2,
  "kind": "rarity",
  "label": "Mythic",
  "translation_key": "example.pool.mythic"
}
```

### Validation and disabled pools

- An invalid pool file is discarded and reported.
- Presets that name a missing pool still load, but they are excluded from natural drops. A warning is logged.
- A source whose `pool_weights` names a missing pool is disabled. A disabled pool still counts as a known pool, so a source that weights it stays enabled. The disabled pool is simply never selected, and a warning is logged when a positive weight points to it.
- If no valid pool exists at all, natural writ drops are switched off entirely.

## Presets

A preset defines one trade that trade writs can carry.

File: `data/<namespace>/writs/presets/<path>.json`, with ID `<namespace>:<path>`

### Preset fields

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `pool` | pool ID | yes | — | The pool (tier) of this preset. A bare name means the `writ` namespace. |
| `weight` | integer ≥ 0 | yes | — | Weight within the pool. `0` means the preset never drops naturally. |
| `trade` | object | yes | — | The trade. See [Trade object](#trade-object). |
| `allowed_professions` | array of profession IDs | no | any profession | Professions that can sign the writ. Every ID must be a registered villager profession. An empty array also means any profession. |
| `sources` | array of source keys | no | any source | Sources at which the preset can drop. See [Source restrictions](#source-restrictions). If present, the array must be non-empty, contain only strings and contain no duplicates. |
| `loot_modes` | array of `"TRADE"` / `"CRAFT_9"` | no | both | Obtain modes in which the preset drops naturally. An empty array means it never drops naturally, and a warning is logged. |
| `display_name` | non-empty string | no | none | Plain-text name of the trade. |
| `display_translation_key` | string | no | none | Language key for the name of the trade. |
| `default_variant_label` | non-empty string | no | none | Plain-text name of the default choice. Only shown when the preset has variants. |
| `default_variant_translation_key` | non-empty string | no | none | Language key for the default choice. |
| `variants` | array | no | none | Ordered alternative trades. See [Variants](#variants). |

### Trade object

| Field | Type | Required | Description |
|---|---|---|---|
| `cost_a` | item stack | yes | First payment. |
| `cost_b` | item stack or `null` | no | Second payment. Omit it or use `null` for none. |
| `result` | item stack | yes | What the villager sells. |
| `max_uses` | integer > 0 | yes | Uses before the trade is out of stock until the villager restocks. |
| `villager_xp` | integer ≥ 0 | yes | Experience the villager gains per trade. |
| `price_multiplier` | number ≥ 0 | yes | Vanilla price multiplier: how strongly demand and reputation move the price. Vanilla commonly uses `0.05`, and `0.2` for tools and armor. Must be finite. |

### Item stacks

| Field | Type | Required | Description |
|---|---|---|---|
| `item` | item ID | yes | A registered item. Air is not allowed. |
| `count` | integer | yes | From 1 to the item's maximum stack size. For example, a pickaxe allows only 1 and an ender pearl at most 16. |
| `nbt` | SNBT string or JSON object | no | Item NBT. |

`nbt` rules:

- It must describe a compound tag.
- An SNBT string is recommended, because it keeps exact NBT types such as `1b` or `5s`. In the JSON object form, numbers are converted without explicit types.
- It must not contain the keys `preset_id`, `contract_version` or `display_snapshot`.
- Its serialized size must not exceed 65,536 bytes.

```json
{
  "item": "minecraft:diamond_pickaxe",
  "count": 1,
  "nbt": "{Enchantments:[{id:\"minecraft:efficiency\",lvl:5s},{id:\"minecraft:silk_touch\",lvl:1s}]}"
}
```

When any item in the trade has NBT, the writ stores the full item for its tooltip. Players can then hold Shift to read enchantments, potion effects or container contents.

### Container payments

To sell a filled container, use an empty shulker box as `cost_b` and a filled one as `result`, as the built-in kits do. Vanilla trade matching ignores NBT on payments, so a player could pay with a box that still holds items. Writ returns the contents of such a consumed payment to the player. This happens for any villager trade, except one whose cost itself specifies container contents (`BlockEntityTag.Items`).

### Display names

The writ's name is *Trade Writ: …* followed by the name of the trade. The trade name is resolved in this order:

1. `display_translation_key`. If the client has no such key, `display_name` is used, then the preset ID.
2. `display_name`.
3. The language key `<namespace>.preset.<path>`. If the client has no such key, the raw preset ID is used.

A datapack cannot ship language files. Always provide `display_name` as a readable fallback, and ship a resource pack if you need translations. The name and tooltip are stored on each writ when it is generated, so later changes only affect new writs.

### Natural eligibility

A preset can drop naturally only if all of these hold:

- its pool exists and is not disabled;
- its `weight` is greater than 0;
- its `loot_modes` contains the server's current obtain mode;
- the server is not in `CRAFT_9` mode while the preset's result is `writ:chaos_writ`. In `CRAFT_9` mode, presets that sell chaos writs never drop.

A preset that fails these checks is *manual-only*. It still loads, and any trade writ that carries its ID still works (see [Testing a preset](#testing-a-preset)).

### Invalid presets

If any field of a preset is invalid, including any field inside one of its variants, the whole preset is discarded and the error is logged. Writs that carry the ID of a discarded preset are refused with "The trade on this writ is misconfigured on this server". Writs for IDs that do not exist at all are refused with "This server doesn't know the trade on this writ".

## Variants

Variants turn one preset into an ordered list of choices. Players cycle through the choices on the writ. The preset's own trade is always the first choice, and the enabled variants follow in array order.

### Variant fields

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `id` | string | yes | — | Lowercase letters, digits and `_`, at most 128 characters. Must not be `default` and must be unique within the preset, disabled variants included. |
| `label` | non-empty string | no | none | Plain-text name of the choice. |
| `translation_key` | non-empty string | no | none | Language key for the choice. If the client has no such key, `label` is used, then `id`. |
| `enabled` | boolean | no | `true` | `false` hides the variant. It is still validated. |
| `max_uses` | integer > 0 | no | the preset's | Overrides `max_uses`. |
| `trade` | object | no | the preset's | May contain only `cost_a`, `cost_b` and `result`. |

Other rules:

- A preset can have at most 64 variant entries, disabled ones included.
- Any field not listed above is an error, both in the variant and in its `trade` object.
- Item stacks inside a variant must state `count` explicitly as an integer.
- A preset whose result is `writ:chaos_writ` cannot have variants.
- If a choice has neither a translation key nor a label, it is named after its result item.

### Inheritance

| Property | Variant behavior |
|---|---|
| `cost_a`, `result` | Inherited unless given in `trade`. |
| `cost_b` | Inherited unless given in `trade`. `"cost_b": null` removes the second payment. |
| `max_uses` | Inherited unless given. |
| `villager_xp`, `price_multiplier` | Always the preset's values. |
| `pool`, `weight`, `sources`, `allowed_professions`, `loot_modes`, display name | Always the preset's values. Variants do not change what drops or who can sign. |

### Example

```json
{
  "pool": "r",
  "weight": 2,
  "sources": ["desert_pyramid", "simple_dungeon"],
  "allowed_professions": ["minecraft:mason"],
  "display_name": "Glass Supply",
  "default_variant_label": "Buy Outright",
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
      "label": "Smelt Your Sand",
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

### Changing variants later

A writ remembers its choice by the variant `id`:

- **Reordering** variants is safe.
- **Removing, disabling or renaming** a variant makes writs that selected it invalid. Applying such a writ is refused with "This writ's choice is no longer offered". When the player cycles that writ, it resets to the default choice.
- If a preset loses all of its variants, cycling a writ that still has an old choice clears that choice.
- New writs are always generated with the default choice selected.

## Acquisition sources

### How a writ is selected

Every chest attempt and every boss attempt works the same way:

1. **Chance.** The attempt succeeds with probability `contract_drop_chance`. If it fails, nothing drops.
2. **Candidates.** Writ collects the presets that are [naturally eligible](#natural-eligibility) and allowed at this source (see [Source restrictions](#source-restrictions)).
3. **Pool.** A pool is drawn by its [effective weight at this source](#pool-weights-at-a-source). Only pools with a positive effective weight **and** at least one candidate take part. Pools without candidates therefore do not dilute the roll.
4. **Preset.** A preset is drawn from that pool's candidates by preset `weight`.
5. The writ is created with the default choice selected. If no pool qualifies in step 3, nothing drops.

### Pool weights at a source

The effective weight of a pool at a source is determined as follows:

- If the pool is listed in the source's `pool_weights`, the listed value is used.
- Otherwise, if `inherit_global_weights` is `true` (the default), the pool's own global `weight` is used.
- Otherwise the effective weight is 0.

Set `inherit_global_weights` to `false` when you want a source to roll only the pools it lists. All built-in sources do this.

### Source restrictions

A preset's `sources` lists the source keys at which it may drop. A source key is the source file's ID without the `loot/sources/` or `loot/bosses/` folder:

| Source file | Key |
|---|---|
| `data/writ/writs/loot/sources/nether_bridge.json` | `writ:nether_bridge` |
| `data/writ/writs/loot/bosses/warden.json` | `writ:warden` |
| `data/example/writs/loot/sources/plains_houses.json` | `example:plains_houses` |

- If `sources` is omitted, the preset can drop from **every** source that gives its pool a positive weight, including the built-in sources. Use `sources` to keep custom presets out of built-in structures.
- A key that matches no enabled source only causes a warning. The preset just never drops there.
- Chest sources and boss sources share one key space. Do not give a chest source file and a boss source file the same name in the same namespace.

Built-in source keys: `writ:abandoned_mineshaft`, `writ:ancient_city`, `writ:bastion`, `writ:bastion_treasure`, `writ:buried_treasure`, `writ:desert_pyramid`, `writ:end_city`, `writ:nether_bridge`, `writ:pillager_outpost`, `writ:shipwreck`, `writ:simple_dungeon`, `writ:stronghold`, `writ:underwater_ruin`, `writ:woodland_mansion`, and the bosses `writ:elder_guardian`, `writ:ender_dragon`, `writ:warden`, `writ:wither`.

### Chest sources

File: `data/<namespace>/writs/loot/sources/<name>.json`

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `loot_table` | loot table ID | one of the two | — | A single loot table. |
| `loot_tables` | array of loot table IDs | one of the two | — | Several loot tables that share the settings of this file. The array must be non-empty and contain no duplicates. |
| `contract_drop_chance` | number from 0 to 1 | yes | — | Chance of each attempt. |
| `pool_weights` | object: pool ID → integer ≥ 0 | no | `{}` | Per-pool weights at this source. Keys may omit the `writ` namespace. Every key must name an existing pool. |
| `inherit_global_weights` | boolean | no | `true` | Whether unlisted pools use their global weight. |

Exactly one of `loot_table` and `loot_tables` must be present.

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

#### The two attempts

Whenever a listed loot table generates loot, Writ makes **two independent attempts**. Each attempt has its own chance roll, pool roll and preset roll. One generation can therefore yield 0, 1 or 2 writs, and the two writs may be identical. With a chance `p`:

- the chance of at least one writ is `1 − (1 − p)²`, for example 0.39 → about 63%;
- the expected number of writs is `2p`.

Only the loot table that is actually being generated counts. Tables pulled in through `minecraft:loot_table` entries inside another table do not trigger attempts of their own.

#### Validation

Each loot table in a source file is validated separately:

- The loot table must exist as a JSON file in the loaded data (`data/<ns>/loot_tables/<path>.json`). Unknown tables are disabled and reported.
- **A loot table may be claimed by only one source file.** If two or more files list the same table, that table is disabled for all of them. To change a built-in structure, override the built-in file at its path. Do not add a second file for the same table.
- An invalid `contract_drop_chance`, an unknown pool in `pool_weights` or a negative weight disables the file's tables.
- If a file cannot be parsed at all, it still claims its readable tables, and those tables are disabled.

#### Custom and modded loot tables

Any loot table that exists as a data file can be listed, including tables from your own datapack and from other mods, for example `somemod:chests/tower`. Writ adds its writs on top of the table's normal loot and does not modify the table itself. Sources are designed for chest loot tables. Loot tables that are not stored as data files cannot be detected and are disabled. Mods that replace how containers generate loot have not been verified.

### Boss sources

File: `data/<namespace>/writs/loot/bosses/<name>.json`

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity` | entity type ID | yes | — | A registered entity type. Exactly one entity per file. The `entities` field is not supported and is an error. |
| `contract_drop_chance` | number from 0 to 1 | yes | — | Chance per death. |
| `pool_weights` | object: pool ID → integer ≥ 0 | no | `{}` | As for chest sources. |
| `inherit_global_weights` | boolean | no | `true` | As for chest sources. |

```json
{
  "entity": "minecraft:evoker",
  "contract_drop_chance": 0.1,
  "inherit_global_weights": false,
  "pool_weights": { "sr": 1 }
}
```

Behavior:

- Despite the folder name, any living entity type can be used.
- There is **one attempt per death**, and each entity can award at most one writ.
- The writ goes to the player who gets credit for the kill: into their inventory, or dropped at their feet if the inventory is full. No writ is given if the credited killer is not a real online player (for example, a mod's fake player), or if the `doMobLoot` game rule is `false`.
- Validation matches chest sources. An unregistered entity is disabled, and an entity named by more than one boss file is disabled for all of them.

## Chaos configuration

File: `data/writ/writs/chaos/config.json`

Only this exact path in the `writ` namespace is read. Files at `chaos/config.json` in other namespaces are ignored with a warning. To change the configuration, override this file in your datapack. The built-in file excludes nothing:

```json
{
  "exclude": []
}
```

### Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `exclude` | array of entries | `[]` | Never drawn, neither as payment nor as result. |
| `payment_allow` | array of entries | every item | When present, payments are drawn only from these entries. |
| `result_allow` | array of entries | every item | When present, results are drawn only from these entries. |

Each entry is a string: an item ID such as `"minecraft:bedrock"`, or an item tag prefixed with `#`, such as `"#minecraft:logs"`. Tags are expanded with the item tags of the current reload.

### How the lists are built

The payment and result lists are each built in three steps:

1. Start from the allow list if one is given. Otherwise start from every registered item, including items from other mods.
2. Remove the fixed **hard-deny** items. These are always removed and cannot be re-allowed: `minecraft:air`, `minecraft:command_block`, `minecraft:chain_command_block`, `minecraft:repeating_command_block`, `minecraft:command_block_minecart`, `minecraft:structure_block`, `minecraft:structure_void`, `minecraft:jigsaw`, `writ:trade_writ`, `writ:random_writ` and `writ:chaos_writ`.
3. Remove everything in `exclude`.

Each chaos trade then draws one payment and one result, uniformly and independently.

### Validation

- An unknown item or tag only causes a warning. That entry is skipped.
- If the file is malformed, for example a field is not an array or an entry is not a string, the whole configuration is rejected and **both lists become empty**.
- If either list is empty, chaos writs are refused with "No chaos trades are available". An empty allow list such as `"payment_allow": []` is therefore a way to switch chaos trades off.

### Example

```json
{
  "exclude": [
    "minecraft:bedrock",
    "minecraft:barrier",
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

In this example, payments are always logs, wool or an emerald. Results can be any item except the excluded and hard-denied ones.

## Server configuration

Some behavior is set in `config/writ-common.toml` rather than in data: the per-villager limits, the chaos writ obtain mode, the uses, experience and price multiplier of chaos trades, and Hero of the Village gift replacement. The file is read only at game or dedicated server process startup. Every option, its default and range, migration from 1.0.0 and backup recovery are described in [Server settings](gameplay.md#server-settings).

The obtain mode interacts with datapack data in two ways:

- It decides which presets drop naturally, through each preset's `loot_modes` and the chaos-writ rule in [Natural eligibility](#natural-eligibility).
- It decides whether the crafting recipe `writ:chaos_writ` exists. The recipe is an ordinary recipe file (`data/writ/recipes/chaos_writ.json`) that a datapack can override, but Writ removes it whenever the mode is not `CRAFT_9`.

Hero gift replacement has no data fields. It only depends on data in one way: if no preset is a [random writ candidate](#random-writ-candidates), or the chaos lists are empty, the corresponding share of gifts stays vanilla.

## Complete example

This datapack adds one farmer trade with two choices and makes it drop from plains and savanna village houses.

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

**`data/example/writs/presets/honey_supply.json`** defines preset `example:honey_supply`:

```json
{
  "pool": "r",
  "weight": 1,
  "sources": ["example:village_houses"],
  "allowed_professions": ["minecraft:farmer"],
  "display_name": "Honey Supply",
  "default_variant_label": "Honey Bottles",
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
      "label": "Honeycomb",
      "trade": {
        "cost_b": null,
        "result": { "item": "minecraft:honeycomb", "count": 8 }
      }
    }
  ]
}
```

**`data/example/writs/loot/sources/village_houses.json`** defines source `example:village_houses`:

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

How the pieces fit together:

- The preset is in tier R (`"r"` → `writ:r`). Only farmers can sign it.
- Its default choice sells 4 honey bottles for 3 emeralds and 4 glass bottles. The `honeycomb` variant drops the glass bottles (`"cost_b": null`) and sells 8 honeycomb. It keeps the preset's price and `max_uses`.
- `"sources": ["example:village_houses"]` keeps the preset out of the built-in structures, which also weight tier R.
- The source claims two village house loot tables that no built-in source uses, so there is no conflict. Each generated house chest makes two attempts at 25%.
- `inherit_global_weights: false` with only `r` listed means this source rolls tier R only. Because no built-in preset lists `example:village_houses`, it produces only this preset, plus any tier R preset from other packs that omits `sources`.

Install the folder (or a zip of it) in the world's `datapacks` folder and run `/reload`.

## Reloading, validation and troubleshooting

### Reading the log

After every reload Writ writes a summary to the server log, similar to this:

```text
Contract reload: pools=6 (disabled=0), presets=… (natural=…, manual-only=…), sources=14/14 enabled, bosses=4/4 enabled, trade-chain=reachable
```

Problems are logged under these headings:

| Log heading | Meaning |
|---|---|
| `Discarded contract presets` | Invalid presets, with the reason for each. |
| `Contract preset diagnostics` | Presets that loaded but cannot drop naturally (unknown pool, empty `loot_modes`). |
| `Contract pool diagnostics` | Invalid pool files. |
| `Natural contract loot disabled: no valid pools` | No pool loaded, so nothing drops. |
| `Contract loot source diagnostics` | Disabled chest sources: unknown or conflicting loot tables, invalid chances or weights. |
| `Contract boss diagnostics` | Disabled boss sources. |
| `Positive weights reference disabled pools` | A source weights a disabled pool. |
| `Contract sources with no positive-weight candidates` | An enabled source that can never produce a writ. |
| `Preset sources reference no enabled source` | A preset lists a source key that does not exist or is disabled. |
| `Natural contract presets unreachable from every enabled source` | A naturally eligible preset that no source can produce. |
| `TRADE acquisition chain unreachable` | In `TRADE` mode, no source can drop a preset that sells chaos writs. |
| `Chaos config diagnostics` | Unknown chaos entries, an ignored foreign config, or a rejected config. |
| `Chaos candidate pool is empty; offers disabled` | Chaos writs cannot be used. |

### Testing a preset

Any trade writ whose `preset_id` tag names a preset will work, so you can test without finding loot:

```mcfunction
/give @s writ:trade_writ{preset_id:"example:honey_supply"}
```

A writ created this way has no stored preview. Its tooltip says "No preview available for this trade", and the name falls back as described in [Display names](#display-names). For a preset with variants, cycling the writ once stores a preview. Loot functions that set the same tag work the same way.

### Troubleshooting

| Symptom | Check |
|---|---|
| A new preset never drops | Is it listed in the log as discarded or unreachable? Is its pool weighted by a source that it is allowed at? Is `weight` > 0? Does `loot_modes` include the current mode? |
| A custom preset appears in vanilla structures | It omits `sources`, so every source weighting its pool can drop it. Add `sources`. |
| A built-in structure stopped dropping writs | Another source file lists the same loot table, which disables it for both. Override the built-in file instead of adding a second one. |
| A source is "unknown loot table" | Check the spelling and namespace. The table must exist as a data file. |
| Writs are refused as "misconfigured" | The preset failed validation. See `Discarded contract presets`. |
| Writs are refused as "doesn't know the trade" | No preset with that ID is loaded. Check the path and namespace, and that the pack is enabled. |
| The tooltip shows old values | Tooltips are stored when a writ is generated. The villager still receives the current definition. |
| Chaos writs are refused | Check `Chaos config diagnostics`. Malformed config or an empty allow list empties the lists. |
| A change to the obtain mode does nothing | Restart the game or dedicated server process; switching worlds does not reread it. |
## General loot and dynamic results

Set `"general_loot": true` on a preset to opt it into general Overworld container loot. It defaults to `false` for compatibility. Existing `sources` and their special attempts remain in effect; use `"sources": ["writ:general"]` for a general-only preset. Each recognized container makes two general attempts at `general_loot_chance` in `config/writ-common.toml` (default 0.25). Set it to zero to disable general attempts; general-only presets then also leave the random writ candidate pool. Only `rarity` pools participate, using the existing pool and preset weights.

For a dynamic result, for example use `"dynamic_result": {"tag": "minecraft:logs", "exclude_tag": "writ:log_contract_exclusions"}`. The result choices are the item tag's members, excluding both `#writ:contract_exclusions` and the designated exclusion tag. The built-in sapling-specific exclusion is `#writ:sapling_contract_exclusions`. These are ordinary item tags: datapacks can merge them, nest tags, or use `replace`. They are empty by default; pack authors can exclude valuable logs here and create a separately priced explicit preset. Empty or invalid choices do not enter natural or random draws. An unsigned writ whose choice becomes invalid cannot be signed until its choice is changed. Existing signed trades are not rewritten.

`writ:sapling_for_logs` uses the existing explicit `variants` format for sapling-to-log pairs. Datapacks can override or extend those pairs; they are not inferred from tags and do not automatically use dynamic exclusions.

General loot uses the dimension and loot context to approximate chest, barrel and chest-minecart identification. Mod containers can be missed, and other loot generated at a container's position can be misidentified. Unknown preset, `trade`, and `dynamic_result` fields are logged with their locations and ignored, preserving compatibility with extension metadata; correct any misspellings in the log.
