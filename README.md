[English](README.md) | [简体中文](README.zh-CN.md)

<p align="center">
  <img src="docs/icon.png" width="96" height="96" alt="Writ icon">
</p>

<h1 align="center">Writ (商契)</h1>

<p align="center">
  <strong>Find trade writs, then have villagers sign them to learn new trades.</strong>
</p>

Writ is a Minecraft Forge mod that turns villager trading into something you discover. Trade writs are scattered through structure chests and dropped by bosses. Each one carries a specific trade, such as smelting your ore, selling enchanted gear, or restocking rare materials. Hand a writ to a villager and that trade joins the villager's list for good. For the adventurous there is also the chaos writ, which teaches a villager a completely random trade.

## Features

- **Trades as loot.** Writs drop from chests in 13 kinds of structures and from the Elder Guardian, Warden, Ender Dragon and Wither. Each location has its own themed trades, so exploring pays off in new trading options.
- **Five tiers.** Writs range from Common bulk supplies to Legendary end-game trades. Each tier has its own name color, so you can judge a find at a glance.
- **Every profession covered.** All 13 vanilla professions have their own writ trades: processing services, filled supply kits, enchanted books and gear, and rare materials.
- **Choices on one writ.** Some writs offer several versions of a trade, such as every terracotta color. Cycle the choice in your hand before you apply the writ.
- **Random writs.** Craft four trade writs into one random writ, or receive one as a village hero gift. It draws a suitable preset trade when signed.
- **Chaos writs.** These teach a villager a random trade drawn from every item in the game, including items from other mods.
- **Fair limits.** Each villager accepts a limited number of writs, and the server owner can configure the limits.
- **Fully data-driven.** Trades, tiers, drop sources and chaos rules are defined by datapacks, so modpacks and servers can reshape the content.
- **Localized** in English, Simplified Chinese and French.

## Requirements

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 or newer (below 48) |
| Java | 17 |
| Sides | **Required on both server and client.** Players without the mod cannot join a server that runs it. |

## Installation

1. Install Minecraft Forge for 1.20.1 (47.4.10 or newer).
2. Put the Writ `.jar` into the `mods` folder of your game, and of your server if you play multiplayer.
3. Start the game. Writ adds the **Writ** creative tab with all three items.

Server settings are in `config/writ-common.toml` under the game or server root and are shared by all worlds. Restart the game or dedicated server process after editing; `/reload` and switching worlds do not reread this file. The old per-world `serverconfig/writ-server.toml` is ignored; copy any settings you want to keep into the new file manually. Normal starts do not rewrite it; schema upgrades create a `.bak` backup and comment-only edits are not written. If the active config is corrupt or missing and no usable recovery file exists, startup fails: restore a `.bak` or remove the unusable config and recovery files to regenerate defaults. Unverified staging is preserved as `.bak.tmp.orphan` (or with a numbered suffix) without overwriting a valid `.bak`; inspect or remove it manually. A file whose `schema_version` is newer than this Writ version is always rejected; use a matching Writ version or restore an older backup manually instead of silently downgrading.

## Quick start

1. **Find a trade writ.** Open chests in structures such as dungeons, desert pyramids, shipwrecks or Nether fortresses, or defeat a boss.
2. **Read it.** The tooltip shows the tier, the professions that can sign it, and the exact trade.
3. **Pick a choice (optional).** If the writ offers choices, look at empty space and use the writ to cycle forward, or sneak and use it to cycle backward.
4. **Find a villager.** It must be an adult with the right profession, and you must have traded with it at least once.
5. **Apply it.** Use the writ on the villager. The new trade appears at the end of its trade list, and one writ is used up.

Depending on the server's settings, chaos writs can be obtained from a Chaos Writ Unlock trade found in dungeons or by crafting nine trade writs together. Village hero gifts can also yield random or chaos writs independently of that setting.

## Documentation

- [Gameplay Guide](docs/gameplay.md): everything a player needs, including acquisition, tiers, choices, chaos writs, limits and common problems.
- [Datapack Reference](docs/datapacks.md): the full data format for presets, pools, sources and chaos configuration, with a complete example pack.
- [Changelog](CHANGELOG.md)

## Status

Writ is under active development. Drop chances, prices and limits are starting values that may be tuned in later versions. Feedback on balance and compatibility is welcome.

## Feedback and license

Report bugs and suggestions at [GitHub Issues](https://github.com/AAAlod/writ/issues).

Writ is created by kaster12321 and released under the [MIT License](LICENSE).
