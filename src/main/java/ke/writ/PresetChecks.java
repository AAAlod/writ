package ke.writ;

final class PresetChecks {
    static final int CONTRACT_VERSION = 1;
    private PresetChecks() { }
    static boolean supportedContract(boolean hasId, boolean hasIntVersion, int version) {
        return hasId && (!hasIntVersion || version == CONTRACT_VERSION);
    }
    static void weight(int weight) { if (weight < 0) throw new IllegalArgumentException("negative weight"); }
    static void displayName(String name) { if (name != null && name.isEmpty()) throw new IllegalArgumentException("display_name must not be empty"); }
    static void item(String id, boolean exists, boolean empty, int count, int maxStack) {
        if (!exists || empty || count < 1 || count > maxStack) throw new IllegalArgumentException("invalid item or count: " + id + " x " + count);
    }
    static void trade(int uses, int xp, float multiplier) {
        if (uses <= 0 || xp < 0 || !Float.isFinite(multiplier) || multiplier < 0) throw new IllegalArgumentException("invalid trade parameters");
    }
}
