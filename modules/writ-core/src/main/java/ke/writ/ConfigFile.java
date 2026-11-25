package ke.writ;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.common.ForgeConfigSpec;

/** Loads one standalone config; never passes a file-backed config to Forge. */
final class ConfigFile {
    enum Status { CREATED, LOADED, UPGRADED, CORRECTED, SAVE_FAILED, BACKUP_FAILED, USING_BACKUP, INCOMPATIBLE, FAILED }
    record LoadResult(CommentedConfig config, Status status, String message) { }

    @FunctionalInterface
    interface AtomicMove {
        void move(Path source, Path target) throws IOException;
    }

    private static final AtomicMove MOVE = (source, target) -> Files.move(source, target,
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

    static LoadResult load(ForgeConfigSpec spec, Path path, int currentSchemaVersion) {
        return load(spec, path, currentSchemaVersion, MOVE);
    }

    // The move seam allows tests to exercise failures at the two distinct commit points.
    static LoadResult load(ForgeConfigSpec spec, Path path, int currentSchemaVersion, AtomicMove move) {
        Path target = path.toAbsolutePath();
        Path backup = target.resolveSibling(target.getFileName() + ".bak");
        Path staging = target.resolveSibling(target.getFileName() + ".bak.tmp");
        try {
            if (currentSchemaVersion < 0 || !spec.getSpec().contains("schema_version"))
                return new LoadResult(null, Status.FAILED, "Spec must define schema_version and version must be nonnegative");
            boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
            if (!exists && !Files.notExists(target, LinkOption.NOFOLLOW_LINKS))
                return new LoadResult(null, Status.FAILED, "Cannot determine whether config exists: " + target);
            CommentedConfig staged = readUsable(staging, currentSchemaVersion);
            CommentedConfig config;
            byte[] original = null;
            Path recoverySource = null;
            if (exists) {
                try {
                    original = Files.readAllBytes(target);
                    config = parseUsable(original, currentSchemaVersion);
                } catch (VersionTooNewException tooNew) {
                    return new LoadResult(null, Status.INCOMPATIBLE,
                            "Config " + target + " has a newer schema_version; refusing to fall back to an older backup");
                } catch (Exception primaryError) {
                    config = readUsable(backup, currentSchemaVersion);
                    if (config != null) recoverySource = backup;
                    else {
                        config = staged;
                        if (config != null) recoverySource = staging;
                    }
                    if (config == null) return new LoadResult(null, Status.FAILED, "Cannot read config or a usable backup: " + primaryError);
                }
            } else {
                config = readUsable(backup, currentSchemaVersion);
                if (config != null) recoverySource = backup;
                else {
                    config = staged;
                    if (config != null) recoverySource = staging;
                }
                if (config == null) {
                    boolean backupAbsent = Files.notExists(backup, LinkOption.NOFOLLOW_LINKS);
                    boolean stagingAbsent = Files.notExists(staging, LinkOption.NOFOLLOW_LINKS);
                    if (!backupAbsent || !stagingAbsent) {
                        for (Path candidate : new Path[] { backup, staging }) {
                            if (Files.notExists(candidate, LinkOption.NOFOLLOW_LINKS)) continue;
                            try {
                                parseUsable(Files.readAllBytes(candidate), currentSchemaVersion);
                            } catch (VersionTooNewException tooNew) {
                                return new LoadResult(null, Status.INCOMPATIBLE,
                                        "Config " + target + " is missing and " + candidate
                                                + " has a newer schema_version; refusing to create defaults");
                            } catch (Exception ignored) { }
                        }
                        return new LoadResult(null, Status.FAILED,
                                "Config " + target + " is missing and existing recovery file(s) are unusable: "
                                        + backup + ", " + staging + "; restore or check them and restart");
                    }
                    config = CommentedConfig.inMemory();
                }
            }

            int version = version(config);
            boolean schemaUpgrade = version < currentSchemaVersion;
            AtomicInteger valueCorrections = new AtomicInteger();
            // A comment-only difference is not a reason to rewrite the file or rotate its backup.
            spec.correct(config,
                    (action, path1, incorrectValue, correctedValue) -> valueCorrections.incrementAndGet(),
                    (action, path1, incorrectValue, correctedValue) -> { });
            config.set("schema_version", currentSchemaVersion);
            spec.setConfig(config);
            if (!spec.isCorrect(config) || version(config) != currentSchemaVersion)
                return new LoadResult(config, Status.FAILED, "Spec does not accept current schema_version");
            if (recoverySource != null)
                return new LoadResult(config, Status.USING_BACKUP,
                        "Using " + recoverySource + " in memory; " + target + " left unchanged");

            Path isolatedStaging = null;
            if (staged != null) {
                try {
                    CommentedConfig backupConfig = readUsable(backup, currentSchemaVersion);
                    boolean backupExists = Files.exists(backup, LinkOption.NOFOLLOW_LINKS);
                    if (backupConfig != null && Files.mismatch(staging, backup) == -1) {
                        Files.delete(staging);
                    } else if (backupConfig != null) {
                        isolatedStaging = quarantine(staging);
                    } else if (backupExists) {
                        return new LoadResult(config, Status.BACKUP_FAILED,
                                "Backup exists but is not usable; preserving staging " + staging);
                    } else {
                        move.move(staging, backup);
                    }
                } catch (Exception e) {
                    return new LoadResult(config, Status.BACKUP_FAILED, "Could not resolve staging: " + e);
                }
            }
            boolean shouldSave = !exists || schemaUpgrade || valueCorrections.get() > 0;
            if (!shouldSave) return isolatedStaging == null
                    ? new LoadResult(config, Status.LOADED, "Loaded without changes")
                    : new LoadResult(config, Status.BACKUP_FAILED,
                            "Unverified staging isolated at " + isolatedStaging + "; .bak unchanged.");
            // Never overwrite an unresolved staging file from an earlier interrupted save.
            if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS))
                return new LoadResult(config, Status.SAVE_FAILED, "Unresolved staging file exists: " + staging);
            Path temporary = null;
            boolean stagedHere = false;
            boolean committed = false;
            boolean backupNeeded = exists && schemaUpgrade;
            try {
                Files.createDirectories(target.getParent());
                temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
                byte[] output = new TomlWriter().writeToString(config).getBytes(StandardCharsets.UTF_8);
                writeForced(temporary, output, false);
                CommentedConfig checked = parseUsable(Files.readAllBytes(temporary), currentSchemaVersion);
                if (version(checked) != currentSchemaVersion || !spec.isCorrect(checked)) throw new IOException("Written config failed validation");
                if (backupNeeded) {
                    writeForced(staging, original, true);
                    stagedHere = true;
                }
                move.move(temporary, target);
                committed = true;
                if (backupNeeded) {
                    try {
                        move.move(staging, backup);
                        stagedHere = false;
                    } catch (Exception e) {
                        return new LoadResult(config, Status.BACKUP_FAILED, "Config saved; previous config remains in staging: " + e
                                + (isolatedStaging == null ? "" : "; unverified staging isolated at " + isolatedStaging));
                    }
                }
                if (isolatedStaging != null) return new LoadResult(config, Status.BACKUP_FAILED,
                        schemaUpgrade
                                ? "Config saved and schema upgraded; unverified staging isolated at " + isolatedStaging
                                        + "; the isolated staging did not replace .bak; the normal upgrade updated .bak."
                                : "Saved config; unverified staging isolated at " + isolatedStaging + "; .bak unchanged.");
                return new LoadResult(config, !exists ? Status.CREATED : schemaUpgrade ? Status.UPGRADED : Status.CORRECTED, "Saved config");
            } catch (Exception e) {
                return new LoadResult(config, Status.SAVE_FAILED, "Could not save config: " + e);
            } finally {
                if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                if (stagedHere && !committed) try { Files.deleteIfExists(staging); } catch (IOException ignored) { }
            }
        } catch (Exception e) {
            return new LoadResult(null, Status.FAILED, "Cannot load config: " + e);
        }
    }

    private static Path quarantine(Path staging) throws IOException {
        Path base = staging.resolveSibling(staging.getFileName() + ".orphan");
        for (int n = 0; ; n++) {
            Path candidate = n == 0 ? base : base.resolveSibling(base.getFileName() + "." + n);
            try {
                return Files.move(staging, candidate);
            } catch (java.nio.file.FileAlreadyExistsException ignored) { }
        }
    }

    private static void writeForced(Path path, byte[] bytes, boolean createNew) throws IOException {
        FileChannel channel = createNew
                ? FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                : FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        try (FileChannel out = channel) {
            ByteBuffer data = ByteBuffer.wrap(bytes);
            while (data.hasRemaining()) out.write(data);
            out.force(true);
        } catch (IOException e) {
            if (createNew) try { Files.deleteIfExists(path); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    private static CommentedConfig readUsable(Path path, int current) {
        try { return parseUsable(Files.readAllBytes(path), current); }
        catch (Exception ignored) { return null; }
    }

    private static CommentedConfig parseUsable(byte[] bytes, int current) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        CommentedConfig config = new TomlParser().parse(text);
        if (version(config) > current) throw new VersionTooNewException("schema_version is newer than supported");
        return config;
    }

    private static int version(CommentedConfig config) throws IOException {
        Object raw = config.get("schema_version");
        if (raw == null) return 0;
        if (!(raw instanceof Integer || raw instanceof Long)) throw new IOException("Invalid schema_version");
        long value = ((Number) raw).longValue();
        if (value < 0 || value > Integer.MAX_VALUE) throw new IOException("Invalid schema_version");
        return (int) value;
    }

    private static final class VersionTooNewException extends IOException {
        VersionTooNewException(String message) { super(message); }
    }
}
