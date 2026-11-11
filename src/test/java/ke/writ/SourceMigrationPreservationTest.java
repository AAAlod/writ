package ke.writ;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;

class SourceMigrationPreservationTest {
    /** Pre-redesign presets folded into a parent: old ID -> parent preset and variant (null = parent default). */
    static final java.util.Map<String, String[]> MERGED = java.util.Map.of(
            "processing_strip_oak_log", new String[]{"log_stripping", null}, "processing_strip_spruce_log", new String[]{"log_stripping", "spruce"},
            "processing_raw_iron_iron_ingot", new String[]{"ore_smelting", null}, "processing_raw_copper_copper_ingot", new String[]{"ore_smelting", "copper"},
            "processing_raw_gold_gold_ingot", new String[]{"ore_smelting", "gold"}, "processing_cactus_green_dye", new String[]{"dye_smelting", null},
            "processing_sea_pickle_lime_dye", new String[]{"dye_smelting", "sea_pickle"}, "processing_sand_glass", new String[]{"glass", "smelt_sand"}, "processing_wet_sponge_dry", new String[]{"sponge_supply", "dry_wet"});
    /** Presets whose price was deliberately changed by the trade-system rebalance (see docs/content-catalog.md). */
    static final Set<String> REPRICED = Set.of("golden_apple_supply", "heart_of_the_sea_supply", "potion_turtle_master", "glass");
    @Test void tradeSystemRedesignKeepsEveryPreviousTrade() throws Exception {
        try (var stream=getClass().getResourceAsStream("/migration/trade-system-before.json")) {
            assertNotNull(stream);
            var before=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(72,before.size());
            for (var entry:before.entrySet()) {
                String id=entry.getKey(); var old=entry.getValue().getAsJsonObject();
                if (MERGED.containsKey(id)) {
                    assertFalse(Files.exists(Path.of("src/main/resources/data/writ/writs/presets",id+".json")),"merged preset still present "+id);
                    String[] target=MERGED.get(id);
                    assertEquals(effective(old,null),effective(readPreset(target[0]),target[1]),"merged trade changed "+id);
                    continue;
                }
                assertTrue(Files.exists(Path.of("src/main/resources/data/writ/writs/presets",id+".json")),"preset silently removed "+id);
                var now=readPreset(id);
                if (!REPRICED.contains(id)) assertEquals(effective(old,null),effective(now,null),"default trade changed "+id);
                if (old.has("variants")) for (var raw:old.getAsJsonArray("variants")) {
                    var variant=raw.getAsJsonObject(); String vid=variant.get("id").getAsString();
                    if (!REPRICED.contains(id)) assertEquals(effective(old,vid),effective(now,vid),"variant changed "+id+"/"+vid);
                    boolean wasEnabled=!variant.has("enabled")||variant.get("enabled").getAsBoolean();
                    JsonObject current=null; for (var e:now.getAsJsonArray("variants")) if (e.getAsJsonObject().get("id").getAsString().equals(vid)) current=e.getAsJsonObject();
                    assertEquals(wasEnabled,!current.has("enabled")||current.get("enabled").getAsBoolean(),"variant enablement changed "+id+"/"+vid);
                }
            }
        }
    }
    record Mapping(String oldId,String parent,String variant) { }
    private static JsonObject readPreset(String id) throws Exception {
        try(var r=Files.newBufferedReader(Path.of("src/main/resources/data/writ/writs/presets",id+".json"),StandardCharsets.UTF_8)) { return JsonParser.parseReader(r).getAsJsonObject(); }
    }
    private static JsonObject effective(JsonObject parent,String id) {
        var trade=parent.getAsJsonObject("trade").deepCopy();
        if(id!=null) {
            JsonObject found=null;
            for(var e:parent.getAsJsonArray("variants")) if(e.getAsJsonObject().get("id").getAsString().equals(id)) found=e.getAsJsonObject();
            assertNotNull(found,"variant missing "+id);
            if(found.has("trade")) for(var e:found.getAsJsonObject("trade").entrySet()) trade.add(e.getKey(),e.getValue().deepCopy());
            if(found.has("max_uses")) trade.add("max_uses",found.get("max_uses").deepCopy());
        }
        var result=new JsonObject(); for(String key:List.of("cost_a","cost_b","result","max_uses")) if(trade.has(key)) result.add(key,trade.get(key));
        return result;
    }
    @Test void allPreviousMasonTradesRemainInNewGroups() throws Exception {
        var mappings=List.of(new Mapping("stone_bricks","stone_basics",null),new Mapping("smooth_stone","stone_basics","smooth_stone"),new Mapping("sandstone","stone_basics","sandstone"),new Mapping("mud_bricks","stone_basics","mud_bricks"),new Mapping("bricks","masonry_blocks",null),new Mapping("deepslate_bricks","masonry_blocks","deepslate_bricks"),new Mapping("deepslate_tiles","masonry_blocks","deepslate_tiles"),new Mapping("nether_bricks","nether_blocks",null),new Mapping("polished_blackstone_bricks","nether_blocks","polished_blackstone_bricks"),new Mapping("quartz_block","nether_blocks","quartz_block"),new Mapping("prismarine_bricks","ocean_blocks",null),new Mapping("sea_lantern","ocean_blocks","sea_lantern"),new Mapping("processing_white_concrete","concrete_curing",null),new Mapping("processing_gray_concrete","concrete_curing","gray"),new Mapping("processing_light_gray_concrete","concrete_curing","light_gray"),new Mapping("processing_black_concrete","concrete_curing","black"),new Mapping("processing_stone_bricks_cracked","cracked_bricks",null),new Mapping("processing_deepslate_bricks_cracked","cracked_bricks","deepslate"));
        try(var stream=getClass().getResourceAsStream("/migration/mason-premerge.json")) {
            assertNotNull(stream); var old=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject(); assertEquals(18,old.size());
            for(var m:mappings) {
                assertFalse(Files.exists(Path.of("src/main/resources/data/writ/writs/presets",m.oldId()+".json")),"old preset still present "+m.oldId());
                assertEquals(effective(old.getAsJsonObject(m.oldId()),null),effective(readPreset(m.parent()),m.variant()),"trade changed "+m.oldId());
            }
            assertEquals(effective(old.getAsJsonObject("sea_lantern"),"shards"),effective(readPreset("ocean_blocks"),"shards"),"shards trade changed");
        }
    }
}
