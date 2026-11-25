package ke.writ.resonance.internal;

import ke.writ.resonance.item.ResonanceDeviceItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registration of the module's content.
 *
 * <p>The item is registered under the fixed production namespace {@code writ}, even though the
 * independent development shell uses a different mod id. The namespace is written explicitly here
 * and is never inferred from a mod container.</p>
 */
public final class ResonanceItems {

    /** Fixed production namespace from the specification. */
    public static final String NAMESPACE = "writ";
    /** Fixed item path from the specification. */
    public static final String PATH = "resonance_device";

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, NAMESPACE);

    private static final RegistryObject<Item> RESONANCE_DEVICE = ITEMS.register(
            PATH, () -> new ResonanceDeviceItem(new Item.Properties().stacksTo(1)));

    private ResonanceItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static Item device() {
        return RESONANCE_DEVICE.get();
    }

    public static ResourceLocation id() {
        return new ResourceLocation(NAMESPACE, PATH);
    }

    public static boolean isDevice(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(RESONANCE_DEVICE.get());
    }
}
