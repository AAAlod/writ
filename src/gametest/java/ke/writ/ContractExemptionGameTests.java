package ke.writ;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static ke.writ.ContractExemption.Result.*;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class ContractExemptionGameTests {
    private static ServerPlayer player(GameTestHelper h, String name) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    @GameTest(template = "empty")
    public static void livingTargetOwnershipSurvivesEntitySave(GameTestHelper h) {
        var owner = player(h, "exemption-owner");
        var other = player(h, "exemption-other");
        var chicken = h.spawn(EntityType.CHICKEN, new BlockPos(1, 2, 1));
        h.assertTrue(ContractExemption.apply(owner, chicken, OptionalInt.empty()) == APPLIED,
                "ordinary non-villager could not receive protection");
        h.assertTrue(!chicken.isInvulnerable() && chicken.getActiveEffects().isEmpty(),
                "exemption changed vanilla protection or potion effects");

        var restored = EntityType.CHICKEN.create(h.getLevel());
        restored.load(chicken.saveWithoutId(new CompoundTag()));
        h.assertTrue(ContractExemption.isActive(restored), "permanent protection did not survive entity loading");
        h.assertTrue(ContractExemption.apply(other, restored, OptionalInt.of(1)) == ALREADY_PROTECTED,
                "another player could overwrite protection");
        h.assertTrue(ContractExemption.remove(other, restored) == NOT_OWNER && ContractExemption.isActive(restored),
                "another player could remove protection");
        h.assertTrue(ContractExemption.remove(owner, restored) == REMOVED && !ContractExemption.isActive(restored),
                "original owner could not remove restored protection");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void expiresWithoutRefreshingOnRepeatApply(GameTestHelper h) {
        var owner = player(h, "exemption-owner");
        var other = player(h, "exemption-other");
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        target.setBaby(true);
        h.assertTrue(ContractExemption.apply(owner, target, OptionalInt.of(0)) == INVALID_DURATION,
                "non-positive duration accepted");
        h.assertTrue(ContractExemption.apply(owner, target, OptionalInt.of(3)) == APPLIED,
                "timed protection could not be applied to a baby");
        target.setBaby(false);
        h.runAfterDelay(2, () -> {
            h.assertTrue(ContractExemption.isActive(target), "protection ended early or was lost on aging");
            h.assertTrue(ContractExemption.apply(owner, target, OptionalInt.empty()) == ALREADY_PROTECTED,
                    "repeat application refreshed protection");
        });
        h.runAfterDelay(3, () -> {
            h.assertTrue(!ContractExemption.isActive(target), "protection is active at its deadline");
            h.assertTrue(ContractExemption.apply(other, target, OptionalInt.empty()) == APPLIED,
                    "expired protection prevented a new owner from applying");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
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
            h.runAfterDelay(1, () -> {
                player.setShiftKeyDown(true);
                MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target));
                h.assertTrue(!ContractExemption.isActive(target) && stack.getCount() == 1,
                        "the owner's sneak-use should remove the effect without consuming the device");
                h.succeed();
            });
        });
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

    @GameTest(template = "empty")
    public static void onlyAdminCommandCanForceRemove(GameTestHelper h) {
        var owner = player(h, "exemption-owner");
        var target = h.spawn(EntityType.CHICKEN, new BlockPos(1, 2, 1));
        h.assertTrue(ContractExemption.apply(owner, target, OptionalInt.empty()) == APPLIED,
                "could not prepare protected entity");
        var server = h.getLevel().getServer();
        var commands = server.getCommands();
        var tester = server.createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput();
        String clear = "writ exemption clear " + target.getStringUUID();
        h.assertTrue(commands.performPrefixedCommand(tester.withPermission(1), clear) == 0
                && ContractExemption.isActive(target), "non-admin cleared another player's protection");
        h.assertTrue(commands.performPrefixedCommand(tester.withPermission(2), clear) == 1
                && !ContractExemption.isActive(target), "admin could not clear another player's protection");
        h.succeed();
    }
}
