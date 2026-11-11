package ke.writ;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class VariantRules {
    static boolean canRequest(boolean normal, CompoundTag tag, int count) {
        return normal && count > 0 && tag != null && tag.contains("preset_id", Tag.TAG_STRING)
                && !tag.getString("preset_id").isBlank() && tag.getString("preset_id").length() <= 256
                && (!tag.contains("contract_version") || (tag.contains("contract_version", Tag.TAG_INT) && tag.getInt("contract_version") == PresetChecks.CONTRACT_VERSION))
                && (!tag.contains("selected_variant", Tag.TAG_STRING) || tag.getString("selected_variant").length() <= 128);
    }
    static String selected(CompoundTag tag) { return tag.contains("selected_variant", Tag.TAG_STRING) ? tag.getString("selected_variant") : ""; }
}
