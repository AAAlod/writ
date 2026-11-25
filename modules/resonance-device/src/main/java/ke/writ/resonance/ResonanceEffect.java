package ke.writ.resonance;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Host action used only by a resonance device with an absent or empty effect list.
 *
 * <p>All parameters are supplied or resolved by the server. Handlers decide whether an effect may
 * target the given entity and which additional permissions are required; the module only enforces
 * the device ownership rules.</p>
 */
@FunctionalInterface
public interface ResonanceEffect {

    /**
     * Executes this effect for one operation.
     *
     * @param player        the acting server player, never the client-side counterpart
     * @param target        the living entity that was right-clicked
     * @param operation     apply or remove
     * @return the outcome and an optional player-facing message
     */
    EffectResult execute(ServerPlayer player, LivingEntity target, Operation operation);
}
