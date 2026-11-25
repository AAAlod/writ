package ke.writ;

import java.util.Locale;
import java.util.OptionalInt;
import ke.writ.resonance.EffectResult;
import ke.writ.resonance.Operation;
import ke.writ.resonance.ResonanceModule;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Writ.ID)
public final class WritIntegratedMod {
    public WritIntegratedMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        new Writ(bus);
        ResonanceModule.init(bus, WritIntegratedMod::useDefaultDeviceEffect);
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
