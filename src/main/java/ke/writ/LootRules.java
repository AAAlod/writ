package ke.writ;

import java.util.*;
import java.util.function.LongUnaryOperator;

/** Pure weighted-selection and source-validation rules. */
final class LootRules {
    /** A natural-drop candidate; an empty {@code sources} set means "any source that weights this pool". */
    record Candidate(String id, String rarity, int weight, Set<String> sources) {
        Candidate(String id, String rarity, int weight) { this(id, rarity, weight, Set.of()); }
        boolean eligibleAt(String sourceKey) { return sources.isEmpty() || sources.contains(sourceKey); }
    }
    record Source(String resourceId, String lootTable, double chance, Map<String, Integer> overrides, boolean inheritGlobalWeights) {
        Source(String resourceId, String lootTable, double chance, Map<String, Integer> overrides) { this(resourceId, lootTable, chance, overrides, true); }
        /** The ID presets use in {@code sources}: the file ID without its {@code loot/sources/} or {@code loot/bosses/} prefix. */
        String key() { return sourceKey(resourceId); }
    }
    static String sourceKey(String resourceId) {
        int colon = resourceId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : resourceId.substring(0, colon), path = colon < 0 ? resourceId : resourceId.substring(colon + 1);
        for (String prefix : List.of("loot/sources/", "loot/bosses/")) if (path.startsWith(prefix)) return namespace + ":" + path.substring(prefix.length());
        return resourceId;
    }
    record Sources(Map<String, Source> enabled, List<String> errors) { }
    static boolean naturallyEligible(ObtainMode mode, Set<String> modes, boolean chaosResult) {
        return modes.contains(mode.name()) && (mode != ObtainMode.CRAFT_9 || !chaosResult);
    }
    static Sources validateSources(Collection<Source> input, Set<String> rarities, Set<String> knownTables) {
        return validate(input, rarities, knownTables, "loot table");
    }
    static Sources validateBosses(Collection<Source> input, Set<String> rarities, Set<String> knownEntities) {
        return validate(input, rarities, knownEntities, "entity");
    }
    private static Sources validate(Collection<Source> input, Set<String> rarities, Set<String> knownTables, String kind) {
        Map<String, List<Source>> grouped = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        for (Source s : input.stream().sorted(Comparator.comparing(Source::resourceId)).toList()) {
            grouped.computeIfAbsent(s.lootTable(), k -> new ArrayList<>()).add(s);
        }
        Map<String, Source> result = new TreeMap<>();
        grouped.forEach((id, sources) -> {
            if (sources.size() != 1) { errors.add(id + ": conflicting sources " + sources.stream().map(Source::resourceId).toList()); return; }
            Source s = sources.get(0);
            if (!knownTables.contains(s.lootTable())) { errors.add(s.resourceId() + ": unknown " + kind + " " + s.lootTable()); return; }
            if (!Double.isFinite(s.chance()) || s.chance() < 0 || s.chance() > 1) { errors.add(s.resourceId() + ": invalid chance " + s.chance()); return; }
            if (!rarities.containsAll(s.overrides().keySet())) { errors.add(s.resourceId() + ": unknown pool in " + s.overrides().keySet()); return; }
            if (s.overrides().values().stream().anyMatch(w -> w < 0)) { errors.add(s.resourceId() + ": negative pool weight"); return; }
            result.put(id, s);
        });
        return new Sources(Map.copyOf(result), List.copyOf(errors));
    }
    /** Natural candidates that no enabled source can produce; each is a dead drop weight. */
    static List<String> unreachable(Collection<Candidate> candidates, Map<String, Integer> global, Collection<Source> sources) {
        return candidates.stream().filter(c -> sources.stream().noneMatch(s -> reachableAt(c, global, s))).map(Candidate::id).distinct().sorted().toList();
    }
    static Optional<String> select(List<Candidate> candidates, Map<String, Integer> global, Map<String, Integer> overrides, LongUnaryOperator random) {
        return select(candidates, global, overrides, true, random);
    }
    static int effectiveWeight(String pool, Map<String, Integer> global, Map<String, Integer> overrides, boolean inheritGlobalWeights) {
        if (!global.containsKey(pool)) return 0;
        return overrides.containsKey(pool) ? overrides.get(pool) : inheritGlobalWeights ? global.get(pool) : 0;
    }
    /** Pool-only selection that ignores preset source restrictions (pure pool arithmetic). */
    static Optional<String> select(List<Candidate> candidates, Map<String, Integer> global, Map<String, Integer> overrides, boolean inheritGlobalWeights, LongUnaryOperator random) {
        return select(candidates, global, overrides, inheritGlobalWeights, null, random);
    }
    /** Source-aware selection: only candidates whose {@code sources} are empty or contain the source key take part. */
    static Optional<String> select(List<Candidate> candidates, Map<String, Integer> global, Source source, LongUnaryOperator random) {
        return select(candidates, global, source.overrides(), source.inheritGlobalWeights(), source.key(), random);
    }
    /** True when the source can ever produce this candidate: positive chance, positive pool weight and a matching source list. */
    static boolean reachableAt(Candidate candidate, Map<String, Integer> global, Source source) {
        return candidate.weight() > 0 && global.containsKey(candidate.rarity()) && source.chance() > 0 && candidate.eligibleAt(source.key())
                && effectiveWeight(candidate.rarity(), global, source.overrides(), source.inheritGlobalWeights()) > 0;
    }
    private static Optional<String> select(List<Candidate> candidates, Map<String, Integer> global, Map<String, Integer> overrides, boolean inheritGlobalWeights, String sourceKey, LongUnaryOperator random) {
        Map<String, List<Candidate>> grouped = new TreeMap<>();
        for (Candidate c : candidates) if (c.weight() > 0 && global.containsKey(c.rarity()) && (sourceKey == null || c.eligibleAt(sourceKey))) grouped.computeIfAbsent(c.rarity(), k -> new ArrayList<>()).add(c);
        List<String> rarities = grouped.keySet().stream().filter(r -> effectiveWeight(r, global, overrides, inheritGlobalWeights) > 0).toList();
        if (rarities.isEmpty()) return Optional.empty();
        long sum = 0; for (String r : rarities) sum += effectiveWeight(r, global, overrides, inheritGlobalWeights);
        long roll = random.applyAsLong(sum);
        String selected = null;
        for (String r : rarities) { roll -= effectiveWeight(r, global, overrides, inheritGlobalWeights); if (roll < 0) { selected = r; break; } }
        List<Candidate> entries = grouped.get(selected).stream().sorted(Comparator.comparing(Candidate::id)).toList();
        long total = 0; for (Candidate c : entries) total += c.weight();
        roll = random.applyAsLong(total);
        for (Candidate c : entries) { roll -= c.weight(); if (roll < 0) return Optional.of(c.id()); }
        throw new IllegalStateException("random source returned invalid index");
    }
}
