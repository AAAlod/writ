package ke.writ.resonance.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import ke.writ.resonance.EffectResult;
import ke.writ.resonance.Operation;
import ke.writ.resonance.ResonanceEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-side device interaction logic: binding, ownership enforcement, cooldowns and effect dispatch.
 *
 * <p>Entity interactions arrive through Forge's normal {@link PlayerInteractEvent.EntityInteract}
 * path. The module listener defaults to {@code receiveCanceled == false}, so interactions already
 * cancelled by other mods are never processed, and it re-checks {@link PlayerInteractEvent#isCanceled()}
 * defensively. Only the server-side acting player is ever used; no state is written before the
 * interaction reaches this listener uncancelled.</p>
 */
public final class DeviceInteraction {

    private static final Logger LOGGER = LogManager.getLogger("writ-resonance");

    /** Above this many entries the maps are pruned; it is not a capacity limit. */
    private static final int PRUNE_THRESHOLD = 512;

    /** Remembers the last processed interaction so one physical click can never run twice. */
    private static final Map<UUID, LastInteraction> LAST_INTERACTIONS = new ConcurrentHashMap<>();

    /**
     * Last effective effect dispatch per player, in level game time. The cooldown is per player so
     * swapping to another focus cannot bypass it. The module-wide cooldown value is also what the
     * vanilla item cooldown overlay displays, so every focus shows the same, consistent duration.
     */
    private static final Map<UUID, Long> LAST_EFFECT_USE = new ConcurrentHashMap<>();

    // These ticks are meaningful only within the current server session.
    static void clearSession() {
        LAST_INTERACTIONS.clear();
        LAST_EFFECT_USE.clear();
    }

    /** Relative right-click on air: binds the device when unbound, otherwise does nothing. */
    public static InteractionResult useOnAir(Level level, Player player, InteractionHand hand, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (serverPlayer.isSpectator() || !ResonanceItems.isDevice(stack)) {
            return InteractionResult.PASS;
        }
        DeviceData.OwnerState owner = DeviceData.getOwnerState(stack);
        if (owner instanceof DeviceData.OwnerState.Invalid) {
            send(serverPlayer, Component.translatable(MessageKeys.OWNER_INVALID));
            return InteractionResult.FAIL;
        }
        if (owner instanceof DeviceData.OwnerState.Unbound) {
            bind(serverPlayer, stack);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * Forge entity interaction hook. Only the server-side acting player is used; no identity is
     * ever taken from client-supplied data.
     *
     * @param event the Forge event
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!ResonanceItems.isDevice(stack)) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        if (isDuplicateInteraction(player, target)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        Operation operation = player.isShiftKeyDown() ? Operation.REMOVE : Operation.APPLY;
        handleEntityInteraction(player, target, stack, operation);

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static void handleEntityInteraction(ServerPlayer player, LivingEntity target, ItemStack stack, Operation operation) {
        DeviceData.OwnerState owner = DeviceData.getOwnerState(stack);
        if (owner instanceof DeviceData.OwnerState.Invalid) {
            // Keep the corrupted tag exactly as it is: never let a damaged device become rebindable.
            send(player, Component.translatable(MessageKeys.OWNER_INVALID));
            return;
        }
        if (owner instanceof DeviceData.OwnerState.Unbound) {
            // First legal right-click only binds; the effect is not executed until the next one.
            bind(player, stack);
            return;
        }
        if (!((DeviceData.OwnerState.Bound) owner).owner().equals(player.getUUID())) {
            send(player, Component.translatable(MessageKeys.UNAUTHORIZED));
            return;
        }

        int cooldownTicks = ModuleRuntime.get().defaultCooldownTicks();
        long gameTime = player.level().getGameTime();
        if (cooldownTicks > 0) {
            long remaining = remainingCooldownTicks(player.getUUID(), gameTime, cooldownTicks);
            if (remaining > 0) {
                send(player, Component.translatable(MessageKeys.COOLDOWN,
                        EffectFormat.secondsText((int) remaining)));
                return;
            }
        }

        boolean effective = runEffects(player, target, stack, operation);
        if (effective && cooldownTicks > 0) {
            recordSuccessfulUse(player.getUUID(), gameTime);
            player.getCooldowns().addCooldown(stack.getItem(), cooldownTicks);
        }
    }

    static void bind(ServerPlayer player, ItemStack stack) {
        UUID playerId = player.getUUID();
        DeviceData.setOwner(stack, playerId);
        send(player, Component.translatable(MessageKeys.BOUND, Frequency.of(playerId)));
    }

    /**
     * Validates and executes the item's configured effects.
     *
     * <p>An explicit list is fully validated (field types, ids and effect resolution) before any
     * effect runs; an empty/absent list resolves the host default effect. Effects execute in list
     * order. A target rejecting one effect does not roll back the others.</p>
     *
     * @return {@code true} only when at least one explicit effect was applied or removed, or when
     *         the host default handler reported success; {@code false} for a configuration error,
     *         a missing host default, an all-failed dispatch or a rejected/exceptional host action.
     *         The caller starts the cooldown only for {@code true}.
     */
    static boolean runEffects(ServerPlayer player, LivingEntity target, ItemStack stack, Operation operation) {
        ParsedEffects parsed = EffectsParser.parse(DeviceData.getTagOrNull(stack));
        if (parsed instanceof ParsedEffects.Invalid invalid) {
            send(player, Component.translatable(MessageKeys.CONFIG_ERROR, invalid.detail()));
            return false;
        }
        if (parsed instanceof ParsedEffects.Default) {
            return runDefaultEffect(player, target, operation);
        }

        List<EffectSpec> specs = ((ParsedEffects.Explicit) parsed).effects();
        Map<ResourceLocation, MobEffect> effects = new HashMap<>();
        for (EffectSpec spec : specs) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(spec.id());
            if (effect == null) {
                send(player, Component.translatable(MessageKeys.CONFIG_ERROR,
                        "unregistered effect id: " + spec.id()));
                return false;
            }
            effects.put(spec.id(), effect);
        }

        boolean changed = false;
        for (EffectSpec spec : specs) {
            MobEffect effect = effects.get(spec.id());
            try {
                if (operation == Operation.APPLY) {
                    int duration = effect.isInstantenous()
                            ? 1 : spec.durationTicks().orElse(MobEffectInstance.INFINITE_DURATION);
                    changed |= target.addEffect(new MobEffectInstance(effect, duration, spec.amplifier()), player);
                } else {
                    changed |= target.removeEffect(effect);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Resonance effect {} failed", spec.id(), exception);
            }
        }
        if (!changed) {
            send(player, Component.translatable(MessageKeys.EFFECT_UNAVAILABLE));
            return false;
        }
        return true;
    }

    /**
     * Runs the host's optional default action.
     *
     * <p>The handler's {@link EffectResult#success()} flag is authoritative: a message alone does
     * not turn a rejected action into success. A rejected action is reported to the player (its own
     * message when present, the generic unavailable feedback otherwise) and never starts a
     * cooldown. A successful action starts the cooldown even when it carries no message.</p>
     */
    private static boolean runDefaultEffect(ServerPlayer player, LivingEntity target, Operation operation) {
        ResonanceEffect handler = ModuleRuntime.get().defaultEffect();
        if (handler == null) {
            send(player, Component.translatable(MessageKeys.NO_DEFAULT_EFFECT));
            return false;
        }
        EffectResult result;
        try {
            result = handler.execute(player, target, operation);
        } catch (RuntimeException exception) {
            LOGGER.error("Default resonance action failed", exception);
            send(player, Component.translatable(MessageKeys.EFFECT_UNAVAILABLE));
            return false;
        }
        if (result == null) {
            send(player, Component.translatable(MessageKeys.EFFECT_UNAVAILABLE));
            return false;
        }
        if (result.message() != null) {
            send(player, result.message());
        }
        if (result.success()) {
            return true;
        }
        if (result.message() == null) {
            send(player, Component.translatable(MessageKeys.EFFECT_UNAVAILABLE));
        }
        return false;
    }

    private static boolean isDuplicateInteraction(ServerPlayer player, LivingEntity target) {
        long gameTime = player.level().getGameTime();
        LastInteraction previous = LAST_INTERACTIONS.put(player.getUUID(),
                new LastInteraction(target.getId(), gameTime));
        pruneInteractionState(gameTime);
        return previous != null
                && previous.targetId() == target.getId()
                && previous.gameTime() == gameTime;
    }

    /** Remaining cooldown ticks for one player; {@code 0} means the player may act again. */
    static long remainingCooldownTicks(UUID playerId, long gameTime, int cooldownTicks) {
        Long lastUse = LAST_EFFECT_USE.get(playerId);
        if (lastUse == null || cooldownTicks <= 0) {
            return 0L;
        }
        long elapsed = gameTime - lastUse;
        return elapsed >= cooldownTicks ? 0L : cooldownTicks - elapsed;
    }

    /** Records one effective use and prunes the map only if it has grown past the threshold. */
    static void recordSuccessfulUse(UUID playerId, long gameTime) {
        LAST_EFFECT_USE.put(playerId, gameTime);
        pruneCooldownState(gameTime);
    }

    /**
     * Drops cooldown records whose actual cooldown has expired under the current module value.
     *
     * <p>Expiry is computed from the configured cooldown, never from a fixed wall-clock window, so
     * a still-cooling player can never be released early (for example with a 12000-tick value).
     * Records of players whose cooldown is still running are deliberately kept beyond the
     * threshold: the threshold only decides when to look, not what may be forgotten.</p>
     */
    static void pruneCooldownState(long gameTime) {
        if (LAST_EFFECT_USE.size() <= PRUNE_THRESHOLD) {
            return;
        }
        int cooldownTicks = ModuleRuntime.get().defaultCooldownTicks();
        LAST_EFFECT_USE.entrySet().removeIf(entry -> isCooldownExpired(entry.getValue(), gameTime, cooldownTicks));
    }

    /** {@code true} when a use at {@code lastUseGameTime} may be forgotten at {@code gameTime}. */
    static boolean isCooldownExpired(long lastUseGameTime, long gameTime, int cooldownTicks) {
        return gameTime - lastUseGameTime >= Math.max(0, cooldownTicks);
    }

    /**
     * Interaction history is only meaningful for the same game tick, so entries from earlier ticks
     * can be dropped once the map grows past the threshold. Logging out forgets the player's entry
     * as well; cooldown records are intentionally kept for the rest of the server session.
     */
    static void pruneInteractionState(long gameTime) {
        if (LAST_INTERACTIONS.size() <= PRUNE_THRESHOLD) {
            return;
        }
        LAST_INTERACTIONS.entrySet().removeIf(entry -> entry.getValue().gameTime() < gameTime);
    }

    /** Forgets the one-tick duplicate guard for a player (called when the player logs out). */
    static void forgetInteractionHistory(UUID playerId) {
        LAST_INTERACTIONS.remove(playerId);
    }

    static int cooldownRecordCount() {
        return LAST_EFFECT_USE.size();
    }

    static boolean hasCooldownRecord(UUID playerId) {
        return LAST_EFFECT_USE.containsKey(playerId);
    }

    static int interactionRecordCount() {
        return LAST_INTERACTIONS.size();
    }

    static void recordInteractionForTest(UUID playerId, int targetId, long gameTime) {
        LAST_INTERACTIONS.put(playerId, new LastInteraction(targetId, gameTime));
        pruneInteractionState(gameTime);
    }

    private static void send(ServerPlayer player, Component message) {
        if (message != null) {
            player.displayClientMessage(message, false);
        }
    }

    private record LastInteraction(int targetId, long gameTime) {
    }
}
