package ke.writ;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class PresetChecks {
    static final int CONTRACT_VERSION = 1;
    private PresetChecks() { }
    private static final Set<String> PRESET_FIELDS = Set.of("pool", "rarity", "weight", "loot_modes", "allowed_professions", "trade", "display_name", "display_translation_key", "default_variant_label", "default_variant_translation_key", "variants", "sources", "general_loot", "dynamic_result");
    private static final Set<String> TRADE_FIELDS = Set.of("cost_a", "cost_b", "result", "max_uses", "villager_xp", "price_multiplier");
    private static final Set<String> DYNAMIC_FIELDS = Set.of("tag", "exclude_tag");
    /** Warn on ignored fields without rejecting extension metadata used by existing datapacks. */
    static List<String> unknownFields(JsonObject json) {
        List<String> unknown = new ArrayList<>();
        json.keySet().stream().filter(k -> !PRESET_FIELDS.contains(k)).sorted().forEach(unknown::add);
        for (var section : List.of("trade", "dynamic_result")) {
            if (!json.has(section) || !json.get(section).isJsonObject()) continue;
            Set<String> allowed = section.equals("trade") ? TRADE_FIELDS : DYNAMIC_FIELDS;
            json.getAsJsonObject(section).keySet().stream().filter(k -> !allowed.contains(k)).sorted().forEach(k -> unknown.add(section + "." + k));
        }
        return List.copyOf(unknown);
    }
    static boolean supportedContract(boolean hasId, boolean hasIntVersion, int version) {
        return hasId && (!hasIntVersion || version == CONTRACT_VERSION);
    }
    static void weight(int weight) { if (weight < 0) throw new IllegalArgumentException("negative weight"); }
    static void displayName(String name) { if (name != null && name.isEmpty()) throw new IllegalArgumentException("display_name must not be empty"); }
    static void item(String id, boolean exists, boolean empty, int count, int maxStack) {
        if (!exists || empty || count < 1 || count > maxStack) throw new IllegalArgumentException("invalid item or count: " + id + " x " + count);
    }
    static void trade(int uses, int xp, float multiplier) {
        if (uses <= 0 || xp < 0 || !Float.isFinite(multiplier) || multiplier < 0) throw new IllegalArgumentException("invalid trade parameters");
    }
}
