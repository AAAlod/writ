package ke.writ;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Live checks of the loaded trade system: where contracts drop, how often, and that an appended trade is a permanent supply. */
@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class TradeSystemGameTests {
    @GameTest(template="empty")
    public static void sourcesRollOnlyTheirOwnContent(GameTestHelper h) {
        var s=Presets.INSTANCE.currentSnapshot(); var random=h.getLevel().getRandom();
        var all=new java.util.ArrayList<>(s.sources().values()); all.addAll(s.bosses().values());
        for (var source:all) {
            if (!source.resourceId().startsWith("writ:loot/") || source.key().startsWith("writ:test_")) continue;
            for (int i=0;i<200;i++) {
                String id=LootRules.select(s.candidates(),s.rarities(),source,bound->Math.floorMod(random.nextLong(),bound)).orElseThrow(() -> new IllegalStateException("empty source "+source.key()));
                var preset=s.presets().get(new ResourceLocation(id));
                // Test-datapack presets declare no sources and may appear anywhere their pool is weighted.
                h.assertTrue(preset.sources().isEmpty() || preset.sources().contains(source.key()),id+" dropped from foreign source "+source.key());
                h.assertTrue(source.overrides().getOrDefault(preset.rarity(),0)>0,id+" rolled an unweighted tier at "+source.key());
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void chancesFollowChestCounts(GameTestHelper h) {
        var s=Presets.INSTANCE.currentSnapshot();
        var expected=Map.ofEntries(Map.entry("simple_dungeon",0.60),Map.entry("abandoned_mineshaft",0.06),Map.entry("desert_pyramid",0.12),Map.entry("pillager_outpost",0.39),
                Map.entry("shipwreck_supply",0.22),Map.entry("shipwreck_map",0.22),Map.entry("shipwreck_treasure",0.22),Map.entry("underwater_ruin_small",0.39),Map.entry("underwater_ruin_big",0.39),
                Map.entry("buried_treasure",0.39),Map.entry("nether_bridge",0.15),Map.entry("bastion_other",0.10),Map.entry("bastion_bridge",0.10),Map.entry("bastion_hoglin_stable",0.10),
                Map.entry("bastion_treasure",0.39),Map.entry("stronghold_library",0.10),Map.entry("stronghold_corridor",0.10),Map.entry("stronghold_crossing",0.10),
                Map.entry("woodland_mansion",0.10),Map.entry("ancient_city",0.05),Map.entry("end_city_treasure",0.12));
        for (var e:expected.entrySet()) { var source=s.sources().get("minecraft:chests/"+e.getKey()); h.assertTrue(source!=null && source.chance()==e.getValue() && !source.inheritGlobalWeights(),"wrong source "+e.getKey()); }
        h.assertTrue(ChestLoot.ATTEMPTS==2,"chest attempts changed");
        for (String boss:List.of("elder_guardian","wither","ender_dragon","warden")) h.assertTrue(s.bosses().get("minecraft:"+boss).chance()==0.30,"boss chance "+boss);
        h.succeed();
    }
    @GameTest(template="empty")
    public static void bossLinesAreExclusive(GameTestHelper h) {
        var s=Presets.INSTANCE.currentSnapshot();
        var lines=Map.of("writ:elder_guardian",Set.of("writ:sponge_supply","writ:gear_tide_helmet","writ:gear_tidecaller_trident"),
                "writ:wither",Set.of("writ:wither_rose_supply","writ:nether_star_supply","writ:gear_edge_sword","writ:gear_bulwark_chestplate"),
                "writ:ender_dragon",Set.of("writ:dragon_breath_supply","writ:end_crystal_supply","writ:gear_skyfall_bow"),
                "writ:warden",Set.of("writ:sculk_catalyst_supply","writ:echo_shard_supply","writ:gear_swift_leggings"));
        for (var e:lines.entrySet()) {
            var line=s.candidates().stream().filter(c->c.sources().contains(e.getKey())).map(LootRules.Candidate::id).collect(java.util.stream.Collectors.toSet());
            h.assertTrue(line.equals(e.getValue()),"boss line changed "+e.getKey()+" "+line);
            for (String id:line) h.assertTrue(s.presets().get(new ResourceLocation(id)).sources().equals(Set.of(e.getKey())),id+" shared outside "+e.getKey());
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void appendedTradeRestocksPermanently(GameTestHelper h) {
        var v=h.spawn(EntityType.VILLAGER,new BlockPos(1,1,1)); v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.CLERIC)); v.setVillagerXp(1);
        var contract=Presets.INSTANCE.contractFor(Presets.INSTANCE.currentSnapshot(),"writ:totem_supply");
        var offer=Presets.INSTANCE.offer(contract,v); h.assertTrue(offer!=null && offer.getMaxUses()==1,"totem offer");
        v.getOffers().add(offer);
        offer.increaseUses(); h.assertTrue(offer.isOutOfStock(),"max_uses is the per-restock cap");
        v.restock();
        h.assertTrue(v.getOffers().contains(offer) && !offer.isOutOfStock(),"restock must refill the appended trade");
        h.succeed();
    }
}
