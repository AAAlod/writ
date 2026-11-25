package ke.writ;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.registries.ForgeRegistries;

final class Presets {
    static final Presets INSTANCE = new Presets();
    private static final ResourceLocation CHAOS_CONFIG = new ResourceLocation("writ", "chaos/config");
    record Variant(String id, String label, String translationKey, ItemStack costA, ItemStack costB, ItemStack result, int maxUses) { }
    record Preset(ItemStack costA, ItemStack costB, ItemStack result, int maxUses, int xp, float multiplier,
                  Set<ResourceLocation> professions, String rarity, int weight, Set<String> modes, String displayName, String displayTranslationKey, List<Variant> variants,
                  Set<String> sources, String defaultVariantLabel, String defaultVariantTranslationKey, boolean generalLoot, boolean dynamic, String defaultId) {
        Preset(ItemStack costA, ItemStack costB, ItemStack result, int maxUses, int xp, float multiplier,
               Set<ResourceLocation> professions, String rarity, int weight, Set<String> modes, String displayName, String displayTranslationKey, List<Variant> variants,
               Set<String> sources, String defaultVariantLabel, String defaultVariantTranslationKey) {
            this(costA, costB, result, maxUses, xp, multiplier, professions, rarity, weight, modes, displayName, displayTranslationKey, variants, sources, defaultVariantLabel, defaultVariantTranslationKey, false, false, null);
        }
    }
    record Snapshot(Map<ResourceLocation, Preset> presets, Set<ResourceLocation> invalidPresets, Map<String, PoolData.Pool> pools,
                    Map<String, LootRules.Source> sources, Map<String, LootRules.Source> bosses, List<LootRules.Candidate> candidates, ChaosSampler chaos, Set<ResourceLocation> selectorBlocked) {
        Map<String, Integer> rarities() { return poolWeights(pools); }
    }
    private volatile Snapshot snapshot = new Snapshot(Map.of(), Set.of(), Map.of(), Map.of(), Map.of(), List.of(), new ChaosSampler(List.of()), Set.of());
    private ObtainMode mode = ObtainMode.TRADE;
    private boolean modeReady, resourcesReady;
    private Presets() { }
    net.minecraft.server.packs.resources.PreparableReloadListener reloadListener(net.minecraftforge.common.crafting.conditions.ICondition.IContext context) {
        return new SimpleJsonResourceReloadListener(new Gson(), "writs") {
            @Override protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
                applySnapshot(objects, manager, context);
            }
        };
    }
    private synchronized void applySnapshot(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, net.minecraftforge.common.crafting.conditions.ICondition.IContext reloadContext) {
        List<String> globalErrors = new ArrayList<>(), presetErrors = new ArrayList<>(), presetWarnings = new ArrayList<>(), sourceErrors = new ArrayList<>();
        Map<String, PoolData.Pool> pools = PoolData.load(objects, globalErrors);
        Map<String, Integer> rarities = poolWeights(pools);
        Map<ResourceLocation, Preset> loaded = new TreeMap<>();
        Set<ResourceLocation> invalidPresets = new HashSet<>(), selectorBlocked = new HashSet<>();
        List<LootRules.Source> sources = new ArrayList<>(), bosses = new ArrayList<>();
        List<String> bossErrors = new ArrayList<>();
        Set<String> knownTables = new HashSet<>();
        Set<String> knownEntities = new HashSet<>();
        final Map<String, Integer> validRarities = rarities;
        objects.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation resource = entry.getKey(); String path = resource.getPath();
            if (path.startsWith("presets/")) {
                ResourceLocation id = new ResourceLocation(resource.getNamespace(), path.substring("presets/".length()));
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "preset");
                    var unknownFields = PresetChecks.unknownFields(json);
                    if (!unknownFields.isEmpty()) presetWarnings.add(id + ": unrecognized fields (ignored): " + unknownFields);
                    Preset p = dynamicPreset(parse(json), json, reloadContext);
                    try {
                        VariantNet.validateChoiceBudget(p);
                    } catch (IllegalArgumentException | io.netty.handler.codec.EncoderException e) {
                        selectorBlocked.add(id);
                        presetWarnings.add(id + ": selector unavailable; excluded from natural acquisition (" + e.getMessage() + ")");
                    }
                    loaded.put(id, p);
                    if (!validRarities.isEmpty() && !validRarities.containsKey(p.rarity())) presetWarnings.add(id + ": unknown pool " + p.rarity() + "; excluded from natural loot");
                    if (p.modes().isEmpty()) presetWarnings.add(id + ": empty loot_modes; excluded from natural loot");
                } catch (RuntimeException e) { invalidPresets.add(id); presetErrors.add(id + ": " + e.getMessage()); }
            } else if (path.startsWith("loot/sources/")) {
                List<String> tables = List.of();
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "source");
                    tables = SourceData.tables(json);
                    for (String table : tables) {
                        var id = new ResourceLocation(table);
                        var file = new ResourceLocation(id.getNamespace(), "loot_tables/" + id.getPath() + ".json");
                        if (manager.getResource(file).isPresent()) knownTables.add(table);
                    }
                    double chance = GsonHelper.getAsDouble(json, "contract_drop_chance");
                    Map<String, Integer> override = PoolData.overrides(json);
                    boolean inherit = PoolData.inheritGlobalWeights(json);
                    for (String table : tables) sources.add(new LootRules.Source(resource.toString(), table, chance, override, inherit));
                } catch (RuntimeException e) {
                    sourceErrors.add(resource + ": " + e.getMessage());
                    // Keep a parseable table claim for deterministic conflict disabling.
                    for (String table : tables) sources.add(new LootRules.Source(resource.toString(), table, Double.NaN, Map.of()));
                }
            } else if (path.startsWith("loot/bosses/")) {
                ResourceLocation entity = null;
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "boss");
                    if (json.has("entity")) entity = new ResourceLocation(GsonHelper.getAsString(json, "entity"));
                    SourceData.validateBossFields(json);
                    if (entity == null) throw new IllegalArgumentException("entity is required");
                    if (ForgeRegistries.ENTITY_TYPES.containsKey(entity)) knownEntities.add(entity.toString());
                    double chance = GsonHelper.getAsDouble(json, "contract_drop_chance");
                    Map<String, Integer> override = PoolData.overrides(json);
                    bosses.add(new LootRules.Source(resource.toString(), entity.toString(), chance, override, PoolData.inheritGlobalWeights(json)));
                } catch (RuntimeException e) {
                    bossErrors.add(resource + ": " + e.getMessage());
                    if (entity != null) bosses.add(new LootRules.Source(resource.toString(), entity.toString(), Double.NaN, Map.of()));
                }
            }
        });
        LootRules.Sources validated = LootRules.validateSources(sources, rarities.keySet(), knownTables);
        sourceErrors.addAll(validated.errors());
        LootRules.Sources validatedBosses = LootRules.validateBosses(bosses, rarities.keySet(), knownEntities);
        bossErrors.addAll(validatedBosses.errors());
        List<LootRules.Candidate> candidates = candidates(loaded, pools, mode, selectorBlocked);
        Map<String, LootRules.Source> enabled = !pools.isEmpty() ? validated.enabled() : Map.of();
        Map<String, LootRules.Source> enabledBosses = !pools.isEmpty() ? validatedBosses.enabled() : Map.of();
        var chaosWarnings = new ArrayList<String>();
        objects.keySet().stream().filter(id -> id.getPath().equals("chaos/config") && !id.getNamespace().equals(Writ.ID)).sorted().forEach(id -> chaosWarnings.add("ignored foreign config " + id));
        ChaosSampler chaos = buildChaos(objects.get(CHAOS_CONFIG), chaosWarnings, reloadContext);
        snapshot = new Snapshot(Map.copyOf(loaded), Set.copyOf(invalidPresets), Map.copyOf(pools), enabled, enabledBosses, candidates, chaos, Set.copyOf(selectorBlocked));
        resourcesReady = true;
        if (!globalErrors.isEmpty()) Writ.LOG.warn("Contract pool diagnostics: {}", globalErrors);
        if (pools.isEmpty()) Writ.LOG.error("Natural contract loot disabled: no valid pools");
        if (!presetErrors.isEmpty()) Writ.LOG.error("Discarded contract presets: {}", presetErrors);
        if (!presetWarnings.isEmpty()) Writ.LOG.warn("Contract preset diagnostics: {}", presetWarnings);
        if (!sourceErrors.isEmpty()) Writ.LOG.warn("Contract loot source diagnostics: {}", sourceErrors);
        if (!bossErrors.isEmpty()) Writ.LOG.warn("Contract boss diagnostics: {}", bossErrors);
        var disabledReferences = java.util.stream.Stream.concat(sources.stream(), bosses.stream())
                .flatMap(source -> source.overrides().entrySet().stream().filter(e -> e.getValue() > 0 && pools.containsKey(e.getKey()) && pools.get(e.getKey()).disabled()).map(e -> source.resourceId() + ": " + e.getKey()))
                .distinct().sorted().toList();
        if (!disabledReferences.isEmpty()) Writ.LOG.warn("Positive weights reference disabled pools: {}", disabledReferences);
        if (modeReady) logModeAndReachability(snapshot);
        if (!chaosWarnings.isEmpty()) Writ.LOG.warn("Chaos config diagnostics: {}", chaosWarnings);
        var unlocks = unlockCandidates(loaded, candidates);
        boolean generalUnlock = modeReady && generalUnlockReachable(loaded, pools, unlocks, Writ.generalLootChance());
        int sourceFiles = (int) objects.keySet().stream().filter(id -> id.getPath().startsWith("loot/sources/")).count();
        int bossFiles = (int) objects.keySet().stream().filter(id -> id.getPath().startsWith("loot/bosses/")).count();
        var stats = ReloadStats.calculate(pools, loaded.size(), candidates, enabled.values(), sourceFiles, enabledBosses.values(), bossFiles, mode, modeReady, unlocks, rarities, generalUnlock);
        Writ.LOG.info("Contract reload: pools={} (disabled={}), presets={} (natural={}, manual-only={}), sources={}/{} enabled, bosses={}/{} enabled, trade-chain={}",
                stats.pools(), stats.disabled(), stats.presets(), stats.natural(), stats.manualOnly(), stats.sourcesEnabled(), stats.sourcesTotal(), stats.bossesEnabled(), stats.bossesTotal(), stats.tradeChain());
        Writ.LOG.info("Chaos candidate pool sizes: payment={}, result={}", chaos.size(), chaos.resultSize());
        if (chaos.size() == 0 || chaos.resultSize() == 0) Writ.LOG.warn("Chaos candidate pool is empty; offers disabled");
        Writ.LOG.info("Loaded {} trade contract presets, {} loot sources and {} boss sources", loaded.size(), enabled.size(), enabledBosses.size());
    }
    synchronized void startMode(ObtainMode startMode) {
        if (modeReady && mode == startMode) return;
        mode = startMode;
        modeReady = true;
        if (resourcesReady) {
            Snapshot old = snapshot;
            snapshot = new Snapshot(old.presets(), old.invalidPresets(), old.pools(), old.sources(), old.bosses(), candidates(old.presets(), old.pools(), mode, old.selectorBlocked()), old.chaos(), old.selectorBlocked());
            logModeAndReachability(snapshot);
        }
    }
    synchronized void stopMode() { modeReady = false; resourcesReady = false; mode = ObtainMode.TRADE; }
    synchronized ObtainMode activeMode() { return mode; }
    synchronized boolean modeReady() { return modeReady; }
    private static ChaosSampler buildChaos(JsonElement config, List<String> warnings, net.minecraftforge.common.crafting.conditions.ICondition.IContext context) {
        var all = ForgeRegistries.ITEMS.getKeys().stream().filter(id -> { Item item = ForgeRegistries.ITEMS.getValue(id); return item != null && !new ItemStack(item).isEmpty(); }).map(ResourceLocation::toString).sorted().toList();
        List<String> exclude = List.of(), payment = null, result = null;
        try {
            JsonObject json = config == null ? new JsonObject() : GsonHelper.convertToJsonObject(config, "chaos config");
            exclude = json.has("exclude") ? chaosEntries(json, "exclude") : List.of();
            payment = json.has("payment_allow") ? chaosEntries(json, "payment_allow") : null;
            result = json.has("result_allow") ? chaosEntries(json, "result_allow") : null;
        } catch (RuntimeException e) {
            warnings.add("invalid chaos config; both pools disabled: " + e.getMessage());
            payment = List.of(); result = List.of();
        }
        var pools = ChaosPools.build(all, exclude, payment, result, entry -> {
            boolean tag = entry.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? entry.substring(1) : entry);
            if (id == null) return null;
            if (!tag) return ForgeRegistries.ITEMS.containsKey(id) ? List.of(id.toString()) : null;
            var holders = context.getAllTags(net.minecraft.core.registries.Registries.ITEM).get(id);
            return holders == null ? null : holders.stream().map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString()).toList();
        });
        warnings.addAll(pools.warnings());
        return new ChaosSampler(pools.payment(), pools.result());
    }
    private static List<String> chaosEntries(JsonObject json, String key) {
        var values = new ArrayList<String>();
        for (JsonElement value : GsonHelper.getAsJsonArray(json, key)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must contain strings");
            values.add(value.getAsString());
        }
        return values;
    }
    private static List<LootRules.Candidate> candidates(Map<ResourceLocation, Preset> presets, Map<String, PoolData.Pool> pools, ObtainMode mode,
                                                        Set<ResourceLocation> selectorBlocked) {
        return presets.entrySet().stream().filter(e -> !selectorBlocked.contains(e.getKey()) && pools.containsKey(e.getValue().rarity()) && !pools.get(e.getValue().rarity()).disabled() && e.getValue().weight() > 0 &&
                (!e.getValue().dynamic() || e.getValue().defaultId() != null) && LootRules.naturallyEligible(mode, e.getValue().modes(), hasChaosResult(e.getValue())))
                .map(e -> new LootRules.Candidate(e.getKey().toString(), e.getValue().rarity(), e.getValue().weight(), e.getValue().sources())).toList();
    }
    private static boolean hasChaosResult(Preset p) {
        return p.result().is(Writ.CHAOS.get()) || p.variants().stream().anyMatch(v -> v.result().is(Writ.CHAOS.get()));
    }
    static List<LootRules.Candidate> unlockCandidates(Map<ResourceLocation, Preset> presets, List<LootRules.Candidate> candidates) {
        return candidates.stream().filter(c -> {
            Preset p = presets.get(ResourceLocation.tryParse(c.id()));
            return p != null && p.modes().contains("TRADE") && hasChaosResult(p);
        }).toList();
    }
    static boolean generalUnlockReachable(Map<ResourceLocation, Preset> presets, Map<String, PoolData.Pool> pools,
                                          List<LootRules.Candidate> unlocks, double chance) {
        return chance > 0 && unlocks.stream().anyMatch(c -> {
            Preset p = presets.get(ResourceLocation.tryParse(c.id()));
            PoolData.Pool pool = pools.get(c.rarity());
            return p != null && p.generalLoot() && c.weight() > 0 && pool != null && !pool.disabled()
                    && "rarity".equals(pool.kind()) && pool.weight() > 0;
        });
    }
    static List<LootRules.Candidate> randomCandidates(Snapshot snapshot, ResourceLocation profession) {
        return randomCandidates(snapshot, Set.of(profession));
    }
    Optional<String> randomPreset(Snapshot snapshot, ResourceLocation profession, RandomSource random) {
        List<LootRules.Candidate> candidates = randomCandidates(snapshot, profession);
        if (candidates.isEmpty()) return Optional.empty();
        return LootRules.select(candidates, snapshot.rarities(), Map.of(), bound -> uniform(random, bound));
    }
    static boolean hasAnyUsableRandomCandidate(Snapshot snapshot) {
        return !randomCandidates(snapshot, validProfessions()).isEmpty();
    }
    static Set<ResourceLocation> validProfessions() {
        Set<ResourceLocation> professions = new HashSet<>(BuiltInRegistries.VILLAGER_PROFESSION.keySet());
        professions.remove(BuiltInRegistries.VILLAGER_PROFESSION.getKey(VillagerProfession.NONE));
        professions.remove(BuiltInRegistries.VILLAGER_PROFESSION.getKey(VillagerProfession.NITWIT));
        return Set.copyOf(professions);
    }
    private static List<LootRules.Candidate> randomCandidates(Snapshot snapshot, Set<ResourceLocation> professions) {
        Map<String, Integer> rarities = snapshot.rarities();
        return snapshot.candidates().stream().filter(candidate -> {
            ResourceLocation id = ResourceLocation.tryParse(candidate.id());
            Preset p = id == null ? null : snapshot.presets().get(id);
            if (p == null) return false;
            PoolData.Pool pool = snapshot.pools().get(p.rarity());
            if (pool == null || !"rarity".equals(pool.kind()) || pool.disabled() || pool.weight() <= 0) return false;
            if (!p.professions().isEmpty() && Collections.disjoint(p.professions(), professions)) return false;
            if (isWritResult(p.result()) || p.variants().stream().anyMatch(v -> isWritResult(v.result()))) return false;
            return (p.generalLoot() && Writ.generalLootChance() > 0) || p.sources().isEmpty() || snapshot.sources().values().stream().anyMatch(source -> LootRules.reachableAt(candidate, rarities, source));
        }).sorted(Comparator.comparing(LootRules.Candidate::id)).toList();
    }
    private static boolean isWritResult(ItemStack result) {
        return result.is(Writ.NORMAL.get()) || result.is(Writ.RANDOM.get()) || result.is(Writ.CHAOS.get());
    }
    private void logModeAndReachability(Snapshot current) {
        Writ.LOG.info("Chaos contract obtain mode: {} (locked until server restart)", mode);
        if (mode == ObtainMode.CRAFT_9) {
            var excluded = current.presets().entrySet().stream()
                    .filter(e -> e.getValue().modes().contains("CRAFT_9") && e.getValue().weight() > 0 && hasChaosResult(e.getValue()))
                    .map(e -> e.getKey().toString()).sorted().toList();
            if (!excluded.isEmpty()) Writ.LOG.warn("CRAFT_9 excludes presets with chaos writ results from natural loot: {}", excluded);
        }
        var unlocks = unlockCandidates(current.presets(), current.candidates());
        var sources = new ArrayList<>(current.sources().values());
        sources.addAll(current.bosses().values());
        var emptySources = sources.stream().filter(source -> LootRules.select(current.candidates(), current.rarities(), source, bound -> 0).isEmpty())
                .map(LootRules.Source::resourceId).distinct().sorted().toList();
        if (!emptySources.isEmpty()) Writ.LOG.warn("Contract sources with no positive-weight candidates: {}", emptySources);
        var keys = sources.stream().map(LootRules.Source::key).collect(java.util.stream.Collectors.toSet());
        keys.add("writ:general");
        var unknownReferences = current.presets().entrySet().stream().flatMap(e -> e.getValue().sources().stream().filter(k -> !keys.contains(k)).map(k -> e.getKey() + " -> " + k)).sorted().toList();
        if (!unknownReferences.isEmpty()) Writ.LOG.warn("Preset sources reference no enabled source: {}", unknownReferences);
        var specialOnly = current.candidates().stream().filter(candidate -> {
            Preset p = current.presets().get(ResourceLocation.tryParse(candidate.id()));
            PoolData.Pool pool = current.pools().get(candidate.rarity());
            return p == null || !p.generalLoot() || pool == null || !"rarity".equals(pool.kind()) || pool.weight() <= 0 || Writ.generalLootChance() <= 0;
        }).toList();
        var unreachable = LootRules.unreachable(specialOnly, current.rarities(), sources);
        if (!unreachable.isEmpty()) Writ.LOG.warn("Natural contract presets unreachable from every enabled source: {}", unreachable);
        boolean generalUnlock = generalUnlockReachable(current.presets(), current.pools(), unlocks, Writ.generalLootChance());
        ObtainMode.diagnostics(mode, unlocks, current.rarities(), sources, generalUnlock).forEach(Writ.LOG::warn);
    }
    private static Map<String, Integer> poolWeights(Map<String, PoolData.Pool> pools) {
        Map<String, Integer> values = new TreeMap<>(); pools.forEach((id, pool) -> values.put(id, pool.weight())); return Map.copyOf(values);
    }
    record OfferResult(MerchantOffer offer, String failure) { }
    MerchantOffer offer(ItemStack contract, Villager villager) { return resolveOffer(contract, villager).offer(); }
    OfferResult resolveOffer(ItemStack contract, Villager villager) {
        var tag = contract.getTag();
        if (tag != null && tag.contains("contract_version") && (!tag.contains("contract_version", 3) || tag.getInt("contract_version") != PresetChecks.CONTRACT_VERSION)) return new OfferResult(null, "version_unsupported");
        if (tag == null || !tag.contains("preset_id")) return new OfferResult(null, "preset_missing");
        if (!tag.contains("preset_id", 8)) return new OfferResult(null, "preset_invalid");
        if (tag.getString("preset_id").isEmpty()) return new OfferResult(null, "preset_missing");
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("preset_id"));
        if (id == null) return new OfferResult(null, "preset_invalid");
        Snapshot current = snapshot;
        Preset p = current.presets().get(id);
        if (p == null) return new OfferResult(null, current.invalidPresets().contains(id) ? "preset_invalid" : "preset_missing");
        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        if (!p.professions().isEmpty() && !p.professions().contains(profession)) return new OfferResult(null, "profession_mismatch");
        if (p.dynamic() && p.defaultId() == null) return new OfferResult(null, "variant.no_choices");
        int selection = selection(p, tag);
        if (selection < 0) return new OfferResult(null, "variant_invalid");
        Variant v = selection == 0 ? null : p.variants().get(selection - 1);
        return new OfferResult(offerFor(p, v), null);
    }
    static MerchantOffer offerFor(Preset p, Variant v) {
        return new MerchantOffer(
            (v == null ? p.costA() : v.costA()).copy(),
            (v == null ? p.costB() : v.costB()).copy(),
            (v == null ? p.result() : v.result()).copy(),
            v == null ? p.maxUses() : v.maxUses(),
            p.xp(), p.multiplier());
    }
    ItemStack generate(String table, RandomSource random) {
        Snapshot current = snapshot;
        LootRules.Source source = current.sources().get(table);
        // One independent chance per call; the chest modifier calls this twice.
        if (source == null || random.nextDouble() >= source.chance()) return ItemStack.EMPTY;
        return samplePreset(current, source, random).map(id -> contractFor(current, id)).orElse(ItemStack.EMPTY);
    }
    ItemStack generateGeneral(RandomSource random) {
        Snapshot current = snapshot;
        if (random.nextDouble() >= Writ.generalLootChance()) return ItemStack.EMPTY;
        var candidates = current.candidates().stream().filter(candidate -> {
            Preset preset = current.presets().get(ResourceLocation.tryParse(candidate.id()));
            PoolData.Pool pool = current.pools().get(candidate.rarity());
            return preset != null && preset.generalLoot() && pool != null && "rarity".equals(pool.kind());
        }).toList();
        return LootRules.select(candidates, current.rarities(), Map.of(), bound -> uniform(random, bound))
                .map(id -> contractFor(current, id)).orElse(ItemStack.EMPTY);
    }
    Snapshot currentSnapshot() { return snapshot; }
    Optional<String> samplePreset(Snapshot current, LootRules.Source source, RandomSource random) {
        return LootRules.select(current.candidates(), current.rarities(), source, bound -> uniform(random, bound));
    }
    ItemStack contractFor(Snapshot current, String id) {
        Preset p = current.presets().get(ResourceLocation.tryParse(id));
        if (p == null || (p.dynamic() && p.defaultId() == null)) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(Writ.NORMAL.get());
        CompoundTag tag = stack.getOrCreateTag(); tag.putString("preset_id", id); tag.putInt("contract_version", PresetChecks.CONTRACT_VERSION);
        if (p.dynamic()) tag.putString("selected_variant", p.defaultId());
        writeSnapshot(current, p, tag, 0);
        return stack;
    }
    private static void writeSnapshot(Snapshot current, Preset p, CompoundTag tag, int selection) {
        Variant variant = selection == 0 ? null : p.variants().get(selection - 1);
        ItemStack a = variant == null ? p.costA() : variant.costA(), b = variant == null ? p.costB() : variant.costB(), result = variant == null ? p.result() : variant.result();
        boolean nbt = a.hasTag() || b.hasTag() || result.hasTag();
        CompoundTag display = new CompoundTag(); display.putInt("format", nbt ? 3 : 2); display.putString("rarity", new ResourceLocation(p.rarity()).getPath());
        if (p.displayName() != null) display.putString("preset_name", p.displayName());
        if (p.displayTranslationKey() != null) display.putString("preset_translation_key", p.displayTranslationKey());
        PoolData.Pool pool = current.pools().get(p.rarity());
        if (pool != null) {
            display.putString("pool_id", pool.id()); display.putString("pool_kind", pool.kind()); display.putString("pool_label", pool.label());
            if (pool.translationKey() != null) display.putString("pool_translation_key", pool.translationKey());
        }
        ListTag professions = new ListTag(); p.professions().stream().map(ResourceLocation::toString).sorted().forEach(value -> professions.add(StringTag.valueOf(value)));
        display.put("professions", professions);
        display.put("cost_a", snapshotItem(a, nbt));
        if (!b.isEmpty()) display.put("cost_b", snapshotItem(b, nbt));
        display.put("result", snapshotItem(result, nbt));
        if (!p.variants().isEmpty()) {
            display.putInt("variant_index", selection + 1); display.putInt("variant_total", p.variants().size() + 1);
            // Display only: a key wins over a label; with neither, the client names the selection after its result item.
            String label = variant == null ? p.defaultVariantLabel() : variant.label(), key = variant == null ? p.defaultVariantTranslationKey() : variant.translationKey();
            if (variant != null) display.putString("variant_id", variant.id());
            if (label != null) display.putString("variant_label", label);
            if (key != null) display.putString("variant_translation_key", key);
        }
        tag.put("display_snapshot", display);
    }
    private static int selection(Preset p, CompoundTag tag) {
        if (tag == null || !tag.contains("selected_variant")) return p.dynamic() ? -1 : 0;
        if (!tag.contains("selected_variant", 8)) return -1;
        String selected = tag.getString("selected_variant");
        if (p.dynamic() && selected.equals(p.defaultId())) return 0;
        for (int i = 0; i < p.variants().size(); i++) if (p.variants().get(i).id().equals(selected)) return i + 1;
        return -1;
    }
    /** {@code name} is the player-facing name of the new selection; null on failure, on a silent no-op and when the writ has no variants left. */
    record SwitchResult(String failure, boolean reset, Component name, int index, int total) { }
    /** Names a selection: translation key (falling back to the label, then the ID), else the plain label, else the result item's own name. */
    static Component selectionName(Preset p, Variant v) {
        String label = v == null ? p.defaultVariantLabel() : v.label(), key = v == null ? p.defaultVariantTranslationKey() : v.translationKey();
        if (key != null) return Component.translatableWithFallback(key, label != null ? label : v == null ? "default" : v.id());
        if (label != null) return Component.literal(label);
        return (v == null ? p.result() : v.result()).getHoverName();
    }
    /** Professions the writ's current preset accepts, sorted; empty when unrestricted or unknown. Used only to word the mismatch message. */
    List<ResourceLocation> professionsOf(ItemStack contract) {
        var tag = contract.getTag();
        ResourceLocation id = tag == null ? null : ResourceLocation.tryParse(tag.getString("preset_id"));
        Preset p = id == null ? null : snapshot.presets().get(id);
        return p == null ? List.of() : p.professions().stream().sorted().toList();
    }
    static boolean selectVariant(Snapshot current, Preset preset, ItemStack stack, String id) {
        int index;
        if (id.equals(preset.dynamic() ? preset.defaultId() : "")) {
            index = 0;
        } else {
            index = -1;
            for (int i = 0; i < preset.variants().size(); i++) {
                if (preset.variants().get(i).id().equals(id)) {
                    index = i + 1;
                    break;
                }
            }
        }
        if (index < 0) return false;
        writeSelection(current, preset, stack.getOrCreateTag(), index);
        return true;
    }

    private static void writeSelection(Snapshot current, Preset preset, CompoundTag tag, int index) {
        if (index > 0) tag.putString("selected_variant", preset.variants().get(index - 1).id());
        else if (preset.dynamic()) tag.putString("selected_variant", preset.defaultId());
        else tag.remove("selected_variant");
        writeSnapshot(current, preset, tag, index);
    }

    SwitchResult switchVariant(ItemStack stack, boolean backwards) {
        CompoundTag tag = stack.getTag();
        if (!stack.is(Writ.NORMAL.get()) || tag == null || !tag.contains("preset_id", 8)) return new SwitchResult("preset_missing", false, null, 0, 0);
        if (tag.contains("contract_version") && (!tag.contains("contract_version", 3) || tag.getInt("contract_version") != PresetChecks.CONTRACT_VERSION)) return new SwitchResult("version_unsupported", false, null, 0, 0);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("preset_id"));
        Snapshot current = snapshot; Preset p = id == null ? null : current.presets().get(id);
        if (p == null) return new SwitchResult("preset_invalid", false, null, 0, 0);
        if (p.dynamic() && p.defaultId() == null) return new SwitchResult("variant_invalid", false, null, 0, 0);
        if (p.variants().isEmpty() && !p.dynamic()) {
            if (!tag.contains("selected_variant")) return new SwitchResult(null, false, null, 0, 0);
            tag.remove("selected_variant"); writeSnapshot(current, p, tag, 0);
            return new SwitchResult(null, true, null, 1, 1);
        }
        int previous = selection(p, tag); boolean reset = previous < 0;
        if (p.dynamic() && p.variants().isEmpty() && !reset) return new SwitchResult(null, false, null, 0, 0);
        int next = reset ? 0 : Math.floorMod(previous + (backwards ? -1 : 1), p.variants().size() + 1);
        writeSelection(current, p, tag, next);
        Variant v = next == 0 ? null : p.variants().get(next - 1);
        return new SwitchResult(null, reset, selectionName(p, v), next + 1, p.variants().size() + 1);
    }
    private static CompoundTag snapshotItem(ItemStack stack, boolean full) {
        if (full) return stack.save(new CompoundTag());
        CompoundTag result = new CompoundTag(); result.putString("id", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString()); result.putInt("count", stack.getCount()); return result;
    }
    private static long uniform(RandomSource random, long bound) {
        long r = random.nextLong() >>> 1, value = r % bound;
        while (r - value + (bound - 1) < 0) { r = random.nextLong() >>> 1; value = r % bound; }
        return value;
    }
    static Preset parse(JsonObject json) {
        String rarity = PoolData.presetPool(json); int weight = GsonHelper.getAsInt(json, "weight"); PresetChecks.weight(weight);
        Set<String> modes = new HashSet<>();
        if (json.has("loot_modes")) for (JsonElement e : GsonHelper.getAsJsonArray(json, "loot_modes")) { String mode = e.getAsString(); if (!Set.of("TRADE", "CRAFT_9").contains(mode)) throw new IllegalArgumentException("invalid loot mode " + mode); modes.add(mode); }
        else modes.addAll(Set.of("TRADE", "CRAFT_9"));
        Set<ResourceLocation> professions = new HashSet<>();
        if (json.has("allowed_professions")) for (JsonElement e : GsonHelper.getAsJsonArray(json, "allowed_professions")) {
            ResourceLocation id = new ResourceLocation(e.getAsString());
            if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) throw new IllegalArgumentException("unknown profession " + id);
            professions.add(id);
        }
        JsonObject trade = GsonHelper.getAsJsonObject(json, "trade");
        ItemStack a = item(GsonHelper.getAsJsonObject(trade, "cost_a"));
        ItemStack b = trade.has("cost_b") && !trade.get("cost_b").isJsonNull() ? item(GsonHelper.getAsJsonObject(trade, "cost_b")) : ItemStack.EMPTY;
        ItemStack result = item(GsonHelper.getAsJsonObject(trade, "result"));
        int uses = GsonHelper.getAsInt(trade, "max_uses"), xp = GsonHelper.getAsInt(trade, "villager_xp");
        float multiplier = GsonHelper.getAsFloat(trade, "price_multiplier"); PresetChecks.trade(uses, xp, multiplier);
        String displayName = json.has("display_name") ? GsonHelper.getAsString(json, "display_name") : null;
        PresetChecks.displayName(displayName);
        String displayKey = json.has("display_translation_key") ? GsonHelper.getAsString(json, "display_translation_key") : null;
        String defaultLabel = json.has("default_variant_label") ? requiredString(json, "default_variant_label") : null;
        String defaultKey = json.has("default_variant_translation_key") ? requiredString(json, "default_variant_translation_key") : null;
        List<Variant> variants = parseVariants(json, a, b, result, uses, xp, multiplier);
        if (!variants.isEmpty() && result.is(Writ.CHAOS.get())) throw new IllegalArgumentException("variants: chaos result is not supported");
        return new Preset(a, b, result, uses, xp, multiplier, Set.copyOf(professions), rarity, weight, Set.copyOf(modes), displayName, displayKey, variants, parseSources(json), defaultLabel, defaultKey, GsonHelper.getAsBoolean(json, "general_loot", false), false, null);
    }
    private static Preset dynamicPreset(Preset p, JsonObject json, net.minecraftforge.common.crafting.conditions.ICondition.IContext context) {
        if (!json.has("dynamic_result")) return p;
        if (!p.variants().isEmpty()) throw new IllegalArgumentException("dynamic_result cannot be combined with variants");
        JsonObject dynamic = GsonHelper.getAsJsonObject(json, "dynamic_result");
        ResourceLocation source = new ResourceLocation(GsonHelper.getAsString(dynamic, "tag"));
        ResourceLocation excluded = dynamic.has("exclude_tag") ? new ResourceLocation(requiredString(dynamic, "exclude_tag")) : null;
        var tags = context.getAllTags(net.minecraft.core.registries.Registries.ITEM);
        var sourceItems = tags.get(source);
        if (sourceItems == null) throw new IllegalArgumentException("missing dynamic result tag " + source);
        Set<Item> deny = new HashSet<>();
        List<ResourceLocation> exclusionTags = new ArrayList<>();
        exclusionTags.add(new ResourceLocation("writ", "contract_exclusions"));
        if (excluded != null && !exclusionTags.contains(excluded)) exclusionTags.add(excluded);
        for (ResourceLocation id : exclusionTags) {
            var entries = tags.get(id);
            if (entries == null) throw new IllegalArgumentException("missing dynamic exclusion tag " + id);
            entries.forEach(holder -> deny.add(holder.value()));
        }
        var items = sourceItems.stream().map(holder -> holder.value()).filter(item -> {
                    ItemStack result = dynamicResult(item, p.result());
                    return !deny.contains(item) && !result.isEmpty() && result.getCount() <= result.getMaxStackSize();
                })
                .distinct().sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
        ItemStack first = items.isEmpty() ? p.result() : dynamicResult(items.get(0), p.result());
        String firstId = items.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(items.get(0)).toString();
        List<Variant> variants = items.stream().skip(1).map(item -> new Variant(BuiltInRegistries.ITEM.getKey(item).toString(), null, null,
                p.costA().copy(), p.costB().copy(), dynamicResult(item, p.result()), p.maxUses())).toList();
        return new Preset(p.costA(), p.costB(), first, p.maxUses(), p.xp(), p.multiplier(), p.professions(), p.rarity(), p.weight(), p.modes(),
                p.displayName(), p.displayTranslationKey(), variants, p.sources(), p.defaultVariantLabel(), p.defaultVariantTranslationKey(), p.generalLoot(), true, firstId);
    }
    private static ItemStack dynamicResult(Item item, ItemStack template) {
        ItemStack stack = new ItemStack(item, template.getCount());
        if (template.hasTag()) stack.setTag(template.getTag().copy());
        return stack;
    }
    /** Optional {@code sources}: source file IDs (bare names default to {@code writ:}); absent means any source weighting the pool. */
    static Set<String> parseSources(JsonObject json) {
        if (!json.has("sources")) return Set.of();
        Set<String> values = new TreeSet<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "sources")) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("sources must contain strings");
            String raw = e.getAsString(); ResourceLocation id = ResourceLocation.tryParse(raw.contains(":") ? raw : "writ:" + raw);
            if (raw.isEmpty() || id == null) throw new IllegalArgumentException("invalid source ID " + raw);
            if (!values.add(id.toString())) throw new IllegalArgumentException("duplicate source " + id);
        }
        if (values.isEmpty()) throw new IllegalArgumentException("sources must not be empty; omit it to allow any source");
        return Set.copyOf(values);
    }
    private static List<Variant> parseVariants(JsonObject json, ItemStack a, ItemStack b, ItemStack result, int parentUses, int xp, float multiplier) {
        if (!json.has("variants")) return List.of();
        JsonArray entries = GsonHelper.getAsJsonArray(json, "variants");
        if (entries.size() > 64) throw new IllegalArgumentException("variants: maximum 64");
        List<Variant> values = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            try {
                JsonObject v = GsonHelper.convertToJsonObject(entries.get(i), "variant");
                for (String key : v.keySet()) if (!Set.of("id", "label", "translation_key", "enabled", "max_uses", "trade").contains(key)) throw new IllegalArgumentException(key + ": unknown field");
                String id = requiredString(v, "id");
                if (id.length() > 128 || !id.matches("[a-z0-9_]+") || id.equals("default") || !ids.add(id)) throw new IllegalArgumentException("id: invalid or duplicate " + id);
                boolean enabled = true;
                if (v.has("enabled")) {
                    JsonElement flag = v.get("enabled");
                    if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("enabled: expected boolean");
                    enabled = flag.getAsBoolean();
                }
                String label = v.has("label") ? requiredString(v, "label") : null;
                String key = v.has("translation_key") ? requiredString(v, "translation_key") : null;
                int uses = parentUses;
                if (v.has("max_uses")) {
                    JsonElement n = v.get("max_uses");
                    if (!n.isJsonPrimitive() || !n.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("max_uses: expected positive integer");
                    try { uses = n.getAsBigDecimal().intValueExact(); } catch (ArithmeticException e) { throw new IllegalArgumentException("max_uses: expected positive integer", e); }
                    if (uses <= 0) throw new IllegalArgumentException("max_uses: expected positive integer");
                    PresetChecks.trade(uses, xp, multiplier);
                }
                JsonObject trade = v.has("trade") ? GsonHelper.getAsJsonObject(v, "trade") : new JsonObject();
                for (String field : trade.keySet()) if (!Set.of("cost_a", "cost_b", "result").contains(field)) throw new IllegalArgumentException("trade." + field + ": unknown field");
                ItemStack va = trade.has("cost_a") ? variantItem(trade, "cost_a") : a.copy();
                ItemStack vb = trade.has("cost_b") ? (trade.get("cost_b").isJsonNull() ? ItemStack.EMPTY : variantItem(trade, "cost_b")) : b.copy();
                ItemStack vr = trade.has("result") ? variantItem(trade, "result") : result.copy();
                if (enabled) values.add(new Variant(id, label, key, va, vb, vr, uses));
            } catch (RuntimeException e) { throw new IllegalArgumentException("variants[" + i + "]." + e.getMessage(), e); }
        }
        return List.copyOf(values);
    }
    private static ItemStack variantItem(JsonObject trade, String name) {
        try {
            JsonObject value = GsonHelper.getAsJsonObject(trade, name);
            JsonElement count = value.get("count");
            if (count == null || !count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("count must be an integer");
            try { count.getAsBigDecimal().intValueExact(); } catch (ArithmeticException e) { throw new IllegalArgumentException("count must be an integer", e); }
            return item(value);
        }
        catch (RuntimeException e) { throw new IllegalArgumentException("trade." + name + ": " + e.getMessage(), e); }
    }
    private static String requiredString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) throw new IllegalArgumentException(name + ": expected nonempty string");
        return value.getAsString();
    }
    private static ItemStack item(JsonObject json) {
        ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(json, "item")); Item item = ForgeRegistries.ITEMS.getValue(id); int count = GsonHelper.getAsInt(json, "count");
        PresetChecks.item(id.toString(), ForgeRegistries.ITEMS.containsKey(id) && item != null, item == null || new ItemStack(item).isEmpty(), count, item == null ? 0 : item.getMaxStackSize());
        ItemStack stack = new ItemStack(item, count);
        if (json.has("nbt")) stack.setTag(ItemNbt.parse(json.get("nbt")));
        return stack;
    }
}
