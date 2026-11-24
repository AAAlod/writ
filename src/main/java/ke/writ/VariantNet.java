package ke.writ;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.PacketDistributor;
import java.util.WeakHashMap;
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
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

final class VariantNet {
    private static final String PROTOCOL = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Writ.ID, "variants"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final int CHUNK_BYTES = 256 * 1024;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_OPTIONS = 16384;
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static Consumer<Options> showOptions = ignored -> { };

    record Option(String id, Component name, ItemStack costA, ItemStack costB, ItemStack result) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeComponent(name);
            buf.writeNbt(costA.save(new CompoundTag()));
            buf.writeNbt(costB.save(new CompoundTag()));
            buf.writeNbt(result.save(new CompoundTag()));
        }

        static Option decode(FriendlyByteBuf buf) {
            return new Option(buf.readUtf(), buf.readComponent(), ItemStack.of(buf.readNbt()),
                    ItemStack.of(buf.readNbt()), ItemStack.of(buf.readNbt()));
        }
    }

    record Options(UUID token, int offset, int total, int selected, List<Option> choices) { }

    private record OptionsPayload(UUID token, int offset, int total, int selected, int count, byte[] bytes) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeVarInt(offset);
            buf.writeVarInt(total);
            buf.writeVarInt(selected);
            buf.writeVarInt(count);
            buf.writeBytes(bytes);
        }

        static OptionsPayload decode(FriendlyByteBuf buf) {
            UUID token = buf.readUUID();
            int offset = buf.readVarInt(), total = buf.readVarInt(), selected = buf.readVarInt();
            int count = buf.readVarInt();
            if (total < 1 || total > MAX_OPTIONS || offset < 0 || offset >= total
                    || selected < 0 || selected >= total || count < 1 || count > total - offset
                    || buf.readableBytes() > CHUNK_BYTES) {
                throw new IllegalArgumentException("Invalid Writ choices batch");
            }
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new OptionsPayload(token, offset, total, selected, count, bytes);
        }

        void deliver() {
            var buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try {
                List<Option> choices = new ArrayList<>(count);
                for (int i = 0; i < count; i++) choices.add(Option.decode(buf));
                showOptions.accept(new Options(token, offset, total, selected, choices));
            } finally {
                buf.release();
            }
        }
    }

    record Choice(UUID token, String id, boolean cancel) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeBoolean(cancel);
            if (!cancel) buf.writeUtf(id);
        }

        static Choice decode(FriendlyByteBuf buf) {
            UUID token = buf.readUUID();
            boolean cancel = buf.readBoolean();
            return new Choice(token, cancel ? "" : buf.readUtf(), cancel);
        }
    }

    record Session(UUID token, int slot, int selectedSlot, ItemStack stack, ItemStack original,
                   Presets.Snapshot snapshot, Presets.Preset preset, ServerLevel level,
                   long expires, UUID villager) { }

    static void register() {
        CHANNEL.messageBuilder(VariantPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VariantPacket::encode).decoder(VariantPacket::decode)
                .consumerMainThread((request, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player == null) return;
                    Session session = begin(player, request);
                    if (session != null) open(player, session);
                }).add();
        CHANNEL.messageBuilder(OptionsPayload.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OptionsPayload::encode).decoder(OptionsPayload::decode)
                .consumerMainThread((payload, context) -> payload.deliver()).add();
        CHANNEL.messageBuilder(Choice.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Choice::encode).decoder(Choice::decode)
                .consumerMainThread((choice, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null) finish(player, choice);
                }).add();
    }

    static void installClient(Consumer<Options> handler) {
        showOptions = handler;
    }

    static void send(InteractionHand hand, ItemStack stack) {
        var tag = stack.getTag();
        CHANNEL.sendToServer(new VariantPacket(hand == InteractionHand.MAIN_HAND ? 0 : 1,
                tag.getString("preset_id"), VariantRules.selected(tag), stack.getCount()));
    }

    static void sendChoice(Choice choice) {
        CHANNEL.sendToServer(choice);
    }

    static Session begin(ServerPlayer player, VariantPacket request) {
        int hand = request.hand();
        if (hand < 0 || hand > 1 || player.isSpectator() || !player.isAlive()) return null;
        long now = player.level().getGameTime();
        Long last = LAST.get(player);
        if (last != null && now >= last && now - last < 4) return null;
        LAST.put(player, now);
        if (!clearTarget(player)) return null;
        int slot = hand == 0 ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        ItemStack stack = player.getInventory().getItem(slot);
        if (!request.matches(stack.is(Writ.NORMAL.get()), stack.getTag(), stack.getCount())) return null;
        return prepare(player, slot, stack, null);
    }

    static boolean openRandom(ServerPlayer player, int slot, ItemStack contract, Villager villager) {
        Session session = prepare(player, slot, contract, villager.getUUID());
        return session != null && open(player, session);
    }

    static Session prepare(ServerPlayer player, int slot, ItemStack stack, UUID villager) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var preset = Presets.selectionPreset(snapshot, stack);
        if (preset == null) {
            message(player, "variant_invalid");
            return null;
        }
        String defaultId = preset.dynamic() ? preset.defaultId() : "";
        if (preset.variants().isEmpty() && defaultId.equals(VariantRules.selected(stack.getTag()))
                && (!stack.getTag().contains("selected_variant") || preset.dynamic())) return null;
        return new Session(UUID.randomUUID(), slot, player.getInventory().selected, stack, stack.copy(),
                snapshot, preset, player.serverLevel(), player.level().getGameTime() + 6000, villager);
    }

    private static boolean open(ServerPlayer player, Session session) {
        List<OptionsPayload> packets;
        try {
            packets = encodeOptions(session);
        } catch (IllegalArgumentException | io.netty.handler.codec.EncoderException ex) {
            Writ.LOG.warn("Cannot show Writ choices for {}", session.original().getTag().getString("preset_id"), ex);
            message(player, "variant.too_large");
            return false;
        }
        SESSIONS.put(player, session);
        for (OptionsPayload packet : packets) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        return true;
    }

    private static void finish(ServerPlayer player, Choice choice) {
        Session session = SESSIONS.get(player);
        if (session == null || !session.token().equals(choice.token())) return;
        SESSIONS.remove(player);
        if (!choice.cancel()) apply(player, session, choice.id());
    }

    static boolean apply(ServerPlayer player, Session session, String id) {
        if (player.isSpectator() || !player.isAlive() || player.serverLevel() != session.level()
                || player.containerMenu != player.inventoryMenu
                || player.level().getGameTime() > session.expires()
                || player.getInventory().selected != session.selectedSlot()
                || player.getInventory().getItem(session.slot()) != session.stack()
                || !ItemStack.matches(session.stack(), session.original())
                || Presets.INSTANCE.currentSnapshot() != session.snapshot()) {
            message(player, "variant.stale");
            return false;
        }
        if (!Presets.selectVariant(session.snapshot(), session.preset(), session.stack(), id)) {
            message(player, "variant_invalid");
            return false;
        }
        if (session.villager() == null) {
            message(player, "variant.saved");
        } else if (session.level().getEntity(session.villager()) instanceof Villager villager
                && villager.isAlive() && withinReach(player, villager) && player.hasLineOfSight(villager)) {
            Writ.sign(player, villager, session.stack(), null);
        } else {
            message(player, "variant.sign_later");
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static boolean withinReach(ServerPlayer player, Villager villager) {
        Vec3 eye = player.getEyePosition();
        AABB box = villager.getBoundingBox();
        double x = net.minecraft.util.Mth.clamp(eye.x, box.minX, box.maxX);
        double y = net.minecraft.util.Mth.clamp(eye.y, box.minY, box.maxY);
        double z = net.minecraft.util.Mth.clamp(eye.z, box.minZ, box.maxZ);
        double reach = player.getEntityReach();
        return eye.distanceToSqr(x, y, z) <= reach * reach;
    }

    private static void message(ServerPlayer player, String key) {
        if (player.connection != null) player.displayClientMessage(Component.translatable("writ.message." + key), true);
    }

    private static List<OptionsPayload> encodeOptions(Session session) {
        Presets.Preset preset = session.preset();
        int total = preset.variants().size() + 1;
        if (total > MAX_OPTIONS) throw new IllegalArgumentException("Too many choices");
        int selected = 0;
        String currentId = VariantRules.selected(session.original().getTag());
        List<byte[]> encoded = new ArrayList<>(total);
        int totalBytes = 0;
        for (int i = 0; i < total; i++) {
            Presets.Variant variant = i == 0 ? null : preset.variants().get(i - 1);
            String id = variant == null ? (preset.dynamic() ? preset.defaultId() : "") : variant.id();
            if (id.equals(currentId)) selected = i;
            var option = new Option(id, Presets.selectionName(preset, variant),
                    variant == null ? preset.costA() : variant.costA(),
                    variant == null ? preset.costB() : variant.costB(),
                    variant == null ? preset.result() : variant.result());
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(128, CHUNK_BYTES));
            try {
                option.encode(buf);
                int size = buf.readableBytes();
                totalBytes += size;
                if (totalBytes > MAX_BYTES) throw new IllegalArgumentException("Choices exceed byte budget");
                byte[] bytes = new byte[size];
                buf.readBytes(bytes);
                encoded.add(bytes);
            } catch (IndexOutOfBoundsException ex) {
                throw new IllegalArgumentException("Choice exceeds packet byte budget", ex);
            } finally {
                buf.release();
            }
        }
        List<OptionsPayload> packets = new ArrayList<>();
        for (int offset = 0; offset < total; ) {
            int end = offset;
            int bytes = 32;
            while (end < total && bytes + encoded.get(end).length <= CHUNK_BYTES) {
                bytes += encoded.get(end++).length;
            }
            if (end == offset) throw new IllegalArgumentException("Choice exceeds packet byte budget");
            byte[] batch = new byte[bytes - 32];
            int cursor = 0;
            for (int i = offset; i < end; i++) {
                byte[] choice = encoded.get(i);
                System.arraycopy(choice, 0, batch, cursor, choice.length);
                cursor += choice.length;
            }
            packets.add(new OptionsPayload(session.token(), offset, total, selected, end - offset, batch));
            offset = end;
        }
        return packets;
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
