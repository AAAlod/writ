package ke.writ;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Every player-facing string must exist, with the same arguments, in each shipped language; shipped data must not hardcode one language. */
class LangCoverageTest {
    static final Path LANG = TestSourcePaths.projectFile("src/main/resources/assets/writ/lang"),
            DATA = TestSourcePaths.projectFile("src/main/resources/data/writ/writs"),
            JAVA = TestSourcePaths.projectFile("src/main/java/ke/writ");
    static final List<String> LANGUAGES = List.of("en_us", "zh_cn", "fr_fr");
    /** The vanilla 1.20.1 keys the shipped data borrows, checked against the client's en_us.json; the client translates them into every language. */
    static final Set<String> VANILLA_KEYS = Set.of(
            "instrument.minecraft.admire_goat_horn", "instrument.minecraft.call_goat_horn", "instrument.minecraft.dream_goat_horn",
            "instrument.minecraft.feel_goat_horn", "instrument.minecraft.ponder_goat_horn", "instrument.minecraft.seek_goat_horn",
            "instrument.minecraft.sing_goat_horn", "instrument.minecraft.yearn_goat_horn", "item.minecraft.creeper_banner_pattern.desc",
            "item.minecraft.globe_banner_pattern.desc", "item.minecraft.mojang_banner_pattern.desc", "item.minecraft.piglin_banner_pattern.desc",
            "item.minecraft.skull_banner_pattern.desc", "trim_pattern.minecraft.coast", "trim_pattern.minecraft.dune",
            "trim_pattern.minecraft.eye", "trim_pattern.minecraft.rib", "trim_pattern.minecraft.sentry",
            "trim_pattern.minecraft.silence", "trim_pattern.minecraft.snout", "trim_pattern.minecraft.spire",
            "trim_pattern.minecraft.vex", "trim_pattern.minecraft.ward");
    static final Map<String, Map<String, String>> lang = new LinkedHashMap<>();

    static JsonObject read(Path path) {
        try { return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    static List<Path> files(Path dir, String suffix) {
        try (Stream<Path> stream = Files.walk(dir)) { return stream.filter(p -> p.toString().endsWith(suffix)).sorted().toList(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    @BeforeAll static void load() {
        for (String code : LANGUAGES) {
            Map<String, String> values = new LinkedHashMap<>();
            read(LANG.resolve(code + ".json")).entrySet().forEach(e -> values.put(e.getKey(), e.getValue().getAsString()));
            lang.put(code, values);
        }
    }
    static void assertTranslated(String key, String where) {
        if (VANILLA_KEYS.contains(key)) return;
        assertTrue(key.startsWith("writ."), where + ": " + key + " is neither a writ key nor a known vanilla key");
        for (String code : LANGUAGES) assertTrue(lang.get(code).containsKey(key), where + ": " + key + " missing from " + code);
    }
    /** Argument slots a format string consumes, so "%s (%s/%s)" and "%1$s %3$s %2$s" compare equal. */
    static Set<Integer> arguments(String text) {
        Set<Integer> slots = new TreeSet<>(); int next = 1;
        Matcher m = Pattern.compile("%(?:(\\d+)\\$)?s|%%").matcher(text);
        while (m.find()) { if (m.group().equals("%%")) continue; slots.add(m.group(1) != null ? Integer.parseInt(m.group(1)) : next++); }
        return slots;
    }

    @Test void languagesShareKeysAndArguments() {
        Map<String, String> english = lang.get("en_us");
        for (String code : LANGUAGES) {
            Map<String, String> values = lang.get(code);
            assertEquals(english.keySet(), values.keySet(), code + " key set differs from en_us");
            values.forEach((key, text) -> {
                assertFalse(text.isBlank() && !key.equals("writ.trade.list_separator"), code + ": blank " + key);
                assertFalse(text.isEmpty(), code + ": empty " + key);
                assertEquals(arguments(english.get(key)), arguments(text), code + ": arguments of " + key);
                assertFalse(text.replaceAll("%(\\d+\\$)?s|%%", "").contains("%"), code + ": stray % in " + key);
            });
        }
    }

    @Test void selectorMessagesExistInEveryShippedLanguage() {
        var english = lang.get("en_us");
        var keys = english.keySet().stream()
                .filter(key -> key.startsWith("writ.gui.") || key.startsWith("writ.message.variant."))
                .toList();
        for (Path file : files(LANG, ".json")) {
            var translations = read(file);
            for (String key : keys) {
                assertTrue(translations.has(key), file + ": missing " + key);
                String text = translations.get(key).getAsString();
                assertFalse(text.isBlank(), file + ": blank " + key);
                assertEquals(arguments(english.get(key)), arguments(text), file + ": arguments of " + key);
            }
        }
    }

    @Test void noLanguageIsLeftUntranslated() {
        // Format-only keys and proper nouns are legitimately identical; everything else must differ from English.
        Set<String> shared = Set.of("writ.trade.count", "writ.trade.plus", "writ.trade.arrow", "writ.trade.list_separator");
        for (String code : List.of("zh_cn", "fr_fr")) {
            long same = lang.get(code).entrySet().stream().filter(e -> !shared.contains(e.getKey()) && e.getValue().equals(lang.get("en_us").get(e.getKey()))).count();
            assertTrue(same <= 12, code + " repeats English for " + same + " keys");
        }
        for (String text : lang.get("zh_cn").values()) assertFalse(text.matches(".*[a-z]{4,}.*") && !text.contains("Shift"), "zh_cn contains untranslated text: " + text);
        for (Map.Entry<String, String> e : lang.get("en_us").entrySet()) assertTrue(e.getValue().chars().noneMatch(c -> c >= 0x4E00 && c <= 0x9FFF), "en_us contains Chinese: " + e.getKey());
        for (Map.Entry<String, String> e : lang.get("fr_fr").entrySet()) assertTrue(e.getValue().chars().noneMatch(c -> c >= 0x4E00 && c <= 0x9FFF), "fr_fr contains Chinese: " + e.getKey());
    }

    @Test void everyPresetAndSelectionIsTranslatable() {
        for (Path file : files(DATA.resolve("presets"), ".json")) {
            String name = file.getFileName().toString().replace(".json", ""); JsonObject json = read(file);
            assertTrue(json.has("display_translation_key"), name + " has no display_translation_key");
            assertEquals("writ.preset." + name, json.get("display_translation_key").getAsString(), name);
            assertTranslated(json.get("display_translation_key").getAsString(), name);
            assertFalse(json.has("default_variant_label") && !json.has("default_variant_translation_key"), name + " hardcodes its default selection name");
            if (json.has("default_variant_translation_key")) assertSelectionTranslated(json.get("default_variant_translation_key").getAsString(), json.getAsJsonObject("trade"), name + " default");
            if (!json.has("variants")) { assertFalse(json.has("default_variant_translation_key"), name + " names a default selection without variants"); continue; }
            String defaultItem = json.getAsJsonObject("trade").getAsJsonObject("result").get("item").getAsString();
            boolean defaultNamed = json.has("default_variant_translation_key");
            Set<String> unnamedResults = new HashSet<>();
            if (!defaultNamed) unnamedResults.add(defaultItem);
            for (JsonElement element : json.getAsJsonArray("variants")) {
                JsonObject variant = element.getAsJsonObject(); String where = name + "/" + variant.get("id").getAsString();
                boolean enabled = !variant.has("enabled") || variant.get("enabled").getAsBoolean();
                if (variant.has("translation_key")) { assertSelectionTranslated(variant.get("translation_key").getAsString(), variant.has("trade") ? variant.getAsJsonObject("trade") : json.getAsJsonObject("trade"), where); continue; }
                // Without a key the selection is named after its result item, so a plain-text label would pin one language...
                assertFalse(variant.has("label"), where + " has a label but no translation_key");
                if (!enabled) continue;
                // ...and that item must tell the selection apart from the others.
                JsonObject trade = variant.has("trade") ? variant.getAsJsonObject("trade") : new JsonObject();
                String item = trade.has("result") ? trade.getAsJsonObject("result").get("item").getAsString() : defaultItem;
                assertTrue(unnamedResults.add(item), where + " is named after " + item + ", which another selection already uses");
            }
        }
        for (Path file : files(DATA.resolve("loot/pools"), ".json")) {
            JsonObject json = read(file);
            assertTrue(json.has("translation_key"), file.getFileName() + " has no translation_key");
            assertTranslated(json.get("translation_key").getAsString(), file.getFileName().toString());
        }
    }

    private static void assertSelectionTranslated(String key, JsonObject trade, String where) {
        if (key.startsWith("block.minecraft.")) {
            assertTrue(trade.has("cost_b") && trade.getAsJsonObject("cost_b").get("item").getAsString().equals("minecraft:" + key.substring("block.minecraft.".length())), where + " vanilla translation must name its payment item");
        } else assertTranslated(key, where);
    }

    @Test void everyKeyUsedByCodeExists() throws IOException {
        Set<String> english = lang.get("en_us").keySet();
        Pattern literal = Pattern.compile("\"((?:writ|item\\.writ|itemGroup)\\.[A-Za-z0-9_.]*)\"");
        Set<String> used = new TreeSet<>();
        for (Path file : files(JAVA, ".java")) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = literal.matcher(source);
            while (m.find()) {
                String key = m.group(1);
                if (key.endsWith(".")) assertTrue(english.stream().anyMatch(k -> k.startsWith(key)), file.getFileName() + ": no key under " + key);
                else { assertTrue(english.contains(key), file.getFileName() + ": " + key + " is not translated"); used.add(key); }
            }
            // Failure codes are appended to "writ.message." at the call site.
            Matcher failure = Pattern.compile("(?:OfferResult\\(null, |SwitchResult\\(|message\\(event, )\"([a-z_.]+)\"").matcher(source);
            while (failure.find()) { assertTrue(english.contains("writ.message." + failure.group(1)), file.getFileName() + ": writ.message." + failure.group(1)); used.add("writ.message." + failure.group(1)); }
        }
        for (String code : List.of("villager.baby", "villager.unemployed", "villager.nitwit", "villager.novice", "limit.total", "limit.trade", "limit.chaos",
                "variant.switched", "variant.reset", "variant.cleared", "success", "other_merchant", "container_refund", "busy", "invalid_data", "empty_pool",
                "blank", "preset_missing", "preset_invalid", "version_unsupported", "profession_mismatch", "variant_invalid"))
            assertTrue(english.contains("writ.message." + code), "writ.message." + code);
        assertTrue(english.contains("item.writ.trade_writ.named"));
        // No dead text: every message and tooltip key is referenced from code.
        for (String key : english) if (key.startsWith("writ.tooltip.") || key.startsWith("writ.trade."))
            assertTrue(used.contains(key), key + " is never used by code");
    }
}
