package ke.writ;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

final class ChestLoot extends LootModifier {
    // Each attempt applies the source chance independently; duplicates are allowed.
    static final int ATTEMPTS = 2;
    static final Codec<ChestLoot> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, ChestLoot::new));
    ChestLoot(LootItemCondition[] conditions) { super(conditions); }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        var id = context.getQueriedLootTableId();
        if (id != null) {
            for (int i = 0; i < ATTEMPTS; i++) {
                ItemStack contract = Presets.INSTANCE.generate(id.toString(), context.getRandom());
                if (!contract.isEmpty()) loot.add(contract);
            }
        }
        return loot;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
