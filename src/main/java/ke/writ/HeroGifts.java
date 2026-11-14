package ke.writ;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

final class HeroGifts extends LootModifier {
    static final Set<String> GIFT_TABLES = Set.of(
        "minecraft:gameplay/hero_of_the_village/armorer_gift",
        "minecraft:gameplay/hero_of_the_village/butcher_gift",
        "minecraft:gameplay/hero_of_the_village/cartographer_gift",
        "minecraft:gameplay/hero_of_the_village/cleric_gift",
        "minecraft:gameplay/hero_of_the_village/farmer_gift",
        "minecraft:gameplay/hero_of_the_village/fisherman_gift",
        "minecraft:gameplay/hero_of_the_village/fletcher_gift",
        "minecraft:gameplay/hero_of_the_village/leatherworker_gift",
        "minecraft:gameplay/hero_of_the_village/librarian_gift",
        "minecraft:gameplay/hero_of_the_village/mason_gift",
        "minecraft:gameplay/hero_of_the_village/shepherd_gift",
        "minecraft:gameplay/hero_of_the_village/toolsmith_gift",
        "minecraft:gameplay/hero_of_the_village/weaponsmith_gift");
    static final Codec<HeroGifts> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, HeroGifts::new));
    HeroGifts(LootItemCondition[] conditions) { super(conditions); }

    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        var id = context.getQueriedLootTableId();
        if (id == null || !GIFT_TABLES.contains(id.toString())) return loot;
        if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Villager villager)) return loot;
        var profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        if (profession == null || !id.toString().equals("minecraft:gameplay/hero_of_the_village/" + profession.getPath() + "_gift")) return loot;
        if (loot.isEmpty()) return loot;
        Writ.HeroGiftParams params = Writ.heroGiftParameters();
        if (!params.valid() || !params.enabled()) return loot;
        Presets.Snapshot snapshot = Presets.INSTANCE.currentSnapshot();
        boolean chaosAvailable = snapshot.chaos().size() > 0 && snapshot.chaos().resultSize() > 0;
        boolean randomAvailable = Presets.hasAnyUsableRandomCandidate(snapshot);
        int roll = context.getRandom().nextInt(100);
        return switch (decide(roll, params.randomPercent(), params.chaosPercent(), chaosAvailable, randomAvailable)) {
            case CHAOS -> ObjectArrayList.of(new ItemStack(Writ.CHAOS.get()));
            case RANDOM -> ObjectArrayList.of(new ItemStack(Writ.RANDOM.get()));
            case ORIGINAL -> loot;
        };
    }

    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
    enum GiftAction { ORIGINAL, RANDOM, CHAOS }

    static GiftAction decide(int roll, int randomPercent, int chaosPercent,
                             boolean chaosAvailable, boolean randomAvailable) {
        if (roll < 0 || roll > 99 || randomPercent < 0 || chaosPercent < 0
                || (long) randomPercent + chaosPercent > 100) return GiftAction.ORIGINAL;
        if (roll < chaosPercent) return chaosAvailable ? GiftAction.CHAOS : GiftAction.ORIGINAL;
        if (roll < chaosPercent + randomPercent) return randomAvailable ? GiftAction.RANDOM : GiftAction.ORIGINAL;
        return GiftAction.ORIGINAL;
    }
}
