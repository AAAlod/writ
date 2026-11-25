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

    private MessageKeys() {
    }
}
