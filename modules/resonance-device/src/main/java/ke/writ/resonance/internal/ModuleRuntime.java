package ke.writ.resonance.internal;

import java.util.Objects;

import ke.writ.resonance.ResonanceEffect;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Process-wide module state. The module is deliberately not a mod container: everything is wired
 * from the single {@link ke.writ.resonance.ResonanceModule#init(IEventBus)} entry point.
 */
public final class ModuleRuntime {

    private static final ModuleRuntime INSTANCE = new ModuleRuntime();

    public static ModuleRuntime get() {
        return INSTANCE;
    }

    private ResonanceEffect defaultEffect;
    private boolean initialized;

    private ModuleRuntime() {
    }

    ResonanceEffect defaultEffect() {
        return defaultEffect;
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
    }
}
