package ke.writ.resonance.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Parses the item's {@code effects} compound-list tag. Pure logic: no registries, no side effects.
 *
 * <p>Rules (see the module specification):</p>
 * <ul>
 *   <li>absent {@code effects} tag, or an empty list, requests the host default effect;</li>
 *   <li>every entry must be a compound with a string {@code id};</li>
 *   <li>the optional {@code duration_ticks} must be a positive int;</li>
 *   <li>the optional {@code amplifier} must be an int from 0 to 127;</li>
 *   <li>duplicate ids and unparsable ids are configuration errors;</li>
 *   <li>the whole list is rejected if any entry is invalid.</li>
 * </ul>
 */
public final class EffectsParser {

    private EffectsParser() {
    }

    /**
     * Parses the item tag into a plan.
     *
     * @param itemTag the item's compound tag, may be {@code null}
     * @return the parsed plan, never {@code null}
     */
    public static ParsedEffects parse(CompoundTag itemTag) {
        if (itemTag == null || !itemTag.contains(DeviceData.TAG_EFFECTS)) {
            return new ParsedEffects.Default();
        }

        Tag raw = itemTag.get(DeviceData.TAG_EFFECTS);
        if (!(raw instanceof ListTag list)) {
            return new ParsedEffects.Invalid("effects must be a list");
        }
        if (list.isEmpty()) {
            return new ParsedEffects.Default();
        }

        List<EffectSpec> specs = new ArrayList<>(list.size());
        Set<ResourceLocation> seen = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            Tag element = list.get(index);
            if (!(element instanceof CompoundTag entry)) {
                return new ParsedEffects.Invalid("effects[" + index + "] must be a compound");
            }
            if (!entry.contains(DeviceData.TAG_ID, Tag.TAG_STRING)) {
                return new ParsedEffects.Invalid("effects[" + index + "].id must be a string");
            }
            String rawId = entry.getString(DeviceData.TAG_ID);
            ResourceLocation id;
            try {
                id = ResourceLocation.tryParse(rawId);
            } catch (RuntimeException exception) {
                id = null;
            }
            if (id == null) {
                return new ParsedEffects.Invalid("effects[" + index + "].id is not a valid resource location: " + rawId);
            }
            if (!seen.add(id)) {
                return new ParsedEffects.Invalid("duplicate effect id: " + id);
            }

            OptionalInt durationTicks = OptionalInt.empty();
            if (entry.contains(DeviceData.TAG_DURATION_TICKS)) {
                if (!entry.contains(DeviceData.TAG_DURATION_TICKS, Tag.TAG_INT)) {
                    return new ParsedEffects.Invalid(
                            "effects[" + index + "].duration_ticks must be an int");
                }
                int duration = entry.getInt(DeviceData.TAG_DURATION_TICKS);
                if (duration <= 0) {
                    return new ParsedEffects.Invalid(
                            "effects[" + index + "].duration_ticks must be positive");
                }
                durationTicks = OptionalInt.of(duration);
            }
            int amplifier = 0;
            if (entry.contains(DeviceData.TAG_AMPLIFIER)) {
                if (!entry.contains(DeviceData.TAG_AMPLIFIER, Tag.TAG_INT)) {
                    return new ParsedEffects.Invalid("effects[" + index + "].amplifier must be an int");
                }
                amplifier = entry.getInt(DeviceData.TAG_AMPLIFIER);
                // Minecraft 1.20.1 stores and reloads the amplifier as a signed byte.
                if (amplifier < 0 || amplifier > Byte.MAX_VALUE) {
                    return new ParsedEffects.Invalid("effects[" + index + "].amplifier must be between 0 and 127");
                }
            }
            specs.add(new EffectSpec(id, durationTicks, amplifier));
        }
        return new ParsedEffects.Explicit(List.copyOf(specs));
    }
}
