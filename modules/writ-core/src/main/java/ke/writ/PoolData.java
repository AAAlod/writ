package ke.writ;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

final class PoolData {
    record Pool(String id, int weight, String kind, String label, String translationKey, boolean disabled) {
        Pool(String id, int weight, String kind, String label, String translationKey) { this(id, weight, kind, label, translationKey, false); }
    }

    static Map<String, Pool> load(Map<ResourceLocation, JsonElement> objects, List<String> diagnostics) {
        Map<String, Pool> pools = new TreeMap<>();
        ResourceLocation global = new ResourceLocation("writ", "loot/global");
        try {
            if (objects.containsKey(global)) {
                var legacy = weights(GsonHelper.getAsJsonObject(objects.get(global).getAsJsonObject(), "rarities"));
                legacy.forEach((id, weight) -> {
                    ResourceLocation parsed = new ResourceLocation(id);
                    String path = parsed.getPath();
                    String translationKey = parsed.getNamespace().equals(Writ.ID) ? "writ.rarity." + path : null;
                    pools.put(id, new Pool(id, weight, "rarity", path, translationKey));
                });
            }
        } catch (RuntimeException e) { diagnostics.add("legacy global: " + e.getMessage()); }
        objects.entrySet().stream().sorted(Map.Entry.comparingByKey()).filter(e -> e.getKey().getPath().startsWith("loot/pools/")).forEach(e -> {
            String id = null;
            try {
                var resource = e.getKey(); id = id(resource.getNamespace() + ":" + resource.getPath().substring("loot/pools/".length()));
                Pool pool = parse(id, GsonHelper.convertToJsonObject(e.getValue(), "pool"));
                if (pools.put(id, pool) != null) diagnostics.add("per-pool definition overrides legacy pool " + id);
            } catch (RuntimeException ex) {
                if (id != null) pools.remove(id);
                diagnostics.add(e.getKey() + ": " + ex.getMessage());
            }
        });
        return Map.copyOf(pools);
    }

    static String id(String value) {
        String full = value.contains(":") ? value : "writ:" + value;
        ResourceLocation parsed = ResourceLocation.tryParse(full);
        if (parsed == null || value.isEmpty()) throw new IllegalArgumentException("invalid pool ID " + value);
        return parsed.toString();
    }

    static int weight(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber() || !element.getAsString().matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("weight must be a non-negative integer");
        return Integer.parseInt(element.getAsString());
    }

    static Pool parse(String id, JsonObject json) {
        int weight = weight(json.get("weight"));
        String kind = GsonHelper.getAsString(json, "kind"), label = GsonHelper.getAsString(json, "label");
        if (!kind.equals("rarity") && !kind.equals("purpose")) throw new IllegalArgumentException("invalid kind " + kind);
        if (label.isEmpty()) throw new IllegalArgumentException("empty label");
        String key = json.has("translation_key") ? GsonHelper.getAsString(json, "translation_key") : null;
        boolean disabled = false;
        if (json.has("disabled")) {
            var value = json.get("disabled");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("disabled must be boolean");
            disabled = value.getAsBoolean();
        }
        return new Pool(id, weight, kind, label, key, disabled);
    }

    static Map<String, Integer> weights(JsonObject json) {
        Map<String, Integer> result = new TreeMap<>();
        json.entrySet().forEach(e -> {
            String id = id(e.getKey()); int value = weight(e.getValue());
            if (result.putIfAbsent(id, value) != null) throw new IllegalArgumentException("duplicate normalized pool " + id);
        });
        return Map.copyOf(result);
    }

    static Map<String, Integer> overrides(JsonObject json) {
        Map<String, Integer> legacy = json.has("rarity_weights") ? weights(GsonHelper.getAsJsonObject(json, "rarity_weights")) : null;
        Map<String, Integer> modern = json.has("pool_weights") ? weights(GsonHelper.getAsJsonObject(json, "pool_weights")) : null;
        if (legacy != null && modern != null && !legacy.equals(modern)) throw new IllegalArgumentException("conflicting pool_weights and rarity_weights");
        return modern != null ? modern : legacy != null ? legacy : Map.of();
    }
    static boolean inheritGlobalWeights(JsonObject json) {
        if (!json.has("inherit_global_weights")) return true;
        JsonElement value = json.get("inherit_global_weights");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("inherit_global_weights must be boolean");
        return value.getAsBoolean();
    }

    static String presetPool(JsonObject json) {
        String modern = json.has("pool") ? id(GsonHelper.getAsString(json, "pool")) : null;
        String legacy = json.has("rarity") ? id(GsonHelper.getAsString(json, "rarity")) : null;
        if (modern != null && legacy != null && !modern.equals(legacy)) throw new IllegalArgumentException("conflicting pool and rarity");
        if (modern == null && legacy == null) throw new IllegalArgumentException("pool or rarity required");
        return modern != null ? modern : legacy;
    }
}
