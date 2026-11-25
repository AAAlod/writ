package ke.writ;

import java.util.*;
import java.util.function.Function;

/** Pure pool construction; resolver returns null for an unknown item or tag. */
final class ChaosPools {
    record Pools(List<String> payment, List<String> result, List<String> warnings) { }
    static Pools build(Collection<String> all, List<String> exclude, List<String> paymentAllow,
                       List<String> resultAllow, Function<String, Collection<String>> resolver) {
        var warnings = new TreeSet<String>();
        var excluded = resolve(exclude, resolver, warnings);
        var payment = paymentAllow == null ? new HashSet<>(all) : resolve(paymentAllow, resolver, warnings);
        var result = resultAllow == null ? new HashSet<>(all) : resolve(resultAllow, resolver, warnings);
        payment.retainAll(all); result.retainAll(all);
        payment.removeAll(ChaosSampler.HARD_DENY); result.removeAll(ChaosSampler.HARD_DENY);
        payment.removeAll(excluded); result.removeAll(excluded);
        return new Pools(payment.stream().sorted().toList(), result.stream().sorted().toList(), List.copyOf(warnings));
    }
    private static Set<String> resolve(List<String> entries, Function<String, Collection<String>> resolver, Set<String> warnings) {
        var values = new HashSet<String>();
        for (String entry : entries) {
            Collection<String> found = resolver.apply(entry);
            if (found == null) warnings.add("unknown item or tag: " + entry); else values.addAll(found);
        }
        return values;
    }
}
