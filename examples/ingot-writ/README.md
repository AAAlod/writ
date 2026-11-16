# Selectable ingot writ / 可选金属锭契约

Copy this folder into the world's `datapacks` directory and run `/reload`. The preset `example:ingot_supply` can appear in end-city chests through the existing `writ:end_city` source. Its price is only an example and has not been balanced. The tag includes four vanilla ingots and optionally `#forge:ingots`; modded items appear only when their mods add them to that tag. Public `#writ:contract_exclusions` still applies.

将本文件夹放进存档的 `datapacks` 目录并执行 `/reload`。预置 `example:ingot_supply` 通过现有 `writ:end_city` 来源在末地城箱子中出现。价格仅为示例，未经平衡。Tag 包含四种原版锭，并可选地包含 `#forge:ingots`；模组物品需要由模组加入该 Tag 才会出现。公共 `#writ:contract_exclusions` 仍然生效。

For a dedicated exclusion, create `data/example/tags/items/ingot_exclusions.json` and add `"exclude_tag": "example:ingot_exclusions"` inside `dynamic_result`. / 如需专用排除名单，创建 `data/example/tags/items/ingot_exclusions.json`，并在 `dynamic_result` 中添加 `"exclude_tag": "example:ingot_exclusions"`。
