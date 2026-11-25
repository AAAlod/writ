package ke.writ;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.player.Player;
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
        if (isGeneralContainer(context)) {
            for (int i = 0; i < ATTEMPTS; i++) {
                ItemStack contract = Presets.INSTANCE.generateGeneral(context.getRandom());
                if (!contract.isEmpty()) loot.add(contract);
            }
        }
        return loot;
    }
    static boolean isGeneralContainer(LootContext context) {
        var level = context.getLevel();
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (!level.dimension().equals(Level.OVERWORLD) || origin == null || context.getQueriedLootTableId() == null) return false;
        // Vanilla supplies the opening player as THIS_ENTITY for both block and vehicle containers.
        var subject = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (subject != null && !(subject instanceof Player)) return false;
        // Entity- and block-drop contexts are not container generation, even at a chest's position.
        if (context.hasParam(LootContextParams.DAMAGE_SOURCE) || context.hasParam(LootContextParams.BLOCK_STATE)) return false;
        if (context.getParamOrNull(LootContextParams.KILLER_ENTITY) instanceof AbstractMinecartContainer) return true;
        BlockPos pos = BlockPos.containing(origin);
        if (!level.hasChunkAt(pos)) return false;
        var blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
