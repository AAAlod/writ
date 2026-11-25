package ke.writ;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class ResonanceIntegrationGameTests {
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_resonanceChaosPolicy")
    public static void resonanceDeviceChaosPolicyCanBeOverridden(GameTestHelper h) throws IOException {
        var server = h.getLevel().getServer();
        var packIds = server.getPackRepository().getSelectedIds();
        var path = server.getWorldPath(LevelResource.ROOT)
                .resolve("datapacks/baseline-tests/data/writ/writs/chaos/config.json");
        byte[] original = Files.readAllBytes(path);
        Files.delete(path);
        var check = server.reloadResources(packIds).thenComposeAsync(ignored -> {
            String device = "writ:resonance_device";
            var sampler = Presets.INSTANCE.currentSnapshot().chaos();
            h.assertTrue(sampler.size() > 0 && sampler.resultSize() > 0, "default chaos pools are empty");
            for (int i = 0; i < Math.max(sampler.size(), sampler.resultSize()); i++) {
                int index = i;
                var pair = sampler.sample(size -> index % size).orElseThrow();
                h.assertFalse(device.equals(pair.payment()) || device.equals(pair.result()),
                        "the integrated defaults must exclude the device from both chaos pools");
            }
            try {
                Files.writeString(path, """
                        {"payment_allow":["writ:resonance_device"],"result_allow":["writ:resonance_device"]}
                        """);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
            return server.reloadResources(packIds);
        }, server).thenRunAsync(() -> {
            var sampler = Presets.INSTANCE.currentSnapshot().chaos();
            var pair = sampler.sample(size -> 0).orElseThrow();
            h.assertTrue(sampler.size() == 1 && sampler.resultSize() == 1
                            && "writ:resonance_device".equals(pair.payment()) && pair.payment().equals(pair.result()),
                    "a higher-priority datapack must be able to allow the device on both sides");
        }, server);
        var finished = check.handleAsync((ignored, failure) -> {
            try {
                Files.write(path, original);
            } catch (IOException error) {
                if (failure != null) error.addSuppressed(failure);
                throw new UncheckedIOException(error);
            }
            return server.reloadResources(packIds).whenComplete((restored, restoreFailure) -> {
                if (failure != null) {
                    if (restoreFailure != null) restoreFailure.addSuppressed(failure);
                    else throw new CompletionException(failure);
                }
            });
        }, server).thenCompose(reload -> reload);
        h.succeedWhen(() -> {
            h.assertTrue(finished.isDone(), "waiting for the chaos policy and fixture restore to finish");
            try {
                finished.join();
            } catch (CompletionException error) {
                h.fail("chaos policy or fixture restore failed: " + error.getCause());
            }
        });
    }

    private static ServerPlayer player(GameTestHelper h, String name) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    @GameTest(template = "empty", timeoutTicks = ResonanceHostConfig.MAX_COOLDOWN_TICKS + 20)
    public static void emptyResonanceDeviceInvokesWritDefault(GameTestHelper h) {
        var player = player(h, "device-owner");
        var target = h.spawn(EntityType.CHICKEN, new BlockPos(1, 2, 1));
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("writ:resonance_device"));
        h.assertTrue(item != null, "host did not register the imported device");
        var stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var bind = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target);
        MinecraftForge.EVENT_BUS.post(bind);
        h.assertTrue(bind.isCanceled() && stack.getOrCreateTag().getUUID("owner").equals(player.getUUID())
                && !ContractExemption.isActive(target), "the first click should only bind");
        h.runAfterDelay(1, () -> {
            MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target));
            h.assertTrue(ContractExemption.isActive(target) && !stack.getTag().contains("effects"),
                    "the empty device did not invoke the host default");
            int cooldown = ResonanceHostConfig.cooldownTicks();
            player.setShiftKeyDown(true);
            if (cooldown > 1) {
                h.runAfterDelay(cooldown - 1, () -> {
                    MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target));
                    h.assertTrue(ContractExemption.isActive(target),
                            "the host-configured cooldown should still block removal one tick before expiry");
                });
            }
            h.runAfterDelay(Math.max(1, cooldown), () -> {
                MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target));
                h.assertTrue(!ContractExemption.isActive(target) && stack.getCount() == 1,
                        "sneak-use should remove the effect when the host cooldown ends, without consuming the device");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void rejectedExemptionDoesNotStartDeviceCooldown(GameTestHelper h) {
        var player = player(h, "device-rejected");
        var target = h.spawn(EntityType.CHICKEN, new BlockPos(1, 2, 1));
        var item = ForgeRegistries.ITEMS.getValue(WritIntegratedMod.RESONANCE_DEVICE);
        h.assertTrue(item != null, "host did not register the imported device");
        var stack = new ItemStack(item);
        stack.getOrCreateTag().putUUID("owner", player.getUUID());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(true);
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target));
        h.assertFalse(player.getCooldowns().isOnCooldown(item),
                "trying to remove a missing exemption must not start the item cooldown");
        h.runAfterDelay(1, () -> {
            player.setShiftKeyDown(false);
            MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target));
            h.assertTrue(ContractExemption.isActive(target), "an unsuccessful removal blocked the next application");
            h.assertTrue(player.getCooldowns().isOnCooldown(item) == (ResonanceHostConfig.cooldownTicks() > 0),
                    "a successful application must respect the host cooldown setting");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void resonanceDeviceAppearsInWritCreativeTab(GameTestHelper h) {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(WritIntegratedMod.WRIT_TAB);
        var item = ForgeRegistries.ITEMS.getValue(WritIntegratedMod.RESONANCE_DEVICE);
        h.assertTrue(tab != null && item != null, "the Writ tab and resonance device must be registered");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
        h.assertTrue(tab.contains(new ItemStack(item)), "the device is missing from the Writ creative tab");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitResistanceDoesNotApplyHostDefault(GameTestHelper h)
            throws CommandSyntaxException {
        var owner = player(h, "device-resistance");
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("writ:resonance_device"));
        h.assertTrue(item != null, "host did not register the imported device");
        var stack = new ItemStack(item);
        stack.setTag(TagParser.parseTag("""
                {effects:[{id:"minecraft:resistance",duration_ticks:600,amplifier:3}]}
                """));
        stack.getOrCreateTag().putUUID("owner", owner.getUUID());
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);

        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, target));

        var resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
        h.assertTrue(resistance != null && resistance.getAmplifier() == 3 && resistance.getDuration() == 600,
                "explicit device NBT did not apply 600 ticks of Resistance IV");
        h.assertTrue(!ContractExemption.isActive(target), "the explicit effect also applied the host default");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitListCannotInvokeWritDefault(GameTestHelper h) throws CommandSyntaxException {
        var owner = player(h, "device-explicit-default");
        var target = h.spawn(EntityType.CHICKEN, new BlockPos(1, 2, 1));
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("writ:resonance_device"));
        h.assertTrue(item != null, "host did not register the imported device");
        var stack = new ItemStack(item);
        stack.setTag(TagParser.parseTag("""
                {effects:[{id:"minecraft:resistance"},{id:"writ:contract_exemption"}]}
                """));
        stack.getOrCreateTag().putUUID("owner", owner.getUUID());
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);

        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, target));
        h.assertTrue(!ContractExemption.isActive(target) && !target.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                "an invalid explicit list executed a status effect or the host default");

        h.runAfterDelay(1, () -> {
            stack.getOrCreateTag().put("effects", new ListTag());
            MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, target));
            h.assertTrue(ContractExemption.isActive(target) && !target.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                    "an empty effect list did not invoke only the host default");
            h.succeed();
        });
    }
}
