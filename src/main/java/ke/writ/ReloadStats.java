package ke.writ;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Counts enabled source files, even when one file covers several loot tables. */
final class ReloadStats {
    record Stats(int pools, int disabled, int presets, int natural, int manualOnly,
                 long sourcesEnabled, int sourcesTotal, long bossesEnabled, int bossesTotal, String tradeChain) { }

    static Stats calculate(Map<String, PoolData.Pool> pools, int presetCount, List<LootRules.Candidate> candidates,
                           Collection<LootRules.Source> sources, int sourceFiles,
                           Collection<LootRules.Source> bosses, int bossFiles,
                           ObtainMode mode, boolean modeReady, List<LootRules.Candidate> unlocks, Map<String, Integer> weights, boolean generalUnlockReachable) {
        int disabled = (int) pools.values().stream().filter(PoolData.Pool::disabled).count();
        int natural = (int) candidates.stream().map(LootRules.Candidate::id).distinct().count();
        var all = new java.util.ArrayList<>(sources); all.addAll(bosses);
        String chain = !modeReady ? "pending" : mode != ObtainMode.TRADE ? "not-applicable" :
                ObtainMode.diagnostics(mode, unlocks, weights, all, generalUnlockReachable).isEmpty() ? "reachable" : "unreachable";
        return new Stats(pools.size(), disabled, presetCount, natural, presetCount - natural,
                sources.stream().map(LootRules.Source::resourceId).distinct().count(), sourceFiles,
                bosses.stream().map(LootRules.Source::resourceId).distinct().count(), bossFiles, chain);
    }
}
