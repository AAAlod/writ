package ke.writ.resonance;

import java.util.Objects;

import ke.writ.resonance.internal.ModuleRuntime;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Public entry point of the resonance device module.
 *
 * <p>The module is not a Forge mod itself: it has no {@code @Mod} annotation and never infers the
 * resource namespace from a mod container. The host mod calls {@link #init(IEventBus)} once from
 * its own mod constructor and may supply the default action for an empty device.</p>
 */
public final class ResonanceModule {

    private ResonanceModule() {
    }

    /**
     * Initialises the module: registers {@code writ:resonance_device} and hooks the Forge entity
     * interaction path.
     *
     * @param modEventBus the host mod's mod event bus, obtained from the mod container
     */
    public static void init(IEventBus modEventBus) {
        init(modEventBus, null);
    }

    /**
     * Initializes the module with a host action for devices with an absent or empty effect list.
     * Explicit lists always use the Forge status effect registry.
     */
    public static void init(IEventBus modEventBus, ResonanceEffect defaultEffect) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        ModuleRuntime.get().init(modEventBus, defaultEffect);
    }

    /**
     * Overrides the module-wide cooldown between two effective uses.
     *
     * <p>The module does not read configuration files: the host owns the configuration source and
     * pushes the value here (for example from its own config file). {@code 0} disables the cooldown;
     * the default is 100 ticks (5 seconds). The value is global so the vanilla item cooldown overlay
     * and the server-side check always agree.</p>
     *
     * @param ticks the cooldown in ticks, must not be negative
     */
    public static void setDefaultCooldownTicks(int ticks) {
        ModuleRuntime.get().setDefaultCooldownTicks(ticks);
    }
}
