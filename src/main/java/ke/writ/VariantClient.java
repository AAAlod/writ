package ke.writ;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Writ.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VariantClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            VariantBridge.install((player, hand, stack) -> {
                if (!miss()) return false;
                VariantNet.send(hand, stack);
                return true;
            });
            Texts.detailsHeld = net.minecraft.client.gui.screens.Screen::hasShiftDown;
            Texts.viewer = () -> Minecraft.getInstance().player;
        });
    }
    static boolean miss() {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit != null && hit.getType() == HitResult.Type.MISS;
    }
}
