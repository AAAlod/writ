package ke.writ;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class WoodContractGameTests {
    @GameTest(template = "empty", batch = "isolated_playerContainers")
    public static void playerOpensChestBarrelAndMinecart(GameTestHelper h) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var relative = new BlockPos(1, 1, 1);
        var pos = h.absolutePos(relative);
        for (var block : java.util.List.of(net.minecraft.world.level.block.Blocks.CHEST, net.minecraft.world.level.block.Blocks.BARREL)) {
            int hits = 0;
            for (int i = 0; i < 80; i++) {
                h.getLevel().removeBlock(pos, false);
                h.setBlock(relative, block);
                var container = (net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity) h.getLevel().getBlockEntity(pos);
                container.setLootTable(new ResourceLocation("writ:chests/test_unlisted"), i + 1);
                h.assertTrue(container.createMenu(1, player.getInventory(), player) != null, "player could not open container");
                int found = 0;
                for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(Writ.NORMAL.get())) found++;
                if (found > 0) hits++;
                container.createMenu(2, player.getInventory(), player);
                int again = 0;
                for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(Writ.NORMAL.get())) again++;
                h.assertTrue(found == again, "player opening rerolled a container");
                container.clearContent();
            }
            h.assertTrue(hits >= 15 && hits <= 55, "player opened " + block + " without expected general loot: " + hits);
        }
        int hits = 0;
        for (int i = 0; i < 80; i++) {
            var cart = h.spawn(EntityType.CHEST_MINECART, relative);
            cart.setLootTable(new ResourceLocation("writ:chests/test_unlisted"), i + 1);
            cart.unpackChestVehicleLootTable(player);
            int found = 0;
            for (int slot = 0; slot < cart.getContainerSize(); slot++) if (cart.getItem(slot).is(Writ.NORMAL.get())) found++;
            if (found > 0) hits++;
            cart.unpackChestVehicleLootTable(player);
            int again = 0;
            for (int slot = 0; slot < cart.getContainerSize(); slot++) if (cart.getItem(slot).is(Writ.NORMAL.get())) again++;
            h.assertTrue(found == again, "player opening rerolled a minecart");
            cart.clearContent(); cart.discard();
        }
        h.assertTrue(hits >= 15 && hits <= 55, "player opened minecart without expected general loot: " + hits);
        h.succeed();
    }
    @GameTest(template = "empty", batch = "isolated_nonContainerLoot")
    public static void entityAndBlockLootAtChestDoNotGetGeneralWrits(GameTestHelper h) {
        var relative = new BlockPos(1, 1, 1);
        h.setBlock(relative, net.minecraft.world.level.block.Blocks.CHEST);
        var pos = h.absolutePos(relative);
        var origin = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        var table = h.getLevel().getServer().getLootData().getLootTable(new ResourceLocation("writ:chests/test_unlisted"));
        var zombie = h.spawn(EntityType.ZOMBIE, relative);
        var entityParams = new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, origin)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, zombie)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, h.getLevel().damageSources().generic())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
        var playerParams = new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, origin)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, h.makeMockSurvivalPlayer())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, h.getLevel().damageSources().generic())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
        var blockParams = new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, origin)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, net.minecraft.world.item.ItemStack.EMPTY)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK);
        for (int i = 0; i < 80; i++) {
            h.assertTrue(table.getRandomItems(entityParams).stream().noneMatch(s -> s.is(Writ.NORMAL.get())), "entity drop produced general writ");
            h.assertTrue(table.getRandomItems(playerParams).stream().noneMatch(s -> s.is(Writ.NORMAL.get())), "player death produced general writ");
            h.assertTrue(table.getRandomItems(blockParams).stream().noneMatch(s -> s.is(Writ.NORMAL.get())), "block drop produced general writ");
        }
        h.succeed();
    }
    @GameTest(template = "empty", batch = "isolated_woodChest")
    public static void realChestGeneralLootRunsOnce(GameTestHelper h) {
        var relative = new BlockPos(1, 1, 1);
        var pos = h.absolutePos(relative);
        int hits = 0;
        for (int i = 0; i < 80; i++) {
            h.getLevel().removeBlock(pos, false);
            h.setBlock(relative, net.minecraft.world.level.block.Blocks.CHEST);
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(pos);
            chest.setLootTable(new ResourceLocation("writ:chests/test_unlisted"), i + 1);
            int found = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(Writ.NORMAL.get())) found++;
            if (found > 0) hits++;
            int again = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(Writ.NORMAL.get())) again++;
            h.assertTrue(found == again, "opening the same chest rerolled its loot");
            chest.clearContent();
        }
        h.assertTrue(hits >= 15 && hits <= 55, "general loot did not occur at the expected scale: " + hits);
        h.succeed();
    }
    @GameTest(template = "empty", batch = "isolated_woodMinecart")
    public static void realMinecartGeneralLootRunsOnce(GameTestHelper h) {
        int hits = 0;
        for (int i = 0; i < 80; i++) {
            var cart = h.spawn(EntityType.CHEST_MINECART, new BlockPos(1, 1, 1));
            cart.setLootTable(new ResourceLocation("writ:chests/test_unlisted"), i + 1);
            int found = 0;
            for (int slot = 0; slot < cart.getContainerSize(); slot++) if (cart.getItem(slot).is(Writ.NORMAL.get())) found++;
            if (found > 0) hits++;
            int again = 0;
            for (int slot = 0; slot < cart.getContainerSize(); slot++) if (cart.getItem(slot).is(Writ.NORMAL.get())) again++;
            h.assertTrue(found == again, "opening the same minecart rerolled its loot");
            cart.clearContent();
            cart.discard();
        }
        h.assertTrue(hits >= 15 && hits <= 55, "general minecart loot did not occur at the expected scale: " + hits);
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_woodBlacklist")
    public static void blacklistReloadInvalidatesUnsignedWrit(GameTestHelper h) {
        h.runAfterDelay(20, () -> {
            var server = h.getLevel().getServer();
            var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("datapacks/baseline-tests/data/writ/tags/items/log_contract_exclusions.json");
            byte[][] old = new byte[1][];
            TestRestore.Action restore = () -> { if (old[0] == null) java.nio.file.Files.deleteIfExists(path); else java.nio.file.Files.write(path, old[0]); };
            try {
                old[0] = java.nio.file.Files.exists(path) ? java.nio.file.Files.readAllBytes(path) : null;
                var writ = Presets.INSTANCE.contractFor(Presets.INSTANCE.currentSnapshot(), "writ:log_supply");
                String selected = writ.getTag().getString("selected_variant");
                var villager = h.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
                var signedOffer = Presets.INSTANCE.offer(writ, villager);
                h.assertTrue(signedOffer != null, "initial dynamic offer missing");
                villager.getOffers().add(signedOffer);
                java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.writeString(path, "{\"replace\":false,\"values\":[\"" + selected + "\"]}");
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                    try {
                        if (failure != null) throw new IllegalStateException(failure);
                        h.assertTrue(Presets.INSTANCE.offer(writ, villager) == null, "blacklisted selection signed after reload");
                        h.assertTrue(villager.getOffers().contains(signedOffer) && signedOffer.getResult().getCount() == 64, "already signed offer changed");
                        var switched = Presets.INSTANCE.switchVariant(writ, false);
                        h.assertTrue(switched.reset() && Presets.INSTANCE.offer(writ, villager) != null, "stale option could not reset");
                        h.assertFalse(selected.equals(writ.getTag().getString("selected_variant")), "reset selected excluded item");
                        TestRestore.finish(h, restore, () -> {}, null);
                    } catch (Throwable e) { TestRestore.finish(h, restore, () -> {}, e); }
                }));
            } catch (Throwable e) { TestRestore.finish(h, restore, () -> {}, e); }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_singleWoodChoice")
    public static void singleChoiceIsSilentButStaleChoiceCanReset(GameTestHelper h) {
        h.runAfterDelay(20, () -> {
            var server = h.getLevel().getServer();
            var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("datapacks/baseline-tests/data/minecraft/tags/items/logs.json");
            byte[][] old = new byte[1][];
            TestRestore.Action restore = () -> { if (old[0] == null) java.nio.file.Files.deleteIfExists(path); else java.nio.file.Files.write(path, old[0]); };
            try {
                old[0] = java.nio.file.Files.exists(path) ? java.nio.file.Files.readAllBytes(path) : null;
                java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.writeString(path, "{\"replace\":true,\"values\":[\"minecraft:oak_log\"]}");
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                    try {
                        if (failure != null) throw new IllegalStateException(failure);
                        var writ = Presets.INSTANCE.contractFor(Presets.INSTANCE.currentSnapshot(), "writ:log_supply");
                        h.assertFalse(writ.isEmpty(), "single valid choice must remain available");
                        var before = writ.getTag().copy();
                        var unchanged = Presets.INSTANCE.switchVariant(writ, false);
                        h.assertTrue(unchanged.failure() == null && unchanged.total() == 0 && !unchanged.reset() && before.equals(writ.getTag()), "single valid choice must be silent and unchanged");
                        writ.getTag().putString("selected_variant", "minecraft:removed_log");
                        var reset = Presets.INSTANCE.switchVariant(writ, false);
                        h.assertTrue(reset.reset() && writ.getTag().getString("selected_variant").equals("minecraft:oak_log"), "stale choice must still reset to the sole valid item");
                        TestRestore.finish(h, restore, () -> {}, null);
                    } catch (Throwable e) { TestRestore.finish(h, restore, () -> {}, e); }
                }));
            } catch (Throwable e) { TestRestore.finish(h, restore, () -> {}, e); }
        });
    }
    @GameTest(template = "empty")
    public static void dynamicChoiceUsesStableItemId(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var preset = snapshot.presets().get(new ResourceLocation("writ:log_supply"));
        h.assertTrue(preset != null && preset.dynamic() && preset.variants().size() > 1, "log tag choices missing");
        var writ = Presets.INSTANCE.contractFor(snapshot, "writ:log_supply");
        String first = writ.getTag().getString("selected_variant");
        h.assertTrue(first.equals(preset.defaultId()), "default item ID must be stored");
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        var initial = Presets.INSTANCE.offer(writ, villager);
        h.assertTrue(initial != null && initial.getResult().getCount() == 64, "default offer missing");
        Presets.INSTANCE.switchVariant(writ, false);
        h.assertFalse(first.equals(writ.getTag().getString("selected_variant")), "switch did not update ID");
        writ.getTag().putString("selected_variant", "example:removed_log");
        h.assertTrue(Presets.INSTANCE.offer(writ, villager) == null, "removed item must not sign");
        var reset = Presets.INSTANCE.switchVariant(writ, false);
        h.assertTrue(reset.reset() && first.equals(writ.getTag().getString("selected_variant")), "removed selection did not reset");
        h.assertTrue(initial.getResult().getCount() == 64, "previous offer changed");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void saplingPairsAreExplicit(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var preset = snapshot.presets().get(new ResourceLocation("writ:sapling_for_logs"));
        h.assertTrue(preset != null && preset.variants().size() == 9, "vanilla pairs missing");
        var mangrove = preset.variants().stream().filter(v -> v.id().equals("mangrove_propagule")).findFirst().orElseThrow();
        h.assertTrue(mangrove.costB().is(Items.MANGROVE_PROPAGULE) && mangrove.result().is(Items.MANGROVE_LOG), "incorrect mangrove pair");
        var azalea = preset.variants().stream().filter(v -> v.id().equals("azalea")).findFirst().orElseThrow();
        h.assertTrue(azalea.costB().is(Items.AZALEA) && azalea.result().is(Items.OAK_LOG), "incorrect azalea pair");
        h.succeed();
    }
}
