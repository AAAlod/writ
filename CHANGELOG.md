# Changelog

## 1.1.3

### Gameplay

- Added the Resonance Focus to the Writ Creative tab. It binds to the first player who uses it. An unconfigured focus applies Writ Exemption to a creature, and its owner can sneak-use the focus to remove an exemption they applied.
- Exempt villagers refuse new writs and keep their existing offers. Server operators can clear exemptions with `/writ exemption clear <target>`.
- Administrators can give a focus an explicit NBT list of status effects. Sneak-use removes the listed effects. The per-world `cooldown_ticks` setting in `serverconfig/writ-resonance-server.toml` defaults to 100 ticks (5 seconds). Set it to 0 to disable the cooldown.

## 1.1.2.1

### Bug fixes

- Added choice names to trade-row tooltips so players can distinguish offers with identical item icons.
- Added missing trade selector translations and clarified why a writ cannot be reopened when its preset or choices are unavailable.
- Datapack reloads now diagnose presets whose choices exceed the selector's capacity and keep them out of natural acquisition. Existing writs can still sign with a currently valid choice.

## 1.1.2

### Gameplay

- Replaced cycling with a searchable trade selector. Use a trade writ in the air to preview payments and output, then confirm a choice for the whole stack.
- Random writs with one valid choice still sign immediately. A multi-choice draw produces a trade writ and opens the selector. Confirming tries to sign with the original villager. Canceling or failing to sign leaves the resulting trade writ for later.
- Added Log Supply and Sapling Supply, with choices generated from item tags, and Saplings for Logs, with explicit sapling-to-log pairs.
- Supported Overworld loot chests, barrels and chest minecarts get two additional general loot attempts, each at 25% by default. Structure-specific attempts retain their existing rates.

### Datapacks and compatibility

- Added `general_loot` for presets and `general_loot_chance` for the server's per-attempt chance.
- Added `dynamic_result`, a shared exclusion tag, optional per-preset exclusion tags, and an ingot supply example datapack.
- Fixed natural drop filtering in `CRAFT_9` mode for presets with enabled Chaos Writ outputs. Added warnings for unknown preset fields and improved acquisition diagnostics.
- Existing signed trades keep their terms. Players can recover a stale choice on an unsigned writ by confirming an available choice. Multiplayer requires updating both the client and server.

## 1.1.1

### Localization

- Added German, Spanish (Spain and Latin America), Italian, Japanese, Korean, Polish, Brazilian Portuguese, Russian and Traditional Chinese localizations.
- Reworded several Simplified Chinese and French in-game messages and tooltips.

### Documentation

- Reorganized the documentation; server settings, migration and config recovery are now described in the Gameplay Guide.
- Corrected the config recovery description: a missing config with no recovery files is recreated with defaults, while a config that cannot be read without a usable backup, or a missing config with unusable recovery files, still refuses startup.

## 1.1.0

### Writs and acquisition

- Added random writs: craft one from four trade writs in any 2×2 or 3×3 arrangement. On signing, an eligible preset is drawn for the villager's profession using global pool weights; enabled variants are chosen uniformly after the preset.
- Profession-based Hero of the Village gifts can be replaced by one random writ (8%) or chaos writ (2%); the remaining 90% keeps the original gift by default. This is independent of the chaos obtain mode.
- Trade and random writs share the normal per-villager limit; chaos and total limits remain separate.
- Tooltips show core information by default and expand usage, notices and item details with Shift.
- Newly signed Chaos Writ Unlock trades have a base price of 32 emeralds; existing villager offers are unchanged.

### Customization

- Moved settings to the shared `config/writ-common.toml`; old per-world `serverconfig/writ-server.toml` files are ignored and settings must be copied manually.
- Chaos trades hard-deny structure blocks, structure voids and jigsaws; barriers, light blocks and debug sticks remain eligible.
- Unverified staging is isolated as `.bak.tmp.orphan` (with numbered suffixes when needed) without replacing a valid backup.
- Configuration backups rotate only on schema upgrades; comment-only edits are not written, and a newer `schema_version` is rejected instead of silently using an older backup.
- If the active config is missing and existing `.bak` / `.bak.tmp` files are unusable, startup now fails instead of silently creating defaults.
- Added `[hero_gifts]` server settings: `enabled`, `random_percent` and `chaos_percent`, read at game or dedicated server process startup; `/reload` and switching worlds do not reread them. Invalid percentage totals disable replacement.

## 1.0.0

First public release of Writ (商契) for Minecraft 1.20.1 and Forge 47.4.10+.

### Writs

- **Trade writ:** carries one predefined trade. Using it on a villager adds that trade to the end of the villager's trade list.
- **Chaos writ:** adds a random trade on use. One random item is the payment and one random item is the result, drawn from every registered item except a fixed deny list and any configured exclusions.
- Villagers accept writs only if they are adults, have a profession, are not nitwits, have traded at least once and are not trading with another player. A refused writ is never consumed, and every refusal explains its reason.
- Per-villager limits for trade writs, chaos writs and writs in total (default 8 / 3 / 10).
- Tooltips show the tier, the eligible professions, the exact trade and the current choice. Hold Shift for enchantments, potion effects and container contents.

### Acquisition

- Writs drop from chests in 13 kinds of structures. Each generated chest makes two independent drop attempts, and every structure has its own tier mix and themed trades.
- The Elder Guardian, Warden, Ender Dragon and Wither can award exclusive writs to the player credited with the kill.
- Chaos writ obtain modes: `TRADE` (a Chaos Writ Unlock writ found in dungeons teaches a villager to sell chaos writs) or `CRAFT_9` (craft one chaos writ from nine trade writs).

### Content

- Five tiers: N Common, R Rare, SR Superior, SSR Precious and UR Legendary, each with its own name color.
- Trades for all 13 vanilla villager professions: bulk supplies, processing services, shulker-box supply kits, enchanted books, named enchanted gear and rare materials.
- Ordered variants: some writs offer several versions of a trade. Cycle forward by using the writ in the air, or backward while sneaking. The choice applies to the whole stack.
- When a villager trade is paid with a container that still holds items, the contents are returned to the player.

### Customization

- Datapack-driven presets, pools, chest sources, boss sources and chaos item lists, validated on every `/reload` with diagnostics in the server log.
- Server configuration (`serverconfig/writ-server.toml`) for the per-villager limits, the chaos writ obtain mode and the parameters of chaos trades.

### Localization

- English, Simplified Chinese and French.
