package ke.writ;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VariantNetTest {
    @Test void packetRoundTripsAndLimitsStrings() {
        for (var request : new VariantPacket[] {new VariantPacket(0, "writ:terracotta", "", 64), new VariantPacket(1, "x:y", "blue", 1), new VariantPacket(255, "x:y", "", 0)}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer()); VariantPacket.encode(request, buf);
            assertEquals(request, VariantPacket.decode(buf)); assertEquals(0, buf.readableBytes()); buf.release();
        }
        assertThrows(RuntimeException.class, () -> VariantPacket.encode(new VariantPacket(0, "x".repeat(257), "", 1), new FriendlyByteBuf(Unpooled.buffer())));
        assertThrows(RuntimeException.class, () -> VariantPacket.encode(new VariantPacket(0, "x:y", "x".repeat(129), 1), new FriendlyByteBuf(Unpooled.buffer())));
    }
    @Test void eligibilityAndContextDoNotWriteTags() {
        assertFalse(VariantRules.canRequest(true, null, 1));
        var tag = new CompoundTag(); assertFalse(VariantRules.canRequest(true, tag, 1)); assertTrue(tag.isEmpty());
        tag.putString("preset_id", "writ:terracotta"); var copy = tag.copy();
        assertTrue(VariantRules.canRequest(true, tag, 2)); assertEquals(copy, tag); assertFalse(tag.contains("display_snapshot"));
        assertFalse(VariantRules.canRequest(false, tag, 2)); assertFalse(VariantRules.canRequest(true, tag, 0));
        var good = new VariantPacket(1, "writ:terracotta", "", 2);
        assertTrue(good.matches(true, tag, 2));
        assertFalse(new VariantPacket(2, good.preset(), "", 2).matches(true, tag, 2));
        assertFalse(new VariantPacket(1, "other:id", "", 2).matches(true, tag, 2));
        assertFalse(new VariantPacket(1, good.preset(), "blue", 2).matches(true, tag, 2));
        assertFalse(new VariantPacket(1, good.preset(), "", 1).matches(true, tag, 2));
        tag.putInt("contract_version", 2); assertFalse(VariantRules.canRequest(true, tag, 2));
        tag.putInt("contract_version", 1); assertTrue(VariantRules.canRequest(true, tag, 2));
        tag.putString("selected_variant", "blue"); assertTrue(new VariantPacket(1, good.preset(), "blue", 2).matches(true, tag, 2));
    }
}
