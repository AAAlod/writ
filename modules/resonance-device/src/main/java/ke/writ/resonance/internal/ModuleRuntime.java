package ke.writ.resonance.internal;

import java.util.Objects;

import ke.writ.resonance.ResonanceEffect;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Process-wide module state. The module is deliberately not a mod container: everything is wired
 * from the single {@link ke.writ.resonance.ResonanceModule#init(IEventBus)} entry point.
 *
 * <p>The cooldown is a single module-wide value, not per item data: the host owns the configuration
 * source (its own config file) and pushes the value through
 * {@link ke.writ.resonance.ResonanceModule#setDefaultCooldownTicks(int)}.</p>
 */
public final class ModuleRuntime {

    /** Default cooldown between two effective uses: 100 ticks (5 seconds). */
    public static final int DEFAULT_COOLDOWN_TICKS = 100;

    private static final ModuleRuntime INSTANCE = new ModuleRuntime();

    public static ModuleRuntime get() {
        return INSTANCE;
    }

    private ResonanceEffect defaultEffect;
    private volatile int defaultCooldownTicks = DEFAULT_COOLDOWN_TICKS;
    private boolean initialized;

    private ModuleRuntime() {
    }

    ResonanceEffect defaultEffect() {
        return defaultEffect;
    }

    int defaultCooldownTicks() {
        return defaultCooldownTicks;
    }

    /** Sets the module-wide cooldown between two effective uses. */
    public void setDefaultCooldownTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("default cooldown must not be negative: " + ticks);
        }
        defaultCooldownTicks = ticks;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Registers the {@code writ:resonance_device} item and the Forge entity interaction hook.
     *
     * @param modEventBus the hosting mod's mod event bus
     */
    public void init(IEventBus modEventBus, ResonanceEffect defaultEffect) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        if (initialized) {
            throw new IllegalStateException("ResonanceModule.init(IEventBus) must be called exactly once");
        }
        this.defaultEffect = defaultEffect;
        initialized = true;
        ResonanceItems.register(modEventBus);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, DeviceInteraction::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> DeviceInteraction.clearSession());
    }
}
