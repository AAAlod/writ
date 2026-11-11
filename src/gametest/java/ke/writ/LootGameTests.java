package ke.writ;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class LootGameTests {
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_nbtPresetReload")
    public static void nbtPresetReload(GameTestHelper helper) {
        helper.runAfterDelay(20, () -> {
            var server = helper.getLevel().getServer();
            Path base = server.getWorldPath(LevelResource.ROOT).resolve("datapacks/baseline-tests/data/mypack/writs");
            Path pool = base.resolve("loot/pools/nbt_test.json"), preset = base.resolve("presets/nbt_test.json"), invalid = base.resolve("presets/nbt_bad.json"), source = base.resolve("loot/sources/nbt_test.json");
            var originals = new java.util.LinkedHashMap<Path, byte[]>();
            TestRestore.Action restore = () -> { for (var entry : originals.entrySet()) { if (entry.getValue() == null) Files.deleteIfExists(entry.getKey()); else Files.write(entry.getKey(), entry.getValue()); } };
            TestRestore.Action verify = () -> helper.assertFalse(Presets.INSTANCE.currentSnapshot().presets().containsKey(new ResourceLocation("mypack", "nbt_test")), "temporary preset not removed");
            try {
                for (Path path : java.util.List.of(pool, preset, invalid, source)) { originals.put(path, Files.exists(path) ? Files.readAllBytes(path) : null); Files.createDirectories(path.getParent()); }
                Files.writeString(pool, "{\"weight\":1,\"kind\":\"rarity\",\"label\":\"NBT\"}");
                String payload = "{\"pool\":\"mypack:nbt_test\",\"weight\":1,\"display_name\":\"Miner\",\"display_translation_key\":\"mypack.preset.miner\",\"trade\":{\"cost_a\":{\"item\":\"minecraft:diamond_pickaxe\",\"count\":1,\"nbt\":{\"Enchantments\":[{\"id\":\"minecraft:efficiency\",\"lvl\":8}],\"display\":{\"Name\":\"{\\\"text\\\":\\\"Mars Miner\\\"}\"}}},\"result\":{\"item\":\"minecraft:bread\",\"count\":1,\"nbt\":{\"custom\":\"yes\"}},\"max_uses\":1,\"villager_xp\":0,\"price_multiplier\":0}}";
                Files.writeString(preset, payload);
                Files.writeString(invalid, payload.replace("\"custom\":\"yes\"", "\"preset_id\":\"forbidden\""));
                var weights = new com.google.gson.JsonObject(); Presets.INSTANCE.currentSnapshot().pools().keySet().forEach(id -> weights.addProperty(id, 0)); weights.addProperty("mypack:nbt_test", 1);
                var json = new com.google.gson.JsonObject(); json.addProperty("loot_table", "writ:chests/test_unlisted"); json.addProperty("contract_drop_chance", 1); json.add("pool_weights", weights); Files.writeString(source, json.toString());
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                    try {
                        if (failure != null) throw new IllegalStateException(failure);
                        var current = Presets.INSTANCE.currentSnapshot();
                        helper.assertTrue(current.invalidPresets().contains(new ResourceLocation("mypack", "nbt_bad")), "reserved NBT preset must be rejected");
                        var loot = generate(helper, "test_unlisted"); helper.assertTrue(loot.size() == 2, "expected two NBT preset contracts");
                        var contract = loot.get(0); var display = contract.getOrCreateTag().getCompound("display_snapshot");
                        helper.assertTrue(display.getInt("format") == 3 && display.getString("preset_name").equals("Miner") && display.getString("preset_translation_key").equals("mypack.preset.miner"), "v3 name snapshot missing");
                        var snapCost = display.getCompound("cost_a").getCompound("tag"); var snapResult = display.getCompound("result").getCompound("tag");
                        helper.assertTrue(snapCost.getList("Enchantments", 10).getCompound(0).getShort("lvl") == 8 && snapCost.getCompound("display").getString("Name").contains("Mars Miner"), "snapshot enchantment/name missing");
                        helper.assertTrue(snapResult.getString("custom").equals("yes"), "result tag missing");
                        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1)); var offer = Presets.INSTANCE.offer(contract, villager);
                        helper.assertTrue(offer != null && offer.getBaseCostA().getTag().equals(snapCost) && offer.getResult().getTag().equals(snapResult), "offer NBT differs from preset snapshot");
                        var ordinary = Presets.INSTANCE.contractFor(current, "writ:example"); helper.assertTrue(ordinary.getOrCreateTag().getCompound("display_snapshot").getInt("format") == 2, "no-NBT preset must remain v2");
                        TestRestore.finish(helper, restore, verify, null);
                    } catch (Throwable e) { TestRestore.finish(helper, restore, verify, e); }
                }));
            } catch (Throwable e) { TestRestore.finish(helper, restore, verify, e); }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_poolReloadCompatibility")
    public static void poolReloadCompatibility(GameTestHelper helper) {
        helper.runAfterDelay(20, () -> {
            var server = helper.getLevel().getServer();
            Path pack = server.getWorldPath(LevelResource.ROOT).resolve("datapacks/baseline-tests/data");
            Path pool = pack.resolve("mypack/writs/loot/pools/building.json");
            Path preset = pack.resolve("mypack/writs/presets/building.json");
            Path source = pack.resolve("mypack/writs/loot/sources/building.json");
            Path replacement = pack.resolve("writ/writs/loot/pools/common.json");
            Path extra = pack.resolve("writ/loot_tables/chests/test_multi_extra.json");
            Path conflict = pack.resolve("mypack/writs/loot/sources/multi_conflict.json");
            Path disableUnlock = pack.resolve("writ/writs/loot/pools/unlock.json");
            var originals = new java.util.LinkedHashMap<Path, byte[]>();
            TestRestore.Action restore = () -> {
                for (var entry : originals.entrySet()) {
                    if (entry.getValue() == null) Files.deleteIfExists(entry.getKey()); else Files.write(entry.getKey(), entry.getValue());
                }
            };
            TestRestore.Action verify = () -> {
                helper.assertFalse(Presets.INSTANCE.currentSnapshot().pools().containsKey("mypack:building"), "temporary pool not removed");
                helper.assertTrue(Presets.INSTANCE.currentSnapshot().rarities().get("writ:common") == 100, "legacy common pool not restored");
            };
            try {
                for (Path path : java.util.List.of(pool, preset, source, replacement, extra, conflict, disableUnlock)) {
                    originals.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
                    Files.createDirectories(path.getParent());
                }
                Files.writeString(pool, "{\"weight\":0,\"kind\":\"purpose\",\"label\":\"BUILD\"}");
                Files.writeString(replacement, "{\"weight\":5,\"kind\":\"rarity\",\"label\":\"override\"}");
                Files.writeString(extra, "{\"type\":\"minecraft:chest\",\"pools\":[]}");
                Files.writeString(preset, "{\"pool\":\"mypack:building\",\"weight\":1,\"trade\":{\"cost_a\":{\"item\":\"minecraft:emerald\",\"count\":1},\"result\":{\"item\":\"minecraft:bread\",\"count\":1},\"max_uses\":1,\"villager_xp\":0,\"price_multiplier\":0}} ");
                var weights = new com.google.gson.JsonObject();
                Presets.INSTANCE.currentSnapshot().pools().keySet().forEach(id -> weights.addProperty(id, 0));
                weights.addProperty("mypack:building", 1);
                var json = new com.google.gson.JsonObject(); var tables = new com.google.gson.JsonArray(); tables.add("writ:chests/test_unlisted"); tables.add("writ:chests/test_multi_extra"); json.add("loot_tables", tables); json.addProperty("contract_drop_chance", 1); json.addProperty("inherit_global_weights", false); json.add("pool_weights", weights);
                Files.writeString(source, json.toString());
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                    try {
                        if (failure != null) throw new IllegalStateException(failure);
                        var snapshot = Presets.INSTANCE.currentSnapshot();
                        helper.assertTrue(snapshot.rarities().get("writ:common") == 5, "per-pool override must win over legacy global");
                        helper.assertTrue(snapshot.pools().containsKey("mypack:building"), "cross-namespace pool missing");
                        var loot = generate(helper, "test_unlisted");
                        helper.assertTrue(loot.size() == 2 && loot.stream().allMatch(s -> s.getOrCreateTag().getString("preset_id").equals("mypack:building")), "two attempts must allow repeated presets");
                        var second = generate(helper, "test_multi_extra");
                        helper.assertTrue(second.size() == 2 && second.get(0).getOrCreateTag().getString("preset_id").equals("mypack:building"), "second table did not produce contracts");
                        helper.assertFalse(snapshot.sources().get("writ:chests/test_multi_extra").inheritGlobalWeights(), "flattened source lost inherit flag");
                        Files.writeString(conflict, "{\"loot_table\":\"writ:chests/test_multi_extra\",\"contract_drop_chance\":1}");
                        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((again, secondFailure) -> server.execute(() -> {
                            try {
                                if (secondFailure != null) throw new IllegalStateException(secondFailure);
                                helper.assertTrue(generate(helper, "test_multi_extra").isEmpty(), "conflicting table must be disabled");
                                helper.assertTrue(generate(helper, "test_unlisted").size() == 2, "unconflicted table should remain enabled");
                                var held = Presets.INSTANCE.contractFor(Presets.INSTANCE.currentSnapshot(), "mypack:building");
                                Files.writeString(pool, "{\"weight\":0,\"kind\":\"purpose\",\"label\":\"BUILD\",\"disabled\":true}");
                                Files.writeString(disableUnlock, "{\"weight\":0,\"kind\":\"purpose\",\"label\":\"UNLOCK\",\"disabled\":true}");
                                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((third, disabledFailure) -> server.execute(() -> {
                                    try {
                                        if (disabledFailure != null) throw new IllegalStateException(disabledFailure);
                                        var disabledSnapshot = Presets.INSTANCE.currentSnapshot();
                                        helper.assertTrue(generate(helper, "test_unlisted").isEmpty(), "disabled pool must not drop despite positive override");
                                        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
                                        helper.assertTrue(Presets.INSTANCE.resolveOffer(held, villager).offer() != null, "held contract must remain usable");
                                        helper.assertTrue(disabledSnapshot.candidates().stream().noneMatch(c -> c.id().equals("writ:a_unlock")), "disabled unlock pool must be excluded");
                                        var sources = new java.util.ArrayList<>(disabledSnapshot.sources().values()); sources.addAll(disabledSnapshot.bosses().values());
                                        var unlocks = disabledSnapshot.candidates().stream().filter(c -> c.id().equals("writ:a_unlock")).toList();
                                        helper.assertFalse(ObtainMode.diagnostics(ObtainMode.TRADE, unlocks, disabledSnapshot.rarities(), sources).isEmpty(), "disabled unlock must make chain unreachable");
                                        TestRestore.finish(helper, restore, verify, null);
                                    } catch (Throwable e) { TestRestore.finish(helper, restore, verify, e); }
                                }));
                            } catch (Throwable e) { TestRestore.finish(helper, restore, verify, e); }
                        }));
                    } catch (Throwable e) { TestRestore.finish(helper, restore, verify, e); }
                }));
            } catch (Throwable e) { TestRestore.finish(helper, restore, verify, e); }
        });
    }
    private static java.util.List<ItemStack> generate(GameTestHelper helper, String table) {
        var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        return helper.getLevel().getServer().getLootData().getLootTable(new ResourceLocation("writ", "chests/" + table)).getRandomItems(params);
    }
    @GameTest(template = "empty")
    public static void heartOfTheSeaContent(GameTestHelper helper) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var preset = snapshot.presets().get(new ResourceLocation("writ:heart_of_the_sea_supply"));
        // A conduit core is worth less than a netherite ingot: emeralds plus diamonds, one per restock.
        helper.assertTrue(preset != null && preset.weight() == 1 && preset.costA().is(net.minecraft.world.item.Items.EMERALD) && preset.costA().getCount() == 32 && preset.costB().is(net.minecraft.world.item.Items.DIAMOND) && preset.costB().getCount() == 4 && preset.result().is(net.minecraft.world.item.Items.HEART_OF_THE_SEA) && preset.maxUses() == 1, "heart of the sea trade incorrect");
        helper.assertTrue(preset.rarity().equals("writ:ssr") && preset.sources().equals(java.util.Set.of("writ:buried_treasure")), "heart must stay a buried-treasure SSR");
        var source = snapshot.sources().get("minecraft:chests/buried_treasure");
        helper.assertTrue(source != null && source.chance() == 0.39 && !source.inheritGlobalWeights() && source.overrides().getOrDefault("writ:ssr", 0) > 0, "missing gated source");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void whitelistAndSnapshot(GameTestHelper helper) {
        var loot = generate(helper, "test_white");
        helper.assertTrue(loot.size() == 2 && loot.stream().allMatch(s -> s.is(Writ.NORMAL.get())), "expected two independent contracts");
        var tag = loot.get(0).getTag();
        helper.assertTrue(tag != null && tag.contains("preset_id", 8) && tag.getInt("contract_version") == 1, "missing contract identity");
        var candidates = Presets.INSTANCE.currentSnapshot().candidates();
        var pools = Presets.INSTANCE.currentSnapshot().pools();
        for (String pool : java.util.List.of("n", "r", "sr", "ssr", "ur", "unlock")) helper.assertTrue(pools.containsKey("writ:" + pool), "missing default pool " + pool);
        helper.assertTrue(candidates.stream().noneMatch(c -> c.id().equals("writ:test_unknown_rarity") || c.id().equals("writ:test_empty_modes")), "unknown rarity or empty modes entered candidates");
        boolean hasUnlock = candidates.stream().anyMatch(c -> c.id().equals("writ:a_unlock"));
        helper.assertTrue(hasUnlock == (Presets.INSTANCE.activeMode() == ObtainMode.TRADE), "unlock candidate must follow startup mode");
        var snapshot = tag.getCompound("display_snapshot");
        helper.assertTrue(snapshot.contains("format", 3) && snapshot.getInt("format") == 2, "expected snapshot format 2");
        helper.assertTrue(snapshot.contains("rarity", 8) && !snapshot.getString("rarity").isEmpty(), "missing rarity");
        helper.assertTrue(snapshot.contains("pool_id", 8) && snapshot.contains("pool_kind", 8) && snapshot.contains("pool_label", 8), "missing pool snapshot fields");
        helper.assertTrue(snapshot.get("professions") instanceof net.minecraft.nbt.ListTag list && (list.isEmpty() || list.getElementType() == 8), "professions must be a string list");
        for (String key : java.util.List.of("cost_a", "result")) {
            var value = snapshot.getCompound(key);
            helper.assertTrue(snapshot.contains(key, 10) && value.contains("id", 8) && !value.getString("id").isEmpty() && value.contains("count", 3) && value.getInt("count") > 0, "invalid snapshot " + key);
        }
        helper.assertFalse(snapshot.contains("name") || snapshot.contains("summary"), "v2 must not contain legacy name or summary");
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        var contract = new ItemStack(Writ.NORMAL.get());
        contract.getOrCreateTag().putString("preset_id", "writ:test_single_mode");
        helper.assertTrue(Presets.INSTANCE.offer(contract, villager) != null, "single-mode preset must remain valid for use");
        contract.getOrCreateTag().putString("preset_id", "writ:test_chaos_result");
        helper.assertTrue(Presets.INSTANCE.offer(contract, villager) != null, "chaos-result preset must remain valid for use");
        contract.getOrCreateTag().putString("preset_id", "writ:test_unknown_rarity");
        helper.assertTrue(Presets.INSTANCE.offer(contract, villager) != null, "unknown rarity preset must remain usable");
        contract.getOrCreateTag().putString("preset_id", "writ:test_empty_modes");
        helper.assertTrue(Presets.INSTANCE.offer(contract, villager) != null, "empty modes preset must remain usable");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nonWhitelist(GameTestHelper helper) {
        helper.assertTrue(generate(helper, "test_unlisted").isEmpty(), "unlisted loot table produced contract"); helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nestedTableSingleOpportunity(GameTestHelper helper) {
        helper.assertTrue(generate(helper, "test_nested").size() == 2, "nested generation must produce exactly two attempts"); helper.succeed();
    }
    @GameTest(template = "empty")
    public static void builtInTradeChainIsReachable(GameTestHelper helper) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var preset = snapshot.presets().get(new ResourceLocation("writ", "a_unlock"));
        var source = snapshot.sources().get("minecraft:chests/simple_dungeon");
        helper.assertTrue(preset != null && preset.result().is(Writ.CHAOS.get()) && preset.modes().contains("TRADE") && source != null, "built-in chain missing");
        var unlock = new LootRules.Candidate("writ:a_unlock", preset.rarity(), preset.weight());
        helper.assertTrue(ObtainMode.diagnostics(ObtainMode.TRADE, java.util.List.of(unlock), snapshot.rarities(), java.util.List.of(source)).isEmpty(), "default chain must be reachable");
        var whitelist = new LootRules.Source("test:whitelist", "test:table", 1, java.util.Map.of("writ:n", 1, "writ:r", 1), false);
        var controlled = java.util.List.of(new LootRules.Candidate("n", "writ:n", 1), new LootRules.Candidate("r", "writ:r", 1), new LootRules.Candidate("unlock", "writ:unlock", 1));
        for (int i = 0; i < 30; i++) {
            String picked = LootRules.select(controlled, snapshot.rarities(), whitelist.overrides(), whitelist.inheritGlobalWeights(), bound -> helper.getLevel().getRandom().nextInt((int) bound)).orElseThrow();
            helper.assertTrue(picked.equals("n") || picked.equals("r"), "whitelist selected an unlisted pool");
        }
        helper.assertFalse(ObtainMode.diagnostics(ObtainMode.TRADE, java.util.List.of(unlock), snapshot.rarities(), java.util.List.of(whitelist)).isEmpty(), "whitelist must make unlock chain unreachable");
        helper.assertTrue(LootRules.select(java.util.List.of(unlock), snapshot.rarities(), source.overrides(), bound -> 0).orElseThrow().equals("writ:a_unlock"), "TRADE source must select unlock");
        helper.assertFalse(LootRules.naturallyEligible(ObtainMode.CRAFT_9, preset.modes(), true), "CRAFT_9 must exclude unlock");
        helper.assertTrue(ObtainMode.diagnostics(ObtainMode.TRADE, java.util.List.of(new LootRules.Candidate(unlock.id(), unlock.rarity(), 0)), snapshot.rarities(), java.util.List.of(source)).size() == 1, "zero weight must warn once");
        helper.assertTrue(ObtainMode.diagnostics(ObtainMode.TRADE, java.util.List.of(unlock), snapshot.rarities(), java.util.List.of(new LootRules.Source(source.resourceId(), source.lootTable(), 0, source.overrides()))).size() == 1, "zero chance must warn once");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 400, batch = "isolated_modeStaysFixedAcrossConfigChangeAndReload")
    @SuppressWarnings("unchecked")
    public static void modeStaysFixedAcrossConfigChangeAndReload(GameTestHelper helper) {
        helper.runAfterDelay(80, () -> {
            try {
                var field = Writ.class.getDeclaredField("OBTAIN_MODE"); field.setAccessible(true);
                var config = (net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String>) field.get(null);
                String old = config.get();
                ObtainMode locked = Presets.INSTANCE.activeMode();
                var server = helper.getLevel().getServer();
                try {
                config.set(locked == ObtainMode.TRADE ? "CRAFT_9" : "TRADE");
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                    try {
                        if (failure != null) throw new IllegalStateException("reload failed", failure);
                        helper.assertTrue(Presets.INSTANCE.activeMode() == locked, "reload changed locked mode");
                        RecipeGameTests.assertMode(helper);
                        boolean unlock = Presets.INSTANCE.currentSnapshot().candidates().stream().anyMatch(c -> c.id().equals("writ:a_unlock"));
                        helper.assertTrue(unlock == (locked == ObtainMode.TRADE), "reload changed candidate mode");
                        TestRestore.finish(helper, () -> config.set(old), () -> { helper.assertTrue(Presets.INSTANCE.activeMode() == locked, "restored mode changed"); RecipeGameTests.assertMode(helper); }, null);
                    } catch (Throwable e) {
                        TestRestore.finish(helper, () -> config.set(old), () -> { helper.assertTrue(Presets.INSTANCE.activeMode() == locked, "restored mode changed"); RecipeGameTests.assertMode(helper); }, e);
                    }
                }));
                } catch (Throwable e) {
                    TestRestore.finish(helper, () -> config.set(old), () -> { helper.assertTrue(Presets.INSTANCE.activeMode() == locked, "restored mode changed"); RecipeGameTests.assertMode(helper); }, e);
                }
            } catch (Exception e) { helper.fail("mode test setup: " + e); }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 400, batch = "isolated_reloadChangesSource")
    public static void reloadChangesSource(GameTestHelper helper) {
        helper.runAfterDelay(20, () -> {
            var server = helper.getLevel().getServer();
            Path file = server.getWorldPath(LevelResource.ROOT).resolve("datapacks/baseline-tests/data/writ/writs/loot/sources/test_reload.json");
            Path global = server.getWorldPath(LevelResource.ROOT).resolve("datapacks/baseline-tests/data/writ/writs/loot/global.json");
            String[] saved = new String[2];
            try {
                helper.assertTrue(generate(helper, "test_reload").size() == 2, "fixture should start enabled");
                String old = Files.readString(file);
                String oldGlobal = Files.readString(global);
                saved[0] = old; saved[1] = oldGlobal;
                Files.writeString(file, old.replace("\"contract_drop_chance\":1", "\"contract_drop_chance\":0"));
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, failure) -> server.execute(() -> {
                    try {
                        Files.writeString(file, old);
                        if (failure != null) throw new IllegalStateException("reload failed", failure);
                        helper.assertTrue(generate(helper, "test_reload").isEmpty(), "reloaded source should stop drops");
                        Files.writeString(global, "{\"rarities\":{\"common\":-1}}");
                        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, secondFailure) -> server.execute(() -> {
                            try {
                                Files.writeString(global, oldGlobal);
                                if (secondFailure != null) throw new IllegalStateException("invalid global reload failed", secondFailure);
                                helper.assertTrue(generate(helper, "test_reload").isEmpty(), "source with unavailable legacy pools must be disabled");
                                helper.assertTrue(Presets.INSTANCE.currentSnapshot().pools().containsKey("writ:n"), "valid pool files must survive invalid legacy global");
                                helper.assertTrue(Presets.INSTANCE.currentSnapshot().sources().containsKey("minecraft:chests/simple_dungeon"), "valid pool source must survive invalid legacy global");
                                var v = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
                                var contract = new ItemStack(Writ.NORMAL.get());
                                contract.getOrCreateTag().putString("preset_id", "writ:example");
                                helper.assertTrue(Presets.INSTANCE.offer(contract, v) != null, "invalid global must not disable valid preset");
                                contract.getOrCreateTag().putString("preset_id", "writ:test_unknown_rarity");
                                helper.assertTrue(Presets.INSTANCE.offer(contract, v) != null, "invalid global must not disable unknown-rarity preset");
                                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((restored, restoreFailure) -> server.execute(() -> {
                                    if (restoreFailure != null) { helper.fail("restore reload failed: " + restoreFailure); return; }
                                    helper.assertTrue(generate(helper, "test_reload").size() == 2, "restored source must produce loot again");
                                    helper.succeed();
                                }));
                            } catch (Throwable e) { TestRestore.finish(helper, () -> { Files.writeString(file, old); Files.writeString(global, oldGlobal); }, () -> helper.assertTrue(generate(helper, "test_reload").size() == 2, "restored source must produce loot again"), e); }
                        }));
                    } catch (Throwable e) { TestRestore.finish(helper, () -> { Files.writeString(file, old); Files.writeString(global, oldGlobal); }, () -> helper.assertTrue(generate(helper, "test_reload").size() == 2, "restored source must produce loot again"), e); }
                }));
            } catch (Throwable e) {
                if (saved[0] == null || saved[1] == null) { helper.fail("reload setup: " + e); return; }
                TestRestore.finish(helper, () -> { Files.writeString(file, saved[0]); Files.writeString(global, saved[1]); }, () -> helper.assertTrue(generate(helper, "test_reload").size() == 2, "restored source must produce loot again"), e);
            }
        });
    }
}
