package ke.writ;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class RandomWritGameTests {
    @GameTest(template = "empty")
    public static void randomWritIsRegistered(GameTestHelper helper) {
        helper.assertTrue(Writ.RANDOM.get() != null && Writ.RANDOM.get() != Items.AIR, "random writ must be registered as an item");
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(new ResourceLocation("writ:random_writ")), "random writ registry key must exist");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void randomWritRecipeLoads(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(new ResourceLocation("writ", "random_writ")).isPresent(), "random writ recipe must load");
        helper.succeed();
    }
}
