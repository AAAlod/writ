package ke.writ;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotViewTest {
    @Test void optionalVariantFieldsAreTolerant() {
        var tag = valid();
        tag.putString("variant_id", "blue"); tag.putString("variant_label", "蓝色");
        tag.putInt("variant_index", 13); tag.putInt("variant_total", 17);
        var view = SnapshotView.parse(root(tag));
        assertEquals("blue", view.variantId()); assertEquals(13, view.variantIndex()); assertEquals(17, view.variantTotal());
        tag.putString("variant_index", "bad"); tag.putInt("variant_label", 2);
        view = SnapshotView.parse(root(tag));
        assertEquals(SnapshotView.Kind.V2, view.kind()); assertEquals(0, view.variantIndex()); assertEquals("", view.variantLabel());
    }
    private CompoundTag stack(String id, int count) { var t = new CompoundTag(); t.putString("id", id); t.putInt("count", count); return t; }
    private CompoundTag root(CompoundTag snapshot) { var t = new CompoundTag(); t.put("display_snapshot", snapshot); return t; }
    private CompoundTag valid() {
        var tag = new CompoundTag(); tag.putInt("format", 2); tag.putString("rarity", "rare");
        var professions = new ListTag(); professions.add(StringTag.valueOf("minecraft:librarian")); tag.put("professions", professions);
        tag.put("cost_a", stack("minecraft:emerald", 4)); tag.put("cost_b", stack("minecraft:book", 1)); tag.put("result", stack("minecraft:bread", 2)); return tag;
    }
    @Test void parsesV2IncludingOptionalPaymentAndEmptyProfessions() {
        var tag = valid(); var view = SnapshotView.parse(root(tag));
        assertEquals(SnapshotView.Kind.V2, view.kind()); assertEquals("rare", view.rarity());
        assertEquals(java.util.List.of("minecraft:librarian"), view.professions());
        assertEquals(new SnapshotView.Stack("minecraft:emerald", 4), view.costA());
        assertEquals(new SnapshotView.Stack("minecraft:book", 1), view.costB());
        tag.remove("cost_b"); tag.put("professions", new ListTag());
        view = SnapshotView.parse(root(tag)); assertEquals(SnapshotView.Kind.V2, view.kind()); assertNull(view.costB()); assertTrue(view.professions().isEmpty());
    }
    @Test void parsesLegacyWithoutInterpretingText() {
        var tag = new CompoundTag(); tag.putString("name", "旧名称"); tag.putString("rarity", "rare"); tag.putString("professions", "Any profession"); tag.putString("summary", "4 emeralds -> 8 bread");
        var view = SnapshotView.parse(root(tag)); assertEquals(SnapshotView.Kind.LEGACY, view.kind());
        assertEquals(java.util.List.of("旧名称", "rare", "Any profession", "4 emeralds -> 8 bread"), view.legacy());
    }
    @Test void parsesOptionalPoolMetadataAndRejectsWrongTypes() {
        var tag = valid(); var old = SnapshotView.parse(root(tag)); assertEquals("", old.poolLabel());
        tag.putString("pool_id", "writ:n"); tag.putString("pool_kind", "rarity"); tag.putString("pool_label", "N"); tag.putString("pool_translation_key", "example.pool.n");
        var view = SnapshotView.parse(root(tag)); assertEquals("writ:n", view.poolId()); assertEquals("N", view.poolLabel()); assertEquals("example.pool.n", view.poolTranslationKey());
        tag.putInt("pool_label", 1); assertEquals(SnapshotView.Kind.INVALID, SnapshotView.parse(root(tag)).kind());
    }
    @Test void presetNameFieldsAndValidation() {
        assertThrows(IllegalArgumentException.class, () -> PresetChecks.displayName(""));
        assertDoesNotThrow(() -> PresetChecks.displayName(null));
        var tag = valid(); tag.putString("preset_name", "Miner"); tag.putString("preset_translation_key", "pack.preset.miner");
        var view = SnapshotView.parse(root(tag)); assertEquals("Miner", view.presetName()); assertEquals("pack.preset.miner", view.presetTranslationKey());
    }
    @Test void handlesMissingMalformedAndFutureFormats() {
        assertEquals(SnapshotView.Kind.MISSING, SnapshotView.parse(null).kind());
        assertEquals(SnapshotView.Kind.MISSING, SnapshotView.parse(new CompoundTag()).kind());
        var invalid = valid(); invalid.putString("format", "2"); assertEquals(SnapshotView.Kind.INVALID, SnapshotView.parse(root(invalid)).kind());
        invalid = valid(); invalid.putString("cost_a", "bad"); assertEquals(SnapshotView.Kind.INVALID, SnapshotView.parse(root(invalid)).kind());
        invalid = valid(); invalid.getCompound("result").putInt("count", -1); assertEquals(SnapshotView.Kind.INVALID, SnapshotView.parse(root(invalid)).kind());
        invalid = valid(); var ints = new ListTag(); ints.add(IntTag.valueOf(1)); invalid.put("professions", ints); assertEquals(SnapshotView.Kind.INVALID, SnapshotView.parse(root(invalid)).kind());
        invalid = valid(); invalid.putInt("format", 4); assertEquals(SnapshotView.Kind.UNSUPPORTED, SnapshotView.parse(root(invalid)).kind());
    }
    @Test void parsesV3AndRejectsMalformedStacks() {
        var tag = valid(); tag.putInt("format", 3);
        for (String key : java.util.List.of("cost_a", "cost_b", "result")) {
            var item = tag.getCompound(key); int count = item.getInt("count"); item.remove("count"); item.putByte("Count", (byte) count);
        }
        var itemTag = new CompoundTag(); itemTag.putString("custom", "value"); tag.getCompound("cost_a").put("tag", itemTag);
        var view = SnapshotView.parse(root(tag)); assertEquals(SnapshotView.Kind.V3, view.kind()); assertEquals("value", view.costA().serialized().getCompound("tag").getString("custom"));
        tag.remove("cost_b"); assertNull(SnapshotView.parse(root(tag)).costB());
        tag.getCompound("result").putString("Count", "bad"); assertEquals(SnapshotView.Kind.INVALID, SnapshotView.parse(root(tag)).kind());
    }
}
