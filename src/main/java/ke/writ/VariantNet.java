package ke.writ;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

final class VariantNet {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Writ.ID, "variants"), () -> "2", "2"::equals, "2"::equals);
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();
    static void register() {
        CHANNEL.messageBuilder(VariantPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VariantPacket::encode)
                .decoder(VariantPacket::decode)
                .consumerNetworkThread(VariantNet::receive).add();
    }
    static void send(InteractionHand hand, net.minecraft.world.item.ItemStack stack) {
        var tag = stack.getTag();
        CHANNEL.sendToServer(new VariantPacket(hand == InteractionHand.MAIN_HAND ? 0 : 1, tag.getString("preset_id"), VariantRules.selected(tag), stack.getCount()));
    }
    private static void receive(VariantPacket request, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> { ServerPlayer player = context.getSender(); if (player != null) handle(player, request); });
        context.setPacketHandled(true);
    }
    static void handle(ServerPlayer player, VariantPacket request) {
        var result = handleRequest(player, request);
        if (result == null) return;
        if (result.failure() != null) { player.displayClientMessage(Component.translatable("writ.message." + result.failure()), true); return; }
        if (result.total() == 0 && !result.reset()) return;
        // The preset lost all its variants: there is nothing left to name, only a stale choice that was dropped.
        if (result.name() == null) { player.displayClientMessage(Component.translatable("writ.message.variant.cleared"), true); return; }
        player.displayClientMessage(Component.translatable(result.reset() ? "writ.message.variant.reset" : "writ.message.variant.switched", result.name(), result.index(), result.total()), true);
    }
    static Presets.SwitchResult handleRequest(ServerPlayer player, VariantPacket request) {
        int hand = request.hand();
        if (hand < 0 || hand > 1 || player.isSpectator()) return null;
        long now = player.level().getGameTime();
        Long last = LAST.get(player);
        if (last != null && now >= last && now - last < 2) return null;
        LAST.put(player, now);
        if (!clearTarget(player)) return null;
        var stack = player.getItemInHand(hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (!request.matches(stack.is(Writ.NORMAL.get()), stack.getTag(), stack.getCount())) return null;
        return Presets.INSTANCE.switchVariant(stack, player.isShiftKeyDown());
    }
    static boolean clearTarget(ServerPlayer player) {
        double reach = Math.max(player.getBlockReach(), player.getEntityReach());
        Vec3 from = player.getEyePosition(), to = from.add(player.getViewVector(1).scale(reach));
        // Refuse a ray crossing any unloaded chunk before asking vanilla to clip it.
        for (double d = 0; d <= reach + 0.5; d += 0.5) {
            Vec3 point = from.add(player.getViewVector(1).scale(Math.min(d, reach)));
            if (!player.level().hasChunkAt(BlockPos.containing(point))) return false;
        }
        if (player.pick(reach, 1, false).getType() != HitResult.Type.MISS) return false;
        AABB search = new AABB(from, to).inflate(1.0);
        return player.level().getEntities(player, search, e -> !e.isSpectator() && e.isPickable()).stream()
                .noneMatch(e -> e.getBoundingBox().inflate(e.getPickRadius()).contains(from) || e.getBoundingBox().inflate(e.getPickRadius()).clip(from, to).isPresent());
    }
}
