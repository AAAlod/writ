package ke.writ;

import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class ProfessionExpansionGameTests {
    @GameTest(template="empty")
    public static void groupsAndProfessions(GameTestHelper h) {
        var s=Presets.INSTANCE.currentSnapshot();
        var groupWeights=Map.of("stone_basics",1,"masonry_blocks",2,"nether_blocks",2,"ocean_blocks",2,"concrete_curing",2,"cracked_bricks",1);
        for(var e:groupWeights.entrySet()) {
            var p=s.presets().get(new ResourceLocation("writ",e.getKey()));
            h.assertTrue(p!=null && p.weight()==e.getValue() && p.professions().equals(java.util.Set.of(new ResourceLocation("minecraft:mason"))),"wrong group "+e.getKey());
            h.assertTrue(s.candidates().stream().filter(c->c.id().equals("writ:"+e.getKey())).count()==1,"group must have one candidate "+e.getKey());
        }
        for(String id:List.of("stone_bricks","smooth_stone","sandstone","mud_bricks","bricks","deepslate_bricks","deepslate_tiles","nether_bricks","polished_blackstone_bricks","quartz_block","prismarine_bricks","sea_lantern","processing_white_concrete","processing_gray_concrete","processing_light_gray_concrete","processing_black_concrete","processing_stone_bricks_cracked","processing_deepslate_bricks_cracked")) h.assertFalse(s.presets().containsKey(new ResourceLocation("writ",id)),"old preset still loaded "+id);
        for(String id:List.of("glass","copper_block","processing_copper_oxidized","processing_sandstone_smooth_sandstone","processing_quartz_smooth_quartz","processing_chorus_fruit_popped","terracotta","terracotta_kit","concrete_palette_kit")) h.assertTrue(s.presets().containsKey(new ResourceLocation("writ",id)),"preserved mason preset missing "+id);
        h.assertTrue(s.presets().entrySet().stream().filter(e->e.getKey().getNamespace().equals("writ") && e.getValue().professions().contains(new ResourceLocation("minecraft:mason"))).count()==15,"mason total must be 15 after folding sand smelting into glass");
        h.assertTrue(s.presets().get(new ResourceLocation("writ:glass")).variants().stream().anyMatch(v->v.id().equals("smelt_sand") && v.costB().is(Items.SAND) && v.result().is(Items.GLASS)),"sand smelting must live on as a glass variant");
        for(String id:List.of("exchange_string_name_tag","exchange_ink_sac_glow_ink_sac")) {
            var p=s.presets().get(new ResourceLocation("writ",id)); h.assertTrue(p!=null && p.weight()==1 && p.professions().contains(new ResourceLocation("minecraft:librarian")) && p.rarity().equals("writ:r") && p.maxUses()==4,"librarian preset invalid "+id);
        }
        var wool=s.presets().get(new ResourceLocation("writ:wool_supply")); var concrete=s.presets().get(new ResourceLocation("writ:concrete_curing"));
        h.assertTrue(wool!=null && wool.weight()==2 && wool.professions().contains(new ResourceLocation("minecraft:shepherd")) && wool.result().is(Items.WHITE_WOOL) && wool.variants().size()==15,"wool preset invalid");
        h.assertTrue(concrete.variants().size()==15 && concrete.costB().is(Items.WHITE_CONCRETE_POWDER) && concrete.result().is(Items.WHITE_CONCRETE) && concrete.maxUses()==24,"concrete default invalid");
        for(String color:List.of("orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black")) {
            var w=wool.variants().stream().filter(v->v.id().equals(color)).findFirst().orElseThrow(); var c=concrete.variants().stream().filter(v->v.id().equals(color)).findFirst().orElseThrow();
            h.assertTrue(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(w.result().getItem()).toString().equals("minecraft:"+color+"_wool") && w.result().getCount()==32 && w.maxUses()==6,"wool variant invalid "+color);
            h.assertTrue(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(c.costB().getItem()).toString().equals("minecraft:"+color+"_concrete_powder") && c.costB().getCount()==32 && net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(c.result().getItem()).toString().equals("minecraft:"+color+"_concrete") && c.result().getCount()==32 && c.maxUses()==24,"concrete variant invalid "+color);
        }
        var bee=s.presets().get(new ResourceLocation("writ:bee_products")); h.assertTrue(bee!=null && bee.professions().contains(new ResourceLocation("minecraft:farmer")) && bee.result().is(Items.HONEYCOMB) && bee.result().getCount()==16 && bee.variants().size()==1 && bee.variants().get(0).costB().is(Items.GLASS_BOTTLE) && bee.variants().get(0).costB().getCount()==16 && bee.variants().get(0).result().is(Items.HONEY_BOTTLE),"bee preset invalid");
        var rabbit=s.presets().get(new ResourceLocation("writ:rabbit_foot_supply")); h.assertTrue(rabbit!=null && rabbit.professions().contains(new ResourceLocation("minecraft:butcher")) && rabbit.costB().is(Items.RABBIT_HIDE) && rabbit.costB().getCount()==4 && rabbit.result().is(Items.RABBIT_FOOT),"rabbit preset invalid");
        var ocean=s.presets().get(new ResourceLocation("writ:ocean_blocks")); h.assertTrue(ocean.variants().stream().anyMatch(v->v.id().equals("shards") && v.costB().is(Items.PRISMARINE_SHARD) && v.maxUses()==2),"shards variant missing");
        for(String id:List.of("wool_supply","bee_products","rabbit_foot_supply")) h.assertTrue(s.candidates().stream().filter(c->c.id().equals("writ:"+id)).count()==1,"new preset must have one candidate "+id);
        h.succeed();
    }
}
