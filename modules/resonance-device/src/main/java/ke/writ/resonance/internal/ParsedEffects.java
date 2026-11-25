package ke.writ.resonance.internal;

import java.util.List;

/**
 * Result of parsing an item's {@code effects} tag into a plan.
 */
public sealed interface ParsedEffects {

    /** An explicit, fully validated {@code effects} list; it is used instead of the host default. */
    record Explicit(List<EffectSpec> effects) implements ParsedEffects {
    }

    /** No {@code effects} tag, or an explicitly empty list: the host default effect is requested. */
    record Default() implements ParsedEffects {
    }

    /** The tag is present but malformed; no effect may execute. */
    record Invalid(String detail) implements ParsedEffects {
    }
}
