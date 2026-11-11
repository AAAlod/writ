package ke.writ;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.ListTag;

final class ItemNbt {
    static final int MAX_BYTES = 65536;
    private static final Set<String> RESERVED = Set.of("preset_id", "contract_version", "display_snapshot");

    static CompoundTag parse(JsonElement value) {
        try {
            Tag tag;
            if (value != null && value.isJsonObject()) tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, value);
            else if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) tag = TagParser.parseTag(value.getAsString());
            else throw new IllegalArgumentException("nbt must be a JSON object or SNBT string");
            if (!(tag instanceof CompoundTag compound)) throw new IllegalArgumentException("nbt must be a compound");
            for (String key : RESERVED) if (compound.contains(key)) throw new IllegalArgumentException("reserved NBT key " + key);
            validateStrings(compound);
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) { NbtIo.write(compound, output); }
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("nbt exceeds " + MAX_BYTES + " bytes");
            return compound.copy();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | IOException e) {
            throw new IllegalArgumentException("invalid item nbt: " + e.getMessage(), e);
        }
    }

    private static void validateStrings(Tag tag) throws IOException {
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) { utfSize(key); validateStrings(compound.get(key)); }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) validateStrings(child);
        } else if (tag.getId() == Tag.TAG_STRING) utfSize(tag.getAsString());
    }

    private static void utfSize(String value) throws IOException {
        try (var out = new DataOutputStream(new ByteArrayOutputStream())) { out.writeUTF(value); }
    }
}
