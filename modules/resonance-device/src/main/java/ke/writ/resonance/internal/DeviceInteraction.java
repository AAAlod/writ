package ke.writ.resonance.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Server-side device interaction logic: binding, ownership enforcement and effect dispatch.
 *
 * <p>Entity interactions arrive through Forge's normal {@link PlayerInteractEvent.EntityInteract}
 * path. The module listener defaults to {@code receiveCanceled == false}, so interactions already
 * cancelled by other mods are never processed, and it re-checks {@link PlayerInteractEvent#isCanceled()}
 * defensively.</p>
 */
public final class DeviceInteraction {

    private static final Logger LOGGER = LogManager.getLogger("writ-resonance");

    /** Remembers the last processed interaction so one physical click can never run twice. */
    private static final Map<UUID, LastInteraction> LAST_INTERACTIONS = new ConcurrentHashMap<>();

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
        if (DeviceData.getOwner(stack).isEmpty()) {
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
        Optional<UUID> owner = DeviceData.getOwner(stack);
        if (owner.isEmpty()) {
            // First legal right-click only binds; the effect is not executed until the next one.
            bind(player, stack);
            return;
        }
        if (!owner.get().equals(player.getUUID())) {
            send(player, Component.translatable(MessageKeys.UNAUTHORIZED));
            return;
        }
        runEffects(player, target, stack, operation);
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
     */
    static void runEffects(ServerPlayer player, LivingEntity target, ItemStack stack, Operation operation) {
        ParsedEffects parsed = EffectsParser.parse(DeviceData.getTagOrNull(stack));
        if (parsed instanceof ParsedEffects.Invalid invalid) {
            send(player, Component.translatable(MessageKeys.CONFIG_ERROR, invalid.detail()));
            return;
        }
        if (parsed instanceof ParsedEffects.Default) {
            runDefaultEffect(player, target, operation);
            return;
        }

        List<EffectSpec> specs = ((ParsedEffects.Explicit) parsed).effects();
        Map<ResourceLocation, MobEffect> effects = new HashMap<>();
        for (EffectSpec spec : specs) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(spec.id());
            if (effect == null) {
                send(player, Component.translatable(MessageKeys.CONFIG_ERROR,
                        "unregistered effect id: " + spec.id()));
                return;
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
        }
    }

    private static void runDefaultEffect(ServerPlayer player, LivingEntity target, Operation operation) {
        ResonanceEffect handler = ModuleRuntime.get().defaultEffect();
        if (handler == null) {
            send(player, Component.translatable(MessageKeys.NO_DEFAULT_EFFECT));
            return;
        }
        try {
            EffectResult result = handler.execute(player, target, operation);
            if (result != null && result.message() != null) {
                send(player, result.message());
                return;
            }
            if (result != null && result.success()) {
                return;
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Default resonance action failed", exception);
        }
        send(player, Component.translatable(MessageKeys.EFFECT_UNAVAILABLE));
    }

    private static boolean isDuplicateInteraction(ServerPlayer player, LivingEntity target) {
        long gameTime = player.level().getGameTime();
        LastInteraction previous = LAST_INTERACTIONS.put(player.getUUID(),
                new LastInteraction(target.getId(), gameTime));
        return previous != null
                && previous.targetId() == target.getId()
                && previous.gameTime() == gameTime;
    }

    private static void send(ServerPlayer player, Component message) {
        if (message != null) {
            player.displayClientMessage(message, false);
        }
    }

    private record LastInteraction(int targetId, long gameTime) {
    }
}
