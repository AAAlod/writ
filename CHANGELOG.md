# Changelog

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
