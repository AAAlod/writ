package ke.writ.resonance.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ke.writ.resonance.ResonanceEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Client-readable item tooltip. It only displays already synchronised item data; ownership is never
 * decided on the client.
 *
 * <p>Besides ownership state, the tooltip resolves the item's own data the same way the server does:
 * an explicit {@code effects} list renders one line per effect with the localised effect name, the
 * Roman level and the duration (seconds / permanent / instant); an absent or empty list explains the
 * host default state; a malformed or unresolvable configuration renders the configuration error. The
 * module-wide cooldown is shown as well (absent when it is disabled).</p>
 */
public final class DeviceTooltip {

    private DeviceTooltip() {
    }

    public static void append(ItemStack stack, List<Component> tooltip) {
        DeviceData.OwnerState owner = DeviceData.getOwnerState(stack);
        if (owner instanceof DeviceData.OwnerState.Invalid) {
            tooltip.add(Component.translatable(MessageKeys.TOOLTIP_OWNER_INVALID)
                    .withStyle(ChatFormatting.RED));
        } else if (owner instanceof DeviceData.OwnerState.Unbound) {
            tooltip.add(Component.translatable(MessageKeys.TOOLTIP_UNBOUND).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(MessageKeys.TOOLTIP_BOUND,
                            Frequency.of(((DeviceData.OwnerState.Bound) owner).owner()))
                    .withStyle(ChatFormatting.AQUA));
        }
        appendEffects(stack, tooltip);
        appendCooldown(tooltip);
    }

    private static void appendEffects(ItemStack stack, List<Component> tooltip) {
        ParsedEffects parsed = EffectsParser.parse(DeviceData.getTagOrNull(stack));
        if (parsed instanceof ParsedEffects.Invalid invalid) {
            tooltip.add(Component.translatable(MessageKeys.CONFIG_ERROR, invalid.detail())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (parsed instanceof ParsedEffects.Default) {
            ResonanceEffect handler = ModuleRuntime.get().defaultEffect();
            tooltip.add(Component.translatable(handler == null
                            ? MessageKeys.NO_DEFAULT_EFFECT
                            : MessageKeys.TOOLTIP_DEFAULT_EFFECT)
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        List<EffectSpec> specs = ((ParsedEffects.Explicit) parsed).effects();
        Map<ResourceLocation, MobEffect> effects = new HashMap<>();
        for (EffectSpec spec : specs) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(spec.id());
            if (effect == null) {
                // Mirror the server: one unresolvable id rejects the whole list.
                tooltip.add(Component.translatable(MessageKeys.CONFIG_ERROR,
                                "unregistered effect id: " + spec.id())
                        .withStyle(ChatFormatting.RED));
                return;
            }
            effects.put(spec.id(), effect);
        }

        for (EffectSpec spec : specs) {
            MobEffect effect = effects.get(spec.id());
            tooltip.add(Component.translatable(MessageKeys.TOOLTIP_EFFECT,
                            effect.getDisplayName(),
                            Component.literal(EffectFormat.roman(spec.amplifier() + 1)),
                            duration(effect, spec))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void appendCooldown(List<Component> tooltip) {
        int cooldownTicks = ModuleRuntime.get().defaultCooldownTicks();
        if (cooldownTicks > 0) {
            tooltip.add(Component.translatable(MessageKeys.TOOLTIP_COOLDOWN,
                            EffectFormat.secondsText(cooldownTicks))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component duration(MobEffect effect, EffectSpec spec) {
        if (effect.isInstantenous()) {
            return Component.translatable(MessageKeys.TOOLTIP_DURATION_INSTANT);
        }
        if (spec.durationTicks().isEmpty()) {
            return Component.translatable(MessageKeys.TOOLTIP_DURATION_INFINITE);
        }
        return Component.translatable(MessageKeys.TOOLTIP_DURATION_SECONDS,
                EffectFormat.secondsText(spec.durationTicks().getAsInt()));
    }
}
