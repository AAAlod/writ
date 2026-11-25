package ke.writ;

import com.mojang.authlib.GameProfile;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
