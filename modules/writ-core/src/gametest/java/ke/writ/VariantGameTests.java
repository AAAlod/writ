package ke.writ;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class VariantGameTests {
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_variantReload")
    public static void variantReload(GameTestHelper h) {
        h.runAfterDelay(20, () -> {
            var server = h.getLevel().getServer();
            var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("datapacks/baseline-tests/data/mypack/writs/presets/variant_reload.json");
            byte[][] old = new byte[1][];
            TestRestore.Action restore = () -> { if (old[0] == null) java.nio.file.Files.deleteIfExists(path); else java.nio.file.Files.write(path, old[0]); };
            TestRestore.Action verify = () -> h.assertFalse(Presets.INSTANCE.currentSnapshot().presets().containsKey(new net.minecraft.resources.ResourceLocation("mypack:variant_reload")), "fixture not removed");
            try {
                old[0] = java.nio.file.Files.exists(path) ? java.nio.file.Files.readAllBytes(path) : null; java.nio.file.Files.createDirectories(path.getParent());
                String base = "{\"pool\":\"n\",\"weight\":1,\"trade\":{\"cost_a\":{\"item\":\"minecraft:emerald\",\"count\":1},\"result\":{\"item\":\"minecraft:terracotta\",\"count\":1},\"max_uses\":1,\"villager_xp\":0,\"price_multiplier\":0},\"variants\":[{\"id\":\"blue\",\"trade\":{\"result\":{\"item\":\"minecraft:blue_terracotta\",\"count\":1}}}]}";
                java.nio.file.Files.writeString(path, base);
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                    try {
                        if (failure != null) throw new IllegalStateException(failure);
                        var stack = Presets.INSTANCE.contractFor(Presets.INSTANCE.currentSnapshot(), "mypack:variant_reload");
                        Presets.INSTANCE.switchVariant(stack, false);
                        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
                        h.assertTrue(Presets.INSTANCE.offer(stack, villager).getResult().is(Items.BLUE_TERRACOTTA), "variant fixture not active");
                        var openPlayer = serverPlayer(h);
                        openPlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
                        var openBeforeReload = VariantNet.begin(openPlayer, packet(0, stack));
                        h.assertTrue(openBeforeReload != null, "could not open selector before reload");
                        java.nio.file.Files.writeString(path, base.replace("minecraft:blue_terracotta", "minecraft:red_terracotta"));
                        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused2, failure2) -> server.execute(() -> {
                            try {
                                if (failure2 != null) throw new IllegalStateException(failure2);
                                h.assertTrue(Presets.INSTANCE.offer(stack, villager).getResult().is(Items.RED_TERRACOTTA), "changed variant not applied");
                                h.assertFalse(VariantNet.apply(openPlayer, openBeforeReload, "blue"),
                                        "confirmed old terms after the same option ID changed on reload");
                                var empty = com.google.gson.JsonParser.parseString(base).getAsJsonObject(); empty.add("variants", new com.google.gson.JsonArray());
                                java.nio.file.Files.writeString(path, empty.toString());
                                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused3, failure3) -> server.execute(() -> {
                                    try {
                                        if (failure3 != null) throw new IllegalStateException(failure3);
                                        h.assertTrue(Presets.INSTANCE.offer(stack, villager) == null && stack.getTag().getString("selected_variant").equals("blue"), "removed variant silently downgraded");
                                        var resetPlayer = serverPlayer(h); resetPlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
                                        var reset = VariantNet.begin(resetPlayer, packet(0, stack));
                                        h.assertTrue(reset != null && VariantNet.apply(resetPlayer, reset, ""), "removed selection must be recoverable through the selector");
                                        h.assertFalse(stack.getTag().contains("selected_variant"), "reset must remove selection");
                                        h.assertTrue(Presets.INSTANCE.offer(stack, villager).getResult().is(Items.TERRACOTTA), "reset default offer missing");
                                        h.assertTrue(SnapshotView.parse(stack.getTag()).variantTotal() == 0 && SnapshotView.parse(stack.getTag()).result().id().equals("minecraft:terracotta"), "reset snapshot must match default without variant");
                                        var before = stack.getTag().copy(); Presets.INSTANCE.switchVariant(stack, false);
                                        h.assertTrue(before.equals(stack.getTag()), "empty variants must be no-op");
                                        TestRestore.finish(h, restore, verify, null);
                                    } catch (Throwable e) { TestRestore.finish(h, restore, verify, e); }
                                }));
                            } catch (Throwable e) { TestRestore.finish(h, restore, verify, e); }
                        }));
                    } catch (Throwable e) { TestRestore.finish(h, restore, verify, e); }
                }));
            } catch (Throwable e) { TestRestore.finish(h, restore, verify, e); }
        });
    }
    private static net.minecraft.server.level.ServerPlayer serverPlayer(GameTestHelper h) {
        var p = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "variant-test")) {
            @Override public void displayClientMessage(net.minecraft.network.chat.Component text, boolean overlay) { }
        };
        var pos = h.absolutePos(new BlockPos(1, 20, 1)); p.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5); p.setYRot(0); p.setXRot(0); return p;
    }
    private static VariantPacket packet(int hand, net.minecraft.world.item.ItemStack stack) {
        var tag = stack.getTag(); return new VariantPacket(hand, tag == null ? "" : tag.getString("preset_id"), tag == null ? "" : VariantRules.selected(tag), stack.getCount());
    }
    @GameTest(template = "empty")
    public static void legacyAndContextValidation(GameTestHelper h) {
        var old = new net.minecraft.world.item.ItemStack(Writ.NORMAL.get(), 2); old.getOrCreateTag().putString("preset_id", "writ:terracotta");
        var original = old.getTag().copy();
        for (var wrong : java.util.List.of(new VariantPacket(0, "other:id", "", 2), new VariantPacket(0, "writ:terracotta", "blue", 2), new VariantPacket(0, "writ:terracotta", "", 1))) {
            var p = serverPlayer(h); p.setItemInHand(InteractionHand.MAIN_HAND, old);
            h.assertTrue(VariantNet.begin(p, wrong) == null && old.getTag().equals(original), "mismatched context changed stack");
        }
        var valid = serverPlayer(h); valid.setItemInHand(InteractionHand.MAIN_HAND, old);
        var result = VariantNet.begin(valid, packet(0, old));
        h.assertTrue(result != null && VariantNet.apply(valid, result, "white")
                && old.getCount() == 2 && old.getTag().getString("selected_variant").equals("white")
                && SnapshotView.parse(old.getTag()).variantTotal() == 17, "legacy stack selection was not saved");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void serverIntentChecks(GameTestHelper h) {
        var p = serverPlayer(h);
        var main = Presets.INSTANCE.contractFor(Presets.INSTANCE.currentSnapshot(), "writ:terracotta");
        var off = main.copy(); p.setItemInHand(InteractionHand.MAIN_HAND, main); p.setItemInHand(InteractionHand.OFF_HAND, off);
        h.assertTrue(VariantNet.begin(p, packet(-1, main)) == null && !main.getTag().contains("selected_variant"), "invalid hand accepted");
        var opened = VariantNet.begin(p, packet(1, off));
        h.assertTrue(opened != null && !off.getTag().contains("selected_variant"), "opening mutated the contract");
        h.assertFalse(VariantNet.apply(p, opened, "not_an_option"), "server accepted an unknown option");
        h.assertTrue(VariantNet.apply(p, opened, "white") && off.getTag().getString("selected_variant").equals("white")
                && !main.getTag().contains("selected_variant"), "offhand did not switch alone");
        var swappedPlayer = serverPlayer(h);
        swappedPlayer.setItemInHand(InteractionHand.MAIN_HAND, main);
        var beforeSwap = VariantNet.begin(swappedPlayer, packet(0, main));
        h.assertTrue(beforeSwap != null, "could not open before stack replacement");
        swappedPlayer.setItemInHand(InteractionHand.MAIN_HAND, main.copy());
        h.assertFalse(VariantNet.apply(swappedPlayer, beforeSwap, "white"), "accepted a replacement stack with identical contents");
        h.assertTrue(VariantNet.begin(p, packet(0, main)) == null && !main.getTag().contains("selected_variant"), "rapid second request accepted");
        var blockPlayer = serverPlayer(h); var block = blockPlayer.blockPosition().above().south(2);
        h.getLevel().setBlockAndUpdate(block, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        blockPlayer.setItemInHand(InteractionHand.MAIN_HAND, main.copy());
        h.assertTrue(!VariantNet.clearTarget(blockPlayer) && VariantNet.begin(blockPlayer, packet(0, main)) == null, "block must reject intent");
        h.getLevel().removeBlock(block, false);
        var entityPlayer = serverPlayer(h);
        var entity = EntityType.ARMOR_STAND.create(h.getLevel());
        entity.setPos(entityPlayer.getX(), entityPlayer.getY(), entityPlayer.getZ() + 2); h.getLevel().addFreshEntity(entity);
        entityPlayer.setItemInHand(InteractionHand.MAIN_HAND, main.copy());
        h.assertTrue(!VariantNet.clearTarget(entityPlayer) && VariantNet.begin(entityPlayer, packet(0, main)) == null, "entity must reject intent");
        entity.discard();
        var other = serverPlayer(h); other.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(Items.STONE));
        h.assertTrue(VariantNet.begin(other, packet(0, main)) == null, "non-contract accepted");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void randomChoiceSignsOnlyWithOriginalVillagerInReach(GameTestHelper h) {
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.MASON));
        villager.setVillagerXp(1);
        var player = serverPlayer(h);
        player.setPos(villager.getX(), villager.getY(), villager.getZ() + 1);
        int slot = 10;
        var contract = Presets.INSTANCE.contractFor(Presets.INSTANCE.currentSnapshot(), "writ:terracotta");
        player.getInventory().setItem(slot, contract);
        int initial = villager.getOffers().size();
        var session = VariantNet.prepare(player, slot, contract, villager.getUUID());
        h.assertTrue(session != null && VariantNet.apply(player, session, "white"), "random choice failed");
        h.assertTrue(villager.getOffers().size() == initial + 1 && contract.isEmpty()
                && villager.getOffers().get(initial).getResult().is(Items.WHITE_TERRACOTTA),
                "random choice did not sign selected trade and consume the writ");

        var fallback = Presets.INSTANCE.contractFor(Presets.INSTANCE.currentSnapshot(), "writ:terracotta");
        player.getInventory().setItem(slot, fallback);
        session = VariantNet.prepare(player, slot, fallback, villager.getUUID());
        player.setPos(villager.getX(), villager.getY() + 20, villager.getZ());
        h.assertTrue(session != null && VariantNet.apply(player, session, "white"), "out-of-reach choice was not saved");
        h.assertTrue(villager.getOffers().size() == initial + 1 && fallback.getCount() == 1
                && fallback.getTag().getString("selected_variant").equals("white"), "out-of-reach signing lost the fallback");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void protectionAddedDuringSelectionKeepsChosenWrit(GameTestHelper h) {
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.MASON));
        villager.setVillagerXp(1);
        var player = serverPlayer(h);
        player.setPos(villager.getX(), villager.getY(), villager.getZ() + 1);
        int slot = 10;
        var contract = Presets.INSTANCE.contractFor(Presets.INSTANCE.currentSnapshot(), "writ:terracotta");
        player.getInventory().setItem(slot, contract);
        int initial = villager.getOffers().size();
        var session = VariantNet.prepare(player, slot, contract, villager.getUUID());
        h.assertTrue(session != null && !ContractExemption.isActive(villager), "could not open an eligible choice");
        h.assertTrue(ContractExemption.apply(player, villager, java.util.OptionalInt.empty()) == ContractExemption.Result.APPLIED,
                "could not protect the original villager");
        h.assertTrue(VariantNet.apply(player, session, "white"), "could not confirm the choice");
        h.assertTrue(player.getInventory().getItem(slot) == contract && contract.getCount() == 1
                && contract.getTag().getString("selected_variant").equals("white"),
                "choice was lost when auto-sign was refused");
        h.assertTrue(villager.getOffers().size() == initial && !villager.getPersistentData().contains(Writ.ID),
                "auto-sign added a trade or changed quota despite protection");

        var other = h.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 1));
        other.setVillagerData(other.getVillagerData().setProfession(VillagerProfession.MASON));
        other.setVillagerXp(1);
        int otherInitial = other.getOffers().size();
        Writ.sign(player, other, contract, null);
        h.assertTrue(other.getOffers().size() == otherInitial + 1 && contract.isEmpty()
                && other.getOffers().get(otherInitial).getResult().is(Items.WHITE_TERRACOTTA),
                "saved choice could not sign with another villager");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void selectorTransmitsItemCountsAndFullTags(GameTestHelper h) {
        var result = new net.minecraft.world.item.ItemStack(Items.ENCHANTED_BOOK);
        var enchantments = new net.minecraft.nbt.ListTag();
        var entry = new net.minecraft.nbt.CompoundTag();
        entry.putString("id", "minecraft:mending");
        entry.putShort("lvl", (short) 1);
        enchantments.add(entry);
        result.getOrCreateTag().put("StoredEnchantments", enchantments);
        var choice = new VariantNet.Option("book", net.minecraft.network.chat.Component.literal("Book"),
                new net.minecraft.world.item.ItemStack(Items.EMERALD, 16),
                net.minecraft.world.item.ItemStack.EMPTY, result);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            choice.encode(buffer);
            var decoded = VariantNet.Option.decode(buffer);
            h.assertTrue(decoded.id().equals(choice.id()) && decoded.name().equals(choice.name())
                    && decoded.costA().getCount() == 16 && decoded.costB().isEmpty()
                    && net.minecraft.world.item.ItemStack.matches(result, decoded.result())
                    && buffer.readableBytes() == 0, "choice preview lost item count, tag or optional payment");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void variantValidation(GameTestHelper h) {
        var base = com.google.gson.JsonParser.parseString("{\"pool\":\"n\",\"weight\":1,\"trade\":{\"cost_a\":{\"item\":\"minecraft:emerald\",\"count\":1},\"cost_b\":{\"item\":\"minecraft:white_concrete_powder\",\"count\":32},\"result\":{\"item\":\"minecraft:white_concrete\",\"count\":32},\"max_uses\":8,\"villager_xp\":0,\"price_multiplier\":0}} ").getAsJsonObject();
        h.assertTrue(Presets.parse(base).variants().isEmpty(), "legacy preset must load");
        base.add("variants", com.google.gson.JsonParser.parseString("[{\"id\":\"blue\",\"trade\":{\"cost_b\":{\"item\":\"minecraft:blue_concrete_powder\",\"count\":32},\"result\":{\"item\":\"minecraft:blue_concrete\",\"count\":32}}},{\"id\":\"free\",\"trade\":{\"cost_b\":null}}]"));
        var variants = Presets.parse(base).variants();
        h.assertTrue(variants.get(0).costB().is(Items.BLUE_CONCRETE_POWDER) && variants.get(0).result().is(Items.BLUE_CONCRETE), "paired replacement failed");
        h.assertTrue(variants.get(1).costB().isEmpty() && variants.get(1).result().is(Items.WHITE_CONCRETE), "null must remove cost_b and inherit result");
        base.add("variants", com.google.gson.JsonParser.parseString("[{\"id\":\"off\",\"enabled\":false},{\"id\":\"on\",\"enabled\":true},{\"id\":\"implicit\"}]"));
        var enabled = Presets.parse(base).variants();
        h.assertTrue(enabled.size() == 2 && enabled.get(0).id().equals("on") && enabled.get(1).id().equals("implicit"), "disabled variant must be skipped without changing order");
        for (String badFlag : java.util.List.of("null", "1", "\"false\"", "{}")) {
            base.add("variants", com.google.gson.JsonParser.parseString("[{\"id\":\"x\",\"enabled\":" + badFlag + "}]"));
            try { Presets.parse(base); h.fail("accepted malformed enabled flag " + badFlag); return; }
            catch (IllegalArgumentException expected) { h.assertTrue(expected.getMessage().contains("variants[0].enabled"), "error lacks enabled path"); }
        }
        for (String invalid : java.util.List.of("[{\"id\":\"default\"}]", "[{\"id\":\"\"}]", "[{\"id\":\"x\"},{\"id\":\"x\"}]", "[{\"id\":\"X\"}]", "[{\"id\":\"x\",\"pool\":\"n\"}]", "[{\"id\":\"x\",\"trade\":{\"max_uses\":2}}]", "[{\"id\":\"x\",\"trade\":{\"cost_b\":7}}]", "[{\"id\":\"x\",\"trade\":{\"result\":{\"item\":\"minecraft:air\",\"count\":1}}}]", "[{\"id\":\"x\",\"trade\":{\"result\":{\"item\":\"minecraft:stone\",\"count\":65}}}]", "[{\"id\":\"x\",\"trade\":{\"result\":{\"item\":\"minecraft:stone\",\"count\":1.5}}}]", "[{\"id\":\"x\",\"trade\":{\"result\":{\"item\":\"minecraft:stone\",\"count\":1,\"nbt\":\"{broken\"}}}]")) {
            base.add("variants", com.google.gson.JsonParser.parseString(invalid));
            try { Presets.parse(base); h.fail("accepted invalid variants " + invalid); return; }
            catch (IllegalArgumentException expected) { h.assertTrue(expected.getMessage().contains("variants["), "error lacks array index"); }
        }
        base.add("variants", com.google.gson.JsonParser.parseString("{}"));
        try { Presets.parse(base); h.fail("non-array variants accepted"); return; } catch (RuntimeException expected) { }
        var many = new com.google.gson.JsonArray(); for (int i = 0; i < 65; i++) { var entry = new com.google.gson.JsonObject(); entry.addProperty("id", "v" + i); many.add(entry); }
        base.add("variants", many);
        try { Presets.parse(base); h.fail("more than 64 variants accepted"); return; } catch (RuntimeException expected) { }
        var tooLong = new com.google.gson.JsonArray(); var longId = new com.google.gson.JsonObject(); longId.addProperty("id", "x".repeat(129)); tooLong.add(longId); base.add("variants", tooLong);
        try { Presets.parse(base); h.fail("variant id longer than packet bound accepted"); return; } catch (RuntimeException expected) { }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void variantsSigningAndStacks(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var p = snapshot.presets().get(new net.minecraft.resources.ResourceLocation("writ:terracotta"));
        h.assertTrue(p != null && p.variants().size() == 16 && p.weight() == 3, "17-option preset missing");
        h.assertTrue(snapshot.candidates().stream().filter(c -> c.id().equals("writ:terracotta")).count() == 1, "variants must not create loot candidates");
        var stack = Presets.INSTANCE.contractFor(snapshot, "writ:terracotta"); stack.setCount(3);
        h.assertFalse(stack.getTag().contains("selected_variant"), "default must not be stored");
        var v = h.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.MASON)); v.setVillagerXp(1);
        h.assertTrue(Presets.INSTANCE.offer(stack, v).getResult().is(Items.TERRACOTTA), "default must be uncolored");
        for (int i = 0; i < 12; i++) Presets.INSTANCE.switchVariant(stack, false);
        h.assertTrue(stack.getTag().getString("selected_variant").equals("blue"), "wrong ordered selection");
        h.assertTrue(Presets.INSTANCE.offer(stack, v).getResult().is(Items.BLUE_TERRACOTTA), "wrong blue offer");
        var split = stack.split(1); h.assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameTags(stack, split) && stack.getCount() + split.getCount() == 3, "split must preserve selection and count"); stack.grow(1);
        var other = stack.copy(); Presets.INSTANCE.switchVariant(other, false);
        h.assertFalse(net.minecraft.world.item.ItemStack.isSameItemSameTags(stack, other), "different choices must not stack");
        var player = h.makeMockSurvivalPlayer(); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        int before = v.getOffers().size();
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, v));
        h.assertTrue(v.getOffers().size() == before + 1 && stack.getCount() == 2 && stack.getTag().getString("selected_variant").equals("blue"), "signing must consume one selected contract");
        Presets.INSTANCE.switchVariant(stack, true);
        h.assertTrue(v.getOffers().get(before).getResult().is(Items.BLUE_TERRACOTTA), "existing offer changed");
        stack.getTag().putString("selected_variant", "removed");
        int count = stack.getCount(); int offers = v.getOffers().size();
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, v));
        h.assertTrue(stack.getCount() == count && v.getOffers().size() == offers, "invalid selection must fail safely");
        h.assertTrue(Presets.INSTANCE.switchVariant(stack, false).reset() && !stack.getTag().contains("selected_variant"), "invalid selection must reset on switch");
        Presets.INSTANCE.switchVariant(stack, true);
        h.assertTrue(stack.getTag().getString("selected_variant").equals("black"), "reverse wraps to last");
        Presets.INSTANCE.switchVariant(stack, false);
        h.assertFalse(stack.getTag().contains("selected_variant"), "forward wraps to default");
        h.succeed();
    }
}
