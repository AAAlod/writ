package ke.writ;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class ContentBatch2GameTests {
    @GameTest(template = "empty")
    public static void equipmentAndSources(GameTestHelper h) {
        var s = Presets.INSTANCE.currentSnapshot();
        var expected = Map.of(
            "elytra_upgrade_service", Map.of("unbreaking",4,"mending",1),
            "gear_vein_pickaxe", Map.of("efficiency",8,"fortune",4,"unbreaking",4,"mending",1),
            "gear_edge_sword", Map.of("sharpness",7,"looting",4,"fire_aspect",2,"unbreaking",4,"mending",1),
            "gear_bulwark_chestplate", Map.of("protection",5,"thorns",3,"unbreaking",4,"mending",1),
            "gear_stride_boots", Map.of("soul_speed",4,"feather_falling",5,"depth_strider",3,"unbreaking",4,"mending",1),
            "gear_skyfall_bow", Map.of("power",7,"punch",2,"flame",1,"infinity",1,"unbreaking",4,"mending",1),
            "gear_tidecaller_trident", Map.of("loyalty",3,"channeling",1,"impaling",5,"unbreaking",4,"mending",1),
            "gear_angler_rod", Map.of("luck_of_the_sea",4,"lure",4,"unbreaking",4,"mending",1),
            "gear_tri_bolt_crossbow", Map.of("multishot",1,"piercing",4,"quick_charge",3,"unbreaking",4,"mending",1),
            "gear_swift_leggings", Map.of("swift_sneak",4,"unbreaking",4,"mending",1));
        for (var entry : expected.entrySet()) {
            var p = s.presets().get(new ResourceLocation("writ", entry.getKey()));
            h.assertTrue(p != null && p.result().hasTag(), "missing preset/NBT " + entry.getKey());
            var tag = p.result().getTag(); var list = tag.getList("Enchantments", 10); var found = new java.util.HashMap<String,Integer>();
            for (int i=0; i<list.size(); i++) { var e=list.getCompound(i); found.put(e.getString("id").replace("minecraft:", ""), (int)e.getShort("lvl")); }
            h.assertTrue(found.equals(entry.getValue()) && !tag.contains("StoredEnchantments") && !tag.contains("RepairCost") && !tag.contains("Unbreakable"), "wrong enchantments " + entry.getKey());
            h.assertTrue(p.weight() > 0 && s.candidates().stream().anyMatch(c -> c.id().equals("writ:" + entry.getKey())), "equipment must be an active candidate " + entry.getKey());
        }
        // Each piece of high-end equipment comes from exactly one coherent source.
        var where = Map.of("elytra_upgrade_service", "writ:end_city", "gear_vein_pickaxe", "writ:bastion_treasure", "gear_stride_boots", "writ:bastion_treasure",
                "gear_edge_sword", "writ:wither", "gear_bulwark_chestplate", "writ:wither", "gear_skyfall_bow", "writ:ender_dragon",
                "gear_tidecaller_trident", "writ:elder_guardian", "gear_swift_leggings", "writ:warden");
        for (var e : where.entrySet()) h.assertTrue(s.presets().get(new ResourceLocation("writ", e.getKey())).sources().equals(java.util.Set.of(e.getValue())), "wrong source " + e.getKey());
        h.assertTrue(s.presets().get(new ResourceLocation("writ:gear_tri_bolt_crossbow")).sources().equals(java.util.Set.of("writ:pillager_outpost", "writ:woodland_mansion")), "crossbow source");
        h.assertTrue(s.presets().get(new ResourceLocation("writ:gear_angler_rod")).sources().equals(java.util.Set.of("writ:shipwreck", "writ:buried_treasure")), "rod source");
        var mansion = s.sources().get("minecraft:chests/woodland_mansion");
        for (int i = 0; i < 64; i++) { final long roll = i; h.assertFalse(LootRules.select(s.candidates(), s.rarities(), mansion, bound -> roll % bound).orElseThrow().equals("writ:gear_skyfall_bow"), "bow incorrectly in mansion"); }
        h.assertTrue(s.presets().values().stream().filter(p -> p.result().is(net.minecraft.world.item.Items.BOW) && p.result().getTag() != null && p.result().getTag().getList("Enchantments",10).size() == 6).count() == 1, "duplicate bow");
        h.succeed();
    }
}
