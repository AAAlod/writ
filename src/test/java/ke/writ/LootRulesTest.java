package ke.writ;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LootRulesTest {
    @Test void explicitPoolWhitelistAndFallback() {
        var global = Map.of("a", 10, "b", 20, "c", 30);
        var overrides = Map.of("a", 5, "b", 1);
        var legacy = new LootRules.Source("x:s", "x:t", 1, overrides);
        assertTrue(legacy.inheritGlobalWeights());
        assertEquals(30, LootRules.effectiveWeight("c", global, overrides, true));
        assertEquals(0, LootRules.effectiveWeight("c", global, overrides, false));
        assertEquals(0, LootRules.effectiveWeight("a", global, Map.of("a", 0), true));
        assertEquals(0, LootRules.effectiveWeight("missing", global, Map.of("missing", 3), true));
        var candidates = List.of(new LootRules.Candidate("A", "a", 1), new LootRules.Candidate("B", "b", 1), new LootRules.Candidate("C", "c", 1));
        var random = new java.util.Random(6);
        for (int i = 0; i < 100; i++) assertTrue(Set.of("A", "B").contains(LootRules.select(candidates, global, overrides, false, random::nextLong).orElseThrow()));
        var unlock = new LootRules.Candidate("unlock", "c", 1);
        assertFalse(ObtainMode.diagnostics(ObtainMode.TRADE, List.of(unlock), global, List.of(new LootRules.Source("x:s", "x:t", 1, overrides, false))).isEmpty());
        assertTrue(ObtainMode.diagnostics(ObtainMode.TRADE, List.of(unlock), global, List.of(legacy)).isEmpty());
    }
    @Test void bossValidationRejectsUnknownDuplicatesInvalidChanceRarityAndNegativeWeight() {
        var entries = List.of(
            new LootRules.Source("x:b", "minecraft:wither", 1, Map.of()),
            new LootRules.Source("x:a", "minecraft:wither", 1, Map.of()),
            new LootRules.Source("x:c", "x:unknown", 1, Map.of()),
            new LootRules.Source("x:d", "x:badchance", Double.NaN, Map.of()),
            new LootRules.Source("x:e", "x:badchance2", 1.01, Map.of()),
            new LootRules.Source("x:f", "x:badrarity", 1, Map.of("missing", 1)),
            new LootRules.Source("x:g", "x:negative", 1, Map.of("rare", -1)),
            new LootRules.Source("x:h", "minecraft:ender_dragon", 1, Map.of("rare", 0)));
        var result = LootRules.validateBosses(entries, Set.of("rare"), Set.of("minecraft:wither", "x:badchance", "x:badchance2", "x:badrarity", "x:negative", "minecraft:ender_dragon"));
        assertEquals(Set.of("minecraft:ender_dragon"), result.enabled().keySet());
        assertEquals(6, result.errors().size());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("unknown entity")));
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("x:a") && e.contains("x:b")));
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("negative pool weight")));
    }
    @Test void modeFilterCoversAbsentAndEveryDeclaredModeWithBothResults() {
        // The parser maps an absent loot_modes to both; keep it distinct from an empty array.
        var declarations = List.of(Set.of("TRADE", "CRAFT_9"), Set.of("TRADE"), Set.of("CRAFT_9"), Set.of("TRADE", "CRAFT_9"), Set.<String>of());
        boolean[] trade = {true, true, false, true, false}, craft = {true, false, true, true, false};
        for (int i = 0; i < declarations.size(); i++) for (boolean chaos : List.of(false, true)) {
            assertEquals(trade[i], LootRules.naturallyEligible(ObtainMode.TRADE, declarations.get(i), chaos));
            assertEquals(craft[i] && !chaos, LootRules.naturallyEligible(ObtainMode.CRAFT_9, declarations.get(i), chaos));
        }
    }
    @Test void tradeReachabilityDiagnosticsUsePresetAndSourceWeights() {
        var unlock = new LootRules.Candidate("n:unlock", "rare", 1);
        var source = new LootRules.Source("n:source", "n:table", .5, Map.of());
        var weights = Map.of("rare", 10);
        assertTrue(ObtainMode.diagnostics(ObtainMode.TRADE, List.of(unlock), weights, List.of(source)).isEmpty());
        assertEquals(1, ObtainMode.diagnostics(ObtainMode.TRADE, List.of(new LootRules.Candidate("n:unlock", "rare", 0)), weights, List.of(source)).size());
        assertEquals(1, ObtainMode.diagnostics(ObtainMode.TRADE, List.of(unlock), weights, List.of(new LootRules.Source("n:s", "n:t", 0, Map.of()))).size());
        assertEquals(1, ObtainMode.diagnostics(ObtainMode.TRADE, List.of(unlock), weights, List.of(new LootRules.Source("n:s", "n:t", 1, Map.of("rare", 0)))).size());
        assertTrue(ObtainMode.diagnostics(ObtainMode.CRAFT_9, List.of(), weights, List.of()).isEmpty());
        assertEquals(ObtainMode.TRADE, ObtainMode.parse("INVALID"));
        assertEquals(ObtainMode.TRADE, ObtainMode.parse(123));
        assertEquals(ObtainMode.CRAFT_9, ObtainMode.parse("CRAFT_9"));
    }
    @Test void choosesRarityThenWeightedPresetAndFallback() {
        var candidates = List.of(new LootRules.Candidate("a", "common", 1), new LootRules.Candidate("b", "common", 3), new LootRules.Candidate("c", "rare", 1));
        Map<String, Integer> global = Map.of("common", 10, "rare", 2);
        assertEquals("a", LootRules.select(candidates, global, Map.of(), b -> 0).orElseThrow());
        assertEquals("c", LootRules.select(candidates, global, Map.of("rare", 100, "common", 0), b -> 0).orElseThrow());
        var seq = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals("b", LootRules.select(candidates, global, Map.of(), b -> seq.getAndIncrement() == 0 ? 0 : 3).orElseThrow());
    }
    @Test void zeroWeightsUnknownRarityAndEmptyDoNotDraw() {
        var candidates = List.of(new LootRules.Candidate("a", "common", 0), new LootRules.Candidate("b", "unknown", 4));
        assertTrue(LootRules.select(candidates, Map.of("common", 1), Map.of(), b -> { fail("empty set must not draw"); return 0; }).isEmpty());
        assertTrue(LootRules.select(List.of(new LootRules.Candidate("a", "common", 1)), Map.of("common", 1), Map.of("common", 0), b -> 0).isEmpty());
    }
    @Test void sourcesRejectUnknownChanceAndDisableAllDuplicates() {
        var a = new LootRules.Source("n:a", "n:table", 1, Map.of());
        var b = new LootRules.Source("n:b", "n:table", 1, Map.of());
        var c = new LootRules.Source("n:c", "n:other", 2, Map.of());
        var d = new LootRules.Source("n:d", "n:unknown", 1, Map.of());
        var e = new LootRules.Source("n:e", "n:rare", 1, Map.of("bogus", 1));
        var valid = new LootRules.Source("n:f", "n:valid", 1, Map.of());
        var result = LootRules.validateSources(List.of(b, a, c, d, e, valid), Set.of("common"), Set.of("n:table", "n:other", "n:rare", "n:valid"));
        assertEquals(Set.of("n:valid"), result.enabled().keySet());
        assertEquals(4, result.errors().size());
        assertTrue(result.errors().get(0).contains("n:c"));
        assertTrue(result.errors().stream().anyMatch(x -> x.contains("n:a") && x.contains("n:b")));
    }
    @Test void presetSourcesRestrictWhereACandidateDrops() {
        assertEquals("writ:nether_bridge", LootRules.sourceKey("writ:loot/sources/nether_bridge"));
        assertEquals("pack:warden", LootRules.sourceKey("pack:loot/bosses/warden"));
        assertEquals("test:whitelist", LootRules.sourceKey("test:whitelist"));
        var global = Map.of("r", 10, "sr", 5);
        var fortress = new LootRules.Source("writ:loot/sources/nether_bridge", "minecraft:chests/nether_bridge", 1, Map.of("r", 1, "sr", 1), false);
        var desert = new LootRules.Source("writ:loot/sources/desert_pyramid", "minecraft:chests/desert_pyramid", 1, Map.of("r", 1), false);
        var nether = new LootRules.Candidate("nether", "r", 1, Set.of("writ:nether_bridge"));
        var sand = new LootRules.Candidate("sand", "r", 1, Set.of("writ:desert_pyramid"));
        var generic = new LootRules.Candidate("generic", "sr", 1);
        var all = List.of(nether, sand, generic);
        var random = new java.util.Random(3);
        Set<String> seen = new TreeSet<>();
        for (int i = 0; i < 200; i++) seen.add(LootRules.select(all, global, fortress, random::nextLong).orElseThrow());
        assertEquals(Set.of("nether", "generic"), seen, "a source only rolls its own and unrestricted candidates");
        for (int i = 0; i < 50; i++) assertEquals("sand", LootRules.select(all, global, desert, random::nextLong).orElseThrow());
        // A tier whose only candidates belong elsewhere does not absorb the source's weight.
        assertEquals(Optional.empty(), LootRules.select(List.of(sand), global, fortress, random::nextLong));
        assertTrue(LootRules.reachableAt(nether, global, fortress));
        assertFalse(LootRules.reachableAt(nether, global, desert));
        assertFalse(LootRules.reachableAt(generic, global, desert), "generic SR is not weighted at the desert");
        assertFalse(LootRules.reachableAt(nether, global, new LootRules.Source(fortress.resourceId(), fortress.lootTable(), 0, fortress.overrides(), false)), "zero chance");
        assertEquals(List.of("sand"), LootRules.unreachable(List.of(nether, sand, generic), global, List.of(fortress)));
        var unlock = new LootRules.Candidate("unlock", "r", 1, Set.of("writ:desert_pyramid"));
        assertFalse(ObtainMode.diagnostics(ObtainMode.TRADE, List.of(unlock), global, List.of(fortress)).isEmpty(), "unlock restricted to another source is unreachable");
        assertTrue(ObtainMode.diagnostics(ObtainMode.TRADE, List.of(unlock), global, List.of(desert)).isEmpty());
    }
}
