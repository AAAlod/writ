package ke.writ;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class ResonanceRecipeGameTests {
    private static final ResourceLocation RECIPE = WritIntegratedMod.RESONANCE_DEVICE;
    private static final ResourceLocation ADVANCEMENT = new ResourceLocation(Writ.ID, "recipes/resonance_device");

    @GameTest(template = "empty")
    public static void resonanceRecipeCraftsAndUnlocks(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var player = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), "focus-recipe"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet, PacketSendListener listener) { }
        };
        player.getAdvancements();
        player.initInventoryMenu();
        h.assertFalse(player.getRecipeBook().contains(RECIPE), "recipe should start locked");
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT));
        player.inventoryMenu.broadcastChanges();
        h.assertFalse(player.getRecipeBook().contains(RECIPE), "copper alone should not unlock the recipe");
        player.getInventory().add(new ItemStack(Items.EMERALD));
        player.inventoryMenu.broadcastChanges();
        var advancement = server.getAdvancements().getAdvancement(ADVANCEMENT);
        h.assertTrue(advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone()
                && player.getRecipeBook().contains(RECIPE), "receiving an emerald should unlock the recipe");

        var level = h.getLevel();
        var grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
        grid.setItem(1, new ItemStack(Items.COPPER_INGOT));
        grid.setItem(2, new ItemStack(Items.EMERALD));
        grid.setItem(4, new ItemStack(Items.STICK));
        grid.setItem(5, new ItemStack(Items.COPPER_INGOT));
        grid.setItem(6, new ItemStack(Items.LEATHER));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        h.assertTrue(recipe.isPresent() && recipe.orElseThrow().getId().equals(RECIPE),
                "the approved workbench layout should match the resonance recipe");
        var result = recipe.orElseThrow().assemble(grid, level.registryAccess());
        h.assertTrue(result.is(ForgeRegistries.ITEMS.getValue(RECIPE)) && result.getCount() == 1 && !result.hasTag(),
                "crafting should produce one unbound device with the host default effect");
        grid.setItem(6, ItemStack.EMPTY);
        h.assertFalse(recipe.orElseThrow().matches(grid, level), "leather should be required");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_resonanceRecipeOverride")
    public static void resonanceRecipeCanBeOverridden(GameTestHelper h) throws IOException {
        var server = h.getLevel().getServer();
        var packIds = server.getPackRepository().getSelectedIds();
        var path = server.getWorldPath(LevelResource.ROOT)
                .resolve("datapacks/baseline-tests/data/writ/recipes/resonance_device.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                {
                  "type": "minecraft:crafting_shapeless",
                  "ingredients": [{ "item": "minecraft:emerald" }],
                  "result": { "item": "writ:resonance_device" }
                }
                """);
        server.reloadResources(packIds).whenComplete((ignored, failure) -> server.execute(() -> {
            Throwable outcome = failure;
            try {
                if (failure == null) {
                    var player = h.makeMockPlayer();
                    var grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 2, 2);
                    grid.setItem(0, new ItemStack(Items.EMERALD));
                    var level = h.getLevel();
                    var replacement = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
                    h.assertTrue(replacement.isPresent() && replacement.orElseThrow().getId().equals(RECIPE),
                            "a datapack should replace the integrated recipe at the same ID");
                }
            } catch (Throwable error) {
                outcome = error;
            }
            TestRestore.finish(h, () -> Files.delete(path), () -> {
                var original = h.getLevel().getRecipeManager().byKey(RECIPE).orElseThrow();
                h.assertFalse(original.canCraftInDimensions(2, 2), "the original 3 by 3 recipe should be restored");
            }, outcome);
        }));
    }
}
