package ke.writ.resonance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Client-readable item tooltip. It only displays already synchronised item data; ownership is never
 * decided on the client.
 */
public final class DeviceTooltip {

    private DeviceTooltip() {
    }

    public static void append(ItemStack stack, List<Component> tooltip) {
        Optional<UUID> owner = DeviceData.getOwner(stack);
        if (owner.isEmpty()) {
            tooltip.add(Component.translatable(MessageKeys.TOOLTIP_UNBOUND).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable(MessageKeys.TOOLTIP_BOUND, Frequency.of(owner.get()))
                .withStyle(ChatFormatting.AQUA));
    }
}
