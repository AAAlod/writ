package ke.writ;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class DatapackCapabilityGameTests {
    @GameTest(template = "empty")
    public static void chaosChoicesRespectObtainMode(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        boolean craft = Presets.INSTANCE.activeMode() == ObtainMode.CRAFT_9;
        for (String name : java.util.List.of("chaos_variant", "dynamic_chaos")) {
            var id = new ResourceLocation("probe", name);
            var preset = snapshot.presets().get(id);
            h.assertTrue(preset != null, name + " was not loaded");
            h.assertTrue(preset.variants().stream().anyMatch(v -> v.result().is(Writ.CHAOS.get())), name + " has no chaos option");
            h.assertTrue(snapshot.candidates().stream().anyMatch(c -> c.id().equals(id.toString())) != craft, name + " did not respect mode");
            var writ = Presets.INSTANCE.contractFor(snapshot, id.toString());
            h.assertTrue(!writ.isEmpty(), name + " cannot be created manually");
            var villager = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new net.minecraft.core.BlockPos(1, 1, 1));
            Presets.INSTANCE.switchVariant(writ, false);
            var offer = Presets.INSTANCE.offer(writ, villager);
            h.assertTrue(offer != null && offer.getResult().is(Writ.CHAOS.get()), name + " manual chaos option cannot be resolved");
        }
        h.assertTrue(snapshot.candidates().stream().anyMatch(c -> c.id().equals("probe:disabled_chaos_variant")), "disabled variant affected natural eligibility");
        h.assertTrue(snapshot.candidates().stream().anyMatch(c -> c.id().equals("probe:ingots")), "ordinary dynamic preset lost natural eligibility");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void optionalExclusionAndExampleLoad(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var preset = snapshot.presets().get(new ResourceLocation("probe", "ingots"));
        h.assertTrue(preset != null && preset.dynamic() && preset.variants().size() == 1, "optional exclusion preset failed");
        h.assertTrue(preset.result().is(Items.GOLD_INGOT) && preset.variants().get(0).result().is(Items.IRON_INGOT), "tag choices or ordering wrong");
        var example = snapshot.presets().get(new ResourceLocation("example", "ingot_supply"));
        h.assertTrue(example != null && example.dynamic(), "published example did not load");
        h.assertTrue(example.variants().stream().anyMatch(v -> v.result().is(Items.IRON_INGOT)), "example iron option missing");
        var writ = Presets.INSTANCE.contractFor(snapshot, "example:ingot_supply");
        h.assertTrue(!writ.isEmpty(), "example writ could not be created");
        var villager = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new net.minecraft.core.BlockPos(1, 1, 1));
        var seen = new java.util.HashSet<net.minecraft.world.item.Item>();
        for (int i = 0; i < example.variants().size() + 1; i++) {
            var offer = Presets.INSTANCE.offer(writ, villager);
            h.assertTrue(offer != null && seen.add(offer.getResult().getItem()), "example choice could not be resolved uniquely");
            Presets.INSTANCE.switchVariant(writ, false);
        }
        h.assertTrue(seen.containsAll(java.util.Set.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.NETHERITE_INGOT)), "example vanilla ingots missing");
        h.assertTrue(Presets.INSTANCE.offer(writ, villager).getResult().getItem() == example.result().getItem(), "example did not cycle to default");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tradeChainRecognizesVariantAndGeneralPool(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var original = snapshot.presets().get(new ResourceLocation("probe", "chaos_variant"));
        var dynamic = snapshot.presets().get(new ResourceLocation("probe", "dynamic_chaos"));
        var ordinary = snapshot.presets().get(new ResourceLocation("probe", "ingots"));
        var general = new Presets.Preset(original.costA(), original.costB(), original.result(), original.maxUses(), original.xp(), original.multiplier(),
                original.professions(), original.rarity(), original.weight(), original.modes(), original.displayName(), original.displayTranslationKey(),
                original.variants(), original.sources(), original.defaultVariantLabel(), original.defaultVariantTranslationKey(), true, original.dynamic(), original.defaultId());
        var presets = java.util.Map.of(new ResourceLocation("probe", "general"), general, new ResourceLocation("probe", "dynamic"), dynamic,
                new ResourceLocation("probe", "ordinary"), ordinary);
        var candidates = java.util.List.of(new LootRules.Candidate("probe:general", general.rarity(), 1),
                new LootRules.Candidate("probe:dynamic", dynamic.rarity(), 1), new LootRules.Candidate("probe:ordinary", ordinary.rarity(), 1));
        var unlocks = Presets.unlockCandidates(presets, candidates);
        h.assertTrue(unlocks.size() == 2 && unlocks.stream().noneMatch(c -> c.id().equals("probe:ordinary")), "chaos variants not identified correctly");
        h.assertTrue(Presets.generalUnlockReachable(presets, snapshot.pools(), unlocks, 0.25), "general-only unlock should be reachable");
        h.assertFalse(Presets.generalUnlockReachable(presets, snapshot.pools(), unlocks, 0), "zero general chance must not be reachable");
        h.assertFalse(Presets.generalUnlockReachable(presets, java.util.Map.of(), unlocks, 0.25), "missing pool must not be reachable");
        var nonRarity = java.util.Map.of(general.rarity(), new PoolData.Pool(general.rarity(), 1, "purpose", "", ""));
        h.assertFalse(Presets.generalUnlockReachable(presets, nonRarity, unlocks, 0.25), "purpose pool must not be reachable");
        var zero = java.util.Map.of(general.rarity(), new PoolData.Pool(general.rarity(), 0, "rarity", "", ""));
        h.assertFalse(Presets.generalUnlockReachable(presets, zero, unlocks, 0.25), "zero pool weight must not be reachable");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_selectorBudget")
    public static void oversizedSelectorPresetsStayManualOnly(GameTestHelper h) {
        h.runAfterDelay(20, () -> {
            var server = h.getLevel().getServer();
            var id = new ResourceLocation("probe", "selector_budget");
            var file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("datapacks/baseline-tests/data/probe/writs/presets/selector_budget.json");
            try {
                String text = "x".repeat(45 * 1024);
                String nbt = "\"nbt\":{\"budget_test\":\"" + text + "\"}";
                String variants = java.util.stream.IntStream.range(0, 64)
                        .mapToObj(i -> "{\"id\":\"choice_" + i + "\"}")
                        .collect(java.util.stream.Collectors.joining(","));
                String preset = """
                        {"pool":"n","weight":1,"general_loot":true,"loot_modes":["TRADE","CRAFT_9"],
                         "trade":{"cost_a":{"item":"minecraft:emerald","count":1,%1$s},
                         "cost_b":{"item":"minecraft:bone_meal","count":1,%1$s},
                         "result":{"item":"minecraft:iron_ingot","count":1,%1$s},
                         "max_uses":4,"villager_xp":0,"price_multiplier":0.05},
                         "variants":[%2$s]}
                        """.formatted(nbt, variants);
                java.nio.file.Files.writeString(file, preset);
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                    try {
                        if (failure != null) throw new IllegalStateException(failure);
                        var snapshot = Presets.INSTANCE.currentSnapshot();
                        var writ = Presets.INSTANCE.contractFor(snapshot, id.toString());
                        h.assertTrue(snapshot.selectorBlocked().contains(id), "oversized preview was not detected on reload");
                        h.assertFalse(snapshot.candidates().stream().anyMatch(candidate -> candidate.id().equals(id.toString())),
                                "oversized preview was eligible for natural acquisition");
                        h.assertFalse(writ.isEmpty(), "oversized preview must not invalidate the preset");
                        var villager = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new net.minecraft.core.BlockPos(1, 1, 1));
                        h.assertTrue(Presets.INSTANCE.resolveOffer(writ, villager).offer() != null, "valid current trade cannot be signed");
                        TestRestore.finish(h, () -> java.nio.file.Files.deleteIfExists(file),
                                () -> h.assertFalse(Presets.INSTANCE.currentSnapshot().selectorBlocked().contains(id), "removed preset still marked oversized"), null);
                    } catch (Throwable e) {
                        TestRestore.finish(h, () -> java.nio.file.Files.deleteIfExists(file), () -> {}, e);
                    }
                }));
            } catch (Throwable e) {
                TestRestore.finish(h, () -> java.nio.file.Files.deleteIfExists(file), () -> {}, e);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_capabilityReload")
    public static void publicExclusionAndChaosEligibilityReload(GameTestHelper h) {
        h.runAfterDelay(20, () -> {
            var server = h.getLevel().getServer();
            var root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("datapacks/baseline-tests/data");
            var publicTag = root.resolve("writ/tags/items/contract_exclusions.json");
            var chaosTag = root.resolve("probe/tags/items/chaos_items.json");
            var missingPreset = root.resolve("probe/writs/presets/missing_exclusion.json");
            byte[][] old = new byte[3][];
            TestRestore.Action restore = () -> {
                if (old[0] == null) java.nio.file.Files.deleteIfExists(publicTag); else java.nio.file.Files.write(publicTag, old[0]);
                if (old[1] == null) java.nio.file.Files.deleteIfExists(chaosTag); else java.nio.file.Files.write(chaosTag, old[1]);
                if (old[2] == null) java.nio.file.Files.deleteIfExists(missingPreset); else java.nio.file.Files.write(missingPreset, old[2]);
            };
            try {
                old[0] = java.nio.file.Files.exists(publicTag) ? java.nio.file.Files.readAllBytes(publicTag) : null;
                old[1] = java.nio.file.Files.readAllBytes(chaosTag);
                old[2] = java.nio.file.Files.exists(missingPreset) ? java.nio.file.Files.readAllBytes(missingPreset) : null;
                java.nio.file.Files.createDirectories(publicTag.getParent());
                java.nio.file.Files.writeString(publicTag, "{\"replace\":false,\"values\":[\"minecraft:iron_ingot\"]}");
                java.nio.file.Files.writeString(chaosTag, "{\"replace\":true,\"values\":[\"minecraft:gold_ingot\"]}");
                var fixture = java.nio.file.Files.readString(root.resolve("probe/writs/presets/ingots.json"));
                java.nio.file.Files.writeString(missingPreset, fixture.replace("\"tag\": \"probe:ingots\"", "\"tag\": \"probe:ingots\", \"exclude_tag\": \"probe:does_not_exist\""));
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                    try {
                        if (failure != null) throw new IllegalStateException(failure);
                        var snapshot = Presets.INSTANCE.currentSnapshot();
                        h.assertTrue(snapshot.invalidPresets().contains(new ResourceLocation("probe", "missing_exclusion")), "explicit missing exclusion must be rejected");
                        var ingots = snapshot.presets().get(new ResourceLocation("probe", "ingots"));
                        h.assertTrue(ingots != null && ingots.result().is(Items.GOLD_INGOT) && ingots.variants().isEmpty(), "public exclusion ignored without dedicated tag");
                        h.assertTrue(snapshot.candidates().stream().anyMatch(c -> c.id().equals("probe:dynamic_chaos")), "eligibility not restored after chaos removed");
                        TestRestore.finish(h, restore, () -> {}, null);
                    } catch (Throwable e) { TestRestore.finish(h, restore, () -> {}, e); }
                }));
            } catch (Throwable e) { TestRestore.finish(h, restore, () -> {}, e); }
        });
    }
}
