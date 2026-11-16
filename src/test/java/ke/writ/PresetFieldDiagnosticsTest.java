package ke.writ;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PresetFieldDiagnosticsTest {
    @Test void reportsIgnoredFieldsWithLocationWithoutRemovingThem() {
        var json = JsonParser.parseString("""
                {"pool":"n", "allowed_profession":["minecraft:farmer"], "loot_mode":["TRADE"],
                 "trade":{"cost_a":{}, "max_use":4},
                 "dynamic_result":{"tag":"minecraft:logs", "exclude_tags":"writ:blocked"},
                 "extension_metadata": {"purpose":"example"}}
                """).getAsJsonObject();
        assertEquals(List.of("allowed_profession", "extension_metadata", "loot_mode", "trade.max_use", "dynamic_result.exclude_tags"), PresetChecks.unknownFields(json));
        assertEquals("example", json.getAsJsonObject("extension_metadata").get("purpose").getAsString());
    }
}
