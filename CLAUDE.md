# Writ (商契)

Forge 1.20.1 mod. Players find trade writs in structure chests and from bosses, then have villagers sign them to learn new trades. Code is in `src/main/java/ke/writ`. Built-in content (presets, pools, sources, recipes, loot modifiers) is in `src/main/resources/data/writ`. Player-facing strings are in `src/main/resources/assets/writ/lang`. `en_us` is the source; `zh_cn` and `fr_fr` are maintained by hand, and other locales follow `en_us`.

## Facts come from the implementation

- No document is authoritative, in either language. Before you write or keep a number, default, item list, condition or message, check it against the code, the built-in data or the lang files. Examples: drop chances (`contract_drop_chance` in `data/writ/writs/loot`), pool weights, config keys, defaults and ranges (`Writ.java`), config recovery (`ConfigFile.java`), the hard deny list (`ChaosSampler.HARD_DENY`), tooltip lines (`WritItem.appendHoverText`), random writ candidates (`Presets.randomCandidates`).
- When you quote an in-game message or name, copy it from that language's lang file. If you change a lang value, update every doc that quotes it.
- Keep version-sensitive information (migration notes, changed defaults or prices) in `CHANGELOG.md` and in the one document that owns the topic.
- `scripts/verify-release.ps1` requires certain lang keys to exist. Changing a value is fine. Renaming or removing a key, or changing a placeholder, is a code change.

## Documentation types

Each detailed topic has one owning document. Other documents summarize it in a sentence or two and link to it.

| File | Reader | Owns | Keep out |
|---|---|---|---|
| `README*.md` | Someone deciding whether to install | Pitch, features, requirements, install, quick start | Config details beyond the file name and a link, internal field names |
| `docs/gameplay*.md` | Players and server owners | Getting and using writs, refusal messages, troubleshooting, **all server settings including migration and backup recovery** | Datapack fields, NBT and counter names, full draw algorithms |
| `docs/datapacks*.md` | Datapack and modpack authors | Every data field, validation, exact draw rules, log diagnostics | Gameplay walkthroughs, config option tables |
| `docs/curseforge.md` | CurseForge visitors | Pitch, features, short how-to, links | Long reference material, recovery details |
| `CHANGELOG.md` | Updating users (English only) | What changed per version | Re-explaining whole features |
| Lang files | In-game UI | One short line per key | Explanations that belong in docs |

The CurseForge project title and summary are separate platform fields. Don't repeat them in `docs/curseforge.md`. CurseForge requires English before other languages and links at the bottom. Use absolute GitHub links there. Check rendering in the CurseForge preview before relying on tables or in-page anchors.

## Two languages, written separately

- Write the Chinese docs for Chinese Minecraft players. Don't translate them sentence by sentence from English. Structure, examples and emphasis may differ. Facts (numbers, conditions, names, defaults) must match.
- When a fact changes, update both languages in the same change. When only wording changes, the other language doesn't have to follow.
- README, gameplay guides and CurseForge text should stay approachable and may keep some personality. Reference docs stay precise and plain. Remove translation artifacts, filler, hype and misplaced technical detail. Don't remove personality itself.

## Chinese

MC百科's editing rules and the CFPA translation guide are references, not house rules.

- Vanilla names follow the in-game zh_cn names for **1.20.1** (some names changed later, such as 鳞甲 → 海龟鳞甲). Prefer vanilla terms: 交易列表, 补货, 声望, 盔甲, 村庄英雄, 工作站点方块, 傻子, 首领, 战利品表, 数据包, 提示框, 快捷栏.
- Use full-width Chinese punctuation. Use half-width punctuation only inside code or whole English phrases.
- Don't translate key names (Shift). Name actions rather than default keys (使用, 潜行时使用, 右击). In lang files, show keys through keybind arguments such as `key.use`.
- `zh_cn.json` follows CFPA localization conventions: no spaces between Chinese and Latin letters or digits (`按住Shift`, `16色`). Exceptions kept on purpose: tier labels (`N 普通`) and enchantment levels (`保护 IV`, matching vanilla). Markdown docs use spaces between Chinese and Latin text or numbers. Don't copy either file type's spacing rule into the other.
- Avoid translationese: 一种……的东西, 为……准备的, 具体取决于, 某……分支, 被……所, and long English-shaped sentences joined by semicolons.

## French

`fr_fr.json` uses vanilla 1.20.1 fr_fr names and French typography: a space before `:` and `;`, and `Maj` for Shift. It has no French docs, so keep its in-game meaning in line with `en_us`.

## Other locales

Every locale must have exactly the keys and placeholders of `en_us`. Vanilla names (items, enchantments, dimensions) must match that locale's 1.20.1 language file from Mojang's assets. Follow each locale's vanilla spacing (for example, `zh_tw` keeps a space around Latin text, as vanilla does: `左 Shift`). When you add or change an `en_us` key, update every locale in the same change.

## Terminology

- Mod: Writ / 商契. Items: Trade Writ 交易契约, Random Writ 随机契约, Chaos Writ 混沌契约. "Writ" alone is 契约. Signing is 签约 / 签订.
- Tiers: N 普通, R 稀有, SR 精良, SSR 珍稀, UR 传说. Say 档位 when talking about specific tiers. 契约稀有度 is fine for the system as a whole. Point out where it matters that this is not vanilla item rarity.
- Preset: 预设 in technical docs. Player-facing text usually doesn't need the word; say 交易 instead.
- Variant: 选项 for what players select (UI: 当前选择), 变体 for the datapack concept (`variants`).
- Data states: `disabled` / `enabled: false` are 禁用. A preset with `weight: 0` "不再自然掉落". It is not called disabled.
- Content names (presets, choices, named gear such as 壁垒专精·守护) are intentional. Keep 混沌契约解锁 as the unlock's name and improve the surrounding prose instead. Judge 供货 / 补给 / no suffix per trade name; there is no global rule.
- Obtain modes `TRADE` / `CRAFT_9`, config keys and datapack fields stay in code font and untranslated.

## Style

- Lead with what the reader does or gets. Put conditions and edge cases after the main point.
- One idea per paragraph. Use a list or table only for parallel items. Bold lead-ins on feature bullets are fine. Don't bold for emphasis throughout a paragraph.
- Use two heading levels in normal docs. Long guides and references get a table of contents.
- State limits plainly ("尚未验证" / "has not been verified").
- When you move or shorten text, keep every behavior, constraint and troubleshooting step somewhere. Check the owning document before deleting anything.
