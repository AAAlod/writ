package ke.writ;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class RecipeGameTests {
    private static final ResourceLocation ID = new ResourceLocation("writ", "chaos_writ");
    static void assertMode(GameTestHelper h) {
        boolean craft = Presets.INSTANCE.activeMode() == ObtainMode.CRAFT_9;
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ID).isPresent() == craft, "recipe presence must follow locked mode");
        var player = h.makeMockSurvivalPlayer();
        var grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
        for (int shift = 0; shift < 9; shift++) {
            for (int slot = 0; slot < 9; slot++) {
                var stack = new ItemStack(Writ.NORMAL.get());
                switch ((slot + shift) % 3) {
                    case 1 -> stack.getOrCreateTag().putString("preset_id", "missing:invalid");
                    case 2 -> { stack.getOrCreateTag().putString("preset_id", "writ:example"); stack.getOrCreateTag().putInt("contract_version", 99); stack.getOrCreateTag().putString("display_snapshot", "malformed"); }
                    default -> { }
                }
                grid.setItem(slot, stack);
            }
            var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel());
            h.assertTrue(recipe.isPresent() == craft, "9 contracts should match only in CRAFT_9");
            if (craft) {
                var output = recipe.orElseThrow().assemble(grid, h.getLevel().registryAccess());
                h.assertTrue(output.is(Writ.CHAOS.get()) && output.getCount() == 1, "expected one chaos contract");
            }
        }
        if (craft) {
            grid.setItem(4, new ItemStack(Items.STONE));
            h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).isEmpty(), "non-contract ingredient matched");
            var villager = h.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
            var historical = new ItemStack(Writ.NORMAL.get());
            historical.getOrCreateTag().putString("preset_id", "writ:a_unlock");
            var offer = Presets.INSTANCE.offer(historical, villager);
            h.assertTrue(offer != null && offer.getResult().is(Writ.CHAOS.get()), "historical A contract must remain usable in B");
        }
    }
    @GameTest(template = "empty")
    public static void recipeMatchesLockedMode(GameTestHelper h) { assertMode(h); h.succeed(); }
    @GameTest(template = "empty")
    public static void syncMarksOnlyEnabledRecipeKnown(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var player = new net.minecraft.server.level.ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), "recipe-test"));
        MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(), player));
        h.assertTrue(player.getRecipeBook().contains(ID) == (Presets.INSTANCE.activeMode() == ObtainMode.CRAFT_9), "known recipe must follow enabled mode");
        h.succeed();
    }
}
