package ke.writ;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

final class SourceData {
    static List<String> tables(JsonObject json) {
        boolean single = json.has("loot_table"), multiple = json.has("loot_tables");
        if (single == multiple) throw new IllegalArgumentException("exactly one of loot_table or loot_tables is required");
        List<String> result = new ArrayList<>();
        if (single) result.add(table(json.get("loot_table")));
        else {
            for (JsonElement value : GsonHelper.getAsJsonArray(json, "loot_tables")) result.add(table(value));
            if (result.isEmpty()) throw new IllegalArgumentException("loot_tables must not be empty");
        }
        if (new HashSet<>(result).size() != result.size()) throw new IllegalArgumentException("duplicate loot table in source");
        return List.copyOf(result);
    }
    private static String table(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("loot table must be a string");
        return new ResourceLocation(value.getAsString()).toString();
    }
    static void validateBossFields(JsonObject json) {
        if (json.has("entities")) throw new IllegalArgumentException("entities is not supported; use entity");
    }
}
