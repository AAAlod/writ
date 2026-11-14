package ke.writ;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BaselineTest {
    @Test void chaosExcludesEveryHardDeniedIdAndDeduplicates() {
        var required = List.of("minecraft:air", "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:command_block_minecart", "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw", "writ:trade_writ", "writ:random_writ", "writ:chaos_writ");
        assertEquals(Set.copyOf(required), ChaosSampler.HARD_DENY);
        var ids = new ArrayList<>(required);
        ids.addAll(List.of("test:a", "test:a", "test:b"));
        var pools = ChaosPools.build(ids, List.of(), null, null, key -> null);
        assertEquals(List.of("test:a", "test:b"), pools.payment());
        assertEquals(List.of("test:a", "test:b"), pools.result());
        var sampler = new ChaosSampler(ids);
        assertEquals(2, sampler.size());
        Random random = new Random(911);
        for (int i = 0; i < 1000; i++) {
            var pair = sampler.sample(random::nextInt).orElseThrow();
            assertFalse(ChaosSampler.HARD_DENY.contains(pair.payment()));
            assertFalse(ChaosSampler.HARD_DENY.contains(pair.result()));
        }
    }
    @Test void separateDrawsPermitAllOrderedPairsWithSeededSmallPool() {
        var sampler = new ChaosSampler(List.of("test:a", "test:b"));
        int[][] counts = new int[2][2];
        Random random = new Random(42);
        for (int i = 0; i < 20000; i++) {
            var pair = sampler.sample(random::nextInt).orElseThrow();
            counts[pair.payment().equals("test:a") ? 0 : 1][pair.result().equals("test:a") ? 0 : 1]++;
        }
        for (int[] row : counts) for (int count : row) assertTrue(count > 4600 && count < 5400, "ordered-pair count " + count);
        AtomicInteger call = new AtomicInteger();
        var forced = sampler.sample(bound -> call.getAndIncrement()).orElseThrow();
        assertEquals(new ChaosSampler.Pair("test:a", "test:b"), forced);
        assertEquals(2, call.get());
    }
    @Test void emptyPoolDoesNotDraw() {
        assertTrue(new ChaosSampler(ChaosSampler.HARD_DENY).sample(n -> { fail("must not draw"); return 0; }).isEmpty());
    }
    @Test void limitsCoverCategoryTotalZeroInvalidAndOverflow() {
        assertTrue(Limits.allows(7, 2, 8, 3, 10, true));
        assertFalse(Limits.allows(8, 0, 8, 3, 10, true));
        assertFalse(Limits.allows(0, 3, 8, 3, 10, false));
        assertFalse(Limits.allows(7, 3, 8, 4, 10, true));
        assertFalse(Limits.allows(0, 0, 0, 3, 10, true));
        assertFalse(Limits.allows(-1, 0, 8, 3, 10, true));
        assertFalse(Limits.allows(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true));
    }
    @Test void presetAndContractValidation() {
        assertTrue(PresetChecks.supportedContract(true, true, 1));
        assertFalse(PresetChecks.supportedContract(false, true, 1));
        assertTrue(PresetChecks.supportedContract(true, false, 1));
        assertTrue(PresetChecks.supportedContract(true, false, 0));
        assertFalse(PresetChecks.supportedContract(true, true, 2));
        assertDoesNotThrow(() -> { PresetChecks.weight(0); PresetChecks.trade(1, 1, 0.05f); PresetChecks.item("x", true, false, 64, 64); });
        assertThrows(IllegalArgumentException.class, () -> PresetChecks.weight(-1));
        assertThrows(IllegalArgumentException.class, () -> PresetChecks.trade(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PresetChecks.trade(1, 0, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> PresetChecks.item("x", true, false, 65, 64));
        assertThrows(IllegalArgumentException.class, () -> PresetChecks.item("x", false, true, 1, 64));
    }
}
