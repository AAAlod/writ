package ke.writ;

import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;

@Mod(Writ.ID)
public final class Writ {
    public static final String ID = "writ";
    static final int CONFIG_SCHEMA_VERSION = 1;
    static final Logger LOG = LogUtils.getLogger();
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ID);
    private static final RegistryObject<Codec<? extends IGlobalLootModifier>> CHEST_LOOT = LOOT_MODIFIERS.register("chest_loot", () -> ChestLoot.CODEC);
    private static final RegistryObject<Codec<? extends IGlobalLootModifier>> HERO_GIFTS = LOOT_MODIFIERS.register("hero_gifts", () -> HeroGifts.CODEC);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, ID);
    public static final RegistryObject<Item> NORMAL = ITEMS.register("trade_writ", () -> new WritItem(new Item.Properties()));
    public static final RegistryObject<Item> RANDOM = ITEMS.register("random_writ", () -> new WritItem(new Item.Properties()));
    public static final RegistryObject<Item> CHAOS = ITEMS.register("chaos_writ", () -> new WritItem(new Item.Properties()));
    private static final RegistryObject<CreativeModeTab> TAB = TABS.register("writs", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.writ")).withTabsBefore(CreativeModeTabs.SPAWN_EGGS).icon(() -> new ItemStack(NORMAL.get())).displayItems((p, out) -> { out.accept(NORMAL.get()); out.accept(RANDOM.get()); out.accept(CHAOS.get()); }).build());
    private static final ForgeConfigSpec CONFIG;
    private static ObtainMode configuredMode = ObtainMode.TRADE;
    private static final ForgeConfigSpec.IntValue NORMAL_LIMIT, CHAOS_LIMIT, TOTAL_LIMIT;
    private static final ForgeConfigSpec.ConfigValue<String> OBTAIN_MODE;
    private static final ForgeConfigSpec.IntValue CHAOS_USES, CHAOS_XP;
    private static final ForgeConfigSpec.DoubleValue CHAOS_MULTIPLIER;
    private static final ForgeConfigSpec.BooleanValue HERO_GIFTS_ENABLED;
    private static final ForgeConfigSpec.IntValue HERO_GIFTS_RANDOM, HERO_GIFTS_CHAOS;
    record ChaosParams(int uses, int xp, float multiplier) { }
    private static volatile ChaosParams chaosParameters = new ChaosParams(12, 0, 0.05f);
    static ChaosParams chaosParameters() { return chaosParameters; }
    record HeroGiftParams(boolean enabled, int randomPercent, int chaosPercent, boolean valid) { }
    private static volatile HeroGiftParams heroGiftParams = new HeroGiftParams(false, 0, 0, false);
    static HeroGiftParams heroGiftParameters() { return heroGiftParams; }
    static void refreshHeroGiftParameters() {
        boolean enabled = HERO_GIFTS_ENABLED.get();
        int random = HERO_GIFTS_RANDOM.get(), chaos = HERO_GIFTS_CHAOS.get();
        boolean valid = random >= 0 && chaos >= 0 && (long) random + chaos <= 100;
        if (!valid) LOG.error("Invalid hero_gifts percentages: random_percent={}, chaos_percent={}; hero gift replacement disabled", random, chaos);
        heroGiftParams = new HeroGiftParams(enabled, random, chaos, valid);
    }
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Internal configuration schema version. Do not edit; used by Writ's recovery logic.")
                .defineInRange("schema_version", CONFIG_SCHEMA_VERSION, 0, Integer.MAX_VALUE);
        NORMAL_LIMIT = b.comment("Maximum trade and random writs one villager can accept together. 0 disables both.",
                        "Checked each time a writ is used; lowering it never removes trades already added.")
                .defineInRange("max_normal_added_per_villager", 8, 0, 1000000);
        CHAOS_LIMIT = b.comment("Maximum chaos writs one villager can accept. 0 disables chaos writs.",
                        "Checked each time a writ is used; lowering it never removes trades already added.")
                .defineInRange("max_chaos_added_per_villager", 3, 0, 1000000);
        TOTAL_LIMIT = b.comment("Maximum trade, random and chaos writs combined that one villager can accept; applies on top of the two limits above.",
                        "Trade and random writs share normalAppliedCount; chaos writs use chaosAppliedCount.")
                .defineInRange("max_total_added_per_villager", 10, 0, 2000000);
        b.push("chaos_writ");
        OBTAIN_MODE = b.comment("How chaos writs are obtained:",
                        "  TRADE   - Chaos Writ Unlock writs drop naturally; villagers that sign one sell chaos writs. The crafting recipe is removed.",
                        "  CRAFT_9 - Nine trade writs craft one chaos writ. Writs that sell chaos writs no longer drop naturally.",
                        "Village hero gifts are independent of these two chaos writ sources.",
                        "Any other value falls back to TRADE. Read at game or dedicated server process startup; restart it after changing this setting.")
                .define("obtain_mode", "TRADE", value -> value != null);
        CHAOS_USES = b.comment("Uses of each new chaos trade before the villager must restock. Read at game or dedicated server process startup; existing trades keep their values.")
                .defineInRange("max_uses", 12, 1, Integer.MAX_VALUE);
        CHAOS_XP = b.comment("Villager experience granted per chaos trade. Read at game or dedicated server process startup; existing trades keep their values.")
                .defineInRange("villager_xp", 0, 0, Integer.MAX_VALUE);
        CHAOS_MULTIPLIER = b.comment("Vanilla price multiplier of new chaos trades: how strongly demand and reputation change the price.",
                        "Read at game or dedicated server process startup; existing trades keep their values.")
                .defineInRange("price_multiplier", 0.05, 0.0, (double) Float.MAX_VALUE);
        b.pop();
        b.push("hero_gifts");
        HERO_GIFTS_ENABLED = b.comment(
                "Whether village hero gifts can be replaced by writs.",
                "Read at game or dedicated server process startup; existing worlds keep their behaviour until restart.")
            .define("enabled", true);
        HERO_GIFTS_RANDOM = b.comment(
                "Chance (0-100) that a hero gift becomes one random writ.",
                "random_percent + chaos_percent must not exceed 100; otherwise hero gift replacement is disabled.")
            .defineInRange("random_percent", 8, 0, 100);
        HERO_GIFTS_CHAOS = b.comment(
                "Chance (0-100) that a hero gift becomes one chaos writ.",
                "The remaining chance keeps the vanilla gift.")
            .defineInRange("chaos_percent", 2, 0, 100);
        b.pop();
        CONFIG = b.build();
    }
    public Writ() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("writ-common.toml");
        ConfigFile.LoadResult loaded = ConfigFile.load(CONFIG, configPath, CONFIG_SCHEMA_VERSION);
        switch (loaded.status()) {
            case INCOMPATIBLE -> throw new IllegalStateException("Writ config " + configPath + " is newer than this Writ version: " + loaded.message());
            case FAILED -> throw new IllegalStateException("Cannot load Writ config " + configPath + ": " + loaded.message());
            case CREATED, UPGRADED, CORRECTED -> LOG.info("Writ config {}: {} ({})", configPath, loaded.status(), loaded.message());
            case SAVE_FAILED, BACKUP_FAILED, USING_BACKUP -> LOG.warn("Writ config {}: {} ({})", configPath, loaded.status(), loaded.message());
            case LOADED -> LOG.debug("Writ config {}: {}", configPath, loaded.message());
        }
        applyConfig();
        VariantNet.register();
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus); TABS.register(bus); LOOT_MODIFIERS.register(bus);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new BossDrops());
        MinecraftForge.EVENT_BUS.register(new ContainerPayments());
    }
    private static void applyConfig() {
        float multiplier = CHAOS_MULTIPLIER.get().floatValue();
        PresetChecks.trade(CHAOS_USES.get(), CHAOS_XP.get(), multiplier);
        chaosParameters = new ChaosParams(CHAOS_USES.get(), CHAOS_XP.get(), multiplier);
        refreshHeroGiftParameters();
        Object raw = OBTAIN_MODE.get();
        ObtainMode mode = ObtainMode.parse(raw);
        if (!(raw instanceof String text) || !text.equals(mode.name())) LOG.error("Invalid chaos_writ.obtain_mode {}; using TRADE until restart", raw);
        configuredMode = mode;
        Presets.INSTANCE.startMode(configuredMode);
    }
    static ObtainMode recipeMode() {
        if (Presets.INSTANCE.modeReady()) return Presets.INSTANCE.activeMode();
        return configuredMode;
    }
    @SubscribeEvent public void reload(AddReloadListenerEvent event) {
        Presets.INSTANCE.startMode(configuredMode);
        event.addListener(Presets.INSTANCE.reloadListener(event.getConditionContext()));
        event.addListener(new RecipeFilter(event.getServerResources().getRecipeManager()));
    }
    @SubscribeEvent public void serverAboutToStart(ServerAboutToStartEvent event) {
        Presets.INSTANCE.startMode(configuredMode);
        refreshHeroGiftParameters();
    }
    @SubscribeEvent public void serverStopped(ServerStoppedEvent event) {
        Presets.INSTANCE.stopMode();
        RecipeFilter.clear();
        heroGiftParams = new HeroGiftParams(false, 0, 0, false);
    }
    @SubscribeEvent public void syncRecipeBook(OnDatapackSyncEvent event) {
        // Vanilla sends the recipe definitions and then the initial known-recipe book after this event.
        if (Presets.INSTANCE.activeMode() != ObtainMode.CRAFT_9) return;
        var manager = event.getPlayerList().getServer().getRecipeManager();
        manager.byKey(new net.minecraft.resources.ResourceLocation(RecipeRules.CHAOS_RECIPE))
                .ifPresent(recipe -> event.getPlayers().forEach(player -> player.getRecipeBook().add(recipe)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void interact(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        boolean normal = stack.is(NORMAL.get()), random = stack.is(RANDOM.get()), chaos = stack.is(CHAOS.get());
        if (!normal && !random && !chaos) return;
        boolean usesNormalQuota = normal || random;
        if (event.getTarget().getType() != EntityType.VILLAGER) {
            if (!event.getLevel().isClientSide && isOtherMerchant(event.getTarget()))
                event.getEntity().displayClientMessage(Component.translatable("writ.message.other_merchant"), true);
            return;
        }
        // Provisional Forge event path: client/server behavior must be verified in a real client.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (event.getLevel().isClientSide) return;
        Villager v = (Villager) event.getTarget();
        // Same eligibility rule as before; each cause gets its own wording so the player knows what to fix.
        VillagerProfession profession = v.getVillagerData().getProfession();
        String ineligible = v.isBaby() ? "villager.baby" : profession == VillagerProfession.NONE ? "villager.unemployed" : profession == VillagerProfession.NITWIT ? "villager.nitwit" : v.getVillagerXp() <= 0 ? "villager.novice" : null;
        if (ineligible != null) { refuse(event, v, Component.translatable("writ.message." + ineligible)); return; }
        if (v.isTrading()) { message(event, "busy"); return; }
        CompoundTag root = v.getPersistentData();
        if (root.contains(ID) && !root.contains(ID, Tag.TAG_COMPOUND)) { message(event, "invalid_data"); return; }
        CompoundTag data = root.getCompound(ID);
        if (root.contains(ID) && (!data.contains("version", Tag.TAG_INT) || !data.contains("normalAppliedCount", Tag.TAG_INT) || !data.contains("chaosAppliedCount", Tag.TAG_INT))) { LOG.error("Incomplete contract data on villager {}", v.getUUID()); message(event, "invalid_data"); return; }
        if (!validCounter(data, "normalAppliedCount") || !validCounter(data, "chaosAppliedCount") || (data.contains("version") && (!data.contains("version", Tag.TAG_INT) || data.getInt("version") != 1))) { LOG.error("Invalid contract data on villager {}", v.getUUID()); message(event, "invalid_data"); return; }
        int n = data.getInt("normalAppliedCount"), c = data.getInt("chaosAppliedCount");
        if (!Limits.allows(n, c, NORMAL_LIMIT.get(), CHAOS_LIMIT.get(), TOTAL_LIMIT.get(), usesNormalQuota)) { refuse(event, v, Component.translatable("writ.message.limit." + ((long) n + c >= TOTAL_LIMIT.get() ? "total" : usesNormalQuota ? "trade" : "chaos"))); return; }
        MerchantOffer offer;
        if (random) {
            Presets.Snapshot snapshot = Presets.INSTANCE.currentSnapshot();
            ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            Optional<MerchantOffer> resolved = Presets.INSTANCE.randomOffer(snapshot, professionId, event.getLevel().getRandom());
            if (resolved.isEmpty()) { message(event, "random.empty_pool"); return; }
            offer = resolved.get();
        } else {
            Presets.OfferResult resolution = normal ? Presets.INSTANCE.resolveOffer(stack, v) : null;
            offer = normal ? resolution.offer() : Chaos.offer(event.getLevel().getRandom());
            if (offer == null) {
                if (normal && resolution.failure().equals("profession_mismatch")) refuse(event, v, Component.translatable("writ.message.profession_mismatch", Texts.professions(Presets.INSTANCE.professionsOf(stack))));
                else message(event, !normal ? "empty_pool" : resolution.failure().equals("preset_missing") && (!stack.hasTag() || stack.getTag().getString("preset_id").isEmpty()) ? "blank" : resolution.failure());
                return;
            }
        }
        v.getOffers().add(offer);
        data.putInt("version", 1);
        data.putInt("normalAppliedCount", n);
        data.putInt("chaosAppliedCount", c);
        data.putInt(usesNormalQuota ? "normalAppliedCount" : "chaosAppliedCount", usesNormalQuota ? n + 1 : c + 1);
        root.put(ID, data);
        if (!event.getEntity().getAbilities().instabuild) stack.shrink(1);
        // Mirror vanilla's own cues: the villager agrees aloud and sparkles as it does when it gains trades.
        v.playSound(SoundEvents.VILLAGER_YES, 1.0F, v.getVoicePitch());
        event.getLevel().broadcastEntityEvent(v, (byte) 14);
        event.getEntity().displayClientMessage(Component.translatable("writ.message.success", Texts.trade(offer)), true);
    }
    /** The villager itself turns the writ down: it shakes its head, as it does when it has nothing to trade. */
    private static void refuse(PlayerInteractEvent.EntityInteract e, Villager v, Component reason) {
        v.setUnhappyCounter(40);
        v.playSound(SoundEvents.VILLAGER_NO, 1.0F, v.getVoicePitch());
        e.getEntity().displayClientMessage(reason, true);
    }
    private static boolean validCounter(CompoundTag tag, String key) { return !tag.contains(key) || (tag.contains(key, Tag.TAG_INT) && tag.getInt(key) >= 0); }
    static boolean isOtherMerchant(net.minecraft.world.entity.Entity entity) { return entity.getType() != EntityType.VILLAGER && entity instanceof Merchant; }
    private static void message(PlayerInteractEvent.EntityInteract e, String key) { e.getEntity().displayClientMessage(Component.translatable("writ.message." + key), true); }
}
