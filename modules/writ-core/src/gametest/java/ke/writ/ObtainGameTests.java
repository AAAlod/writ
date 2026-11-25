package ke.writ;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class ObtainGameTests {
    private static Path path(Path pack, String resource) {
        var id = new ResourceLocation(resource);
        return pack.resolve("data").resolve(id.getNamespace()).resolve("writs").resolve(id.getPath() + ".json");
    }
    private static void write(Map<Path, byte[]> old, Path path, String data) throws Exception {
        if (!old.containsKey(path)) old.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
        Files.createDirectories(path.getParent());
        Files.writeString(path, data);
    }
    private static void restore(Map<Path, byte[]> old) throws Exception {
        for (var entry : old.entrySet()) {
            if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
            else Files.write(entry.getKey(), entry.getValue());
        }
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "isolated_reloadEmitsExactlyOneWarningPerUnreachableChain")
    public static void reloadEmitsExactlyOneWarningPerUnreachableChain(GameTestHelper h) {
        if (Presets.INSTANCE.activeMode() != ObtainMode.TRADE) { h.succeed(); return; }
        h.runAfterDelay(150, () -> {
            var server = h.getLevel().getServer();
            var pack = server.getWorldPath(LevelResource.ROOT).resolve("datapacks/baseline-tests");
            var snapshot = Presets.INSTANCE.currentSnapshot();
            Map<Path, byte[]> original = new LinkedHashMap<>();
            AtomicInteger warnings = new AtomicInteger();
            var logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(Writ.class);
            var appender = new AbstractAppender("acquisition-test", null, null, false, null) {
                @Override public void append(LogEvent event) {
                    if (event.getMessage().getFormattedMessage().startsWith("TRADE acquisition chain unreachable")) warnings.incrementAndGet();
                }
            };
            appender.start(); logger.addAppender(appender);
            try {
                // Disable all actual TRADE unlock candidates, including chaos-result variants.
                var unlockIds = Presets.unlockCandidates(snapshot.presets(), snapshot.candidates()).stream().map(LootRules.Candidate::id).collect(java.util.stream.Collectors.toSet());
                for (var entry : snapshot.presets().entrySet()) if (unlockIds.contains(entry.getKey().toString())) {
                    String id = entry.getKey().toString();
                    Path target = path(pack, entry.getKey().getNamespace() + ":presets/" + entry.getKey().getPath());
                    var originalResource = server.getResourceManager().getResourceOrThrow(new ResourceLocation(entry.getKey().getNamespace(), "writs/presets/" + entry.getKey().getPath() + ".json"));
                    JsonObject json;
                    try (var reader = originalResource.openAsReader()) { json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject(); }
                    json.addProperty("weight", 0); write(original, target, json.toString());
                }
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, first) -> server.execute(() -> {
                    try {
                        if (first != null || warnings.get() != 1) throw new IllegalStateException("zero preset weight expected one warning: " + warnings.get(), first);
                        restore(original);
                        for (var source : snapshot.sources().values()) {
                            JsonObject json = new JsonObject(); json.addProperty("loot_table", source.lootTable()); json.addProperty("contract_drop_chance", 0);
                            write(original, path(pack, source.resourceId()), json.toString());
                        }
                        for (var source : snapshot.bosses().values()) {
                            JsonObject json = new JsonObject(); json.addProperty("entity", source.lootTable()); json.addProperty("contract_drop_chance", 0);
                            write(original, path(pack, source.resourceId()), json.toString());
                        }
                        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((again, second) -> server.execute(() -> {
                            try {
                                if (second != null || warnings.get() != 2) throw new IllegalStateException("zero source chance expected one additional warning: " + warnings.get(), second);
                                restore(original);
                                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((restored, third) -> server.execute(() -> {
                                    logger.removeAppender(appender); appender.stop();
                                    if (third != null || warnings.get() != 2) h.fail("restore reload or unexpected warning: " + third);
                                    else h.succeed();
                                }));
                            } catch (Throwable e) { cleanup(h, original, logger, appender, e); }
                        }));
                    } catch (Throwable e) { cleanup(h, original, logger, appender, e); }
                }));
            } catch (Throwable e) { cleanup(h, original, logger, appender, e); }
        });
    }
    private static void cleanup(GameTestHelper h, Map<Path, byte[]> files, org.apache.logging.log4j.core.Logger logger, AbstractAppender appender, Throwable error) {
        logger.removeAppender(appender); appender.stop();
        TestRestore.finish(h, () -> restore(files), () -> {
            var snapshot = Presets.INSTANCE.currentSnapshot();
            var unlocks = Presets.unlockCandidates(snapshot.presets(), snapshot.candidates());
            boolean general = Presets.generalUnlockReachable(snapshot.presets(), snapshot.pools(), unlocks, Writ.generalLootChance());
            if (!ObtainMode.diagnostics(ObtainMode.TRADE, unlocks, snapshot.rarities(), java.util.stream.Stream.concat(snapshot.sources().values().stream(), snapshot.bosses().values().stream()).toList(), general).isEmpty())
                throw new IllegalStateException("acquisition chain was not restored");
        }, error);
    }
}
