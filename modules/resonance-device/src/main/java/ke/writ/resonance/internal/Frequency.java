package ke.writ.resonance.internal;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Derives the device's displayed frequency from the owner UUID.
 *
 * <p>The derivation only uses the UUID: every device of the same player shows the same frequency,
 * and renaming a player does not change it. The frequency is display data only; ownership checks
 * always compare the full UUID on the server.</p>
 */
public final class Frequency {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private Frequency() {
    }

    /**
     * Returns a short, stable, display-only frequency label for the given owner, for example
     * {@code "A3F1"}.
     *
     * @param owner the owner UUID
     * @return four upper-case hexadecimal digits
     */
    public static String of(UUID owner) {
        byte[] digest = sha256(owner);
        int value = ((digest[0] & 0xFF) << 8) | (digest[1] & 0xFF);
        char[] out = new char[4];
        for (int index = 3; index >= 0; index--) {
            out[index] = HEX[value & 0x0F];
            value >>>= 4;
        }
        return new String(out);
    }

    private static byte[] sha256(UUID owner) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocate(16);
            buffer.putLong(owner.getMostSignificantBits());
            buffer.putLong(owner.getLeastSignificantBits());
            return digest.digest(buffer.array());
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is mandatory on every Java platform; keep a deterministic fallback anyway.
            byte[] fallback = new byte[2];
            int hash = owner.hashCode();
            fallback[0] = (byte) (hash >>> 8);
            fallback[1] = (byte) hash;
            return fallback;
        }
    }
}
