package ke.writ;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class ContentBatch4GameTests {
    @GameTest(template="empty")
    public static void exchanges(GameTestHelper h) {
        var s=Presets.INSTANCE.currentSnapshot();
        for (String id:List.of("exchange_rotten_flesh_leather","exchange_blaze_rod_magma_cream","exchange_string_phantom_membrane")) {
            var p=s.presets().get(new ResourceLocation("writ",id));
            h.assertTrue(p!=null && p.weight()==1 && s.candidates().stream().filter(c->c.id().equals("writ:"+id)).count()==1,"active exchange absent "+id);
            h.assertTrue(LootRules.select(List.of(new LootRules.Candidate("writ:"+id,p.rarity(),p.weight())),s.rarities(),Map.of(p.rarity(),1),false,bound->0).orElseThrow().equals("writ:"+id),"exchange not selectable "+id);
        }
        var spider=s.presets().get(new ResourceLocation("writ","exchange_spider_eye_emerald")); h.assertTrue(spider!=null && spider.weight()==0 && s.candidates().stream().noneMatch(c->c.id().equals("writ:exchange_spider_eye_emerald")),"emerald-output exchange must stay manual-only");
        var checks=List.of(new Object[]{"slime_ball_supply","spider_eyes",VillagerProfession.TOOLSMITH,Items.SPIDER_EYE,32,Items.SLIME_BALL,16,4},new Object[]{"gunpowder_supply","rotten_flesh",VillagerProfession.FLETCHER,Items.ROTTEN_FLESH,32,Items.GUNPOWDER,12,6},new Object[]{"ocean_blocks","shards",VillagerProfession.MASON,Items.PRISMARINE_SHARD,32,Items.SEA_LANTERN,8,2});
        for (var check:checks) {
            String id=(String)check[0]; var p=s.presets().get(new ResourceLocation("writ",id));
            h.assertTrue(p!=null && p.weight()>0 && p.variants().stream().anyMatch(x->x.id().equals(check[1])) && s.candidates().stream().filter(c->c.id().equals("writ:"+id)).count()==1,"parent/variant candidate wrong "+id);
            var v=h.spawn(EntityType.VILLAGER,new BlockPos(1,1,1)); v.setVillagerData(v.getVillagerData().setProfession((VillagerProfession)check[2]));
            var stack=Presets.INSTANCE.contractFor(s,"writ:"+id); Presets.INSTANCE.switchVariant(stack,false); if(id.equals("ocean_blocks")) Presets.INSTANCE.switchVariant(stack,false); var offer=Presets.INSTANCE.offer(stack,v);
            h.assertTrue(offer!=null && offer.getBaseCostA().is(Items.EMERALD) && offer.getBaseCostA().getCount()==1 && offer.getCostB().is((net.minecraft.world.item.Item)check[3]) && offer.getCostB().getCount()==(int)check[4] && offer.getResult().is((net.minecraft.world.item.Item)check[5]) && offer.getResult().getCount()==(int)check[6] && offer.getMaxUses()==(int)check[7],"wrong exchange offer "+id);
        }
        var sea=s.presets().get(new ResourceLocation("writ:ocean_blocks")); h.assertTrue(sea.costA().is(Items.EMERALD) && sea.costA().getCount()==8 && sea.costB().isEmpty() && sea.result().is(Items.PRISMARINE_BRICKS) && sea.result().getCount()==16,"ocean default changed");
        var shell=s.presets().get(new ResourceLocation("writ:shulker_shell_supply")); h.assertTrue(shell.variants().isEmpty() && shell.rarity().equals("writ:ssr"),"disabled shell variant active");
        var explosive=s.presets().get(new ResourceLocation("writ:explosive_supply")); h.assertTrue(explosive.weight()>0 && explosive.variants().stream().noneMatch(x->x.id().equals("gunpowder")),"disabled TNT variant active");
        for (var pair:List.of(new String[]{"shulker_shell_supply","spider_eyes"},new String[]{"explosive_supply","gunpowder"})) {
            try (var reader=h.getLevel().getServer().getResourceManager().getResourceOrThrow(new ResourceLocation("writ","writs/presets/"+pair[0]+".json")).openAsReader()) {
                var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject(); var array=json.getAsJsonArray("variants"); com.google.gson.JsonObject found=null;
                for (var raw:array) if(raw.getAsJsonObject().get("id").getAsString().equals(pair[1])) found=raw.getAsJsonObject();
                h.assertTrue(found!=null && found.has("enabled") && !found.get("enabled").getAsBoolean(),"missing disabled declaration "+pair[0]);
                var contract=Presets.INSTANCE.contractFor(s,"writ:"+pair[0]); contract.getTag().putString("selected_variant",pair[1]);
                var villager=h.spawn(EntityType.VILLAGER,new BlockPos(1,1,1)); villager.setVillagerData(villager.getVillagerData().setProfession(pair[0].equals("explosive_supply") ? VillagerProfession.TOOLSMITH : VillagerProfession.CARTOGRAPHER));
                h.assertTrue(Presets.INSTANCE.offer(contract,villager)==null && Presets.INSTANCE.switchVariant(contract,false).reset(),"disabled selection did not reject and reset");
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
        for (var e:Map.of("n",45,"r",35,"sr",14,"ssr",5,"ur",1).entrySet()) h.assertTrue(s.rarities().get("writ:"+e.getKey()).equals(e.getValue()),"base pool weight changed");
        h.succeed();
    }
}
