package ke.writ.resonance;

import net.minecraft.network.chat.Component;

/**
 * The result of one effect handler invocation: whether the operation succeeded and an optional
 * player-facing message.
 *
 * @param success whether the requested operation was carried out
 * @param message a short, player-facing message; may be {@code null} when no feedback is wanted
 */
public record EffectResult(boolean success, Component message) {

    public static EffectResult success(Component message) {
        return new EffectResult(true, message);
    }

    public static EffectResult failure(Component message) {
        return new EffectResult(false, message);
    }
}
