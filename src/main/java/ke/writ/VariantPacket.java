package ke.writ;

import net.minecraft.network.FriendlyByteBuf;

record VariantPacket(int hand, String preset, String selected, int count) {
    static void encode(VariantPacket message, FriendlyByteBuf buf) { buf.writeByte(message.hand()); buf.writeUtf(message.preset(), 256); buf.writeUtf(message.selected(), 128); buf.writeVarInt(message.count()); }
    static VariantPacket decode(FriendlyByteBuf buf) { return new VariantPacket(buf.readUnsignedByte(), buf.readUtf(256), buf.readUtf(128), buf.readVarInt()); }
    boolean matches(boolean normal, net.minecraft.nbt.CompoundTag tag, int actualCount) {
        return (hand == 0 || hand == 1) && preset != null && selected != null && preset.length() <= 256 && selected.length() <= 128
                && count > 0 && VariantRules.canRequest(normal, tag, actualCount) && count == actualCount
                && preset.equals(tag.getString("preset_id")) && selected.equals(VariantRules.selected(tag));
    }
}
