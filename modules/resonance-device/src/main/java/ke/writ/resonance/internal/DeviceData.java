package ke.writ.resonance.internal;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

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
 *
 * <p>The {@code owner} field is parsed as a tri-state {@link OwnerState}: absent means the device is
 * unbound and may be bound, a well-formed four-int UUID means it is bound, and a present but
 * malformed field is {@link OwnerState.Invalid}. A malformed field must never be treated as "no
 * owner", because that would silently re-bind a corrupted device to the next player.</p>
 */
public final class DeviceData {

    public static final String TAG_OWNER = "owner";
    public static final String TAG_EFFECTS = "effects";
    public static final String TAG_ID = "id";
    public static final String TAG_AMPLIFIER = "amplifier";
    public static final String TAG_DURATION_TICKS = "duration_ticks";

    /**
     * Tri-state view of the persisted {@code owner} field.
     *
     * <p>Callers must keep the three cases distinct: only {@link Unbound} may be bound, only
     * {@link Bound} may be authorised against the acting player, and {@link Invalid} must be
     * rejected (while leaving the item tag untouched) with a corruption prompt.</p>
     */
    public sealed interface OwnerState {

        /** No {@code owner} field at all: the device is unbound and a first click may bind it. */
        record Unbound() implements OwnerState {
        }

        /** A well-formed four-int UUID: the device is bound to that player. */
        record Bound(UUID owner) implements OwnerState {
        }

        /** The {@code owner} field exists but cannot be trusted; it must never be re-bound or used. */
        record Invalid(String detail) implements OwnerState {
        }
    }

    private DeviceData() {
    }

    /**
     * Reads the {@code owner} field out of a raw item tag.
     *
     * <p>Pure function: it never mutates {@code tag}, never touches an {@link ItemStack} and never
     * throws for malformed input. Missing key (or a {@code null} tag) is {@link OwnerState.Unbound};
     * a present value of the wrong type or the wrong length is {@link OwnerState.Invalid} with a
     * diagnostic detail; a four-int array is decoded with Minecraft's own UUID arithmetic, including
     * values that use the sign bit.</p>
     *
     * @param tag the item compound tag, may be {@code null}
     * @return the tri-state owner field, never {@code null}
     */
    static OwnerState readOwner(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains(TAG_OWNER)) {
            return new OwnerState.Unbound();
        }
        Tag raw = tag.get(TAG_OWNER);
        if (!(raw instanceof IntArrayTag array)) {
            return new OwnerState.Invalid("owner must be an int array of 4 ints, got " + typeName(raw));
        }
        int[] parts = array.getAsIntArray();
        if (parts.length != 4) {
            return new OwnerState.Invalid(
                    "owner must be an int array of 4 ints, got " + parts.length + " ints");
        }
        long most = ((long) parts[0] << 32) | (parts[1] & 0xFFFFFFFFL);
        long least = ((long) parts[2] << 32) | (parts[3] & 0xFFFFFFFFL);
        return new OwnerState.Bound(new UUID(most, least));
    }

    /**
     * Returns the tri-state owner of {@code stack}.
     *
     * @param stack the device item stack
     * @return the tri-state owner field, never {@code null}
     */
    public static OwnerState getOwnerState(ItemStack stack) {
        return readOwner(stack.getTag());
    }

    /**
     * Returns the stored owner, or empty when the item is unbound or its owner field is malformed.
     *
     * <p>This is the convenience accessor for authorisation checks that only care about a usable
     * UUID. Callers that must distinguish "unbound" from "corrupted" use
     * {@link #getOwnerState(ItemStack)} or {@link #readOwner(CompoundTag)} instead.</p>
     */
    public static Optional<UUID> getOwner(ItemStack stack) {
        return getOwnerState(stack) instanceof OwnerState.Bound bound
                ? Optional.of(bound.owner())
                : Optional.empty();
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

    /**
     * Human-readable name of an NBT tag's type, used in {@link OwnerState.Invalid} details.
     *
     * @param tag the tag, may be {@code null}
     */
    private static String typeName(@Nullable Tag tag) {
        if (tag == null) {
            return "nothing";
        }
        return switch (tag.getId()) {
            case Tag.TAG_END -> "end";
            case Tag.TAG_BYTE -> "byte";
            case Tag.TAG_SHORT -> "short";
            case Tag.TAG_INT -> "int";
            case Tag.TAG_LONG -> "long";
            case Tag.TAG_FLOAT -> "float";
            case Tag.TAG_DOUBLE -> "double";
            case Tag.TAG_BYTE_ARRAY -> "byte array";
            case Tag.TAG_STRING -> "string";
            case Tag.TAG_LIST -> "list";
            case Tag.TAG_COMPOUND -> "compound";
            case Tag.TAG_INT_ARRAY -> "int array";
            case Tag.TAG_LONG_ARRAY -> "long array";
            default -> "nbt type " + tag.getId();
        };
    }
}
