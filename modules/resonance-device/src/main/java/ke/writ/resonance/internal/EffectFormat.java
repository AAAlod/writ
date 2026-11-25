package ke.writ.resonance.internal;

import java.util.Locale;

/**
 * Pure formatting helpers used by the item tooltip.
 *
 * <p>Kept free of Minecraft types so the arithmetic can be unit-tested on a plain JVM: levels are
 * rendered with Roman numerals (the module allows amplifiers 0-127, i.e. levels I-CXXVIII) and
 * durations are rendered as seconds, keeping up to two decimals for sub-second intervals.</p>
 */
public final class EffectFormat {

    private static final int[] ROMAN_VALUES = {
            1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1
    };
    private static final String[] ROMAN_SYMBOLS = {
            "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"
    };

    private EffectFormat() {
    }

    /**
     * Renders a positive level as an upper-case Roman numeral.
     *
     * @param level the one-based level (amplifier + 1), must be positive
     * @return the Roman numeral, e.g. {@code 4 -> "IV"}
     */
    public static String roman(int level) {
        if (level <= 0) {
            throw new IllegalArgumentException("level must be positive: " + level);
        }
        int remaining = level;
        StringBuilder roman = new StringBuilder();
        for (int index = 0; index < ROMAN_VALUES.length; index++) {
            while (remaining >= ROMAN_VALUES[index]) {
                roman.append(ROMAN_SYMBOLS[index]);
                remaining -= ROMAN_VALUES[index];
            }
        }
        return roman.toString();
    }

    /**
     * Renders a tick duration as seconds: whole seconds stay integral, sub-second durations keep at
     * most two decimals (e.g. {@code 600 -> "30"}, {@code 30 -> "1.5"}, {@code 1 -> "0.05"}).
     *
     * @param ticks the positive duration in ticks
     * @return the seconds value without a unit suffix
     */
    public static String secondsText(int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks must be positive: " + ticks);
        }
        if (ticks % 20 == 0) {
            return Integer.toString(ticks / 20);
        }
        String text = String.format(Locale.ROOT, "%.2f", ticks / 20.0d);
        if (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
