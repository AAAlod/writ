package ke.writ;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReloadStatsTest {
    @Test void countsFilesNotExpandedTablesAndReportsChain() {
        var pools = Map.of("a", new PoolData.Pool("a", 1, "rarity", "A", null), "b", new PoolData.Pool("b", 0, "purpose", "B", null, true));
        var unlock = new LootRules.Candidate("unlock", "a", 1);
        var sources = List.of(new LootRules.Source("x:file", "x:first", 1, Map.of()), new LootRules.Source("x:file", "x:second", 1, Map.of()));
        var stats = ReloadStats.calculate(pools, 3, List.of(unlock), sources, 2, List.of(), 1, ObtainMode.TRADE, true, List.of(unlock), Map.of("a", 1, "b", 0));
        assertEquals(2, stats.pools()); assertEquals(1, stats.disabled()); assertEquals(1, stats.natural()); assertEquals(2, stats.manualOnly());
        assertEquals(1, stats.sourcesEnabled()); assertEquals(2, stats.sourcesTotal()); assertEquals(0, stats.bossesEnabled()); assertEquals(1, stats.bossesTotal()); assertEquals("reachable", stats.tradeChain());
        assertEquals("pending", ReloadStats.calculate(pools, 0, List.of(), List.of(), 0, List.of(), 0, ObtainMode.TRADE, false, List.of(), Map.of()).tradeChain());
        assertEquals("not-applicable", ReloadStats.calculate(pools, 0, List.of(), List.of(), 0, List.of(), 0, ObtainMode.CRAFT_9, true, List.of(), Map.of()).tradeChain());
    }
}
