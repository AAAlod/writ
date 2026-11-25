[English](gameplay.md) | [简体中文](gameplay.zh-CN.md)

# Writ Gameplay Guide

This guide covers finding, reading and using writs in survival, plus the server settings that change what players experience. To customize content with datapacks, see the [Datapack Reference](datapacks.md).

## Contents

- [The three writs](#the-three-writs)
- [Getting writs](#getting-writs)
- [Using a writ on a villager](#using-a-writ-on-a-villager)
- [Reading a trade writ](#reading-a-trade-writ)
- [Tiers](#tiers)
- [Choices](#choices)
- [Random writs](#random-writs)
- [Chaos writs](#chaos-writs)
- [Content overview](#content-overview)
- [Server settings](#server-settings)
- [Common problems](#common-problems)
- [Compatibility and limitations](#compatibility-and-limitations)

## The three writs

| Item | What it does |
|---|---|
| **Trade Writ** | Carries one specific trade. Using it on a villager adds that trade to the villager's trade list. |
| **Random Writ** | Carries no trade. Using it on a villager draws a preset for that villager's profession; a multi-choice preset must be selected before signing. |
| **Chaos Writ** | Carries no trade. When a villager signs it, a trade is rolled on the spot: one random item as payment for one random item. |

A trade writ tells you exactly what you will get. A random writ gives you a fitting trade from the regular pool, you just don't know which. A chaos writ is a gamble on anything in the game.

All three items appear in the **Writ** creative tab. The trade writ there is *blank*: it carries no trade and cannot be applied. Trade writs with trades come from loot, boss kills or server commands.

## Getting writs

### Structure chests

When a chest in one of the supported structures generates its loot (normally when it is first opened), Writ makes **two independent special attempts** to add a trade writ, each with that structure's chance. Structure-specific attempts can produce duplicate writs.

Each structure favors certain tiers and kinds of trades: a desert pyramid leans toward masonry and desert trades, a Nether fortress toward Nether materials. See [Structure sources](#structure-sources).

### General Overworld loot

When a supported Overworld loot chest, barrel or chest minecart generates its loot, it also gets **two independent attempts** to add a general writ, each with a **25%** chance by default. A structure chest can yield writs from both pools, and duplicate writs are possible.

This pool includes **Log Supply**, **Sapling Supply** and **Saplings for Logs**. The first two offer items from the server's log and sapling tags, including compatible modded items. Saplings for Logs uses explicit sapling-to-log pairs.

### Bosses

Killing the Elder Guardian, Ender Dragon, Warden or Wither has a 30% chance (with the built-in data) to award a trade writ to the player credited with the kill:

- The writ goes straight into that player's inventory, or drops at their feet if it is full.
- Each boss awards at most one writ.
- Only a real, online player can receive it. Kills credited to "fake players" from automation mods award nothing.
- Nothing is awarded when the `doMobLoot` game rule is `false`.

Each boss has exclusive trades that never appear in chests. See [Boss sources](#boss-sources).

### Crafting random writs

Four trade writs in any arrangement, in a 2×2 or 3×3 grid, craft one random writ. The inputs can be any trade writs, including blank ones and writs with different trades or choices; the random writ keeps nothing from them. The recipe appears in your recipe book once you pick up a trade writ.

### Hero of the Village gifts

When a villager with a profession throws a gift to a player with Hero of the Village, the gift can be replaced by a writ. By default: 90% the normal gift, 8% a random writ, 2% a chaos writ. If the server currently has no usable random or chaos trades, that share keeps the normal gift. The server can change or disable this (see [Server settings](#server-settings)), and it works the same in both obtain modes.

### Chaos writs and obtain modes

Besides hero gifts, chaos writs come from one of two sources, chosen by the server's obtain mode:

| | `TRADE` (default) | `CRAFT_9` |
|---|---|---|
| **Chaos Writ Unlock** trade writs in dungeon chests | Yes | No |
| Crafting recipe: 9 trade writs → 1 chaos writ (full 3×3 grid, any trade writs) | Not available | Available, and added to every player's recipe book |
| Other trade writs | Unchanged | Unchanged |

**Chaos Writ Unlock** is a trade writ that any employed villager can sign. That villager then sells chaos writs for emeralds. A newly signed unlock trade has a base price of 32 emeralds; demand, reputation and Hero of the Village still change the actual price as with any trade. Unlock trades signed in an earlier version keep their old price.

Unlock writs that players already own still work after a switch to `CRAFT_9`. They just stop appearing in new loot.

## Using a writ on a villager

Hold the writ and use it on a villager (right-click by default). If the villager accepts:

- The new trade is added to the **end** of its trade list.
- One writ is used up (not in Creative mode).
- The villager makes an agreeing sound and shows green particles, and a message above the hotbar names the added trade.

Added trades are permanent. They are saved with the villager, run out after their maximum uses and restock like any other trade. Writ has no way to remove an added trade.

### Which villagers can sign

| Condition | Message if it fails |
|---|---|
| Adult | "This villager is too young to sign a writ" |
| Has a profession | "This villager needs a profession first" |
| Not a nitwit | "Nitwits don't trade" |
| Has traded at least once | "Trade with this villager once before offering a writ" |
| Not trading with another player at the moment | "This villager is busy with another customer" |

The trading requirement exists because a villager that has traded keeps its profession for good, so added trades cannot be lost to a later profession change.

Only regular villagers can sign. Using a writ on a wandering trader or a merchant from another mod shows "Only villagers can sign writs", and the normal interaction continues: a wandering trader still opens its trade screen.

### Profession restrictions

Most trade writs list the professions that can sign them on the tooltip's **For:** line. A villager with another profession refuses with "Wrong profession; this writ is for: …". **Any profession** means any employed villager. Random writs pick a trade that suits the villager, and chaos writs have no profession restriction.

### Per-villager limits

| Limit | Default |
|---|---|
| Trade writs and random writs combined | 8 |
| Chaos writs | 3 |
| All writs combined | 10 |

A villager at a limit refuses with a message naming that limit. The server can change these numbers (see [Server settings](#server-settings)).

### When a villager refuses

A refused writ is **never** consumed. For eligibility, profession and limit problems the villager shakes its head and the reason appears above your hotbar. Other problems, such as a trade the server no longer knows, are reported with a message only. See [Common problems](#common-problems).

## Reading a trade writ

A trade writ is named after its trade, for example *Trade Writ: Ore Smelting*, and the name is colored by its [tier](#tiers). The tooltip shows:

- The **tier**, or the **purpose** for special writs such as Chaos Writ Unlock.
- **For:** the professions that can sign it.
- **The trade**, as *payment (+ second payment) → result*.
- The **current choice**, if the writ offers [choices](#choices).

By default only this core information is shown, with one hint line when there is more. Hold **Shift** to also see enchantments, potion effects or container contents of the result, a usage line, the choice controls, and a line of flavor text. Blank and error lines are always visible. Random and chaos writs show a one-line summary, and Shift adds how to use them.

The tooltip shows the trade as it was when the writ was created. The villager always receives the trade **as the server currently defines it**, so if the server's data has changed since, the result can differ from the tooltip.

## Tiers

Trade writs come in five tiers, each with its own name color.

| Tier | Name | Color | What to expect |
|---|---|---|---|
| N | Common | White | Everyday bulk goods: building blocks, food, maps, coal. |
| R | Rare | Green | Larger supplies and processing services, such as smelting ore or curing concrete. |
| SR | Superior | Aqua | Enchanted books, potions, armor trims, filled supply kits, single enchanted tools. |
| SSR | Precious | Light purple | Rare materials and high-end enchanted gear: Mending, Heart of the Sea, netherite templates. |
| UR | Legendary | Gold | End-game items: Nether Star, Totem of Undying, Enchanted Golden Apple, top-tier named gear. |

Higher tiers are **rarer** and come mostly from dangerous structures and bosses. They also allow **fewer uses** before restocking (many SSR and UR trades allow only one or two), and give the **villager more experience** per trade.

Writ tiers are separate from vanilla item rarity. Chaos Writ Unlock has no tier; its tooltip shows **Purpose: Unlock**.

## Choices

Some trade writs offer several versions of one trade. *Terracotta Supply* offers plain terracotta or any of the 16 dyed colors; *Glass Supply* lets you buy glass outright or bring your own sand to have it smelted. Such a writ shows a line like **Choice: Blue Terracotta (13/17)**.

Hold the writ and use it while looking at **empty space** to open the trade selector. Scroll the list or search by option name, choice ID, or the name or ID of an item in the trade. Click an option to preview its payments and result on the parchment, then click the green check mark to save it. Press Esc to close without saving. The inventory key (E by default) also closes the screen while the search field is unfocused.

A choice applies to the **whole stack** in your hand. To set different choices, split the stack first. Writs with different choices do not stack.

The villager receives the selected choice. If the server removes that choice, use the writ in the air and confirm an available option before signing. If all options are gone, the pack author must restore an eligible option first.

## Random writs

When a random writ is used on an eligible villager, the server draws one preset from the trades that trade writs can carry naturally:

- Only trades the villager's profession can sign are considered.
- Lower tiers are more likely. The draw uses the global tier weights (built-in N 45, R 35, SR 14, SSR 5, UR 1), not any structure's drop chances.
- Boss-exclusive trades and trades that sell writs (such as Chaos Writ Unlock) are never drawn.
- If the preset has one valid choice, the villager signs it immediately. For multiple choices, the draw creates a trade writ and opens the selector. Confirming saves the selection and attempts to sign with the original villager. If you close the screen or signing fails, you can use the resulting writ with any eligible villager later. If the selector cannot open, for example because a full inventory has no slot for the result, pick up or find the resulting writ and use it manually. The number of choices does not affect the preset's chance.

If nothing fits the villager, it refuses with "No random trades are available; please tell an administrator", and the writ is kept. Random writs count toward the same limit as trade writs. The exact rules are in the [Datapack Reference](datapacks.md#random-writ-candidates).

## Chaos writs

When a villager signs a chaos writ, the server rolls a trade:

- **Payment:** one random item, quantity 1.
- **Result:** one random item, quantity 1.
- Both are drawn independently from every item in the game, **including items from other installed mods**.
- By default the trade can be used 12 times before restocking and gives the villager no experience. Both are server settings.

These items can never be drawn: air, all command blocks, the command block minecart, structure blocks, structure voids, jigsaws, and the three writ items. Other technical items, such as barriers, light blocks and debug sticks, can be drawn unless the server excludes them.

Keep in mind:

- Any eligible villager can sign a chaos writ, whatever its profession.
- The draw is truly random. The trade can be a bargain, useless, or impossible to pay for, for example an item that cannot be obtained in survival. Server owners can exclude such items; see [Chaos configuration](datapacks.md#chaos-configuration).
- Chaos writs count toward the villager's chaos limit and total limit.
- If the server's chaos item lists are empty or misconfigured, villagers refuse with "No chaos trades are available; please tell an administrator", and the writ is kept.

## Content overview

The built-in content changes between versions, so this section gives examples rather than a full catalog.

### Kinds of trades

- **Supplies:** a fixed amount of a useful item for emeralds, such as bookshelves, rails, ender pearls or golden carrots.
- **Processing services:** bring raw materials and a few emeralds, and the villager returns the processed form: raw iron to iron ingots, concrete powder to concrete, logs to stripped logs, sand to glass.
- **Supply kits:** pay emeralds and an **empty** shulker box, receive a filled one, such as the Engineer's Kit with redstone components or the Expedition Kit.
- **Enchanted gear and books:** tools, weapons and armor with fixed enchantments, plus enchanted books.
- **Rare materials:** items that are normally hard to farm, such as Echo Shards, Nether Stars, Totems of Undying, Sniffer Eggs and Hearts of the Sea.

If you pay for a kit with a shulker box (or any container) that still holds items, the trade goes through and Writ returns the contents to your inventory; anything that does not fit drops near you. This applies to every villager trade paid with a container.

### Professions

Built-in trade writs cover all 13 vanilla professions. Chaos Writ Unlock is the only built-in trade writ every profession can sign.

| Profession | Example trades |
|---|---|
| Armorer | Armor Trims, Elytra Reinforcement, Bulwark |
| Butcher | Meat Smoking, Froglights, Sniffer Eggs |
| Cartographer | Empty Maps, Eye of Ender Resupply, Heart of the Sea Resupply |
| Cleric | Long potions, Blaze Rod Supply, Echo Shard Resupply, Nether Star |
| Farmer | Composting, Golden Carrot Supply, Enchanted Golden Apple Resupply |
| Fisherman | Cooked Fish Supply, Axolotl Buckets, Sea's Favor Fishing Rod |
| Fletcher | Log Stripping, Expedition Kit, Skyfall |
| Leatherworker | Bookbinding, Leather Tanning, Frost Walker Boots |
| Librarian | Bookshelf Supply, Enchantment books, Mending |
| Mason | Terracotta Supply, Concrete Curing, 16-Color Concrete Palette Kit |
| Shepherd | 16-Color Wool Supply, Banner Patterns, Wither Roses |
| Toolsmith | Ore Smelting, Engineer's Kit, Motherlode |
| Weaponsmith | Anvil Supply, Netherite Upgrade Resupply, Executioner |

### Structure sources

The chance applies to each of the two attempts per chest.

| Structure | Chests | Tiers | Chance per attempt |
|---|---|---|---|
| Dungeon | Dungeon chest | N, R, SR, plus Chaos Writ Unlock in `TRADE` mode | 60% |
| Mineshaft | Mineshaft chest | N, R, SR | 6% |
| Desert Pyramid | Pyramid chests | R, SR | 12% |
| Pillager Outpost | Outpost chest | N, R, SR, SSR | 39% |
| Shipwreck | Supply, map and treasure chests | N, R, SR, SSR | 22% |
| Ocean Ruins | Small and big ruin chests | R, SR | 39% |
| Buried Treasure | Treasure chest | SR, SSR | 39% |
| Stronghold | Library, corridor and crossing chests | N, R, SR, SSR | 10% |
| Woodland Mansion | Mansion chests | N, SR, SSR, UR | 10% |
| Nether Fortress | Fortress chests | R, SR, SSR | 15% |
| Bastion Remnant | Generic, bridge and hoglin stable chests | R, SR, SSR | 10% |
| Bastion Remnant | Treasure room chest | SSR, UR | 39% |
| Ancient City | Ancient City chests | SR, SSR | 5% |
| End City | End City treasure chests | R, SR, SSR, UR | 12% |

Each structure only offers trades that fit it: a mineshaft gives toolsmith and mining trades, an ocean ruin ocean-themed ones, and so on.

### Boss sources

Each kill has a 30% chance to award a writ.

| Boss | Tiers | Example exclusive trades |
|---|---|---|
| Elder Guardian | SR, SSR, UR | Sponge Resupply, Deep Diver, Tidecaller Trident |
| Warden | SR, SSR, UR | Sculk Catalysts, Echo Shard Resupply, Shadow |
| Ender Dragon | SR, SSR, UR | Dragon's Breath Supply, End Crystal Resupply, Skyfall |
| Wither | SSR, UR | Wither Roses, Nether Star, Bulwark, Executioner |

## Server settings

Writ's trade settings live in `config/writ-common.toml` in the game folder (single player) or the server folder. One file applies to every world. Writ creates it with defaults on first start; the comments in the file describe each option.

Writ reads `writ-common.toml` only when the game or dedicated server process starts. `/reload` and switching worlds do not reread it. **Restart the game or server process after editing trade settings.**

| Section | Option | Default | Range | Effect |
|---|---|---|---|---|
| (top) | `general_loot_chance` | 0.25 | 0–1 | Chance per general Overworld container attempt; two attempts per recognized container. |
| (top) | `max_normal_added_per_villager` | 8 | 0–1000000 | Trade writs and random writs one villager can accept together. `0` blocks both. |
| (top) | `max_chaos_added_per_villager` | 3 | 0–1000000 | Chaos writs one villager can accept. `0` blocks them. |
| (top) | `max_total_added_per_villager` | 10 | 0–2000000 | All writs one villager can accept, on top of the two limits above. |
| `[chaos_writ]` | `obtain_mode` | `TRADE` | `TRADE`, `CRAFT_9` | See [Chaos writs and obtain modes](#chaos-writs-and-obtain-modes). Any other value falls back to `TRADE`. |
| `[chaos_writ]` | `max_uses` | 12 | 1 or more | Uses of each new chaos trade before restocking. |
| `[chaos_writ]` | `villager_xp` | 0 | 0 or more | Villager experience per chaos trade. |
| `[chaos_writ]` | `price_multiplier` | 0.05 | 0 or more | How strongly demand and reputation change the price of new chaos trades. |
| `[hero_gifts]` | `enabled` | `true` | | Whether hero gifts can be replaced by writs. |
| `[hero_gifts]` | `random_percent` | 8 | 0–100 | Chance that a gift becomes a random writ. |
| `[hero_gifts]` | `chaos_percent` | 2 | 0–100 | Chance that a gift becomes a chaos writ. |

- Limits are checked each time a writ is used. Lowering a limit never removes trades that were already added.
- Chaos trade parameters apply to newly signed chaos trades only; existing trades keep their values.
- If `random_percent` and `chaos_percent` add up to more than 100, gift replacement is disabled and an error is logged.
- Leave `schema_version` alone. Writ uses it to recognize the file format.

Which trades exist, where they drop and how often are set by datapacks, not by this file. See the [Datapack Reference](datapacks.md).

### Upgrading from 1.0.0

Writ 1.0.0 used a per-world `serverconfig/writ-server.toml` (and `defaultconfigs/writ-server.toml`). Writ 1.1.0 and later ignore these files. Copy any settings you changed into `config/writ-common.toml` by hand.

### When the file is rewritten

Writ leaves the file untouched on a normal start. It writes the file only when:

- the file does not exist yet (it is created with defaults);
- a value is missing or out of range (it is corrected and saved);
- a newer Writ version changes the file format. The previous file is kept as `writ-common.toml.bak` first.

Edits that only change comments are never overwritten.

### Backups and recovery

Writ refuses to start when a config file or recovery file exists but cannot be used, rather than silently discarding your settings. The cases, and what to do:

| Situation | What Writ does | What to do |
|---|---|---|
| The file cannot be read (for example, broken TOML), but `.bak` or `.bak.tmp` is usable | Starts with the backup's settings in memory, leaves the broken file as it is, and logs a warning | Fix or replace `writ-common.toml`, then restart |
| The file is missing, but a usable `.bak` or `.bak.tmp` exists | Starts with the backup's settings in memory and logs a warning | Copy the backup to `writ-common.toml`, or delete the backups to get a fresh default file |
| The file cannot be read, or a recovery file exists but cannot be used | Refuses to start | Restore a working `.bak`, or delete the broken config and unusable recovery files so Writ can create defaults |
| The file is missing and no `.bak` or `.bak.tmp` exists | Creates a new file with defaults | Nothing; adjust the settings as needed |
| The file, or a recovery file used when it is missing, has a `schema_version` newer than this Writ version | Refuses to start, and never falls back to an older backup | Use a matching Writ version, or restore an older backup yourself |

`writ-common.toml.bak.tmp` is a backup that was being written when the game stopped. On the next start Writ finishes the job: it becomes the `.bak` if none exists, and is deleted if it matches the existing `.bak`. If it differs from a valid `.bak`, Writ keeps it aside as `writ-common.toml.bak.tmp.orphan` (or `.orphan.1`, `.orphan.2`, …) and never lets it replace the valid backup. Check an orphan file and delete it once you no longer need it.

Every one of these cases is reported in the game or server log.

### Resonance device

[French guide](resonance.fr-FR.md)

Craft one Resonance Focus at a crafting table. Picking up an emerald unlocks the recipe in both acquisition modes. You can also find the focus in the Writ Creative tab.

| Left | Center | Right |
| --- | --- | --- |
| Empty | Copper Ingot | Emerald |
| Empty | Stick | Copper Ingot |
| Leather | Empty | Empty |

The first use binds the focus to you. Once bound, use it on a creature to apply Writ Exemption. Armor stands are unsupported. Exempt villagers refuse new writs and keep their existing trades. Sneak-use to remove an exemption you applied. If you lose the focus, bind a replacement and use it to remove your exemption. Server operators can clear any exemption with `/writ exemption clear <target>`.

The cooldown is configured per world in `<world>/serverconfig/writ-resonance-server.toml`. The `cooldown_ticks` setting defaults to 100 ticks (5 seconds), accepts 0 to 6000, and can be set to 0 to disable the cooldown. Stop the server or close the single-player world before editing, then reopen it to apply the change. To set a default for new worlds, place the file in `defaultconfigs/`.

If Forge reports that it cannot load `writ-resonance-server.toml`, fix the TOML syntax while the world is closed. To regenerate the file, move the damaged file out of `serverconfig/` and reopen the world. If you supplied a template in `defaultconfigs/`, check its syntax too.

Chaos writs exclude the device from both payment and result by default. Pack authors can change this in the [chaos configuration](datapacks.md#chaos-configuration).

## Common problems

**"Trade with this villager once before offering a writ."**
Complete at least one ordinary trade with the villager first. This also locks its profession.

**"Wrong profession; this writ is for: …"**
Find a villager with one of the listed professions, or give a new villager the matching job site block before its first trade.

**"This villager can't take on any more … writs."**
The villager has reached a limit. Use another villager.

**"This writ is blank; it carries no trade."**
Blank trade writs come from the creative tab and cannot be used. Writs found as loot always carry a trade.

**"This server doesn't know the trade on this writ."**
The trade was removed from the server, or the writ came from a world with different data. Keep the writ: it works again if the trade comes back.

**"The trade on this writ is misconfigured on this server."**
The server has a broken definition for this trade. Report it to the server owner.

**"This writ comes from an incompatible version of the mod."**
The writ was created by an incompatible version of Writ and cannot be used here.

**"This villager's writ record is damaged; please tell an administrator."**
The data Writ stores on this villager is corrupted. Writ will not change this villager until the data is repaired.

**"No random trades are available" / "No chaos trades are available".**
The server's data leaves nothing to draw from. Tell the server owner; the writ is kept.

**The trade I received differs from the tooltip.**
The server's data changed after the writ was created. The villager always receives the current definition.

**The tooltip says "No preview available for this trade".**
The writ was probably created with a command and has no stored preview. It still works, and the villager receives the server's trade.

**Using the writ in the air does nothing.**
Look at empty space beyond reach and check that the writ offers another choice or has an outdated choice to recover. The tooltip may reflect older data after a reload. Spectator mode cannot open the selector.

**I can't craft a chaos writ.**
The server is in `TRADE` mode. Look for Chaos Writ Unlock writs in dungeons instead.

**A chaos trade asks for something I can't get.**
Chaos trades can include unobtainable items. The server owner can exclude them in the chaos configuration.

**The game or server refuses to start with a Writ config error.**
See [Backups and recovery](#backups-and-recovery).

## Compatibility and limitations

- Install the same Writ version on **both the server and every client**.
- Only the vanilla villager accepts writs. Wandering traders and villager-like entities from other mods are not supported. Professions added by other mods are recognized, but the built-in trade writs only target vanilla professions.
- Chaos trades can draw eligible items from any installed mod, including creative-only and technical items.
- Behavior with mods that reroll, reset or replace villager trades, or that change how structure chests generate loot (for example per-player loot mods), has not been verified.
- What happens to added trades and writ counts when a villager is turned into a zombie villager and cured has not been verified.
- All built-in drop chances, prices and limits are starting values and may be tuned in later versions.
