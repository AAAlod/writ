package ke.writ;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;

final class ChaosSampler {
    static final Set<String> HARD_DENY = Set.of("minecraft:air", "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:command_block_minecart", "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw", "writ:trade_writ", "writ:random_writ", "writ:chaos_writ");
    record Pair(String payment, String result) { }
    private final List<String> payment, result;
    ChaosSampler(Collection<String> ids) { this(ids, ids); }
    ChaosSampler(Collection<String> payment, Collection<String> result) {
        this.payment = clean(payment); this.result = clean(result);
    }
    private static List<String> clean(Collection<String> ids) { return ids.stream().filter(id -> !HARD_DENY.contains(id)).distinct().sorted().toList(); }
    int size() { return payment.size(); }
    int resultSize() { return result.size(); }
    Optional<Pair> sample(IntUnaryOperator nextInt) {
        if (payment.isEmpty() || result.isEmpty()) return Optional.empty();
        return Optional.of(new Pair(payment.get(nextInt.applyAsInt(payment.size())), result.get(nextInt.applyAsInt(result.size()))));
    }
}
