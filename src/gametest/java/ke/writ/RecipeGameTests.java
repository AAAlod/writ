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
    private static final ResourceLocation RANDOM_ID = new ResourceLocation("writ", "random_writ");
    private static final ResourceLocation RANDOM_ADVANCEMENT = new ResourceLocation("writ", "recipes/random_writ");
    private static net.minecraft.server.level.ServerPlayer recipePlayer(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var player = new net.minecraft.server.level.ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), "recipe-test"));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection, player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) { }
        };
        return player;
    }
    @GameTest(template = "empty")
    public static void randomRecipeAdvancementReward(GameTestHelper h) {
        var player = recipePlayer(h);
        var advancement = h.getLevel().getServer().getAdvancements().getAdvancement(RANDOM_ADVANCEMENT);
        h.assertTrue(advancement != null, "random recipe advancement missing");
        h.assertTrue(java.util.Arrays.asList(advancement.getRewards().getRecipes()).contains(RANDOM_ID), "recipe reward missing");
        h.assertFalse(player.getRecipeBook().contains(RANDOM_ID), "recipe should start locked");
        h.assertTrue(player.getAdvancements().award(advancement, "has_trade_writ"), "criterion award failed");
        h.assertTrue(player.getRecipeBook().contains(RANDOM_ID), "advancement reward did not unlock recipe");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void randomRecipeUnlocksOnInventoryChange(GameTestHelper h) {
        var player = recipePlayer(h);
        var advancement = h.getLevel().getServer().getAdvancements().getAdvancement(RANDOM_ADVANCEMENT);
        h.assertTrue(advancement != null, "random recipe advancement missing");
        h.assertFalse(player.getRecipeBook().contains(RANDOM_ID), "recipe should start locked");
        player.getAdvancements();
        player.initInventoryMenu();
        h.assertTrue(player.getInventory().add(new ItemStack(Writ.NORMAL.get())), "inventory addition failed");
        player.inventoryMenu.broadcastChanges();
        h.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(), "inventory change did not complete advancement");
        h.assertTrue(player.getRecipeBook().contains(RANDOM_ID), "inventory change did not unlock recipe");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_randomRecipeReload")
    public static void randomRecipeAndAdvancementSurviveReload(GameTestHelper h) {
        var server = h.getLevel().getServer();
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
            try {
                if (failure != null) throw new IllegalStateException("reload failed", failure);
                h.assertTrue(h.getLevel().getRecipeManager().byKey(RANDOM_ID).isPresent(), "recipe missing after reload");
                h.assertTrue(server.getAdvancements().getAdvancement(RANDOM_ADVANCEMENT) != null, "advancement missing after reload");
                h.succeed();
            } catch (Throwable e) { h.fail("random recipe reload failed: " + e); }
        }));
    }
    private static ItemStack ingredient(int index) {
        var stack = new ItemStack(Writ.NORMAL.get());
        if (index == 1) stack.getOrCreateTag().putString("preset_id", "writ:example");
        if (index == 2) { stack.getOrCreateTag().putString("preset_id", "other:missing"); stack.getOrCreateTag().putString("display_snapshot", "different"); }
        if (index == 3) stack.getOrCreateTag().putInt("selected_variant", 2);
        return stack;
    }
    private static void randomRecipeMatches(GameTestHelper h, TransientCraftingContainer grid) {
        var found = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel());
        h.assertTrue(found.isPresent() && found.orElseThrow().getId().equals(RANDOM_ID), "four writs must match random recipe");
        var output = found.orElseThrow().assemble(grid, h.getLevel().registryAccess());
        h.assertTrue(output.is(Writ.RANDOM.get()) && output.getCount() == 1, "expected one random writ");
        h.assertTrue(!output.hasTag() || (!output.getTag().contains("preset_id") && !output.getTag().contains("display_snapshot")), "result inherited input data");
    }
    @GameTest(template = "empty")
    public static void randomWritRecipeCrafting(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        h.assertTrue(manager.byKey(RANDOM_ID).isPresent(), "random recipe must exist in active mode");
        var player = h.makeMockSurvivalPlayer();
        var small = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 2, 2);
        for (int i = 0; i < 4; i++) small.setItem(i, ingredient(i));
        randomRecipeMatches(h, small);
        var grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
        for (int[] positions : new int[][] {{0, 2, 4, 8}, {1, 3, 6, 7}}) {
            grid.clearContent();
            for (int i = 0; i < 4; i++) grid.setItem(positions[i], ingredient(i));
            randomRecipeMatches(h, grid);
        }
        for (int count : new int[] {3, 5}) {
            grid.clearContent();
            for (int i = 0; i < count; i++) grid.setItem(i, ingredient(i));
            h.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).isEmpty(), count + " writs must not match");
        }
        for (var wrong : new net.minecraft.world.item.Item[] {Items.STONE, Writ.RANDOM.get(), Writ.CHAOS.get()}) {
            grid.clearContent();
            for (int i = 0; i < 4; i++) grid.setItem(i, ingredient(i));
            if (wrong == Items.STONE) grid.setItem(4, new ItemStack(wrong)); else grid.setItem(3, new ItemStack(wrong));
            h.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).isEmpty(), "wrong ingredient matched: " + wrong);
        }
        grid.clearContent();
        for (int i = 0; i < 9; i++) grid.setItem(i, ingredient(i));
        var nine = manager.getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel());
        boolean craft = Presets.INSTANCE.activeMode() == ObtainMode.CRAFT_9;
        h.assertTrue(nine.isPresent() == craft, "nine writs must follow chaos mode");
        if (craft) {
            h.assertTrue(nine.orElseThrow().getId().equals(ID), "nine writs must match chaos recipe");
            h.assertTrue(nine.orElseThrow().assemble(grid, h.getLevel().registryAccess()).is(Writ.CHAOS.get()), "nine writs must produce chaos writ");
        }
        h.assertFalse(((net.minecraft.world.item.crafting.CraftingRecipe) manager.byKey(RANDOM_ID).orElseThrow()).matches(grid, h.getLevel()), "random recipe must reject nine writs");
        h.succeed();
    }
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
