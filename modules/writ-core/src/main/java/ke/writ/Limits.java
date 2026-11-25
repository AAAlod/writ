package ke.writ;

final class Limits {
    private Limits() { }
    static boolean allows(int normal, int chaos, int normalLimit, int chaosLimit, int totalLimit, boolean isNormal) {
        if (normal < 0 || chaos < 0 || normalLimit < 0 || chaosLimit < 0 || totalLimit < 0) return false;
        return (long) normal + chaos < totalLimit && (isNormal ? normal < normalLimit : chaos < chaosLimit);
    }
}
