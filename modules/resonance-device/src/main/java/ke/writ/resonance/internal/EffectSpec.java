package ke.writ.resonance.internal;

import java.util.OptionalInt;

import net.minecraft.resources.ResourceLocation;

/**
 * One parsed entry of the item's explicit {@code effects} list.
 *
 * @param id            the effect id
 * @param durationTicks the positive configured duration, or empty when omitted
 * @param amplifier     the zero-based strength for Minecraft status effects
 */
public record EffectSpec(ResourceLocation id, OptionalInt durationTicks, int amplifier) {
}
