package ke.writ;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoolDataTest {
    @Test void disabledPoolFlagIsStrictAndLegacyDefaultsToEnabled() {
        var enabled = PoolData.parse("x:p", JsonParser.parseString("{\"weight\":0,\"kind\":\"purpose\",\"label\":\"P\"}").getAsJsonObject());
        var disabled = PoolData.parse("x:p", JsonParser.parseString("{\"weight\":0,\"kind\":\"purpose\",\"label\":\"P\",\"disabled\":true}").getAsJsonObject());
        assertFalse(enabled.disabled()); assertTrue(disabled.disabled());
        assertThrows(IllegalArgumentException.class, () -> PoolData.parse("x:p", JsonParser.parseString("{\"weight\":0,\"kind\":\"purpose\",\"label\":\"P\",\"disabled\":\"true\"}").getAsJsonObject()));
        var legacy = new net.minecraft.resources.ResourceLocation("writ", "loot/global");
        var file = new net.minecraft.resources.ResourceLocation("writ", "loot/pools/p");
        var merged = PoolData.load(java.util.Map.of(legacy, JsonParser.parseString("{\"rarities\":{\"p\":1}}"), file, JsonParser.parseString("{\"weight\":0,\"kind\":\"purpose\",\"label\":\"P\",\"disabled\":true}")), new java.util.ArrayList<>());
        assertTrue(merged.get("writ:p").disabled());
        var invalid = PoolData.load(java.util.Map.of(legacy, JsonParser.parseString("{\"rarities\":{\"p\":1}}"), file, JsonParser.parseString("{\"weight\":0,\"kind\":\"purpose\",\"label\":\"P\",\"disabled\":\"true\"}")), new java.util.ArrayList<>());
        assertFalse(invalid.containsKey("writ:p"), "invalid per-pool disabled value must not fall back to legacy");
    }
    @Test void idsAndAliases() {
        assertEquals("writ:n", PoolData.id("n"));
        assertEquals("mypack:n", PoolData.id("mypack:n"));
        assertThrows(IllegalArgumentException.class, () -> PoolData.id("Bad Name"));
        assertEquals("writ:n", PoolData.presetPool(JsonParser.parseString("{\"pool\":\"n\",\"rarity\":\"writ:n\"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PoolData.presetPool(JsonParser.parseString("{\"pool\":\"n\",\"rarity\":\"r\"}").getAsJsonObject()));
    }
    @Test void sourceAliasesAndValidation() {
        var both = JsonParser.parseString("{\"pool_weights\":{\"n\":3},\"rarity_weights\":{\"writ:n\":3}}").getAsJsonObject();
        assertEquals(java.util.Map.of("writ:n", 3), PoolData.overrides(both));
        assertThrows(IllegalArgumentException.class, () -> PoolData.overrides(JsonParser.parseString("{\"pool_weights\":{\"n\":1},\"rarity_weights\":{\"n\":2}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PoolData.overrides(JsonParser.parseString("{\"pool_weights\":{\"n\":-1}}").getAsJsonObject()));
    }
    @Test void poolSchemaAndSeededSamplingRespectEnabledPools() {
        var json = JsonParser.parseString("{\"weight\":282,\"kind\":\"rarity\",\"label\":\"N\"}").getAsJsonObject();
        var pool = PoolData.parse("writ:n", json);
        assertEquals(282, pool.weight()); assertEquals("N", pool.label());
        assertThrows(IllegalArgumentException.class, () -> PoolData.parse("writ:n", JsonParser.parseString("{\"weight\":-1,\"kind\":\"rarity\",\"label\":\"N\"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PoolData.parse("writ:n", JsonParser.parseString("{\"weight\":1,\"kind\":\"other\",\"label\":\"N\"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PoolData.parse("writ:n", JsonParser.parseString("{\"weight\":1,\"kind\":\"rarity\",\"label\":\"\"}").getAsJsonObject()));
        var candidates = java.util.List.of(new LootRules.Candidate("a", "writ:n", 1), new LootRules.Candidate("b", "writ:r", 1), new LootRules.Candidate("c", "writ:ur", 1));
        var random = new java.util.Random(117);
        for (int i = 0; i < 100; i++) assertTrue(java.util.Set.of("a", "c").contains(LootRules.select(candidates, java.util.Map.of("writ:n", 282, "writ:r", 0, "writ:ur", 31), java.util.Map.of(), random::nextLong).orElseThrow()));
        assertTrue(LootRules.select(candidates, java.util.Map.of(), java.util.Map.of(), random::nextLong).isEmpty());
    }
    @Test void legacyAndPoolFilesMergeAndInvalidInputsDoNotDisableValidPools() {
        var global = new net.minecraft.resources.ResourceLocation("writ", "loot/global");
        var file = new net.minecraft.resources.ResourceLocation("writ", "loot/pools/n");
        var legacy = JsonParser.parseString("{\"rarities\":{\"n\":100}}");
        var perPool = JsonParser.parseString("{\"weight\":7,\"kind\":\"rarity\",\"label\":\"N\"}");
        var errors = new java.util.ArrayList<String>();
        assertEquals(7, PoolData.load(java.util.Map.of(global, legacy, file, perPool), errors).get("writ:n").weight());
        assertFalse(errors.isEmpty());
        assertEquals(7, PoolData.load(java.util.Map.of(file, perPool), new java.util.ArrayList<>()).get("writ:n").weight());
        assertEquals(7, PoolData.load(java.util.Map.of(global, JsonParser.parseString("{\"rarities\":{\"n\":-1}}"), file, perPool), new java.util.ArrayList<>()).get("writ:n").weight());
        assertTrue(PoolData.load(java.util.Map.of(global, JsonParser.parseString("{\"rarities\":{\"n\":-1}}"), file, JsonParser.parseString("{\"weight\":-1}")), new java.util.ArrayList<>()).isEmpty());
        var legacyOnly = PoolData.load(java.util.Map.of(global, legacy), new java.util.ArrayList<>()).get("writ:n");
        assertEquals(100, legacyOnly.weight());
        assertEquals("writ.rarity.n", legacyOnly.translationKey());
    }
}
