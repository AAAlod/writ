package ke.writ;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigFileTest {
    @TempDir Path dir;
    private final ForgeConfigSpec spec;
    private final ForgeConfigSpec.IntValue valueA, valueB;

    ConfigFileTest() {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.defineInRange("schema_version", 1, 0, Integer.MAX_VALUE);
        valueA = b.defineInRange("value_a", 4, 0, 100);
        b.push("new_section");
        valueB = b.defineInRange("value_b", 7, 0, 100);
        b.pop();
        spec = b.build();
    }

    private Path file() { return dir.resolve("config.toml"); }
    private Path backup() { return dir.resolve("config.toml.bak"); }
    private Path staging() { return dir.resolve("config.toml.bak.tmp"); }
    private ConfigFile.LoadResult load() { return ConfigFile.load(spec, file(), 1); }
    private void write(Path path, String text) throws IOException { Files.writeString(path, text); }
    private static final String OLD = "value_a = 9\n";
    private static final String VALID = "schema_version = 1\nvalue_a = 9\n[new_section]\nvalue_b = 7\n";
    private static void atomicMove(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    @Test void createsDefaults() throws Exception {
        assertEquals(ConfigFile.Status.CREATED, load().status());
        assertEquals(4, valueA.get()); assertEquals(7, valueB.get());
        assertEquals(1, load().config().<Integer>get("schema_version"));
        assertFalse(Files.exists(backup()));
    }

    @Test void multilineCommentsRoundTrip() throws Exception {
        var builder = new ForgeConfigSpec.Builder();
        builder.comment("Internal configuration schema version. Do not edit; used by Writ's recovery logic.")
                .defineInRange("schema_version", 1, 0, Integer.MAX_VALUE);
        builder.push("chaos_writ");
        builder.comment("How chaos writs are obtained:",
                        "  TRADE   - Chaos Writ Unlock writs drop naturally; villagers that sign one sell chaos writs. The crafting recipe is removed.",
                        "  CRAFT_9 - Nine trade writs craft one chaos writ. Writs that sell chaos writs no longer drop naturally.",
                        "Village hero gifts are independent of these two chaos writ sources.",
                        "Any other value falls back to TRADE. Read once at server start: restart the server and have players reconnect after changing it.")
                .define("obtain_mode", "TRADE", value -> value != null);
        builder.pop();
        ForgeConfigSpec actual = builder.build();
        var result = ConfigFile.load(actual, file(), 1);
        assertEquals(ConfigFile.Status.CREATED, result.status(), result.message());
        assertEquals(ConfigFile.Status.LOADED, ConfigFile.load(actual, file(), 1).status());
    }

    @Test void upgradesOldConfigAndBacksUpOriginal() throws Exception {
        write(file(), OLD);
        assertEquals(ConfigFile.Status.UPGRADED, load().status());
        assertEquals(9, valueA.get()); assertEquals(7, valueB.get());
        assertEquals(OLD, Files.readString(backup()));
        assertEquals(1, load().config().<Integer>get("schema_version"));
        assertFalse(Files.exists(staging()));
    }

    @Test void unchangedConfigDoesNotWriteOrUpdateBackup() throws Exception {
        write(file(), OLD); load();
        byte[] contents = Files.readAllBytes(file()), saved = Files.readAllBytes(backup());
        var fileTime = Files.getLastModifiedTime(file()); var backupTime = Files.getLastModifiedTime(backup());
        assertEquals(ConfigFile.Status.LOADED, load().status());
        assertArrayEquals(contents, Files.readAllBytes(file())); assertArrayEquals(saved, Files.readAllBytes(backup()));
        assertEquals(fileTime, Files.getLastModifiedTime(file())); assertEquals(backupTime, Files.getLastModifiedTime(backup()));
    }

    @Test void commentOnlyChangeDoesNotWriteOrRotateBackup() throws Exception {
        write(file(), OLD); load();
        byte[] backupBefore = Files.readAllBytes(backup());
        String commented = "# user note\n" + Files.readString(file());
        write(file(), commented);
        assertEquals(ConfigFile.Status.LOADED, load().status());
        assertEquals(commented, Files.readString(file()));
        assertArrayEquals(backupBefore, Files.readAllBytes(backup()));
    }

    @Test void invalidValueCorrectionDoesNotRotateUpgradeBackup() throws Exception {
        write(file(), OLD); load();
        byte[] backupBefore = Files.readAllBytes(backup());
        write(file(), Files.readString(file()).replace("value_a = 9", "value_a = -1"));
        assertEquals(ConfigFile.Status.CORRECTED, load().status());
        assertEquals(0, valueA.get());
        assertArrayEquals(backupBefore, Files.readAllBytes(backup()));
        assertFalse(Files.exists(staging()));
    }

    @Test void primaryReplaceFailurePreservesBothFilesAndLoadsCorrectedMemory() throws Exception {
        write(file(), OLD); write(backup(), "older backup");
        var result = ConfigFile.load(spec, file(), 1, (from, to) -> { throw new IOException("injected primary failure"); });
        assertEquals(ConfigFile.Status.SAVE_FAILED, result.status());
        assertTrue(result.message().contains("primary failure"));
        assertEquals(OLD, Files.readString(file())); assertEquals("older backup", Files.readString(backup()));
        assertEquals(9, valueA.get()); assertEquals(7, valueB.get()); assertFalse(Files.exists(staging()));
        try (var paths = Files.list(dir)) { assertEquals(2, paths.count()); }
    }

    @Test void blockedStagingPreservesPrimaryAndBackup() throws Exception {
        write(file(), OLD); write(backup(), VALID); Files.createDirectory(staging());
        assertEquals(ConfigFile.Status.SAVE_FAILED, load().status());
        assertEquals(OLD, Files.readString(file())); assertEquals(VALID, Files.readString(backup()));
        assertEquals(9, valueA.get()); assertEquals(7, valueB.get());
    }

    @Test void backupPromotionFailureKeepsCommittedConfigAndStaging() throws Exception {
        write(file(), OLD); write(backup(), VALID);
        var result = ConfigFile.load(spec, file(), 1, (from, to) -> {
            if (to.equals(backup())) throw new IOException("injected backup failure");
            atomicMove(from, to);
        });
        assertEquals(ConfigFile.Status.BACKUP_FAILED, result.status());
        assertEquals(VALID, Files.readString(backup())); assertEquals(OLD, Files.readString(staging()));
        assertNotEquals(OLD, Files.readString(file())); assertEquals(9, valueA.get());
        var retry = load();
        assertEquals(ConfigFile.Status.BACKUP_FAILED, retry.status());
        assertTrue(retry.message().contains(".orphan"));
        assertEquals(VALID, Files.readString(backup())); assertFalse(Files.exists(staging()));
        assertEquals(OLD, Files.readString(dir.resolve("config.toml.bak.tmp.orphan")));
        String modified = Files.readString(file()).replace("schema_version = 1", "schema_version = 0");
        assertTrue(modified.contains("schema_version = 0"));
        write(file(), modified);
        assertEquals(ConfigFile.Status.UPGRADED, load().status());
        assertEquals(modified, Files.readString(backup())); assertFalse(Files.exists(staging()));
        assertEquals(9, valueA.get()); assertEquals(7, valueB.get());
    }

    @Test void interruptedStagingPromotesWhenBackupMissing() throws Exception {
        assertEquals(ConfigFile.Status.CREATED, load().status());
        write(staging(), OLD);
        byte[] original = Files.readAllBytes(file());
        assertEquals(ConfigFile.Status.LOADED, load().status());
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertEquals(OLD, Files.readString(backup())); assertFalse(Files.exists(staging()));
    }

    @Test void interruptedStagingPromotionFailureIsReportedAndRetained() throws Exception {
        assertEquals(ConfigFile.Status.CREATED, load().status());
        write(staging(), OLD);
        var result = ConfigFile.load(spec, file(), 1, (from, to) -> { throw new IOException("promotion failure"); });
        assertEquals(ConfigFile.Status.BACKUP_FAILED, result.status());
        assertEquals(OLD, Files.readString(staging())); assertFalse(Files.exists(backup()));
        assertEquals(4, valueA.get());
    }

    @Test void corruptPrimaryUsesValidBackupWithoutOverwritingIt() throws Exception {
        write(file(), "broken = ["); write(backup(), VALID);
        assertEquals(ConfigFile.Status.USING_BACKUP, load().status());
        assertEquals(9, valueA.get()); assertEquals("broken = [", Files.readString(file()));
        assertEquals(VALID, Files.readString(backup()));
    }

    @Test void missingPrimaryUsesBackupBeforeCreatingDefaults() throws Exception {
        write(backup(), VALID);
        assertEquals(ConfigFile.Status.USING_BACKUP, load().status());
        assertEquals(9, valueA.get()); assertEquals(7, valueB.get());
        assertFalse(Files.exists(file())); assertEquals(VALID, Files.readString(backup()));
    }

    @Test void missingPrimaryAndCorruptBackupFailWithoutWriting() throws Exception {
        write(backup(), "broken = [");
        byte[] saved = Files.readAllBytes(backup());
        var result = load();
        assertEquals(ConfigFile.Status.FAILED, result.status());
        assertTrue(result.message().contains(file().toString()));
        assertTrue(result.message().contains(backup().toString()));
        assertFalse(Files.exists(file())); assertArrayEquals(saved, Files.readAllBytes(backup()));
        try (var paths = Files.list(dir)) { assertEquals(1, paths.count()); }
    }

    @Test void missingPrimaryAndFutureBackupAreIncompatible() throws Exception {
        write(backup(), "schema_version = 99\n"); write(staging(), "broken = [");
        byte[] saved = Files.readAllBytes(backup()), staged = Files.readAllBytes(staging());
        var result = load();
        assertEquals(ConfigFile.Status.INCOMPATIBLE, result.status());
        assertTrue(result.message().contains(file().toString()));
        assertTrue(result.message().contains(backup().toString()));
        assertFalse(Files.exists(file())); assertArrayEquals(saved, Files.readAllBytes(backup()));
        assertArrayEquals(staged, Files.readAllBytes(staging()));
        write(backup(), "broken = ["); write(staging(), "schema_version = 99\n");
        result = load();
        assertEquals(ConfigFile.Status.INCOMPATIBLE, result.status());
        assertTrue(result.message().contains(staging().toString()));
        assertFalse(Files.exists(file()));
    }

    @Test void missingPrimaryAndCorruptStagingFailWithoutWriting() throws Exception {
        write(staging(), "broken = [");
        byte[] saved = Files.readAllBytes(staging());
        var result = load();
        assertEquals(ConfigFile.Status.FAILED, result.status());
        assertTrue(result.message().contains(file().toString()));
        assertTrue(result.message().contains(staging().toString()));
        assertFalse(Files.exists(file())); assertArrayEquals(saved, Files.readAllBytes(staging()));
        try (var paths = Files.list(dir)) { assertEquals(1, paths.count()); }
    }

    @Test void missingPrimaryPrefersBackupOverStaging() throws Exception {
        write(backup(), VALID); write(staging(), "value_a = 13\n");
        assertEquals(ConfigFile.Status.USING_BACKUP, load().status());
        assertEquals(9, valueA.get()); assertFalse(Files.exists(file()));
        assertEquals(VALID, Files.readString(backup())); assertEquals("value_a = 13\n", Files.readString(staging()));
    }

    @Test void missingPrimaryUsesStagingWhenBackupMissingOrInvalid() throws Exception {
        write(staging(), "value_a = 13\n");
        var result = load();
        assertEquals(ConfigFile.Status.USING_BACKUP, result.status());
        assertTrue(result.message().contains(staging().toString()));
        assertTrue(result.message().contains(file().toString()));
        assertEquals(13, valueA.get()); assertFalse(Files.exists(file()));
        write(backup(), "broken = [");
        assertEquals(ConfigFile.Status.USING_BACKUP, load().status());
        assertEquals(13, valueA.get()); assertEquals("broken = [", Files.readString(backup()));
        assertEquals("value_a = 13\n", Files.readString(staging()));
    }

    @Test void corruptPrimaryAndBackupFail() throws Exception {
        write(file(), "broken = ["); write(backup(), "also = [");
        assertEquals(ConfigFile.Status.FAILED, load().status());
    }

    @Test void newerVersionIsRejectedEvenWithBackup() throws Exception {
        write(file(), "schema_version = 99\n");
        assertEquals(ConfigFile.Status.INCOMPATIBLE, load().status());
        write(backup(), VALID);
        assertEquals(ConfigFile.Status.INCOMPATIBLE, load().status());
        assertEquals("schema_version = 99\n", Files.readString(file()));
        assertEquals(VALID, Files.readString(backup()));
    }

    @Test void interruptedSaveUsesStagingWhenPrimaryAndBackupInvalid() throws Exception {
        write(file(), "broken = ["); write(backup(), "broken = ["); write(staging(), VALID);
        assertEquals(ConfigFile.Status.USING_BACKUP, load().status());
        assertEquals(9, valueA.get()); assertEquals(VALID, Files.readString(staging()));
        assertEquals("broken = [", Files.readString(file()));
    }

    @Test void staleCurrentStagingDoesNotReplaceValidBackup() throws Exception {
        write(file(), OLD); load(); write(staging(), VALID);
        byte[] original = Files.readAllBytes(file()), saved = Files.readAllBytes(backup());
        assertEquals(ConfigFile.Status.BACKUP_FAILED, load().status());
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertArrayEquals(saved, Files.readAllBytes(backup()));
        assertFalse(Files.exists(staging()));
        assertEquals(VALID, Files.readString(dir.resolve("config.toml.bak.tmp.orphan")));
    }

    @Test void pendingUpgradeStagingDoesNotReplaceOlderBackup() throws Exception {
        write(file(), OLD); load(); write(staging(), "value_a = 13\n");
        byte[] original = Files.readAllBytes(file());
        write(dir.resolve("config.toml.bak.tmp.orphan"), "existing");
        var result = load();
        assertEquals(ConfigFile.Status.BACKUP_FAILED, result.status());
        assertEquals("Unverified staging isolated at " + dir.resolve("config.toml.bak.tmp.orphan.1")
                + "; .bak unchanged.", result.message());
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertEquals(OLD, Files.readString(backup()));
        assertEquals("existing", Files.readString(dir.resolve("config.toml.bak.tmp.orphan")));
        assertEquals("value_a = 13\n", Files.readString(dir.resolve("config.toml.bak.tmp.orphan.1")));
        assertFalse(Files.exists(staging()));
    }

    @Test void matchingStagingIsRemovedWithoutReplacingBackup() throws Exception {
        write(file(), OLD); load(); write(staging(), OLD);
        var before = Files.getLastModifiedTime(backup());
        var result = ConfigFile.load(spec, file(), 1, (from, to) -> { throw new IOException("unexpected move"); });
        assertEquals(ConfigFile.Status.LOADED, result.status());
        assertEquals(before, Files.getLastModifiedTime(backup()));
        assertEquals(OLD, Files.readString(backup())); assertFalse(Files.exists(staging()));
    }

    @Test void quarantineUsesNextAvailableNumberedPath() throws Exception {
        write(file(), OLD); load(); write(staging(), "value_a = 13\n");
        byte[] original = Files.readAllBytes(file()), saved = Files.readAllBytes(backup());
        Files.createDirectory(dir.resolve("config.toml.bak.tmp.orphan"));
        Files.writeString(dir.resolve("config.toml.bak.tmp.orphan.1"), "existing");
        var result = load();
        assertEquals(ConfigFile.Status.BACKUP_FAILED, result.status());
        assertArrayEquals(original, Files.readAllBytes(file())); assertArrayEquals(saved, Files.readAllBytes(backup()));
        assertFalse(Files.exists(staging()));
        assertEquals("value_a = 13\n", Files.readString(dir.resolve("config.toml.bak.tmp.orphan.2")));
    }

    @Test void quarantinedStagingStillAllowsCorrectionSave() throws Exception {
        write(file(), VALID.replace("value_a = 9", "value_a = -1"));
        write(backup(), OLD); write(staging(), "value_a = 13\n");
        byte[] saved = Files.readAllBytes(backup());
        var orphan = dir.resolve("config.toml.bak.tmp.orphan");
        var result = load();
        assertEquals(ConfigFile.Status.BACKUP_FAILED, result.status());
        assertEquals("Saved config; unverified staging isolated at " + orphan + "; .bak unchanged.", result.message());
        assertTrue(Files.readString(file()).contains("value_a = 0"));
        assertEquals(0, valueA.get());
        assertArrayEquals(saved, Files.readAllBytes(backup()));
        assertFalse(Files.exists(staging()));
        assertEquals("value_a = 13\n", Files.readString(orphan));
    }

    @Test void quarantinedStagingStillAllowsSchemaUpgrade() throws Exception {
        write(file(), OLD); write(backup(), VALID); write(staging(), "value_a = 13\n");
        var orphan = dir.resolve("config.toml.bak.tmp.orphan");
        var result = load();
        assertEquals(ConfigFile.Status.BACKUP_FAILED, result.status());
        assertEquals("Config saved and schema upgraded; unverified staging isolated at " + orphan
                + "; the isolated staging did not replace .bak; the normal upgrade updated .bak.", result.message());
        assertTrue(Files.readString(file()).contains("schema_version = 1"));
        assertEquals(OLD, Files.readString(backup()));
        assertEquals("value_a = 13\n", Files.readString(orphan));
        assertFalse(Files.exists(staging()));
    }

    @Test void upgradeBackupFailureReportsIsolatedStaging() throws Exception {
        write(file(), OLD); write(backup(), VALID); write(staging(), "value_a = 13\n");
        var orphan = dir.resolve("config.toml.bak.tmp.orphan");
        var result = ConfigFile.load(spec, file(), 1, (from, to) -> {
            if (to.equals(backup())) throw new IOException("injected backup failure");
            atomicMove(from, to);
        });
        assertEquals(ConfigFile.Status.BACKUP_FAILED, result.status());
        assertTrue(result.message().contains("Config saved; previous config remains in staging: "));
        assertTrue(result.message().contains(orphan.toString()));
        assertTrue(Files.readString(file()).contains("schema_version = 1"));
        assertEquals(VALID, Files.readString(backup()));
        assertEquals(OLD, Files.readString(staging()));
        assertEquals("value_a = 13\n", Files.readString(orphan));
    }

    @Test void unusableBackupIsNotOverwrittenByStaging() throws Exception {
        write(file(), OLD); load(); write(staging(), "value_a = 13\n"); write(backup(), "broken = [");
        byte[] original = Files.readAllBytes(file());
        assertEquals(ConfigFile.Status.BACKUP_FAILED, load().status());
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertEquals("broken = [", Files.readString(backup()));
        assertEquals("value_a = 13\n", Files.readString(staging()));
    }

    @Test void unrelatedTemporaryFileIsNeverLoaded() throws Exception {
        write(file(), "broken = ["); write(dir.resolve("config.toml.abandoned.tmp"), VALID);
        assertEquals(ConfigFile.Status.FAILED, load().status());
    }
}
