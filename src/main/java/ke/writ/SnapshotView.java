package ke.writ;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Parses display data only. It never authorizes or constructs a server offer. */
final class SnapshotView {
    enum Kind { MISSING, INVALID, LEGACY, V2, V3, UNSUPPORTED }
    record Stack(String id, int count, CompoundTag serialized) {
        Stack(String id, int count) { this(id, count, null); }
    }
    record View(Kind kind, String rarity, List<String> professions, Stack costA, Stack costB, Stack result, List<String> legacy,
                String poolId, String poolKind, String poolLabel, String poolTranslationKey, String presetName, String presetTranslationKey,
                String variantId, String variantLabel, String variantKey, int variantIndex, int variantTotal) { }
    private static View empty(Kind kind) { return new View(kind, "", List.of(), null, null, null, List.of(), "", "", "", "", "", "", "", "", "", 0, 0); }
    static View parse(CompoundTag root) {
        if (root == null || !root.contains("display_snapshot")) return empty(Kind.MISSING);
        if (!root.contains("display_snapshot", Tag.TAG_COMPOUND)) return empty(Kind.INVALID);
        CompoundTag tag = root.getCompound("display_snapshot");
        if (!tag.contains("format")) {
            List<String> values = new ArrayList<>();
            for (String key : List.of("name", "rarity", "professions", "summary")) {
                if (tag.contains(key) && !tag.contains(key, Tag.TAG_STRING)) return empty(Kind.INVALID);
                if (tag.contains(key, Tag.TAG_STRING)) values.add(tag.getString(key));
            }
            return values.isEmpty() ? empty(Kind.INVALID) : new View(Kind.LEGACY, "", List.of(), null, null, null, List.copyOf(values), "", "", "", "", "", "", "", "", "", 0, 0);
        }
        if (!tag.contains("format", Tag.TAG_INT)) return empty(Kind.INVALID);
        int format = tag.getInt("format");
        if (format > 3) return empty(Kind.UNSUPPORTED);
        if ((format != 2 && format != 3) || !tag.contains("rarity", Tag.TAG_STRING) || !tag.contains("professions", Tag.TAG_LIST)) return empty(Kind.INVALID);
        ListTag raw = tag.getList("professions", Tag.TAG_STRING);
        if (!(tag.get("professions") instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_STRING)) return empty(Kind.INVALID);
        List<String> professions = new ArrayList<>(); for (int i = 0; i < raw.size(); i++) professions.add(raw.getString(i));
        Stack a = stack(tag, "cost_a", format), result = stack(tag, "result", format), b = tag.contains("cost_b") ? stack(tag, "cost_b", format) : null;
        if (a == null || result == null || (tag.contains("cost_b") && b == null)) return empty(Kind.INVALID);
        for (String key : List.of("pool_id", "pool_kind", "pool_label", "pool_translation_key", "preset_name", "preset_translation_key"))
            if (tag.contains(key) && !tag.contains(key, Tag.TAG_STRING)) return empty(Kind.INVALID);
        return new View(format == 2 ? Kind.V2 : Kind.V3, tag.getString("rarity"), List.copyOf(professions), a, b, result, List.of(),
                tag.getString("pool_id"), tag.getString("pool_kind"), tag.getString("pool_label"), tag.getString("pool_translation_key"), tag.getString("preset_name"), tag.getString("preset_translation_key"),
                tag.contains("variant_id", Tag.TAG_STRING) ? tag.getString("variant_id") : "", tag.contains("variant_label", Tag.TAG_STRING) ? tag.getString("variant_label") : "", tag.contains("variant_translation_key", Tag.TAG_STRING) ? tag.getString("variant_translation_key") : "",
                tag.contains("variant_index", Tag.TAG_INT) ? tag.getInt("variant_index") : 0, tag.contains("variant_total", Tag.TAG_INT) ? tag.getInt("variant_total") : 0);
    }
    private static Stack stack(CompoundTag parent, String key, int format) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = parent.getCompound(key);
        if (!tag.contains("id", Tag.TAG_STRING) || tag.getString("id").isEmpty()) return null;
        if (format == 3) {
            if (!tag.contains("Count", Tag.TAG_BYTE) || tag.getByte("Count") <= 0 || (tag.contains("tag") && !tag.contains("tag", Tag.TAG_COMPOUND))) return null;
            return new Stack(tag.getString("id"), tag.getByte("Count"), tag.copy());
        }
        if (!tag.contains("count", Tag.TAG_INT) || tag.getInt("count") <= 0) return null;
        return new Stack(tag.getString("id"), tag.getInt("count"));
    }
}
