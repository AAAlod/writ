package ke.writ.resonance.internal;

/**
 * Translation keys owned by the module. Only the item name is fixed by the specification
 * ({@code item.writ.resonance_device}); every other key uses the {@code writ.resonance.} prefix so
 * the host can merge the module language snippets into its own language files.
 */
public final class MessageKeys {

    /** Item display name; resolved by vanilla from the registry name {@code writ:resonance_device}. */
    public static final String ITEM_NAME = "item.writ.resonance_device";

    /** Tooltip line shown while the device is unbound. */
    public static final String TOOLTIP_UNBOUND = "writ.resonance.tooltip.unbound";
    /** Tooltip line shown for a bound device; one argument: the derived frequency label. */
    public static final String TOOLTIP_BOUND = "writ.resonance.tooltip.bound";
    /** One explicit effect line; arguments: localised effect name, Roman level, duration text. */
    public static final String TOOLTIP_EFFECT = "writ.resonance.tooltip.effect";
    /** Duration of an effect in seconds; one argument: the seconds value. */
    public static final String TOOLTIP_DURATION_SECONDS = "writ.resonance.tooltip.duration_seconds";
    /** Duration text of an effect that never expires. */
    public static final String TOOLTIP_DURATION_INFINITE = "writ.resonance.tooltip.duration_infinite";
    /** Duration text of an instant effect. */
    public static final String TOOLTIP_DURATION_INSTANT = "writ.resonance.tooltip.duration_instant";
    /** Tooltip line for an empty device whose host provides a default effect. */
    public static final String TOOLTIP_DEFAULT_EFFECT = "writ.resonance.tooltip.default_effect";
    /** Tooltip line with the effective cooldown; one argument: the cooldown in seconds. */
    public static final String TOOLTIP_COOLDOWN = "writ.resonance.tooltip.cooldown";

    /** Feedback after a successful first binding; one argument: the derived frequency label. */
    public static final String BOUND = "writ.resonance.bound";
    /** Feedback when somebody other than the owner tries to use or rebind the device. */
    public static final String UNAUTHORIZED = "writ.resonance.unauthorized";
    /** Feedback when an empty device is used but the host configured no default effect. */
    public static final String NO_DEFAULT_EFFECT = "writ.resonance.no_default_effect";
    /** Feedback for malformed or unknown explicit effect configuration. */
    public static final String CONFIG_ERROR = "writ.resonance.config_error";
    /** Feedback when no effect could be applied to the target. */
    public static final String EFFECT_UNAVAILABLE = "writ.resonance.effect_unavailable";
    /** Feedback when a use is attempted during the cooldown; one argument: remaining seconds. */
    public static final String COOLDOWN = "writ.resonance.cooldown";

    private MessageKeys() {
    }
}
