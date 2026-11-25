package ke.writ;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.registries.ForgeRegistries;

final class Chaos {
    static MerchantOffer offer(RandomSource random) {
        var pair = Presets.INSTANCE.currentSnapshot().chaos().sample(random::nextInt);
        if (pair.isEmpty()) return null;
        Item pay = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pair.get().payment()));
        Item result = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pair.get().result()));
        var params = Writ.chaosParameters();
        return new MerchantOffer(new ItemStack(pay), new ItemStack(result), params.uses(), params.xp(), params.multiplier());
    }
}
