package ke.writ;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class RandomWritGameTests {
    private static Villager farmer(GameTestHelper h) {
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.setVillagerXp(1);
        return villager;
    }
    private static PlayerInteractEvent.EntityInteract interact(Player player, Villager villager, InteractionHand hand) {
        var event = new PlayerInteractEvent.EntityInteract(player, hand, villager);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }
    private static Player randomPlayer(GameTestHelper h) {
        var player = h.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Writ.RANDOM.get(), 2));
        return player;
    }
    private static void counters(Villager villager, int normal, int chaos) {
        var data = new CompoundTag();
        data.putInt("version", 1);
        data.putInt("normalAppliedCount", normal);
        data.putInt("chaosAppliedCount", chaos);
        villager.getPersistentData().put(Writ.ID, data);
    }
    private static void state(GameTestHelper h, Villager v, Player p, PlayerInteractEvent.EntityInteract event,
                              int offers, int normal, int chaos, int held) {
        var data = v.getPersistentData().getCompound(Writ.ID);
        h.assertTrue(event.isCanceled(), "villager event must be canceled");
        h.assertTrue(v.getOffers().size() == offers, "wrong offer count");
        h.assertTrue(data.getInt("normalAppliedCount") == normal && data.getInt("chaosAppliedCount") == chaos, "wrong counters");
        h.assertTrue(p.getMainHandItem().getCount() == held, "wrong held count");
    }
    private static void rejectedUnchanged(GameTestHelper h, Villager v, Player p) {
        var before = v.getPersistentData().copy();
        int offers = v.getOffers().size(), held = p.getMainHandItem().getCount();
        var event = interact(p, v, InteractionHand.MAIN_HAND);
        h.assertTrue(event.isCanceled(), "rejected villager event must be canceled");
        h.assertTrue(v.getOffers().size() == offers && p.getMainHandItem().getCount() == held, "rejection changed offers or held count");
        h.assertTrue(v.getPersistentData().equals(before), "rejection changed persistent data or counters");
    }
    @GameTest(template = "empty")
    public static void randomWritLimitsAndChaosQuota(GameTestHelper h) {
        for (int chaos : new int[] {0, 2}) {
            var v = farmer(h); var p = randomPlayer(h); counters(v, 7, chaos);
            int before = v.getOffers().size();
            state(h, v, p, interact(p, v, InteractionHand.MAIN_HAND), before + 1, 8, chaos, 1);
            state(h, v, p, interact(p, v, InteractionHand.MAIN_HAND), before + 1, 8, chaos, 1);
        }
        var total = farmer(h); var p = randomPlayer(h); counters(total, 7, 3);
        int before = total.getOffers().size();
        state(h, total, p, interact(p, total, InteractionHand.MAIN_HAND), before, 7, 3, 2);
        var independent = farmer(h); counters(independent, 5, 2);
        var chaosPlayer = h.makeMockSurvivalPlayer(); chaosPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Writ.CHAOS.get(), 2));
        int initial = independent.getOffers().size();
        state(h, independent, chaosPlayer, interact(chaosPlayer, independent, InteractionHand.MAIN_HAND), initial + 1, 5, 3, 1);
        state(h, independent, p, interact(p, independent, InteractionHand.MAIN_HAND), initial + 2, 6, 3, 1);
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void randomWritEligibilityAndInvalidData(GameTestHelper h) {
        for (int i = 0; i < 5; i++) {
            var v = farmer(h); var p = randomPlayer(h);
            switch (i) {
                case 0 -> v.setAge(-24000);
                case 1 -> v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
                case 2 -> v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NITWIT));
                case 3 -> v.setVillagerXp(0);
                case 4 -> v.setTradingPlayer(h.makeMockSurvivalPlayer());
            }
            rejectedUnchanged(h, v, p);
        }
        for (int i = 0; i < 5; i++) {
            var v = farmer(h); var p = randomPlayer(h);
            counters(v, 1, 1);
            var root = v.getPersistentData(); var data = root.getCompound(Writ.ID);
            switch (i) {
                case 0 -> root.putString(Writ.ID, "bad");
                case 1 -> data.remove("normalAppliedCount");
                case 2 -> data.remove("chaosAppliedCount");
                case 3 -> data.putInt("normalAppliedCount", -1);
                case 4 -> data.putInt("version", 2);
            }
            rejectedUnchanged(h, v, p);
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void randomWritOtherTargetsAndPlayers(GameTestHelper h) {
        var p = randomPlayer(h);
        for (var type : List.of(EntityType.COW, EntityType.WANDERING_TRADER, EntityType.PIG)) {
            var target = h.spawn(type, new BlockPos(1, 1, 1));
            var event = new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, target);
            MinecraftForge.EVENT_BUS.post(event);
            h.assertFalse(event.isCanceled(), "non-villager interaction canceled");
            h.assertTrue(p.getMainHandItem().getCount() == 2, "non-villager consumed writ");
            h.assertTrue(!target.getPersistentData().contains(Writ.ID), "non-villager received writ data");
            h.assertTrue(Writ.isOtherMerchant(target) == (type == EntityType.WANDERING_TRADER), "merchant classification incorrect");
        }
        var result = Writ.RANDOM.get().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult() == InteractionResult.PASS && p.getMainHandItem().getCount() == 2, "air use changed item");
        var v = farmer(h); var other = h.makeMockSurvivalPlayer();
        var normal = new ItemStack(Writ.NORMAL.get(), 2); normal.getOrCreateTag().putString("preset_id", "writ:example");
        other.setItemInHand(InteractionHand.MAIN_HAND, normal);
        int before = v.getOffers().size();
        state(h, v, other, interact(other, v, InteractionHand.MAIN_HAND), before + 1, 1, 0, 1);
        state(h, v, p, interact(p, v, InteractionHand.MAIN_HAND), before + 2, 2, 0, 1);
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Writ.CHAOS.get(), 2));
        state(h, v, other, interact(other, v, InteractionHand.MAIN_HAND), before + 3, 2, 1, 1);
        h.assertTrue(p.getMainHandItem().getCount() == 1, "other player's interaction changed random writ stack");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_randomWritEmptyPool")
    public static void randomWritEmptyPool(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var pack = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("datapacks/baseline-tests/data");
        Map<java.nio.file.Path, byte[]> originals = new LinkedHashMap<>();
        TestRestore.Action restore = () -> {
            for (var entry : originals.entrySet()) {
                if (entry.getValue() == null) java.nio.file.Files.deleteIfExists(entry.getKey());
                else java.nio.file.Files.write(entry.getKey(), entry.getValue());
            }
        };
        TestRestore.Action verify = () -> h.assertFalse(Presets.randomCandidates(Presets.INSTANCE.currentSnapshot(), id("minecraft:farmer")).isEmpty(), "farmer candidates not restored");
        try {
            var snapshot = Presets.INSTANCE.currentSnapshot();
            h.assertFalse(Presets.randomCandidates(snapshot, id("minecraft:farmer")).isEmpty(), "fixture starts empty");
            for (var pool : snapshot.pools().values()) {
                if (!pool.kind().equals("rarity")) continue;
                var poolId = id(pool.id());
                var path = pack.resolve(poolId.getNamespace()).resolve("writs/loot/pools").resolve(poolId.getPath() + ".json");
                originals.put(path, java.nio.file.Files.exists(path) ? java.nio.file.Files.readAllBytes(path) : null);
                java.nio.file.Files.createDirectories(path.getParent());
                var json = new com.google.gson.JsonObject();
                json.addProperty("weight", pool.weight()); json.addProperty("kind", "rarity");
                json.addProperty("label", pool.label()); json.addProperty("disabled", true);
                if (pool.translationKey() != null) json.addProperty("translation_key", pool.translationKey());
                java.nio.file.Files.writeString(path, json.toString());
            }
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                try {
                    if (failure != null) throw new IllegalStateException("empty pool reload failed", failure);
                    h.assertTrue(Presets.INSTANCE.randomOffer(Presets.INSTANCE.currentSnapshot(), id("minecraft:farmer"), h.getLevel().getRandom()).isEmpty(), "random pool is not empty");
                    var v = farmer(h); var p = randomPlayer(h);
                    rejectedUnchanged(h, v, p);
                    TestRestore.finish(h, restore, verify, null);
                } catch (Throwable e) { TestRestore.finish(h, restore, verify, e); }
            }));
        } catch (Throwable e) { TestRestore.finish(h, restore, verify, e); }
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_randomWritReloadKeepsOffer")
    public static void randomWritReloadKeepsOffer(GameTestHelper h) {
        var v = farmer(h); var p = randomPlayer(h);
        int before = v.getOffers().size();
        state(h, v, p, interact(p, v, InteractionHand.MAIN_HAND), before + 1, 1, 0, 1);
        var saved = v.getOffers().createTag().copy();
        var oldSnapshot = Presets.INSTANCE.currentSnapshot();
        var server = h.getLevel().getServer();
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
            try {
                if (failure != null) throw new IllegalStateException("reload failed", failure);
                h.assertTrue(Presets.INSTANCE.currentSnapshot() != oldSnapshot, "reload did not replace snapshot");
                h.assertTrue(v.getOffers().size() == before + 1 && v.getOffers().createTag().equals(saved), "reload changed existing offers");
                h.succeed();
            } catch (Throwable e) { h.fail("random offer reload test failed: " + e); }
        }));
    }
    private static void verifySigning(GameTestHelper h, Villager villager, Player player, InteractionHand hand,
                                      PlayerInteractEvent.EntityInteract event, int offers, int normal, int held) {
        var data = villager.getPersistentData().getCompound(Writ.ID);
        h.assertTrue(event.isCanceled(), "interaction must be canceled");
        h.assertTrue(villager.getOffers().size() == offers, "incorrect offer count");
        h.assertTrue(data.getInt("normalAppliedCount") == normal && data.getInt("chaosAppliedCount") == 0, "incorrect counters");
        h.assertTrue(player.getItemInHand(hand).getCount() == held, "incorrect held count");
        var offer = villager.getOffers().get(offers - 1);
        var result = offer.getResult();
        h.assertFalse(result.is(Writ.NORMAL.get()) || result.is(Writ.RANDOM.get()) || result.is(Writ.CHAOS.get()), "result must not be a writ");
        h.assertFalse(offer.getBaseCostA().isEmpty(), "cost must not be empty");
    }
    @GameTest(template = "empty")
    public static void randomWritSignsTwice(GameTestHelper h) {
        var villager = farmer(h);
        var player = h.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Writ.RANDOM.get(), 2));
        int before = villager.getOffers().size();
        verifySigning(h, villager, player, InteractionHand.MAIN_HAND, interact(player, villager, InteractionHand.MAIN_HAND), before + 1, 1, 1);
        h.assertTrue(player.getMainHandItem().getTag() == null, "signing must not write item NBT");
        verifySigning(h, villager, player, InteractionHand.MAIN_HAND, interact(player, villager, InteractionHand.MAIN_HAND), before + 2, 2, 0);
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void randomWritCreativeAndOffhand(GameTestHelper h) {
        var villager = farmer(h);
        var player = h.makeMockSurvivalPlayer();
        player.getAbilities().instabuild = true;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Writ.RANDOM.get(), 2));
        int before = villager.getOffers().size();
        verifySigning(h, villager, player, InteractionHand.MAIN_HAND, interact(player, villager, InteractionHand.MAIN_HAND), before + 1, 1, 2);
        var offhandVillager = farmer(h);
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Writ.RANDOM.get(), 2));
        int offhandBefore = offhandVillager.getOffers().size();
        verifySigning(h, offhandVillager, player, InteractionHand.OFF_HAND, interact(player, offhandVillager, InteractionHand.OFF_HAND), offhandBefore + 1, 1, 1);
        h.assertTrue(player.getMainHandItem().getCount() == 2, "offhand interaction changed main hand");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void randomWritStacksAndPassesInAir(GameTestHelper h) {
        var first = new ItemStack(Writ.RANDOM.get());
        var second = new ItemStack(Writ.RANDOM.get());
        h.assertTrue(first.getTag() == null && second.getTag() == null, "new random writs must have no NBT");
        h.assertTrue(ItemStack.isSameItemSameTags(first, second) && first.isStackable(), "random writs must stack");
        var player = h.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, first);
        var result = Writ.RANDOM.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult() == InteractionResult.PASS && result.getObject() == first, "use in air must pass with the same stack");
        h.assertTrue(first.getCount() == 1 && first.getTag() == null, "use in air changed the item");
        h.assertFalse(VariantRules.canRequest(false, first.getTag(), first.getCount()), "random writ must not request variants");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void randomWritIsRegistered(GameTestHelper helper) {
        helper.assertTrue(Writ.RANDOM.get() != null && Writ.RANDOM.get() != Items.AIR, "random writ must be registered as an item");
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(new ResourceLocation("writ:random_writ")), "random writ registry key must exist");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void randomWritRecipeLoads(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(new ResourceLocation("writ", "random_writ")).isPresent(), "random writ recipe must load");
        helper.succeed();
    }

    private static ResourceLocation id(String path) { return new ResourceLocation(path); }
    private static Set<String> ids(List<LootRules.Candidate> candidates) {
        return candidates.stream().map(LootRules.Candidate::id).collect(java.util.stream.Collectors.toSet());
    }
    @GameTest(template = "empty")
    public static void baselineRandomCandidates(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var farmer = id("minecraft:farmer");
        var candidates = Presets.randomCandidates(snapshot, farmer);
        h.assertTrue(!candidates.isEmpty(), "farmer candidates must not be empty");
        h.assertFalse(ids(candidates).contains("writ:a_unlock"), "unlock must be excluded");
        var names = candidates.stream().map(LootRules.Candidate::id).toList();
        h.assertTrue(names.equals(names.stream().sorted().toList()), "candidates must be sorted");
        for (var candidate : candidates) {
            var preset = snapshot.presets().get(id(candidate.id()));
            var pool = snapshot.pools().get(preset.rarity());
            h.assertTrue(pool != null && pool.kind().equals("rarity") && pool.weight() > 0 && !pool.disabled(), "invalid pool: " + candidate.id());
            h.assertTrue(preset.professions().isEmpty() || preset.professions().contains(farmer), "wrong profession: " + candidate.id());
            h.assertTrue(preset.sources().isEmpty() || snapshot.sources().values().stream().anyMatch(s -> LootRules.reachableAt(candidate, snapshot.rarities(), s)), "unreachable: " + candidate.id());
        }
        h.assertTrue(Presets.hasAnyUsableRandomCandidate(snapshot), "baseline should have usable candidates");
        try { candidates.add(candidates.get(0)); h.fail("candidate list must be immutable"); return; }
        catch (UnsupportedOperationException expected) { }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void professionVariantsAndBossOnly(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        for (var pair : Map.of("mason", "writ:terracotta", "shepherd", "writ:bed_supply").entrySet()) {
            var candidates = Presets.randomCandidates(snapshot, id("minecraft:" + pair.getKey()));
            h.assertTrue(candidates.stream().filter(c -> c.id().equals(pair.getValue())).count() == 1, pair.getValue() + " must appear once");
            var preset = snapshot.presets().get(id(pair.getValue()));
            h.assertTrue(!preset.result().isEmpty() && preset.variants().size() > 1, pair.getValue() + " needs default and multiple variants");
        }
        var excluded = Map.of(
            "cleric", List.of("nether_star_supply", "dragon_breath_supply", "echo_shard_supply", "end_crystal_supply", "sculk_catalyst_supply"),
            "armorer", List.of("gear_bulwark_chestplate", "gear_swift_leggings", "gear_tide_helmet"),
            "weaponsmith", List.of("gear_edge_sword", "gear_tidecaller_trident"),
            "fletcher", List.of("gear_skyfall_bow"), "fisherman", List.of("sponge_supply"), "shepherd", List.of("wither_rose_supply"));
        for (var entry : excluded.entrySet()) {
            var present = ids(Presets.randomCandidates(snapshot, id("minecraft:" + entry.getKey())));
            for (String name : entry.getValue()) {
                var preset = snapshot.presets().get(id("writ:" + name));
                h.assertTrue(preset != null && (preset.professions().isEmpty() || preset.professions().contains(id("minecraft:" + entry.getKey()))), "fixture profession mismatch: " + name);
                h.assertFalse(present.contains("writ:" + name), "boss-only candidate included: " + name);
            }
        }
        h.succeed();
    }

    private static Presets.Preset preset(Set<String> sources, Set<ResourceLocation> professions, Item result, List<Presets.Variant> variants, String pool) {
        return new Presets.Preset(new ItemStack(Items.EMERALD), ItemStack.EMPTY, new ItemStack(result), 1, 1, 0.05f,
            professions, pool, 1, Set.of("TRADE"), "", "", variants, sources, "", "");
    }
    private static Presets.Variant variant(Item result) {
        return new Presets.Variant("variant", "", "", new ItemStack(Items.EMERALD), ItemStack.EMPTY, new ItemStack(result), 1);
    }
    private static LootRules.Source source(String name, double chance, Map<String, Integer> overrides, boolean inherit) {
        return new LootRules.Source("test:loot/sources/" + name, "minecraft:chests/simple_dungeon", chance, overrides, inherit);
    }
    @GameTest(template = "empty")
    public static void baselineRandomOffers(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var random = RandomSource.create(12345L);
        for (int i = 0; i < 50; i++) {
            var offer = Presets.INSTANCE.randomOffer(snapshot, id("minecraft:farmer"), random);
            h.assertTrue(offer.isPresent(), "farmer offer missing at draw " + i);
            var result = offer.orElseThrow().getResult();
            h.assertFalse(result.is(Writ.NORMAL.get()) || result.is(Writ.RANDOM.get()) || result.is(Writ.CHAOS.get()), "writ result at draw " + i);
            h.assertFalse(offer.orElseThrow().getBaseCostA().isEmpty(), "empty cost at draw " + i);
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void randomOfferVariantsAndEmptyCandidates(GameTestHelper h) {
        String rarity = "writ:r";
        var presetId = id("test:variants");
        var p = preset(Set.of(), Set.of(), Items.DIAMOND, List.of(variant(Items.EMERALD), variant(Items.GOLD_INGOT), variant(Items.IRON_INGOT)), rarity);
        var pool = new PoolData.Pool(rarity, 1, "rarity", "R", "writ.rarity.r");
        var candidates = List.of(new LootRules.Candidate(presetId.toString(), rarity, 1));
        var snapshot = new Presets.Snapshot(Map.of(presetId, p), Set.of(), Map.of(rarity, pool), Map.of(), Map.of(), candidates, new ChaosSampler(List.of()));
        var random = RandomSource.create(987654L);
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (Item item : List.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT)) counts.put(item, 0);
        for (int i = 0; i < 4000; i++) {
            var offer = Presets.INSTANCE.randomOffer(snapshot, id("minecraft:farmer"), random);
            h.assertTrue(offer.isPresent(), "synthetic offer missing at draw " + i);
            Item result = offer.orElseThrow().getResult().getItem();
            h.assertTrue(counts.containsKey(result), "unexpected result: " + result);
            counts.put(result, counts.get(result) + 1);
        }
        System.out.println("Random offer variant counts: diamond=" + counts.get(Items.DIAMOND) + ", emerald=" + counts.get(Items.EMERALD)
            + ", gold_ingot=" + counts.get(Items.GOLD_INGOT) + ", iron_ingot=" + counts.get(Items.IRON_INGOT));
        for (var entry : counts.entrySet()) h.assertTrue(entry.getValue() >= 850 && entry.getValue() <= 1150, "variant count outside range: " + entry);
        var empty = new Presets.Snapshot(Map.of(), Set.of(), Map.of(), Map.of(), Map.of(), List.of(), new ChaosSampler(List.of()));
        h.assertTrue(Presets.INSTANCE.randomOffer(empty, id("minecraft:farmer"), random).isEmpty(), "empty snapshot must return empty offer");
        var restricted = preset(Set.of(), Set.of(id("minecraft:farmer")), Items.DIAMOND, List.of(), rarity);
        var restrictedSnapshot = new Presets.Snapshot(Map.of(presetId, restricted), Set.of(), Map.of(rarity, pool), Map.of(), Map.of(), candidates, new ChaosSampler(List.of()));
        h.assertTrue(Presets.INSTANCE.randomOffer(restrictedSnapshot, id("minecraft:mason"), random).isEmpty(), "mismatched profession must return empty offer");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void syntheticCandidateBoundaries(GameTestHelper h) {
        String rarity = "test:rarity";
        var farmer = id("minecraft:farmer");
        Map<String, PoolData.Pool> pools = Map.of(rarity, new PoolData.Pool(rarity, 1, "rarity", "", ""),
            "test:zero", new PoolData.Pool("test:zero", 0, "rarity", "", ""),
            "test:other", new PoolData.Pool("test:other", 1, "other", "", ""),
            "test:disabled", new PoolData.Pool("test:disabled", 1, "rarity", "", "", true));
        Map<String, LootRules.Source> sources = Map.of("zero", source("zero", 0, Map.of(), true), "no_weight", source("no_weight", 1, Map.of(), false),
            "override_zero", source("override_zero", 1, Map.of(rarity, 0), true), "ok", source("ok", 1, Map.of(), true));
        var boss = new LootRules.Source("test:loot/bosses/boss", "minecraft:wither", 1, Map.of(), true);
        Map<ResourceLocation, Presets.Preset> presets = new HashMap<>();
        presets.put(id("test:zero_chance"), preset(Set.of("test:zero"), Set.of(), Items.DIAMOND, List.of(), rarity));
        presets.put(id("test:zero_weight"), preset(Set.of("test:no_weight"), Set.of(), Items.DIAMOND, List.of(), rarity));
        presets.put(id("test:zero_override"), preset(Set.of("test:override_zero"), Set.of(), Items.DIAMOND, List.of(), rarity));
        presets.put(id("test:reachable"), preset(Set.of("test:ok"), Set.of(farmer), Items.DIAMOND, List.of(), rarity));
        presets.put(id("test:boss_bad"), preset(Set.of("test:boss", "test:zero"), Set.of(), Items.DIAMOND, List.of(), rarity));
        presets.put(id("test:boss_good"), preset(Set.of("test:boss", "test:ok"), Set.of(), Items.DIAMOND, List.of(), rarity));
        presets.put(id("test:unrestricted"), preset(Set.of(), Set.of(), Items.DIAMOND, List.of(), rarity));
        presets.put(id("test:other_profession"), preset(Set.of(), Set.of(id("minecraft:cleric")), Items.DIAMOND, List.of(), rarity));
        presets.put(id("test:invalid_profession"), preset(Set.of(), Set.of(id("test:unregistered")), Items.DIAMOND, List.of(), rarity));
        for (var entry : Map.of("pool_zero", "test:zero", "pool_other", "test:other", "pool_disabled", "test:disabled", "pool_missing", "test:missing").entrySet())
            presets.put(id("test:" + entry.getKey()), preset(Set.of(), Set.of(), Items.DIAMOND, List.of(), entry.getValue()));
        List<Item> writs = List.of(Writ.NORMAL.get(), Writ.RANDOM.get(), Writ.CHAOS.get());
        for (int i = 0; i < writs.size(); i++) {
            presets.put(id("test:default_writ_" + i), preset(Set.of(), Set.of(), writs.get(i), List.of(), rarity));
            presets.put(id("test:variant_writ_" + i), preset(Set.of(), Set.of(), Items.DIAMOND, List.of(variant(Items.DIAMOND), variant(writs.get(i))), rarity));
        }
        var candidates = new ArrayList<LootRules.Candidate>();
        presets.forEach((key, p) -> candidates.add(new LootRules.Candidate(key.toString(), p.rarity(), p.weight(), p.sources())));
        candidates.add(new LootRules.Candidate("test:missing_preset", rarity, 1));
        var snapshot = new Presets.Snapshot(Map.copyOf(presets), Set.of(), pools, sources, Map.of("boss", boss), candidates, new ChaosSampler(List.of()));
        var actual = ids(Presets.randomCandidates(snapshot, farmer));
        h.assertTrue(actual.equals(Set.of("test:reachable", "test:boss_good", "test:unrestricted")), "unexpected farmer candidates: " + actual);
        h.assertTrue(Presets.hasAnyUsableRandomCandidate(snapshot), "valid candidate should exist");
        var valid = Presets.validProfessions();
        var expected = new HashSet<>(BuiltInRegistries.VILLAGER_PROFESSION.keySet());
        expected.remove(BuiltInRegistries.VILLAGER_PROFESSION.getKey(VillagerProfession.NONE));
        expected.remove(BuiltInRegistries.VILLAGER_PROFESSION.getKey(VillagerProfession.NITWIT));
        h.assertTrue(valid.equals(expected), "valid professions differ from registry");
        try { valid.clear(); h.fail("professions must be immutable"); return; }
        catch (UnsupportedOperationException expectedException) { }
        var invalidOnly = new Presets.Snapshot(snapshot.presets(), Set.of(), pools, sources, Map.of(),
            candidates.stream().filter(c -> c.id().equals("test:invalid_profession")).toList(), new ChaosSampler(List.of()));
        h.assertFalse(Presets.hasAnyUsableRandomCandidate(invalidOnly), "unregistered profession must not be usable");
        var empty = new Presets.Snapshot(Map.of(), Set.of(), Map.of(), Map.of(), Map.of(), List.of(), new ChaosSampler(List.of()));
        h.assertTrue(Presets.randomCandidates(empty, farmer).isEmpty(), "empty snapshot must return empty list");
        h.assertFalse(Presets.hasAnyUsableRandomCandidate(empty), "empty snapshot must not be usable");
        h.succeed();
    }
}
