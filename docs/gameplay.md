[English](gameplay.md) | [简体中文](gameplay.zh-CN.md)

# Writ Gameplay Guide

This guide explains how to find, read and use writs in survival play. It also covers the server settings that change what players experience. For customizing content with datapacks, see the [Datapack Reference](datapacks.md).

## Contents

- [The two writs](#the-two-writs)
- [Getting writs](#getting-writs)
- [Obtain modes](#obtain-modes)
- [Using a writ on a villager](#using-a-writ-on-a-villager)
- [Reading a trade writ](#reading-a-trade-writ)
- [Tiers](#tiers)
- [Choices (variants)](#choices-variants)
- [Chaos writs](#chaos-writs)
- [Content overview](#content-overview)
- [Server settings that affect players](#server-settings-that-affect-players)
- [Common problems](#common-problems)
- [Compatibility and limitations](#compatibility-and-limitations)

## The two writs

Writ adds two items.

| Item | What it does |
|---|---|
| **Trade Writ** | Carries one specific, predefined trade. Using it on a villager adds that trade to the villager's trade list. |
| **Chaos Writ** | Carries no trade. Using it on a villager adds a trade that is drawn at random at that moment: one random item as payment for one random item. |

A trade writ shows you exactly what you will get before you use it. A chaos writ is a gamble: you only learn what the villager will trade after it signs.

Both items appear in the **Writ** creative tab. The trade writ in the creative tab is *blank*: it carries no trade and cannot be applied. Trade writs that carry trades come from loot, from boss kills, or from server commands.

## Getting writs

### Structure chests

Many generated structures can contain trade writs. When a chest from one of these structures generates its loot (normally when it is first opened), Writ makes **two independent attempts** to add a writ. Each attempt uses that structure's drop chance. A single chest can therefore contain no writ, one writ, or two writs, and the two writs may be identical.

Each structure favors certain tiers and certain kinds of trades. A desert pyramid leans toward masonry and desert-themed trades, for example, and a Nether fortress toward Nether materials. See [Structure sources](#structure-sources) for the full list.

### Bosses

Defeating the Elder Guardian, the Ender Dragon, the Warden or the Wither can award a trade writ. With the built-in data each kill has a 30% chance. If the chance succeeds, the writ is given to the player who gets credit for the kill:

- The writ goes directly into that player's inventory. If the inventory is full, it drops at the player's feet.
- Each boss can award at most one writ when it dies.
- Only a real, online player can receive the writ. Kills credited to automated "fake players" from other mods award nothing.
- No writ is awarded when the `doMobLoot` game rule is `false`.

Each boss has its own exclusive set of trades that cannot be found in chests. See [Boss sources](#boss-sources).

### Obtaining chaos writs

How chaos writs are obtained depends on the server's [obtain mode](#obtain-modes):

- **`TRADE` mode (default):** dungeon chests can contain a **Chaos Writ Unlock** trade writ. Any employed villager can sign it. The villager then sells chaos writs for emeralds.
- **`CRAFT_9` mode:** craft one chaos writ from nine trade writs in a full 3×3 crafting grid. Any trade writs can be used.

## Obtain modes

The server owner chooses one obtain mode for chaos writs. The mode is read when the server starts and cannot change while it runs.

| | `TRADE` (default) | `CRAFT_9` |
|---|---|---|
| Chaos Writ Unlock writs in dungeon chests | Yes | No |
| Crafting recipe (9 trade writs → 1 chaos writ) | Not available | Available, and added to every player's recipe book |
| Other trade writs | Unchanged | Unchanged |

Chaos Writ Unlock writs that players already own still work after a switch to `CRAFT_9`. They just stop appearing in new loot.

## Using a writ on a villager

Hold the writ and use it on a villager (right-click by default). If the villager accepts, the following happens:

- The new trade is added to the **end** of the villager's trade list.
- One writ is used up. In Creative mode the writ is not consumed.
- The villager makes an agreeing sound and shows green sparkles, and a message confirms the trade that was added.

Added trades are permanent. They are saved with the villager and behave like the villager's other trades: they run out after their maximum number of uses and restock like ordinary trades. Writ provides no way to remove an added trade.

### Which villagers can sign

A villager must meet all of these conditions:

| Condition | Message if it fails |
|---|---|
| Adult | "This villager is too young to sign a writ" |
| Has a profession | "This villager needs a profession first" |
| Not a nitwit | "Nitwits don't trade" |
| Has traded at least once | "Trade with this villager once before offering a writ" |
| Not trading with another player at the moment | "This villager is busy with another customer" |

A villager that has traded at least once keeps its profession for good. This requirement makes sure that added trades cannot be lost through a later profession change.

Only regular villagers can sign writs. Wandering traders and merchants added by other mods show "Only villagers can sign writs", and the interaction then continues as normal. For a wandering trader, for example, its trade screen opens.

### Profession restrictions

Most trade writs name the professions that can sign them. The tooltip shows this in its **For:** line. If the villager has a different profession, it refuses with "Wrong profession; this writ is for: …". A writ marked **Any profession** can be signed by any employed villager.

### Per-villager limits

Each villager can accept only a limited number of writs. Three limits apply:

| Limit | Default |
|---|---|
| Trade writs per villager | 8 |
| Chaos writs per villager | 3 |
| Writs of either kind per villager | 10 |

When a limit is reached, the villager refuses with a message that names the limit it reached. The server owner can change these numbers (see [Server settings](#server-settings-that-affect-players)).

### When a villager refuses

A writ is **never** consumed when it is refused. For eligibility, profession and limit problems, the villager shakes its head and the reason appears above your hotbar. Other problems, such as a writ whose trade the server no longer knows, are reported with a message only. See [Common problems](#common-problems).

## Reading a trade writ

A trade writ is named after the trade it carries, for example *Trade Writ: Ore Smelting*. The name is colored by its [tier](#tiers). The tooltip shows:

- The **tier**, or the **purpose** for special writs such as Chaos Writ Unlock.
- **For:** the professions that can sign it.
- **The trade itself**, in the form *payment (+ second payment) → result*.
- **"Hold Shift for item details"** when the result has enchantments, potion effects or container contents. Hold Shift to see them.
- The **current choice**, if the writ offers [choices](#choices-variants).

The tooltip shows the trade as it was defined when the writ was created. The villager always receives the trade **as the server currently defines it**. If the server's data has changed since the writ was generated, the trade you receive can differ from the tooltip.

## Tiers

Trade writs come in five tiers. Each tier has its own name color.

| Tier | Name | Color | What to expect |
|---|---|---|---|
| N | Common | White | Everyday bulk goods: building blocks, food, maps, coal. |
| R | Rare | Green | Larger supplies and processing services, such as smelting ore or curing concrete. |
| SR | Superior | Aqua | Enchanted books, potions, armor trims, filled supply kits, single enchanted tools. |
| SSR | Precious | Light purple | Rare materials and high-end enchanted gear: Mending, Heart of the Sea, netherite templates. |
| UR | Legendary | Gold | End-game items: Nether Star, Totem of Undying, Enchanted Golden Apple, top-tier named gear. |

Higher tiers have these traits:

- They are **rarer** and mostly come from dangerous structures and bosses.
- They have **fewer uses** before they run out. Many SSR and UR trades can be used only once or twice before they restock.
- They give the **villager more experience** per trade, so they help villagers level up.

The Chaos Writ Unlock writ is not part of a tier. Its tooltip shows **Purpose: Unlock**.

## Choices (variants)

Some trade writs offer several versions of the same trade. *Terracotta Supply*, for example, offers plain terracotta and all 16 dyed colors. *Glass Supply* offers either buying glass outright or bringing your own sand to have it smelted. A writ with choices shows a line such as **Choice: Blue Terracotta (13/17)** in its tooltip.

To change the choice:

1. Hold the writ and look at **empty space**. There must be no block or entity within reach in front of you, so the sky or a distant view works.
2. **Use** the writ (right-click) to move to the next choice.
3. **Sneak and use** to move to the previous choice.

Choices are ordered. The first choice is the writ's default trade, and cycling wraps around at either end. Each change is confirmed by a message above the hotbar.

**Stacks:** a choice applies to the **whole stack** in your hand. To give writs from one stack different choices, split the stack first and change each part separately. Writs with different choices do not stack together.

When you apply the writ, the villager receives the currently selected choice. If a choice no longer exists on the server, the writ is refused with "This writ's choice is no longer offered". Use it in the air once to reset it to the default choice.

## Chaos writs

When a villager signs a chaos writ, the server draws a trade:

- **Payment:** one random item, quantity 1.
- **Result:** one random item, quantity 1.
- The two items are drawn independently from every item in the game, **including items added by other installed mods**.
- By default a chaos trade can be used 12 times before it needs to restock. It gives the villager no experience. These values are server settings.

These items can never be drawn: air, all command blocks, the command block minecart, and both writ items.

Keep these limitations in mind:

- A chaos trade is not tied to a profession. Any eligible villager can sign one.
- The draw is truly random. The trade can be very good, useless, or impossible to pay for, such as an item that cannot be obtained in survival. Server owners can exclude such items. See the [Datapack Reference](datapacks.md#chaos-configuration).
- Chaos writs count against the villager's chaos limit and total limit.
- If the server's chaos item lists are empty or misconfigured, villagers refuse with "No chaos trades are available; please tell an administrator". The writ is not consumed.

## Content overview

The built-in content changes between versions, so this section describes it in general terms with examples. It is not a full catalog.

### Kinds of trades

- **Supplies:** a fixed amount of a useful item for emeralds, such as bookshelves, rails, ender pearls or golden carrots.
- **Processing services:** you bring raw materials and a few emeralds, and the villager returns the processed form. Examples include raw iron to iron ingots, concrete powder to concrete, logs to stripped logs, and sand to glass.
- **Supply kits:** you pay with emeralds and an **empty** shulker box and receive a filled one, such as an Engineer's Kit with redstone components or an Expedition Kit.
- **Enchanted gear and books:** single enchanted tools, weapons and armor with fixed enchantments, plus enchanted books.
- **Rare materials:** items that are normally hard to farm, such as Echo Shards, Nether Stars, Totems of Undying, Sniffer Eggs and Hearts of the Sea.

If you pay for a kit with a shulker box (or another container) that still holds items, the trade still goes through, and Writ returns the contents to your inventory. Anything that does not fit drops near you. This protection applies to any villager trade that is paid with a container.

### Professions

Built-in trade writs cover all 13 vanilla villager professions. The Chaos Writ Unlock is the only built-in trade writ that every profession can sign.

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

Each structure only offers trades that fit it. A mineshaft gives toolsmith and mining trades, an ocean ruin gives ocean-themed trades, and so on.

### Boss sources

| Boss | Tiers | Example exclusive trades |
|---|---|---|
| Elder Guardian | SR, SSR, UR | Sponge Resupply, Deep Diver, Tidecaller Trident |
| Warden | SR, SSR, UR | Sculk Catalysts, Echo Shard Resupply, Shadow |
| Ender Dragon | SR, SSR, UR | Dragon's Breath Supply, End Crystal Resupply, Skyfall |
| Wither | SSR, UR | Wither Roses, Nether Star, Bulwark, Executioner |

With the built-in data each boss kill has a 30% chance to award a writ.

## Server settings that affect players

Server owners configure Writ in `serverconfig/writ-server.toml` inside the world folder. The comments in that file are the authoritative reference for each option, its default and its valid range. The settings that matter most to players are:

- **Obtain mode** (`obtain_mode` in the `[chaos_writ]` section): `TRADE` or `CRAFT_9`, as described in [Obtain modes](#obtain-modes). A change takes effect only after the server restarts, and players should reconnect afterwards.
- **The three per-villager limits** (`max_normal_added_per_villager`, `max_chaos_added_per_villager`, `max_total_added_per_villager`): how many trade writs, chaos writs and writs in total one villager can accept. A limit of `0` blocks that kind of writ entirely. Lowering a limit never removes trades that were already added. It only stops further writs.
- **Chaos trade parameters** (`max_uses`, `villager_xp`, `price_multiplier` in `[chaos_writ]`): the uses, villager experience and price multiplier of new chaos trades.

Which trades exist, where they drop and how often are controlled by datapacks, not by this file. See the [Datapack Reference](datapacks.md).

## Common problems

**"Trade with this villager once before offering a writ."**
Complete at least one ordinary trade with the villager first. This also locks the villager's profession.

**"Wrong profession; this writ is for: …"**
Find a villager with one of the listed professions, or turn a villager into one by placing the matching job site block before you trade with it for the first time.

**"This villager can't take on any more … writs."**
The villager has reached a limit. Use another villager.

**"This writ is blank; it carries no trade."**
Blank trade writs come from the creative tab and cannot be used. Writs found as loot always carry a trade.

**"This server doesn't know the trade on this writ."**
The trade was removed from the server, or the writ was brought from a world with different data. Keep the writ: it works again if the trade is restored.

**"The trade on this writ is misconfigured on this server."**
The server has a broken definition for this trade. Report it to the server owner.

**"This writ comes from an incompatible version of the mod."**
The writ was created by an incompatible version of Writ and cannot be used here.

**"This villager's writ record is damaged; please tell an administrator."**
The data that Writ stores on this villager is corrupted. Writ does not change this villager until the data is repaired.

**The trade I received differs from the tooltip.**
The server's data changed after the writ was generated. The villager always receives the current definition.

**The tooltip says "No preview available for this trade".**
The writ was probably created with a command and has no stored preview. It still works, and the villager receives the server's trade.

**Using the writ in the air does nothing.**
Make sure that no block or entity is within reach in front of you, and that the writ actually offers choices (its tooltip shows a **Choice:** line). Spectators cannot change choices. Writ must also be installed on your client.

**I can't craft a chaos writ.**
The server is in `TRADE` mode. Look for Chaos Writ Unlock writs in dungeons instead.

**A chaos trade asks for something I can't get.**
Chaos trades are drawn from every item in the game. Ask the server owner to exclude unobtainable items.

## Compatibility and limitations

- Writ must be installed on **both the server and every client**. Players without the mod cannot join a server that runs it.
- Only the vanilla villager accepts writs. Wandering traders and villager-like entities from other mods are not supported. Villager professions added by other mods are recognized, but the built-in writs only target vanilla professions.
- Chaos trades can draw items from any installed mod, including creative-only or technical items, unless the server excludes them.
- Behavior with mods that reroll, reset or replace villager trades, or that change how structure chests generate loot (for example, per-player loot mods), has not been verified.
- Behavior of added trades and writ counters when a villager is turned into a zombie villager and cured has not been verified.
- Every built-in drop chance, price and limit is a starting value that may be tuned in later versions.
