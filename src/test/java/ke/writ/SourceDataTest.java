package ke.writ;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceDataTest {
    private com.google.gson.JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    @Test void validatesSingleAndMultipleTableFields() {
        assertEquals(List.of("minecraft:chests/a"), SourceData.tables(json("{\"loot_table\":\"minecraft:chests/a\"}")));
        assertEquals(List.of("minecraft:chests/a", "minecraft:chests/b"), SourceData.tables(json("{\"loot_tables\":[\"minecraft:chests/a\",\"minecraft:chests/b\"]}")));
        for (String bad : List.of("{}", "{\"loot_table\":\"a\",\"loot_tables\":[\"b\"]}", "{\"loot_tables\":[]}", "{\"loot_tables\":[3]}", "{\"loot_tables\":[\"minecraft:a\",\"minecraft:a\"]}"))
            assertThrows(IllegalArgumentException.class, () -> SourceData.tables(json(bad)), bad);
        assertThrows(IllegalArgumentException.class, () -> SourceData.validateBossFields(json("{\"entities\":[\"minecraft:wither\"]}")));
    }
    @Test void rejectsInvalidInheritanceAndRetainsLegacyDefault() {
        assertTrue(PoolData.inheritGlobalWeights(json("{}")));
        assertFalse(PoolData.inheritGlobalWeights(json("{\"inherit_global_weights\":false}")));
        assertThrows(IllegalArgumentException.class, () -> PoolData.inheritGlobalWeights(json("{\"inherit_global_weights\":\"false\"}")));
        assertThrows(IllegalArgumentException.class, () -> PoolData.inheritGlobalWeights(json("{\"inherit_global_weights\":null}")));
    }
}
