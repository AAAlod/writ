[English](README.md) | [简体中文](README.zh-CN.md)

<p align="center">
  <img src="docs/icon.png" width="96" height="96" alt="Writ icon">
</p>

<h1 align="center">Writ (商契)</h1>

<p align="center">
  <strong>Find trade writs, then have villagers sign them to learn new trades.</strong>
</p>

Writ is a Minecraft Forge mod that turns villager trading into something you discover. Trade writs appear in loot containers and as boss drops. Each one carries a trade, such as smelting your ore, selling enchanted gear or restocking rare materials. Hand a writ to a villager and that trade joins its list for good. For the adventurous, the chaos writ teaches a villager a completely random trade.

## Features

- **Trades as loot.** Writs drop from chests in 13 kinds of structures and from the Elder Guardian, Warden, Ender Dragon and Wither. Supported Overworld loot containers can also contain general writs. Each structure and boss has its own themed trades.
- **Five tiers.** From Common bulk supplies to Legendary end-game trades, each tier has its own name color, so you can judge a find at a glance.
- **Every profession covered.** All 13 vanilla professions get writ trades: processing services, filled supply kits, enchanted books and gear, rare materials.
- **Choices on one writ.** Search and preview the choices on a writ before you sign it. Try different terracotta colors or choose logs and saplings from item tags.
- **Random writs.** Craft four trade writs into one random writ. Use it on a villager to draw a fitting trade. A draw with multiple choices opens the selector before signing.
- **Chaos writs.** These teach a villager a random trade drawn from every item in the game, including items from other mods.
- **Hero of the Village gifts** can occasionally be a random or chaos writ.
- **Per-villager limits** keep any one villager from collecting endless trades. Server owners can adjust them.
- **Data-driven.** Trades, tiers, drop sources and chaos rules are defined by datapacks, so modpacks and servers can reshape the content.
- **Localized** in English, Simplified and Traditional Chinese, French, German, Italian, Japanese, Korean, Polish, Brazilian Portuguese, Russian and Spanish (Spain and Latin America).

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

Settings are in `config/writ-common.toml`, shared by all worlds and read only at startup. Upgrading from 1.0.0? Your old per-world `serverconfig/writ-server.toml` is no longer read. See [Server settings](docs/gameplay.md#server-settings) for every option, migration, and what to do if the file is damaged.

## Quick start

1. **Find a trade writ.** Check Overworld loot chests, barrels and chest minecarts for general writs, or explore structures and defeat bosses for themed writs.
2. **Read it.** The tooltip shows the tier, the professions that can sign it and the exact trade.
3. **Pick a choice (optional).** If the writ offers choices, use it while looking at empty space. Pick a trade in the selector and confirm.
4. **Find a villager.** It must be an adult with the right profession, and you must have traded with it at least once.
5. **Apply it.** Use the writ on the villager. The new trade appears at the end of its trade list, and one writ is used up.

Chaos writs come either from a Chaos Writ Unlock writ found in dungeons or from crafting nine trade writs, depending on the server's setting.

## Documentation

- [Gameplay Guide](docs/gameplay.md): getting and using writs, tiers, choices, random and chaos writs, server settings and common problems.
- [Datapack Reference](docs/datapacks.md): the full data format for presets, pools, sources and chaos configuration, with a complete example pack.
- [Changelog](CHANGELOG.md)

## Status

Writ is under active development. Drop chances, prices and limits are starting values that may be tuned in later versions. Feedback on balance and compatibility is welcome.

## Feedback and license

Report bugs and suggestions at [GitHub Issues](https://github.com/AAAlod/writ/issues).

Writ is created by kaster12321 and released under the [MIT License](LICENSE). The German, Spanish, Italian, Japanese, Korean, Polish, Brazilian Portuguese, Russian and Traditional Chinese translations were contributed by Claude (Anthropic).
