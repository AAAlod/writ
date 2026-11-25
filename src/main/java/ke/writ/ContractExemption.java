package ke.writ;

import java.util.Locale;
import java.util.OptionalInt;
import ke.writ.resonance.EffectResult;
import ke.writ.resonance.Operation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;

final class ContractExemption {
    static final String ID = Writ.ID + ":contract_exemption";
    private static final String OWNER = "owner";
    private static final String EXPIRES_AT = "expires_at";

    enum Result {
        APPLIED, REMOVED, ALREADY_PROTECTED, NOT_OWNER, NOT_PROTECTED, INVALID_DURATION, INVALID_TARGET
    }

    private ContractExemption() {}

    static EffectResult useDefaultDeviceEffect(ServerPlayer player, LivingEntity target, Operation operation) {
        Result result = switch (operation) {
            case APPLY -> apply(player, target, OptionalInt.empty());
            case REMOVE -> remove(player, target);
        };
        String key = "writ.effect.contract_exemption." + result.name().toLowerCase(Locale.ROOT);
        return new EffectResult(result == Result.APPLIED || result == Result.REMOVED, Component.translatable(key));
    }

    static boolean isActive(LivingEntity target) {
        CompoundTag protection = target.getPersistentData().getCompound(ID);
        return protection.hasUUID(OWNER) && (!protection.contains(EXPIRES_AT)
                || (protection.contains(EXPIRES_AT, Tag.TAG_LONG)
                && protection.getLong(EXPIRES_AT) > target.level().getGameTime()));
    }

    static Result apply(ServerPlayer player, LivingEntity target, OptionalInt durationTicks) {
        if (!canUse(player, target)) return Result.INVALID_TARGET;
        if (durationTicks.isPresent() && durationTicks.getAsInt() <= 0) return Result.INVALID_DURATION;
        if (isActive(target)) return Result.ALREADY_PROTECTED;

        CompoundTag protection = new CompoundTag();
        protection.putUUID(OWNER, player.getUUID());
        if (durationTicks.isPresent()) {
            protection.putLong(EXPIRES_AT, target.level().getGameTime() + durationTicks.getAsInt());
        }
        target.getPersistentData().put(ID, protection);
        return Result.APPLIED;
    }

    static Result remove(ServerPlayer player, LivingEntity target) {
        if (!canUse(player, target)) return Result.INVALID_TARGET;
        if (!isActive(target)) return Result.NOT_PROTECTED;
        if (!target.getPersistentData().getCompound(ID).getUUID(OWNER).equals(player.getUUID())) {
            return Result.NOT_OWNER;
        }
        clear(target);
        return Result.REMOVED;
    }

    private static boolean canUse(ServerPlayer player, LivingEntity target) {
        return !player.isSpectator() && player.isAlive() && target.isAlive()
                && player.serverLevel() == target.level();
    }

    private static boolean clear(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        if (!data.contains(ID)) return false;
        data.remove(ID);
        return true;
    }

    static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Writ.ID)
                .then(Commands.literal("exemption").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> clearFromCommand(context.getSource(),
                                                EntityArgument.getEntity(context, "target")))))));
    }

    private static int clearFromCommand(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof LivingEntity target)) {
            source.sendFailure(Component.translatable("writ.command.exemption.invalid_target"));
            return 0;
        }
        if (!clear(target)) {
            source.sendFailure(Component.translatable("writ.command.exemption.not_found"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("writ.command.exemption.cleared", target.getDisplayName()), true);
        return 1;
    }
}
