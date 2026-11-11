package ke.writ;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Vanilla offer matching ignores the extra NBT on a payment, so a kit's "one shulker box" cost also accepts a
 * filled box and would destroy its contents. {@code MerchantResultSlot#onTake} shrinks the payment stacks, then
 * fires this event, then writes the stacks back; a payment emptied by the trade still carries its tag here.
 * Any consumed payment that held container contents gets those contents returned to the player.
 */
final class ContainerPayments {
    @SubscribeEvent
    public void traded(TradeWithVillagerEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide || !(player.containerMenu instanceof MerchantMenu menu)) return;
        // An offer that deliberately asks for a container with contents keeps them as payment.
        var offer = event.getMerchantOffer();
        if (offer != null && (requiresContents(offer.getBaseCostA()) || requiresContents(offer.getCostB()))) return;
        boolean refunded = false;
        for (int slot = 0; slot < 2; slot++) for (ItemStack content : takeContents(menu.getSlot(slot).getItem())) { player.getInventory().placeItemBackInInventory(content); refunded = true; }
        if (refunded) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("writ.message.container_refund"), true);
    }

    static boolean requiresContents(ItemStack cost) {
        CompoundTag tag = cost.getTag();
        return tag != null && tag.getCompound("BlockEntityTag").contains("Items", Tag.TAG_LIST);
    }

    /** Removes and returns the stored items of a payment stack that the trade consumed; anything else is left alone. */
    static java.util.List<ItemStack> takeContents(ItemStack payment) {
        CompoundTag tag = payment.getTag();
        if (!payment.isEmpty() || tag == null || !tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) return java.util.List.of();
        CompoundTag block = tag.getCompound("BlockEntityTag");
        if (!block.contains("Items", Tag.TAG_LIST)) return java.util.List.of();
        ListTag items = block.getList("Items", Tag.TAG_COMPOUND);
        var result = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < items.size(); i++) { ItemStack stack = ItemStack.of(items.getCompound(i)); if (!stack.isEmpty()) result.add(stack); }
        // The emptied stack object stays in the slot; drop its tag so a later trade cannot refund the same contents.
        payment.setTag(null);
        return result;
    }
}
