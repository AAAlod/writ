package ke.writ;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the shipped trade content against the fixed trade-system principles, using only the JSON on disk. */
class TradeSystemContentTest {
    static final Path DATA = TestSourcePaths.projectFile("src/main/resources/data/writ/writs");
    static final List<String> TIERS = List.of("writ:n", "writ:r", "writ:sr", "writ:ssr", "writ:ur");
    static final List<String> PROFESSIONS = List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
            "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith");
    /** Assumed chests per structure behind each themed chance; the dungeon is the uncalibrated high-frequency entry. */
    static final Map<String, Integer> CHESTS = Map.ofEntries(Map.entry("writ:abandoned_mineshaft", 8), Map.entry("writ:desert_pyramid", 4),
            Map.entry("writ:pillager_outpost", 1), Map.entry("writ:shipwreck", 2), Map.entry("writ:underwater_ruin", 1),
            Map.entry("writ:buried_treasure", 1), Map.entry("writ:nether_bridge", 3), Map.entry("writ:bastion", 5),
            Map.entry("writ:bastion_treasure", 1), Map.entry("writ:stronghold", 5), Map.entry("writ:woodland_mansion", 5),
            Map.entry("writ:ancient_city", 10), Map.entry("writ:end_city", 4));
    record Src(String key, double chance, Map<String, Integer> weights, boolean boss, JsonObject json) { }
    static Map<String, JsonObject> presets = new TreeMap<>(), pools = new TreeMap<>();
    static Map<String, Src> sources = new TreeMap<>();

    static JsonObject read(Path path) {
        try { return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    static Stream<Path> files(String dir) {
        try { return Files.list(DATA.resolve(dir)).filter(p -> p.toString().endsWith(".json")).sorted(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    static String name(Path p) { String f = p.getFileName().toString(); return "writ:" + f.substring(0, f.length() - 5); }
    static String id(String raw) { return raw.contains(":") ? raw : "writ:" + raw; }

    @BeforeAll static void load() {
        files("presets").forEach(p -> presets.put(name(p), read(p)));
        files("loot/pools").forEach(p -> pools.put(name(p), read(p)));
        for (String dir : List.of("loot/sources", "loot/bosses")) files(dir).forEach(p -> {
            JsonObject json = read(p); Map<String, Integer> weights = new TreeMap<>();
            json.getAsJsonObject("pool_weights").entrySet().forEach(e -> weights.put(id(e.getKey()), e.getValue().getAsInt()));
            assertNull(sources.put(name(p), new Src(name(p), json.get("contract_drop_chance").getAsDouble(), weights, dir.endsWith("bosses"), json)), "duplicate source key " + name(p));
        });
    }
    static String pool(JsonObject preset) { return id(preset.get("pool").getAsString()); }
    static int weight(JsonObject preset) { return preset.get("weight").getAsInt(); }
    static List<String> list(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        List<String> out = new ArrayList<>(); for (JsonElement e : json.getAsJsonArray(key)) out.add(e.getAsString()); return out;
    }
    static List<String> sourcesOf(JsonObject preset) { return list(preset, "sources").stream().map(TradeSystemContentTest::id).toList(); }
    static boolean active(JsonObject preset) {
        if (weight(preset) <= 0) return false;
        if (preset.has("general_loot") && preset.get("general_loot").getAsBoolean()) return true;
        return sourcesOf(preset).stream().map(sources::get).anyMatch(s -> s != null && s.chance() > 0 && s.weights().getOrDefault(pool(preset), 0) > 0);
    }
    /** Every offer a preset can produce: the default trade plus each enabled variant with inherited fields. */
    static List<JsonObject> offers(JsonObject preset) {
        JsonObject base = preset.getAsJsonObject("trade"); List<JsonObject> out = new ArrayList<>(List.of(base));
        if (preset.has("variants")) for (JsonElement raw : preset.getAsJsonArray("variants")) {
            JsonObject v = raw.getAsJsonObject();
            if (v.has("enabled") && !v.get("enabled").getAsBoolean()) continue;
            JsonObject merged = base.deepCopy();
            if (v.has("trade")) v.getAsJsonObject("trade").entrySet().forEach(e -> merged.add(e.getKey(), e.getValue().deepCopy()));
            if (v.has("max_uses")) merged.add("max_uses", v.get("max_uses"));
            out.add(merged);
        }
        return out;
    }
    static String item(JsonObject trade, String slot) {
        return trade.has(slot) && trade.get(slot).isJsonObject() ? trade.getAsJsonObject(slot).get("item").getAsString() : null;
    }

    @Test void poolsAreExactlyFiveDifficultyTiersPlusTheChaosUnlock() {
        assertEquals(new TreeSet<>(List.of("writ:n", "writ:r", "writ:sr", "writ:ssr", "writ:ur", "writ:unlock")), pools.keySet());
        for (String tier : TIERS) assertEquals("rarity", pools.get(tier).get("kind").getAsString(), tier);
        for (var e : presets.entrySet()) assertTrue(pools.containsKey(pool(e.getValue())), "preset in unknown pool " + e.getKey());
    }

    @Test void everyEnabledPresetIsReachableAndDeclaresWhereItIsFound() {
        for (var e : presets.entrySet()) {
            JsonObject p = e.getValue();
            assertFalse(sourcesOf(p).isEmpty(), "built-in preset must declare sources: " + e.getKey());
            for (String s : sourcesOf(p)) assertTrue(sources.containsKey(s) || (s.equals("writ:general") && p.has("general_loot") && p.get("general_loot").getAsBoolean()), e.getKey() + " references unknown source " + s);
            if (weight(p) > 0) assertTrue(active(p), "enabled preset is unreachable: " + e.getKey());
        }
        // Only the optional emerald-output economy hook is shipped disabled; it stays usable by ID.
        assertEquals(Set.of("writ:exchange_spider_eye_emerald"), presets.entrySet().stream().filter(e -> weight(e.getValue()) == 0).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void everyPositiveSourceTierHasCandidatesAndEveryTierIsLive() {
        for (Src s : sources.values()) for (var w : s.weights().entrySet()) {
            if (w.getValue() <= 0) continue;
            boolean found = presets.values().stream().anyMatch(p -> weight(p) > 0 && pool(p).equals(w.getKey()) && sourcesOf(p).contains(s.key()));
            assertTrue(found, s.key() + " weights " + w.getKey() + " but has no candidate in it");
        }
        Map<String, Long> perTier = new TreeMap<>();
        for (JsonObject p : presets.values()) if (active(p)) perTier.merge(pool(p), 1L, Long::sum);
        for (String tier : TIERS) assertTrue(perTier.getOrDefault(tier, 0L) >= 5, "tier has too few live candidates: " + tier + " " + perTier);
        // Difficulty, not category: no ordinary or basic-entry structure can roll UR.
        for (String easy : List.of("writ:simple_dungeon", "writ:abandoned_mineshaft", "writ:desert_pyramid", "writ:underwater_ruin"))
            assertEquals(0, sources.get(easy).weights().getOrDefault("writ:ssr", 0) + sources.get(easy).weights().getOrDefault("writ:ur", 0), easy + " is basic and must not roll SSR/UR");
        for (String mid : List.of("writ:pillager_outpost", "writ:shipwreck", "writ:nether_bridge", "writ:bastion", "writ:stronghold", "writ:ancient_city", "writ:buried_treasure"))
            assertEquals(0, sources.get(mid).weights().getOrDefault("writ:ur", 0), mid + " must not roll UR");
        assertTrue(sources.get("writ:simple_dungeon").chance() >= 0.5, "dungeon must stay the high-frequency entry");
        assertTrue(sources.get("writ:simple_dungeon").weights().getOrDefault("writ:n", 0) > 0, "dungeon must offer basic N contracts");
    }

    @Test void themedStructuresAreCalibratedToAboutFivePercentEmptyAcrossThree() {
        for (Src s : sources.values()) {
            if (s.boss() || s.key().equals("writ:simple_dungeon")) continue;
            Integer chests = CHESTS.get(s.key());
            assertNotNull(chests, "uncalibrated source " + s.key());
            double emptyThree = Math.pow(1 - s.chance(), 2.0 * chests * 3);
            assertTrue(emptyThree > 0.035 && emptyThree < 0.07, s.key() + " three-structure miss chance " + emptyThree);
        }
        // Fewer chests per structure means a higher per-table chance.
        for (Src a : sources.values()) for (Src b : sources.values())
            if (CHESTS.containsKey(a.key()) && CHESTS.containsKey(b.key()) && CHESTS.get(a.key()) < CHESTS.get(b.key())) assertTrue(a.chance() > b.chance(), a.key() + " vs " + b.key());
    }

    @Test void everyProfessionHasAtLeastEightDistinctActiveTrades() {
        Map<String, Set<String>> results = new TreeMap<>();
        for (var e : presets.entrySet()) {
            if (!active(e.getValue())) continue;
            for (String prof : list(e.getValue(), "allowed_professions")) results.computeIfAbsent(prof.replace("minecraft:", ""), k -> new TreeSet<>()).add(e.getKey());
        }
        for (String prof : PROFESSIONS) assertTrue(results.getOrDefault(prof, Set.of()).size() >= 8, prof + " has " + results.get(prof));
        // Distinct: no two active presets of one profession sell the same default result item.
        for (String prof : PROFESSIONS) {
            Set<String> seen = new HashSet<>();
            for (String id : results.get(prof)) {
                JsonObject result = presets.get(id).getAsJsonObject("trade").getAsJsonObject("result");
                String key = result.get("item").getAsString() + (result.has("nbt") ? result.get("nbt").toString() : "");
                assertTrue(seen.add(key), prof + " duplicates " + key + " in " + id);
            }
        }
        long anyProfession = presets.values().stream().filter(p -> weight(p) > 0 && !p.has("allowed_professions")).count();
        assertEquals(4, anyProfession, "the chaos unlock and three wood contracts have no profession restriction");
    }

    @Test void bossesHaveUniqueIdentityLinesAtThirtyPercent() {
        var bosses = sources.values().stream().filter(Src::boss).toList();
        assertEquals(Set.of("minecraft:elder_guardian", "minecraft:wither", "minecraft:ender_dragon", "minecraft:warden"),
                bosses.stream().map(s -> s.json().get("entity").getAsString()).collect(java.util.stream.Collectors.toSet()));
        for (Src boss : bosses) {
            assertEquals(0.30, boss.chance(), 1e-9, boss.key());
            assertFalse(boss.json().has("inherit_global_weights") && boss.json().get("inherit_global_weights").getAsBoolean(), boss.key());
            assertEquals(0, boss.weights().getOrDefault("writ:n", 0) + boss.weights().getOrDefault("writ:r", 0), boss.key() + " rolls generic N/R");
            var line = presets.entrySet().stream().filter(e -> active(e.getValue()) && sourcesOf(e.getValue()).contains(boss.key())).toList();
            assertTrue(line.size() >= 2, boss.key() + " line too thin");
            for (var e : line) {
                assertEquals(List.of(boss.key()), sourcesOf(e.getValue()), e.getKey() + " is not exclusive to " + boss.key());
                assertFalse(List.of("writ:n", "writ:r").contains(pool(e.getValue())), e.getKey());
            }
        }
    }

    @Test void kitsTakeOneEmptyPlainBoxAndReturnAPlainBox() {
        int kits = 0;
        for (var e : presets.entrySet()) for (JsonObject offer : offers(e.getValue())) {
            boolean boxCost = Stream.of("cost_a", "cost_b").map(s -> item(offer, s)).filter(Objects::nonNull).anyMatch(i -> i.endsWith("shulker_box"));
            if (!boxCost) { assertFalse(item(offer, "result").endsWith("shulker_box"), "box result without box payment " + e.getKey()); continue; }
            kits++;
            JsonObject a = offer.getAsJsonObject("cost_a"), b = offer.getAsJsonObject("cost_b");
            assertFalse(a.get("item").getAsString().endsWith("shulker_box"), "materials go first: " + e.getKey());
            assertEquals("minecraft:shulker_box", b.get("item").getAsString(), e.getKey());
            assertEquals(1, b.get("count").getAsInt(), e.getKey()); assertFalse(b.has("nbt"), e.getKey());
            assertEquals("minecraft:shulker_box", item(offer, "result"), "kit must return an uncoloured box: " + e.getKey());
        }
        assertTrue(kits >= 7, "kits disappeared: " + kits);
    }

    @Test void highValueSuppliesArePricedForPermanentRestock() {
        Set<String> scarce = Set.of("minecraft:diamond", "minecraft:netherite_ingot", "minecraft:elytra", "minecraft:gold_ingot", "minecraft:gold_block", "minecraft:compass", "minecraft:book", "minecraft:leather", "minecraft:water_bucket", "minecraft:bone_block");
        for (var e : presets.entrySet()) {
            String tier = pool(e.getValue());
            if (!List.of("writ:ssr", "writ:ur").contains(tier) || !active(e.getValue())) continue;
            for (JsonObject offer : offers(e.getValue())) {
                int uses = offer.get("max_uses").getAsInt();
                assertTrue(uses <= (tier.equals("writ:ur") ? 1 : 2), e.getKey() + " restocks too much per cycle: " + uses);
                int emeralds = Stream.of("cost_a", "cost_b").filter(offer::has).map(offer::getAsJsonObject).filter(c -> c != null && c.get("item").getAsString().equals("minecraft:emerald")).mapToInt(c -> c.get("count").getAsInt()).sum();
                boolean material = Stream.of("cost_a", "cost_b").filter(s -> offer.has(s) && offer.get(s).isJsonObject()).anyMatch(s -> scarce.contains(item(offer, s)));
                assertTrue(emeralds >= (tier.equals("writ:ur") ? 32 : 12), e.getKey() + " too cheap: " + emeralds + " emeralds");
                if (tier.equals("writ:ur")) assertTrue(material, e.getKey() + " UR supply must also consume a scarce material");
            }
        }
    }

    @Test void variantsNeverCarryTheirOwnDropWeight() {
        for (var e : presets.entrySet()) if (e.getValue().has("variants")) for (JsonElement v : e.getValue().getAsJsonArray("variants"))
            for (String forbidden : List.of("weight", "pool", "rarity", "sources")) assertFalse(v.getAsJsonObject().has(forbidden), e.getKey() + " variant has " + forbidden);
    }
    @Test void everyListedSourceCanActuallyDropThePreset() {
        for (var e : presets.entrySet()) {
            if (weight(e.getValue()) <= 0) continue;
            for (String key : sourcesOf(e.getValue())) {
                if (key.equals("writ:general") && e.getValue().has("general_loot") && e.getValue().get("general_loot").getAsBoolean()) continue;
                Src s = sources.get(key);
                assertTrue(s != null && s.chance() > 0 && s.weights().getOrDefault(pool(e.getValue()), 0) > 0,
                        e.getKey() + " lists " + key + " but that source never rolls " + pool(e.getValue()));
            }
        }
    }

    /** Emeralds a villager pays per unit (vanilla 1.20.1 buy trades), directly or after one simple craft/smelt. */
    static final Map<String, Double> RESALE = new HashMap<>();
    static {
        double dye = 1.0 / 12;
        for (String c : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"))
            RESALE.put(c + "_dye", dye);
        Object[][] direct = {{"wheat", 20}, {"potato", 26}, {"carrot", 22}, {"beetroot", 15}, {"pumpkin", 6}, {"melon", 4}, {"string", 14}, {"coal", 10},
                {"cod", 15}, {"salmon", 13}, {"tropical_fish", 6}, {"pufferfish", 4}, {"white_wool", 18}, {"gray_wool", 18}, {"black_wool", 18}, {"brown_wool", 18},
                {"stick", 32}, {"flint", 26}, {"feather", 24}, {"tripwire_hook", 8}, {"paper", 24}, {"book", 4}, {"ink_sac", 5}, {"writable_book", 2},
                {"glass_pane", 11}, {"compass", 1}, {"rotten_flesh", 32}, {"gold_ingot", 3}, {"rabbit_foot", 2}, {"scute", 4}, {"glass_bottle", 9},
                {"nether_wart", 22}, {"iron_ingot", 4}, {"diamond", 1}, {"chicken", 14}, {"porkchop", 7}, {"rabbit", 4}, {"mutton", 7}, {"beef", 10},
                {"dried_kelp_block", 10}, {"sweet_berries", 10}, {"leather", 6}, {"rabbit_hide", 9}, {"clay_ball", 10}, {"stone", 20}, {"quartz", 12},
                {"granite", 16}, {"andesite", 16}, {"diorite", 16}};
        for (Object[] d : direct) RESALE.put((String) d[0], 1.0 / (Integer) d[1]);
        Object[][] crafted = {{"glass", "glass_pane", 2.5}, {"glass", "glass_bottle", 1.0}, {"bone_block", "white_dye", 9.0}, {"bone_meal", "white_dye", 1.0},
                {"lapis_lazuli", "blue_dye", 1.0}, {"cocoa_beans", "brown_dye", 1.0}, {"wither_rose", "black_dye", 1.0}, {"ink_sac", "black_dye", 1.0},
                {"cactus", "green_dye", 1.0}, {"sea_pickle", "lime_dye", 1.0}, {"sugar_cane", "paper", 1.0}, {"bookshelf", "book", 3.0},
                {"iron_block", "iron_ingot", 9.0}, {"gold_block", "gold_ingot", 9.0}, {"gold_nugget", "gold_ingot", 1.0 / 9}, {"coal_block", "coal", 9.0},
                {"hay_block", "wheat", 9.0}, {"bamboo", "stick", 0.5}, {"kelp", "dried_kelp_block", 1.0 / 9}, {"stone_bricks", "stone", 0.0}};
        String[][] flowerDyes = {{"dandelion", "yellow"}, {"poppy", "red"}, {"blue_orchid", "light_blue"}, {"allium", "magenta"}, {"azure_bluet", "light_gray"},
                {"red_tulip", "red"}, {"orange_tulip", "orange"}, {"white_tulip", "light_gray"}, {"pink_tulip", "pink"}, {"oxeye_daisy", "light_gray"},
                {"cornflower", "blue"}, {"lily_of_the_valley", "white"}, {"torchflower", "orange"}};
        for (String[] f : flowerDyes) RESALE.merge(f[0], dye, Math::max);
        for (String f : List.of("sunflower", "lilac", "rose_bush", "peony")) RESALE.merge(f, 2 * dye, Math::max);
        for (Object[] c : crafted) RESALE.merge((String) c[0], (Double) c[2] * RESALE.getOrDefault((String) c[1], 0.0), Math::max);
    }

    @Test void emeraldOnlyOffersCannotBeResoldAtAProfit() {
        for (var e : presets.entrySet()) for (JsonObject offer : offers(e.getValue())) {
            int emeralds = 0; boolean onlyEmeralds = true;
            for (String slot : List.of("cost_a", "cost_b")) {
                if (!offer.has(slot) || !offer.get(slot).isJsonObject()) continue;
                JsonObject cost = offer.getAsJsonObject(slot);
                if (cost.get("item").getAsString().equals("minecraft:emerald")) emeralds += cost.get("count").getAsInt(); else onlyEmeralds = false;
            }
            if (!onlyEmeralds) continue;
            JsonObject result = offer.getAsJsonObject("result");
            double value = result.get("count").getAsInt() * RESALE.getOrDefault(result.get("item").getAsString().replace("minecraft:", ""), 0.0);
            assertTrue(value < emeralds, e.getKey() + " sells " + result + " for " + emeralds + " emeralds but it resells for " + value);
        }
    }
}
