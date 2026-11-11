package ke.writ;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class TradeGameTests {
    private static final class OtherMerchant extends net.minecraft.world.entity.decoration.ArmorStand implements net.minecraft.world.item.trading.Merchant {
        private final net.minecraft.world.item.trading.MerchantOffers offers = new net.minecraft.world.item.trading.MerchantOffers();
        OtherMerchant(net.minecraft.world.level.Level level) { super(EntityType.ARMOR_STAND, level); }
        public void setTradingPlayer(Player player) { }
        public Player getTradingPlayer() { return null; }
        public net.minecraft.world.item.trading.MerchantOffers getOffers() { return offers; }
        public void overrideOffers(net.minecraft.world.item.trading.MerchantOffers offers) { }
        public void notifyTrade(net.minecraft.world.item.trading.MerchantOffer offer) { }
        public void notifyTradeUpdated(ItemStack stack) { }
        public int getVillagerXp() { return 0; }
        public void overrideXp(int xp) { }
        public boolean showProgressBar() { return false; }
        public net.minecraft.sounds.SoundEvent getNotifyTradeSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_YES; }
        public boolean isClientSide() { return level().isClientSide; }
    }
    private static Villager villager(GameTestHelper helper) {
        var v = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER));
        v.setVillagerXp(1);
        return v;
    }
    private static Player player(GameTestHelper helper, String preset) {
        var player = helper.makeMockSurvivalPlayer();
        var stack = new ItemStack(Writ.NORMAL.get(), 2);
        stack.getOrCreateTag().putString("preset_id", preset);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }
    private static void counters(Villager villager, int normal, int chaos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putInt("normalAppliedCount", normal);
        tag.putInt("chaosAppliedCount", chaos);
        villager.getPersistentData().put(Writ.ID, tag);
    }
    private static PlayerInteractEvent.EntityInteract interact(Player player, Villager villager) {
        var event = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, villager);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }
    private static void verify(GameTestHelper helper, Villager villager, Player player,
                               PlayerInteractEvent.EntityInteract event, int offers, int normal, int chaos, int held) {
        var data = villager.getPersistentData().getCompound(Writ.ID);
        helper.assertTrue(villager.getOffers().size() == offers, "incorrect offer count");
        helper.assertTrue(data.getInt("normalAppliedCount") == normal, "incorrect normal count");
        helper.assertTrue(data.getInt("chaosAppliedCount") == chaos, "incorrect chaos count");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == held, "incorrect held count");
        helper.assertTrue(event.isCanceled(), "villager interaction must be canceled");
    }

    @GameTest(template = "empty")
    public static void normalContractAppendsWithPresetIdOnly(GameTestHelper helper) {
        var v = villager(helper);
        var p = player(helper, "writ:example");
        int before = v.getOffers().size();
        var event = interact(p, v);
        verify(helper, v, p, event, before + 1, 1, 0, 1);
        var rejected = villager(helper);
        var badVersion = player(helper, "writ:example");
        badVersion.getMainHandItem().getOrCreateTag().putInt("contract_version", 2);
        int rejectedBefore = rejected.getOffers().size();
        verify(helper, rejected, badVersion, interact(badVersion, rejected), rejectedBefore, 0, 0, 2);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void zeroXpIsRejected(GameTestHelper helper) {
        var v = villager(helper); v.setVillagerXp(0);
        var p = player(helper, "writ:example");
        int before = v.getOffers().size();
        verify(helper, v, p, interact(p, v), before, 0, 0, 2);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void wrongProfessionIsRejected(GameTestHelper helper) {
        var v = villager(helper);
        var p = player(helper, "writ:gametest_librarian");
        int before = v.getOffers().size();
        // A librarian must accept the same fixture, excluding a missing preset as the cause.
        var librarian = villager(helper);
        librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
        var allowed = player(helper, "writ:gametest_librarian");
        int allowedBefore = librarian.getOffers().size();
        verify(helper, librarian, allowed, interact(allowed, librarian), allowedBefore + 1, 1, 0, 1);
        verify(helper, v, p, interact(p, v), before, 0, 0, 2);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void categoryAndTotalLimits(GameTestHelper helper) {
        var v = villager(helper); counters(v, 7, 2);
        var p = player(helper, "writ:example");
        int before = v.getOffers().size();
        verify(helper, v, p, interact(p, v), before + 1, 8, 2, 1);
        verify(helper, v, p, interact(p, v), before + 1, 8, 2, 1);
        var total = villager(helper); counters(total, 7, 3);
        var totalPlayer = player(helper, "writ:example");
        int totalBefore = total.getOffers().size();
        verify(helper, total, totalPlayer, interact(totalPlayer, total), totalBefore, 7, 3, 2);
        var category = villager(helper); counters(category, 8, 0);
        var other = player(helper, "writ:example");
        int categoryBefore = category.getOffers().size();
        verify(helper, category, other, interact(other, category), categoryBefore, 8, 0, 2);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void creativeDoesNotConsume(GameTestHelper helper) {
        var v = villager(helper);
        var p = player(helper, "writ:example"); p.getAbilities().instabuild = true;
        int before = v.getOffers().size();
        verify(helper, v, p, interact(p, v), before + 1, 1, 0, 2);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void busyVillagerIsRejected(GameTestHelper helper) {
        var v = villager(helper);
        var p = player(helper, "writ:example");
        v.setTradingPlayer(helper.makeMockSurvivalPlayer());
        helper.assertTrue(v.isTrading(), "fixture must be trading");
        int before = v.getOffers().size();
        verify(helper, v, p, interact(p, v), before, 0, 0, 2);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void repeatedContractsRespectPerVillagerLimits(GameTestHelper helper) {
        var first = villager(helper); var second = villager(helper);
        var p = player(helper, "writ:example"); p.getMainHandItem().setCount(20);
        int a = first.getOffers().size(), b = second.getOffers().size();
        for (int i = 0; i < 8; i++) verify(helper, first, p, interact(p, first), a + i + 1, i + 1, 0, 19 - i);
        verify(helper, first, p, interact(p, first), a + 8, 8, 0, 12);
        verify(helper, second, p, interact(p, second), b + 1, 1, 0, 11);
        helper.assertTrue(first.getPersistentData().getCompound(Writ.ID).getInt("normalAppliedCount") == 8, "other villager affected first counter");
        var c = villager(helper); var chaosPlayer = helper.makeMockSurvivalPlayer();
        chaosPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Writ.CHAOS.get(), 5));
        int before = c.getOffers().size();
        for (int i = 0; i < 3; i++) verify(helper, c, chaosPlayer, interact(chaosPlayer, c), before + i + 1, 0, i + 1, 4 - i);
        verify(helper, c, chaosPlayer, interact(chaosPlayer, c), before + 3, 0, 3, 2);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void presetFailuresAndOtherEntities(GameTestHelper helper) {
        var v = villager(helper); var p = player(helper, "writ:example");
        var stack = p.getMainHandItem();
        stack.setTag(null);
        helper.assertTrue("preset_missing".equals(Presets.INSTANCE.resolveOffer(stack, v).failure()), "missing preset");
        verify(helper, v, p, interact(p, v), v.getOffers().size(), 0, 0, 2);
        stack.getOrCreateTag().putString("preset_id", "writ:test_invalid");
        helper.assertTrue("preset_invalid".equals(Presets.INSTANCE.resolveOffer(stack, v).failure()), "invalid preset");
        verify(helper, v, p, interact(p, v), v.getOffers().size(), 0, 0, 2);
        stack.getOrCreateTag().putString("preset_id", "writ:example"); stack.getOrCreateTag().putInt("contract_version", 2);
        helper.assertTrue("version_unsupported".equals(Presets.INSTANCE.resolveOffer(stack, v).failure()), "unsupported version");
        verify(helper, v, p, interact(p, v), v.getOffers().size(), 0, 0, 2);
        stack.getOrCreateTag().remove("contract_version"); stack.getOrCreateTag().putString("preset_id", "writ:gametest_librarian");
        helper.assertTrue("profession_mismatch".equals(Presets.INSTANCE.resolveOffer(stack, v).failure()), "profession mismatch");
        verify(helper, v, p, interact(p, v), v.getOffers().size(), 0, 0, 2);
        var merchant = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(1, 1, 1));
        var other = helper.spawn(EntityType.COW, new BlockPos(2, 1, 1));
        var customMerchant = new OtherMerchant(helper.getLevel());
        helper.assertTrue(Writ.isOtherMerchant(customMerchant), "non-AbstractVillager merchant not recognized");
        helper.assertFalse(Writ.isOtherMerchant(v), "ordinary villager classified as other merchant");
        helper.assertFalse(Writ.isOtherMerchant(other), "non-merchant classified as merchant");
        helper.assertFalse(MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, customMerchant)), "custom merchant interaction canceled");
        helper.assertFalse(MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, merchant)), "merchant interaction canceled");
        helper.assertFalse(MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, other)), "other interaction canceled");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 1000, batch = "isolated_presetOverrideReloadKeepsOldOffer")
    public static void presetOverrideReloadKeepsOldOffer(GameTestHelper helper) {
        helper.runAfterDelay(260, () -> {
            var server = helper.getLevel().getServer();
            var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("datapacks/baseline-tests/data/writ/writs/presets/example.json");
            boolean[] created = {false};
            try {
                if (java.nio.file.Files.exists(path)) throw new IllegalStateException("override fixture already exists");
                var v = villager(helper); var p = player(helper, "writ:example");
                int before = v.getOffers().size();
                verify(helper, v, p, interact(p, v), before + 1, 1, 0, 1);
                var oldOffer = v.getOffers().get(before);
                helper.assertTrue(oldOffer.getBaseCostA().getCount() == 4, "initial preset wrong");
                var original = server.getResourceManager().getResourceOrThrow(new net.minecraft.resources.ResourceLocation("writ", "writs/presets/example.json"));
                com.google.gson.JsonObject override;
                try (var reader = original.openAsReader()) { override = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject(); }
                override.getAsJsonObject("trade").getAsJsonObject("cost_a").addProperty("count", 2);
                java.nio.file.Files.writeString(path, override.toString(), java.nio.file.StandardOpenOption.CREATE_NEW);
                created[0] = true;
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                    try {
                        if (failure != null) throw new IllegalStateException(failure);
                        verify(helper, v, p, interact(p, v), before + 2, 2, 0, 0);
                        helper.assertTrue(oldOffer.getBaseCostA().getCount() == 4, "old offer was rewritten");
                        helper.assertTrue(v.getOffers().get(before + 1).getBaseCostA().getCount() == 2, "new preset not applied");
                        TestRestore.finish(helper, () -> java.nio.file.Files.deleteIfExists(path), () -> {
                            var stack = new ItemStack(Writ.NORMAL.get()); stack.getOrCreateTag().putString("preset_id", "writ:example");
                            helper.assertTrue(Presets.INSTANCE.offer(stack, v).getBaseCostA().getCount() == 4, "preset restore failed");
                        }, null);
                    } catch (Throwable e) {
                        TestRestore.finish(helper, () -> java.nio.file.Files.deleteIfExists(path), () -> {
                            var stack = new ItemStack(Writ.NORMAL.get()); stack.getOrCreateTag().putString("preset_id", "writ:example");
                            helper.assertTrue(Presets.INSTANCE.offer(stack, v).getBaseCostA().getCount() == 4, "preset restore failed");
                        }, e);
                    }
                }));
            } catch (Throwable e) {
                if (!created[0]) { helper.fail("preset reload setup: " + e); return; }
                TestRestore.finish(helper, () -> java.nio.file.Files.deleteIfExists(path), () -> {
                    var v = villager(helper); var stack = new ItemStack(Writ.NORMAL.get()); stack.getOrCreateTag().putString("preset_id", "writ:example");
                    helper.assertTrue(Presets.INSTANCE.offer(stack, v).getBaseCostA().getCount() == 4, "preset restore failed");
                }, e);
            }
        });
    }
    @GameTest(template = "empty")
    public static void chaosContractAppendsOnce(GameTestHelper helper) {
        var v = villager(helper);
        var p = helper.makeMockSurvivalPlayer();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Writ.CHAOS.get()));
        int before = v.getOffers().size();
        verify(helper, v, p, interact(p, v), before + 1, 0, 1, 0);
        var offer = v.getOffers().get(before);
        String payment = ForgeRegistries.ITEMS.getKey(offer.getBaseCostA().getItem()).toString();
        String result = ForgeRegistries.ITEMS.getKey(offer.getResult().getItem()).toString();
        helper.assertFalse(ChaosSampler.HARD_DENY.contains(payment), "payment was hard denied");
        helper.assertFalse(ChaosSampler.HARD_DENY.contains(result), "result was hard denied");
        helper.assertTrue(payment.equals("minecraft:emerald"), "controlled payment pool not applied: " + payment);
        helper.assertTrue(offer.getResult().is(net.minecraft.tags.ItemTags.PLANKS) && !result.equals("minecraft:oak_planks"), "controlled result tag/exclusion not applied: " + result);
        helper.assertTrue(offer.getMaxUses() == 12 && offer.getXp() == 0 && offer.getPriceMultiplier() == 0.05f, "incorrect chaos parameters");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_emptyChaosAllowRejectsWithoutConsumption")
    public static void emptyChaosAllowRejectsWithoutConsumption(GameTestHelper helper) {
        helper.runAfterDelay(120, () -> {
            var server = helper.getLevel().getServer();
            var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("datapacks/baseline-tests/data/writ/writs/chaos/config.json");
            String[] saved = {null};
            try {
                String original = java.nio.file.Files.readString(path);
                saved[0] = original;
                java.nio.file.Files.writeString(path, "{\"payment_allow\":[],\"result_allow\":[\"minecraft:iron_ingot\"]}");
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                    try {
                        if (failure != null) throw new IllegalStateException(failure);
                        var v = villager(helper); var p = helper.makeMockSurvivalPlayer();
                        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Writ.CHAOS.get(), 2));
                        int before = v.getOffers().size();
                        verify(helper, v, p, interact(p, v), before, 0, 0, 2);
                        TestRestore.finish(helper, () -> java.nio.file.Files.writeString(path, original), () ->
                            helper.assertTrue(Chaos.offer(helper.getLevel().getRandom()) != null, "restored pools must work"), null);
                    } catch (Throwable e) {
                        TestRestore.finish(helper, () -> java.nio.file.Files.writeString(path, original), () ->
                            helper.assertTrue(Chaos.offer(helper.getLevel().getRandom()) != null, "restored pools must work"), e);
                    }
                }));
            } catch (Throwable e) {
                if (saved[0] == null) { helper.fail("empty pool setup: " + e); return; }
                TestRestore.finish(helper, () -> java.nio.file.Files.writeString(path, saved[0]), () ->
                    helper.assertTrue(Chaos.offer(helper.getLevel().getRandom()) != null, "restored pools must work"), e);
            }
        });
    }
}
