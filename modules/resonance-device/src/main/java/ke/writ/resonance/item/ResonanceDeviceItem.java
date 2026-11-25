package ke.writ.resonance.item;

import java.util.List;

import javax.annotation.Nullable;

import ke.writ.resonance.internal.DeviceInteraction;
import ke.writ.resonance.internal.DeviceTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The resonance device item: an unstackable item with a vanilla model and enchantment glint.
 *
 * <p>The item itself only forwards to the module interaction logic; the server is always
 * authoritative about binding and effects.</p>
 */
public class ResonanceDeviceItem extends Item {

    public ResonanceDeviceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionResult result = DeviceInteraction.useOnAir(level, player, hand, stack);
        return new InteractionResultHolder<>(result, stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Vanilla enchantment glint, as required by the specification.
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
            TooltipFlag flag) {
        DeviceTooltip.append(stack, tooltip);
    }
}
