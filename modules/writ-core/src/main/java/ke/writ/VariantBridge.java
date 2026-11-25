package ke.writ;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class VariantBridge {
    interface Handler { boolean request(Player player, InteractionHand hand, ItemStack stack); }
    private static volatile Handler handler = (player, hand, stack) -> false;
    static void install(Handler clientHandler) { handler = clientHandler; }
    static boolean request(Player player, InteractionHand hand, ItemStack stack) { return handler.request(player, hand, stack); }
}
