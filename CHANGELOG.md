# Changelog

## Unreleased

- Added German, Spanish (Spain and Latin America), Italian, Japanese, Korean, Polish, Brazilian Portuguese, Russian and Traditional Chinese localizations.
- Reworded several Simplified Chinese and French in-game messages and tooltips.
- Reorganized the documentation; server settings, migration and config recovery are now described in the Gameplay Guide.

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
