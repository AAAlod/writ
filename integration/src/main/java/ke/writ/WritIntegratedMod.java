package ke.writ;

import java.util.Locale;
import java.util.OptionalInt;
import ke.writ.resonance.EffectResult;
import ke.writ.resonance.Operation;
import ke.writ.resonance.ResonanceModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(Writ.ID)
public final class WritIntegratedMod {
    static final ResourceLocation RESONANCE_DEVICE = new ResourceLocation(Writ.ID, "resonance_device");
    static final ResourceKey<CreativeModeTab> WRIT_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation(Writ.ID, "writs"));

    public WritIntegratedMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        new Writ(bus);
        ResonanceModule.init(bus, WritIntegratedMod::useDefaultDeviceEffect);
        ResonanceHostConfig.register(bus);
        bus.addListener(WritIntegratedMod::addCreativeTabItems);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (WRIT_TAB.equals(event.getTabKey())) {
            event.accept(ForgeRegistries.ITEMS.getValue(RESONANCE_DEVICE));
        }
    }

    static EffectResult useDefaultDeviceEffect(ServerPlayer player, LivingEntity target, Operation operation) {
        ContractExemption.Result result = switch (operation) {
            case APPLY -> ContractExemption.apply(player, target, OptionalInt.empty());
            case REMOVE -> ContractExemption.remove(player, target);
        };
        String key = "writ.effect.contract_exemption." + result.name().toLowerCase(Locale.ROOT);
        boolean changed = result == ContractExemption.Result.APPLIED || result == ContractExemption.Result.REMOVED;
        return new EffectResult(changed, Component.translatable(key));
    }
}
