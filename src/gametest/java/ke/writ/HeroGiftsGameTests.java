package ke.writ;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class HeroGiftsGameTests {
    private static final ResourceLocation FARMER_GIFT = new ResourceLocation("minecraft", "gameplay/hero_of_the_village/farmer_gift");

    // TestRestore reports failures from a future; propagate them on a GameTest tick instead.
    private static GameTestHelper reportingHelper(GameTestHelper helper) throws Exception {
        var field = GameTestHelper.class.getDeclaredField("testInfo");
        field.setAccessible(true);
        return new GameTestHelper((GameTestInfo) field.get(helper)) {
            @Override public void fail(String message) {
                helper.runAfterDelay(1, () -> helper.fail(message));
            }
        };
    }
    private record Config(ForgeConfigSpec.BooleanValue enabled, ForgeConfigSpec.IntValue random,
                          ForgeConfigSpec.IntValue chaos, boolean oldEnabled, int oldRandom, int oldChaos) {
        static Config capture() throws Exception {
            var enabled = (ForgeConfigSpec.BooleanValue) field("HERO_GIFTS_ENABLED");
            var random = (ForgeConfigSpec.IntValue) field("HERO_GIFTS_RANDOM");
            var chaos = (ForgeConfigSpec.IntValue) field("HERO_GIFTS_CHAOS");
            return new Config(enabled, random, chaos, enabled.get(), random.get(), chaos.get());
        }
        private static Object field(String name) throws Exception {
            var field = Writ.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        }
        void set(boolean e, int r, int c) {
            enabled.set(e); random.set(r); chaos.set(c);
            Writ.refreshHeroGiftParameters();
        }
        void restore() { set(oldEnabled, oldRandom, oldChaos); }
    }
    private static Villager farmer(GameTestHelper h) {
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        return villager;
    }
    private static List<ItemStack> generate(GameTestHelper h, ResourceLocation id, Entity entity) {
        var params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.THIS_ENTITY, entity).create(LootContextParamSets.GIFT);
        return h.getLevel().getServer().getLootData().getLootTable(id).getRandomItems(params);
    }
    private static void original(GameTestHelper h, List<ItemStack> items, String description) {
        h.assertFalse(items.isEmpty(), description + " produced empty loot");
        h.assertTrue(items.stream().noneMatch(s -> s.is(Writ.NORMAL.get()) || s.is(Writ.RANDOM.get()) || s.is(Writ.CHAOS.get())), description + " produced a writ");
    }
    private static void replacement(GameTestHelper h, List<ItemStack> items, net.minecraft.world.item.Item item) {
        h.assertTrue(items.size() == 1 && items.get(0).is(item) && items.get(0).getCount() == 1 && items.get(0).getTag() == null, "wrong replacement: " + items);
    }

    @GameTest(template = "empty", batch = "isolated_heroGiftConfig")
    public static void heroGiftRealPathAndConfig(GameTestHelper h) throws Exception {
        var config = Config.capture();
        try {
            var villager = farmer(h);
            config.set(true, 100, 0);
            replacement(h, generate(h, FARMER_GIFT, villager), Writ.RANDOM.get());
            config.set(true, 0, 100);
            replacement(h, generate(h, FARMER_GIFT, villager), Writ.CHAOS.get());
            config.set(true, 0, 0);
            original(h, generate(h, FARMER_GIFT, villager), "zero rates");
            config.set(false, 100, 0);
            original(h, generate(h, FARMER_GIFT, villager), "disabled");
            config.set(true, 3, 98);
            h.assertFalse(Writ.heroGiftParameters().valid(), "over-100 config accepted");
            original(h, generate(h, FARMER_GIFT, villager), "invalid rates");
            config.set(true, 100, 0);
            original(h, generate(h, new ResourceLocation("minecraft", "gameplay/cat_morning_gift"), villager), "cat gift");
            var cow = h.spawn(EntityType.COW, new BlockPos(2, 1, 1));
            original(h, generate(h, FARMER_GIFT, cow), "cow");
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
            original(h, generate(h, FARMER_GIFT, villager), "profession mismatch");
            var chest = generate(h, new ResourceLocation("writ", "chests/test_white"), villager);
            h.assertTrue(chest.size() == 2 && chest.stream().allMatch(s -> s.is(Writ.NORMAL.get())), "chest loot changed: " + chest);
            // Commands can construct the same table/entity context without a player having Hero of the Village.
        } finally {
            config.restore();
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_heroGiftRandomUnavailable")
    public static void heroGiftRandomUnavailable(GameTestHelper h) {
        h.runAfterDelay(20, () -> {
            try { randomUnavailable(h); } catch (Exception e) { h.fail("random setup failed: " + e); }
        });
    }
    private static void randomUnavailable(GameTestHelper h) throws Exception {
        var reporter = reportingHelper(h);
        var config = Config.capture();
        var server = h.getLevel().getServer();
        var base = server.getWorldPath(LevelResource.ROOT).resolve("datapacks/baseline-tests/data");
        var originals = new LinkedHashMap<Path, byte[]>();
        TestRestore.Action restore = () -> {
            try { for (var entry : originals.entrySet()) {
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey()); else Files.write(entry.getKey(), entry.getValue());
            }} finally { config.restore(); }
        };
        TestRestore.Action verify = () -> {
            h.assertTrue(Presets.hasAnyUsableRandomCandidate(Presets.INSTANCE.currentSnapshot()), "random candidates not restored");
            h.assertTrue(Writ.heroGiftParameters().equals(new Writ.HeroGiftParams(config.oldEnabled(), config.oldRandom(), config.oldChaos(), config.oldRandom() + config.oldChaos() <= 100)), "config not restored");
        };
        try {
            var snapshot = Presets.INSTANCE.currentSnapshot();
            for (var pool : snapshot.pools().values()) {
                if (!pool.kind().equals("rarity")) continue;
                var poolId = new ResourceLocation(pool.id());
                var path = base.resolve(poolId.getNamespace()).resolve("writs/loot/pools").resolve(poolId.getPath() + ".json");
                originals.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
                Files.createDirectories(path.getParent());
                var json = new com.google.gson.JsonObject();
                json.addProperty("weight", pool.weight()); json.addProperty("kind", "rarity");
                json.addProperty("label", pool.label()); json.addProperty("disabled", true);
                if (pool.translationKey() != null) json.addProperty("translation_key", pool.translationKey());
                Files.writeString(path, json.toString());
            }
            config.set(true, 100, 0);
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> {
              try { server.execute(() -> {
                try {
                    if (failure != null) throw new IllegalStateException("empty random pool reload", failure);
                    var current = Presets.INSTANCE.currentSnapshot();
                    h.assertTrue(Presets.randomCandidates(current, new ResourceLocation("minecraft", "farmer")).isEmpty(), "farmer candidates still available");
                    h.assertFalse(Presets.hasAnyUsableRandomCandidate(current), "random candidates still available");
                    original(h, generate(h, FARMER_GIFT, farmer(h)), "unavailable random pool");
                    TestRestore.finish(reporter, restore, verify, null);
                } catch (Throwable e) { TestRestore.finish(reporter, restore, verify, e); }
              }); } catch (Throwable e) { TestRestore.finish(reporter, restore, verify, e); }
            });
        } catch (Throwable e) { TestRestore.finish(reporter, restore, verify, e); }
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_heroGiftChaosUnavailable")
    public static void heroGiftChaosUnavailable(GameTestHelper h) {
        h.runAfterDelay(20, () -> {
            try { chaosUnavailable(h); } catch (Exception e) { h.fail("chaos setup failed: " + e); }
        });
    }
    private static void chaosUnavailable(GameTestHelper h) throws Exception {
        var reporter = reportingHelper(h);
        var config = Config.capture();
        var server = h.getLevel().getServer();
        var path = server.getWorldPath(LevelResource.ROOT).resolve("datapacks/baseline-tests/data/writ/writs/chaos/config.json");
        byte[] original = Files.readAllBytes(path);
        TestRestore.Action restore = () -> { try { Files.write(path, original); } finally { config.restore(); } };
        TestRestore.Action verify = () -> {
            var chaos = Presets.INSTANCE.currentSnapshot().chaos();
            h.assertTrue(chaos.size() > 0 && chaos.resultSize() > 0, "chaos pools not restored");
        };
        try {
            Files.writeString(path, "{\"payment_allow\":[],\"result_allow\":[\"minecraft:iron_ingot\"]}");
            config.set(true, 0, 100);
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> {
              try { server.execute(() -> {
                try {
                    if (failure != null) throw new IllegalStateException("empty chaos pool reload", failure);
                    var paymentEmpty = Presets.INSTANCE.currentSnapshot().chaos();
                    h.assertTrue(paymentEmpty.size() == 0 && paymentEmpty.resultSize() > 0, "expected empty payment pool and nonempty result pool");
                    original(h, generate(h, FARMER_GIFT, farmer(h)), "unavailable chaos payment pool");
                    Files.writeString(path, "{\"payment_allow\":[\"minecraft:iron_ingot\"],\"result_allow\":[]}");
                    server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, resultFailure) -> {
                      try { server.execute(() -> {
                        try {
                            if (resultFailure != null) throw new IllegalStateException("empty chaos result pool reload", resultFailure);
                            var resultEmpty = Presets.INSTANCE.currentSnapshot().chaos();
                            h.assertTrue(resultEmpty.size() > 0 && resultEmpty.resultSize() == 0, "expected nonempty payment pool and empty result pool");
                            original(h, generate(h, FARMER_GIFT, farmer(h)), "unavailable chaos result pool");
                            TestRestore.finish(reporter, restore, verify, null);
                        } catch (Throwable e) { TestRestore.finish(reporter, restore, verify, e); }
                      }); } catch (Throwable e) { TestRestore.finish(reporter, restore, verify, e); }
                    });
                } catch (Throwable e) { TestRestore.finish(reporter, restore, verify, e); }
              }); } catch (Throwable e) { TestRestore.finish(reporter, restore, verify, e); }
            });
        } catch (Throwable e) { TestRestore.finish(reporter, restore, verify, e); }
    }
    @GameTest(template = "empty")
    public static void heroGiftModifierRegistered(GameTestHelper h) {
        h.assertTrue(ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS.get().containsKey(new ResourceLocation("writ", "hero_gifts")), "hero gifts codec not registered");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void globalModifierResourceIncludesHeroGifts(GameTestHelper h) throws Exception {
        try (var stream = Writ.class.getResourceAsStream("/data/forge/loot_modifiers/global_loot_modifiers.json")) {
            h.assertTrue(stream != null, "global modifier resource missing");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            h.assertFalse(json.get("replace").getAsBoolean(), "modifier list must not replace others");
            var entries = json.getAsJsonArray("entries");
            boolean chest = false, hero = false;
            for (var entry : entries) {
                chest |= entry.getAsString().equals("writ:chest_loot");
                hero |= entry.getAsString().equals("writ:hero_gifts");
            }
            h.assertTrue(chest && hero, "expected both global loot modifiers");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heroGiftTableWhitelist(GameTestHelper h) {
        h.assertTrue(HeroGifts.GIFT_TABLES.size() == 13, "expected 13 hero gift tables");
        for (var id : HeroGifts.GIFT_TABLES)
            h.assertTrue(id.startsWith("minecraft:gameplay/hero_of_the_village/"), "unexpected table: " + id);
        h.succeed();
    }
}
