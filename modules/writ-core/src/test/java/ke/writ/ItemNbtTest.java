package ke.writ;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemNbtTest {
    @Test void readsJsonAndSnbt() {
        var json = ItemNbt.parse(JsonParser.parseString("{\"display\":{\"Name\":\"Miner\"},\"power\":8}"));
        assertEquals("Miner", json.getCompound("display").getString("Name")); assertEquals(8, json.getInt("power"));
        var snbt = ItemNbt.parse(new JsonPrimitive("{Enchantments:[{id:\"minecraft:efficiency\",lvl:8s}]}"));
        assertEquals(8, snbt.getList("Enchantments", 10).getCompound(0).getShort("lvl"));
    }
    @Test void rejectsBadRootReservedKeysAndOversizedData() {
        assertThrows(IllegalArgumentException.class, () -> ItemNbt.parse(JsonParser.parseString("[1,2]")));
        assertThrows(IllegalArgumentException.class, () -> ItemNbt.parse(new JsonPrimitive("{broken")));
        for (String key : java.util.List.of("preset_id", "contract_version", "display_snapshot")) {
            var obj = new com.google.gson.JsonObject(); obj.addProperty(key, 1);
            assertThrows(IllegalArgumentException.class, () -> ItemNbt.parse(obj));
        }
        var big = new com.google.gson.JsonObject(); big.addProperty("big", "x".repeat(65536));
        assertThrows(IllegalArgumentException.class, () -> ItemNbt.parse(big));
    }
}
