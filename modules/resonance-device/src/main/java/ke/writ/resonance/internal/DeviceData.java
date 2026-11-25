package ke.writ.resonance.internal;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and writes the device's persistent item data.
 *
 * <p>The owner is stored with Minecraft's standard UUID NBT representation (an int array of four
 * values), exactly as produced by {@link CompoundTag#putUUID(String, UUID)}. Item data is only ever
 * written by the server, from the actual acting player.</p>
 */
public final class DeviceData {

    public static final String TAG_OWNER = "owner";
    public static final String TAG_EFFECTS = "effects";
    public static final String TAG_ID = "id";
    public static final String TAG_AMPLIFIER = "amplifier";
    public static final String TAG_DURATION_TICKS = "duration_ticks";

    private DeviceData() {
    }

    /** Returns the stored owner, or empty when the item is unbound (or the owner tag is malformed). */
    public static Optional<UUID> getOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_OWNER, Tag.TAG_INT_ARRAY)) {
            return Optional.empty();
        }
        Tag raw = tag.get(TAG_OWNER);
        if (!(raw instanceof IntArrayTag array) || array.getAsIntArray().length != 4) {
            return Optional.empty();
        }
        int[] parts = array.getAsIntArray();
        long most = ((long) parts[0] << 32) | (parts[1] & 0xFFFFFFFFL);
        long least = ((long) parts[2] << 32) | (parts[3] & 0xFFFFFFFFL);
        return Optional.of(new UUID(most, least));
    }

    /** Binds the device to {@code owner} using Minecraft's UUID NBT representation. */
    public static void setOwner(ItemStack stack, UUID owner) {
        stack.getOrCreateTag().putUUID(TAG_OWNER, owner);
    }

    /** Returns the raw item tag, or {@code null} when the stack carries no tag. */
    public static CompoundTag getTagOrNull(ItemStack stack) {
        return stack.getTag();
    }

    /** Sets the explicit effects list on the item (used by tests and administrators). */
    public static void setEffects(ItemStack stack, ListTag effects) {
        stack.getOrCreateTag().put(TAG_EFFECTS, effects);
    }
}
