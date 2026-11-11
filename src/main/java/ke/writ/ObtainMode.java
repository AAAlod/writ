package ke.writ;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Pure acquisition rules; the default is a provisional value, not frozen balance. */
enum ObtainMode {
    TRADE, CRAFT_9;

    static ObtainMode parse(Object value) {
        if (value instanceof String text) {
            try { return valueOf(text); } catch (IllegalArgumentException ignored) { }
        }
        return TRADE;
    }
    static List<String> diagnostics(ObtainMode mode, Collection<LootRules.Candidate> unlocks,
                                    Map<String, Integer> rarities, Collection<LootRules.Source> sources) {
        if (mode != TRADE) return List.of();
        boolean reachable = unlocks.stream().anyMatch(p -> sources.stream().anyMatch(s -> LootRules.reachableAt(p, rarities, s)));
        return reachable ? List.of() : List.of("TRADE acquisition chain unreachable: no positive-weight chaos unlock preset has an enabled positive-chance source with positive pool weight");
    }
}
