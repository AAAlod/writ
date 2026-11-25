package ke.writ;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Writ.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VariantClient {
    private static UUID incoming;
    private static final List<VariantNet.Option> choices = new ArrayList<>();

    private static void receive(VariantNet.Options batch) {
        if (batch.offset() == 0) {
            incoming = batch.token();
            choices.clear();
        }
        if (!batch.token().equals(incoming) || batch.offset() != choices.size()) return;
        choices.addAll(batch.choices());
        if (choices.size() == batch.total()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.setScreen(new WritSelectionScreen(batch.token(), List.copyOf(choices), batch.selected()));
            }
            choices.clear();
            incoming = null;
        }
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            VariantBridge.install((player, hand, stack) -> {
                if (!miss()) return false;
                VariantNet.send(hand, stack);
                return true;
            });
            VariantNet.installClient(VariantClient::receive);
            Texts.detailsHeld = net.minecraft.client.gui.screens.Screen::hasShiftDown;
            Texts.viewer = () -> Minecraft.getInstance().player;
        });
    }
    static boolean miss() {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit != null && hit.getType() == HitResult.Type.MISS;
    }

}
