package ke.writ;

import ke.writ.resonance.ResonanceModule;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

final class ResonanceHostConfig {
    private static final int DEFAULT_COOLDOWN_TICKS = 100;
    static final int MAX_COOLDOWN_TICKS = 20 * 60 * 5;
    private static final ForgeConfigSpec.IntValue COOLDOWN;
    private static final ForgeConfigSpec SPEC;

    static {
        var builder = new ForgeConfigSpec.Builder();
        COOLDOWN = builder.comment("Ticks between effective uses of the resonance device by the same player.",
                        "20 ticks = 1 second; 0 disables the cooldown. Restart the world or server after editing.")
                .defineInRange("cooldown_ticks", DEFAULT_COOLDOWN_TICKS, 0, MAX_COOLDOWN_TICKS);
        SPEC = builder.build();
    }

    static void register(IEventBus bus) {
        bus.addListener((ModConfigEvent.Loading event) -> apply(event.getConfig()));
        // Forge also fires Reloading on the client when it receives the server config at login.
        bus.addListener((ModConfigEvent.Reloading event) -> apply(event.getConfig()));
        bus.addListener((ModConfigEvent.Unloading event) -> {
            if (event.getConfig().getSpec() == SPEC) {
                ResonanceModule.setDefaultCooldownTicks(DEFAULT_COOLDOWN_TICKS);
            }
        });
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "writ-resonance-server.toml");
    }

    static int cooldownTicks() {
        return COOLDOWN.get();
    }

    private static void apply(ModConfig config) {
        if (config.getSpec() == SPEC) {
            ResonanceModule.setDefaultCooldownTicks(cooldownTicks());
        }
    }
}
