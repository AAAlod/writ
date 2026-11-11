package ke.writ;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class BossGameTests {
    @GameTest(template = "empty")
    public static void bossSourceProbabilities(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var expected = Map.of("minecraft:ender_dragon", Map.of("writ:sr",40,"writ:ssr",40,"writ:ur",20), "minecraft:wither", Map.of("writ:ssr",50,"writ:ur",50),
                "minecraft:elder_guardian", Map.of("writ:sr",60,"writ:ssr",30,"writ:ur",10), "minecraft:warden", Map.of("writ:sr",40,"writ:ssr",40,"writ:ur",20));
        for (var e : expected.entrySet()) {
            var boss = snapshot.bosses().get(e.getKey());
            h.assertTrue(boss != null && !boss.inheritGlobalWeights() && boss.chance() == 0.30 && boss.overrides().equals(e.getValue()), "boss source invalid " + e.getKey());
        }
        var candidates = snapshot.candidates(); var weights = snapshot.rarities();
        var dragon = snapshot.bosses().get("minecraft:ender_dragon");
        // Tier roll 0..39 is SR, 40..79 SSR, 80..99 UR; each tier of the dragon line has one preset.
        for (var roll : Map.of(0L, "writ:dragon_breath_supply", 40L, "writ:end_crystal_supply", 99L, "writ:gear_skyfall_bow").entrySet()) {
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            String picked = LootRules.select(candidates, weights, dragon, bound -> calls.getAndIncrement() == 0 ? roll.getKey() : 0).orElseThrow();
            h.assertTrue(picked.equals(roll.getValue()), "dragon tier roll " + roll.getKey() + " picked " + picked);
        }
        var wither = snapshot.bosses().get("minecraft:wither");
        var seen = new java.util.TreeSet<String>();
        for (long tier : new long[]{0, 99}) for (long pick = 0; pick < 3; pick++) { final long t = tier, p = pick; var calls = new java.util.concurrent.atomic.AtomicInteger();
            seen.add(LootRules.select(candidates, weights, wither, bound -> calls.getAndIncrement() == 0 ? t : Math.min(p, bound - 1)).orElseThrow()); }
        h.assertTrue(seen.equals(new java.util.TreeSet<>(java.util.Set.of("writ:wither_rose_supply","writ:nether_star_supply","writ:gear_edge_sword","writ:gear_bulwark_chestplate"))), "wither line " + seen);
        h.succeed();
    }
    // GameTest's helper attempts a login with a null Netty channel in this MDK.
    // Register only the UUID lookup synchronously, then always restore it.
    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> online(GameTestHelper helper) throws Exception {
        Field field = PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        return (Map<UUID, ServerPlayer>) field.get(helper.getLevel().getServer().getPlayerList());
    }
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "boss-test"));
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        return player;
    }
    private static int contracts(ServerPlayer p) {
        return p.getInventory().items.stream().filter(s -> s.is(Writ.NORMAL.get())).mapToInt(ItemStack::getCount).sum();
    }
    private static void favorableRoll(net.minecraft.world.entity.LivingEntity entity) {
        for (long seed = 0; seed < 10000; seed++) {
            entity.getRandom().setSeed(seed);
            if (entity.getRandom().nextDouble() < 0.30) { entity.getRandom().setSeed(seed); return; }
        }
        throw new IllegalStateException("no favorable deterministic seed");
    }
    public static final class FavorableDeathRoll {
        private final net.minecraft.world.entity.LivingEntity target;
        private FavorableDeathRoll(net.minecraft.world.entity.LivingEntity target) { this.target=target; }
        @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public void beforeDeath(LivingDeathEvent event) { if (event.getEntity()==target) favorableRoll(target); }
    }
    private static void hurtFavorable(net.minecraft.world.entity.LivingEntity target, Runnable hurt) {
        var listener=new FavorableDeathRoll(target); MinecraftForge.EVENT_BUS.register(listener);
        try { hurt.run(); } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
    }
    @GameTest(template = "empty")
    public static void witherPlayerDeathOnce(GameTestHelper h) throws Exception {
        var p = player(h); var online = online(h); online.put(p.getUUID(), p);
        try {
            var boss = h.spawn(EntityType.WITHER, new BlockPos(1, 1, 1)); boss.setHealth(1);
            hurtFavorable(boss, () -> boss.hurt(h.getLevel().damageSources().playerAttack(p), 100));
            h.assertTrue(boss.isDeadOrDying() && contracts(p) == 1, "player death must grant one contract");
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(boss, h.getLevel().damageSources().playerAttack(p)));
            h.assertTrue(contracts(p) == 1, "repeated death must not grant another");
        } finally { online.remove(p.getUUID()); }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void witherNoCreditOrFakePlayer(GameTestHelper h) {
        var boss = h.spawn(EntityType.WITHER, new BlockPos(1, 1, 1)); boss.kill();
        var fake = FakePlayerFactory.getMinecraft(h.getLevel()); fake.getInventory().clearContent();
        var other = h.spawn(EntityType.WITHER, new BlockPos(1, 1, 1)); other.setHealth(1);
        hurtFavorable(other, () -> other.hurt(h.getLevel().damageSources().playerAttack(fake), 100));
        h.assertTrue(other.isDeadOrDying() && other.getKillCredit() == fake, "fixture must kill with FakePlayer credit");
        h.assertTrue(contracts(fake) == 0, "FakePlayer must not receive a contract");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(new BlockPos(1, 1, 1))).inflate(8)).stream().noneMatch(e -> e.getItem().is(Writ.NORMAL.get())), "uncredited death dropped a contract");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void fullInventoryDropsAtPlayer(GameTestHelper h) throws Exception {
        var p = player(h); var online = online(h); online.put(p.getUUID(), p);
        try {
            p.setPos(p.getX() + 10, p.getY(), p.getZ());
            for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
            var existing = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(3)).stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            var boss = h.spawn(EntityType.WITHER, new BlockPos(1, 1, 1)); boss.setHealth(1);
            hurtFavorable(boss, () -> boss.hurt(h.getLevel().damageSources().playerAttack(p), 100));
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(3)).stream().filter(e -> !existing.contains(e.getUUID()) && e.getItem().is(Writ.NORMAL.get())).toList();
            h.assertTrue(contracts(p) == 0 && drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "full inventory must drop exactly one contract");
            h.assertTrue(Math.abs(drops.get(0).getX() - p.getX()) < 0.01 && Math.abs(drops.get(0).getZ() - p.getZ()) < 0.01, "contract must drop at player, not boss");
        } finally { online.remove(p.getUUID()); }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void dragonPlayerDeathOnce(GameTestHelper h) throws Exception {
        var p = player(h); var online = online(h); online.put(p.getUUID(), p);
        try {
            var dragon = h.spawn(EntityType.ENDER_DRAGON, new BlockPos(1, 2, 1));
            h.assertTrue(dragon.getDragonFight() == null, "fixture must have no dragon fight");
            dragon.setHealth(1);
            hurtFavorable(dragon, () -> dragon.hurt(dragon.head, h.getLevel().damageSources().playerAttack(p), 100));
            h.assertTrue(dragon.isDeadOrDying() && contracts(p) == 1, "dragon death must grant one contract");
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(dragon, h.getLevel().damageSources().playerAttack(p)));
            h.assertTrue(contracts(p) == 1, "dragon death must not grant twice");
            h.runAfterDelay(10, () -> {
                try {
                    h.assertTrue(dragon.dragonDeathTime > 0 && contracts(p) == 1, "dragon death animation must not grant twice");
                    h.succeed();
                } finally { online.remove(p.getUUID()); }
            });
        } catch (Throwable failure) { online.remove(p.getUUID()); throw failure; }
    }
    @GameTest(template = "empty")
    public static void disconnectedCreditGetsNothing(GameTestHelper h) {
        var p = player(h);
        var boss = h.spawn(EntityType.WITHER, new BlockPos(1, 1, 1)); boss.setHealth(1);
        hurtFavorable(boss, () -> boss.hurt(h.getLevel().damageSources().playerAttack(p), 100));
        h.assertTrue(boss.isDeadOrDying() && boss.getKillCredit() == p && contracts(p) == 0, "offline player credit must not receive a contract");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void mobLootRuleControlsBossReward(GameTestHelper h) throws Exception {
        var rule = h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean previous = rule.get();
        var p = player(h); var online = online(h); online.put(p.getUUID(), p);
        try {
            rule.set(true, h.getLevel().getServer());
            var allowed = h.spawn(EntityType.WITHER, new BlockPos(1, 1, 1)); allowed.setHealth(1);
            hurtFavorable(allowed, () -> allowed.hurt(h.getLevel().damageSources().playerAttack(p), 100));
            h.assertTrue(allowed.isDeadOrDying() && contracts(p) == 1, "doMobLoot=true must award one");
            rule.set(false, h.getLevel().getServer());
            var blocked = h.spawn(EntityType.WITHER, new BlockPos(1, 1, 1)); blocked.setHealth(1);
            hurtFavorable(blocked, () -> blocked.hurt(h.getLevel().damageSources().playerAttack(p), 100));
            h.assertTrue(blocked.isDeadOrDying() && contracts(p) == 1, "doMobLoot=false must not award");
        } finally { rule.set(previous, h.getLevel().getServer()); online.remove(p.getUUID()); }
        h.succeed();
    }
}
