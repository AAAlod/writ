package ke.writ;

import com.google.gson.JsonParser;
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
public final class ContentBatch3GameTests {
    private static Presets.Preset preset(String id) { return Presets.INSTANCE.currentSnapshot().presets().get(new ResourceLocation("writ",id)); }
    private static net.minecraft.nbt.ListTag items(net.minecraft.world.item.ItemStack stack) { return stack.getTag().getCompound("BlockEntityTag").getList("Items",10); }
    @GameTest(template="empty")
    public static void fireworksAndVariants(GameTestHelper h) {
        var p=preset("flight_fireworks_supply"); h.assertTrue(p!=null && p.maxUses()==8 && p.result().getTag().getCompound("Fireworks").getByte("Flight")==1 && p.variants().size()==2,"fireworks default invalid");
        var v=h.spawn(EntityType.VILLAGER,new BlockPos(1,1,1)); v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FLETCHER));
        var stack=Presets.INSTANCE.contractFor(Presets.INSTANCE.currentSnapshot(),"writ:flight_fireworks_supply");
        h.assertTrue(Presets.INSTANCE.offer(stack,v).getMaxUses()==8,"default uses incorrect");
        Presets.INSTANCE.switchVariant(stack,false); var offer=Presets.INSTANCE.offer(stack,v);
        h.assertTrue(offer.getMaxUses()==6 && offer.getResult().getTag().getCompound("Fireworks").getByte("Flight")==3 && !offer.getResult().getTag().getCompound("Fireworks").contains("Explosions"),"flight3 offer incorrect");
        Presets.INSTANCE.switchVariant(stack,false); offer=Presets.INSTANCE.offer(stack,v); var pack=items(offer.getResult());
        h.assertTrue(offer.getMaxUses()==1 && pack.size()==4,"fireworks pack incorrect");
        for (int i=0;i<pack.size();i++) { var e=pack.getCompound(i); h.assertTrue(e.getByte("Count")==64 && e.getCompound("tag").getCompound("Fireworks").getByte("Flight")==1 && !e.getCompound("tag").getCompound("Fireworks").contains("Explosions"),"pack rocket incorrect"); }
        var explosive=preset("explosive_supply"); h.assertTrue(explosive!=null && explosive.weight()>0 && explosive.maxUses()==2 && explosive.variants().size()==1 && explosive.variants().get(0).maxUses()==1,"explosive variants invalid");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void consumablesAndKits(GameTestHelper h) {
        var s=Presets.INSTANCE.currentSnapshot();
        for (var e:java.util.Map.of("potion_fire_resistance","long_fire_resistance","potion_regeneration_long","long_regeneration","potion_slow_falling","long_slow_falling","potion_turtle_master","long_turtle_master","potion_invisibility","long_invisibility","tipped_arrow_poison","long_poison").entrySet()) {
            var p=preset(e.getKey()); h.assertTrue(p!=null && p.result().getTag().getString("Potion").equals("minecraft:"+e.getValue()),"potion incorrect "+e.getKey());
        }
        // Previously parked consumables are now live, priced, sourced candidates.
        for (String id:java.util.List.of("potion_turtle_master","potion_invisibility","end_crystal_supply","golden_apple_supply","explosive_supply")) h.assertTrue(preset(id)!=null && preset(id).weight()>0 && !preset(id).sources().isEmpty() && s.candidates().stream().anyMatch(c->c.id().equals("writ:"+id)),"consumable not active "+id);
        h.assertTrue(preset("golden_apple_supply").costB().is(Items.GOLD_INGOT) && preset("golden_apple_supply").costB().getCount()==32 && preset("golden_apple_supply").result().getCount()==4,"golden apple must cost the vanilla gold");
        h.assertTrue(preset("enchanted_golden_apple_supply").weight()==1 && preset("enchanted_golden_apple_supply").result().is(Items.ENCHANTED_GOLDEN_APPLE),"enchanted apple missing");
        for (var e:java.util.Map.of("expedition_kit",10,"engineer_kit",8,"concrete_palette_kit",16,"special_materials_kit",7,"potion_kit",12).entrySet()) {
            var p=preset(e.getKey()); h.assertTrue(p!=null && items(p.result()).size()==e.getValue(),"kit slot count wrong "+e.getKey());
            for (var raw:items(p.result())) { var item=(net.minecraft.nbt.CompoundTag)raw; h.assertTrue(item.contains("Slot",1) && item.contains("Count",1) && item.getByte("Count")>0 && !net.minecraft.world.item.ItemStack.of(item).isEmpty(),"kit slot/count/item invalid"); if (item.getString("id").endsWith("potion")) h.assertTrue(item.getByte("Count")==1 && item.getCompound("tag").contains("Potion",8),"kit potion invalid"); }
        }
        var concrete=items(preset("concrete_palette_kit").result()); for (var raw:concrete) h.assertTrue(((net.minecraft.nbt.CompoundTag)raw).getByte("Count")==32,"concrete count incorrect");
        var terracotta=preset("terracotta_kit"); h.assertTrue(terracotta.costA().is(Items.DIAMOND) && terracotta.costA().getCount()==16 && terracotta.costB().is(Items.SHULKER_BOX) && terracotta.costB().getCount()==1 && items(terracotta.result()).size()==16,"base terracotta modified");
        h.assertTrue(terracotta.variants().size()==1 && terracotta.variants().get(0).id().equals("glazed") && items(terracotta.variants().get(0).result()).size()==16,"glazed missing");
        for (var raw:items(terracotta.variants().get(0).result())) h.assertTrue(((net.minecraft.nbt.CompoundTag)raw).getByte("Count")==32,"glazed count incorrect");
        var exp=preset("expedition_kit"); h.assertTrue(exp.variants().size()==1 && exp.variants().get(0).id().equals("end") && items(exp.variants().get(0).result()).size()==10,"end or disabled variant incorrect");
        var end=items(exp.variants().get(0).result()); for(int i=0;i<2;i++) h.assertTrue(end.getCompound(i).getByte("Count")==64 && end.getCompound(i).getCompound("tag").getCompound("Fireworks").getByte("Flight")==3,"end rockets incorrect");
        for(int i=2;i<4;i++) h.assertTrue(end.getCompound(i).getString("id").equals("minecraft:ender_pearl") && end.getCompound(i).getByte("Count")==16,"end pearls incorrect");
        try (var reader=h.getLevel().getServer().getResourceManager().getResourceOrThrow(new ResourceLocation("writ","writs/presets/explosive_supply.json")).openAsReader()) {
            var json=JsonParser.parseReader(reader).getAsJsonObject(); var pack=json.getAsJsonArray("variants").get(1).getAsJsonObject();
            h.assertFalse(pack.get("enabled").getAsBoolean(),"explosive pack enabled by default"); pack.addProperty("enabled",true);
            var box=items(Presets.parse(json).variants().get(1).result()); h.assertTrue(box.size()==4,"explosive pack size incorrect");
            String[] ids={"minecraft:tnt","minecraft:gunpowder","minecraft:sand","minecraft:flint_and_steel"}; int[] counts={16,64,64,1};
            for(int i=0;i<4;i++) h.assertTrue(box.getCompound(i).getString("id").equals(ids[i]) && box.getCompound(i).getByte("Count")==counts[i],"explosive pack entry incorrect");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        h.assertTrue(preset("enchanted_golden_apple_supply").sources().equals(java.util.Set.of("writ:woodland_mansion")) && preset("enchanted_golden_apple_supply").rarity().equals("writ:ur"),"apple source missing");
        h.assertTrue(preset("tipped_arrow_poison").sources().contains("writ:end_city"),"arrow source missing");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void variantMaxUsesValidation(GameTestHelper h) {
        String base="{\"pool\":\"r\",\"weight\":1,\"trade\":{\"cost_a\":{\"item\":\"minecraft:emerald\",\"count\":1},\"result\":{\"item\":\"minecraft:stone\",\"count\":1},\"max_uses\":8,\"villager_xp\":0,\"price_multiplier\":0},\"variants\":[{\"id\":\"inherited\"},{\"id\":\"override\",\"max_uses\":3}]}";
        var json=JsonParser.parseString(base).getAsJsonObject(); var variants=Presets.parse(json).variants(); h.assertTrue(variants.get(0).maxUses()==8 && variants.get(1).maxUses()==3,"max uses inheritance failed");
        for(String bad:java.util.List.of("0","-1","1.5","\"2\"","null","2147483648")) {
            json.add("variants",JsonParser.parseString("[{\"id\":\"bad\",\"enabled\":false,\"max_uses\":"+bad+"}]"));
            try { Presets.parse(json); h.fail("accepted max_uses " + bad); return; } catch(IllegalArgumentException expected) { h.assertTrue(expected.getMessage().contains("variants[0].max_uses"),"missing error path"); }
        }
        h.succeed();
    }
}
