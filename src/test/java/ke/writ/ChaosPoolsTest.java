package ke.writ;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChaosPoolsTest {
    @Test void poolsResolveTagsExcludeDenyAndEmptyAllow() {
        var all = List.of("test:a", "test:b", "test:c", "minecraft:command_block", "writ:chaos_writ");
        Map<String, Collection<String>> entries = Map.of("#test:tag", List.of("test:a", "test:b"), "test:b", List.of("test:b"), "minecraft:command_block", List.of("minecraft:command_block"), "writ:chaos_writ", List.of("writ:chaos_writ"));
        var pools = ChaosPools.build(all, List.of("test:b", "missing"), List.of("#test:tag", "minecraft:command_block", "writ:chaos_writ"), null, entries::get);
        assertEquals(List.of("test:a"), pools.payment());
        assertEquals(List.of("test:a", "test:c"), pools.result());
        assertEquals(List.of("unknown item or tag: missing"), pools.warnings());
        assertTrue(ChaosPools.build(all, List.of(), List.of(), null, entries::get).payment().isEmpty());
        assertTrue(new ChaosSampler(List.of(), List.of("test:a")).sample(i -> 0).isEmpty());
    }
    @Test void offerParametersMustBeValid() {
        assertDoesNotThrow(() -> PresetChecks.trade(12, 0, .05f));
        assertThrows(IllegalArgumentException.class, () -> PresetChecks.trade(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PresetChecks.trade(1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> PresetChecks.trade(1, 0, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> PresetChecks.trade(1, 0, -1));
    }
}
