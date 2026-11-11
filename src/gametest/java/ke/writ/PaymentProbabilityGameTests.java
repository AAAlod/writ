package ke.writ;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class PaymentProbabilityGameTests {
    private static Presets.Preset preset(String name) { return Presets.INSTANCE.currentSnapshot().presets().get(new ResourceLocation("writ",name)); }
    private static void checkBox(GameTestHelper h, ItemStack cost) { h.assertTrue(cost.is(Items.SHULKER_BOX) && cost.getCount()==1 && !cost.hasTag(),"kit cost must be one untagged shulker box"); }
    @GameTest(template="empty")
    public static void kitPaymentsAndVanillaMatching(GameTestHelper h) throws Exception {
        for(String name:List.of("concrete_palette_kit","engineer_kit","expedition_kit","potion_kit","special_materials_kit","terracotta_kit")) checkBox(h,preset(name).costB());
        for(String name:List.of("concrete_palette_kit","engineer_kit","expedition_kit","potion_kit","special_materials_kit","terracotta_kit")) h.assertTrue(preset(name).result().is(Items.SHULKER_BOX),"kit result must be plain shulker box: "+name);
        var expedition=preset("expedition_kit"); var end=expedition.variants().stream().filter(v->v.id().equals("end")).findFirst().orElseThrow(); checkBox(h,end.costB()); h.assertTrue(end.result().is(Items.SHULKER_BOX),"end kit result must be plain shulker box");
        checkBox(h,preset("terracotta_kit").variants().stream().filter(v->v.id().equals("glazed")).findFirst().orElseThrow().costB());
        h.assertTrue(preset("terracotta_kit").variants().stream().filter(v->v.id().equals("glazed")).findFirst().orElseThrow().result().is(Items.SHULKER_BOX),"glazed kit result must be plain shulker box");
        checkBox(h,preset("flight_fireworks_supply").variants().stream().filter(v->v.id().equals("pack")).findFirst().orElseThrow().costB());
        h.assertTrue(preset("flight_fireworks_supply").variants().stream().filter(v->v.id().equals("pack")).findFirst().orElseThrow().result().is(Items.SHULKER_BOX),"firework pack result must be plain shulker box");
        try(var reader=h.getLevel().getServer().getResourceManager().getResourceOrThrow(new ResourceLocation("writ","writs/presets/explosive_supply.json")).openAsReader()) {
            var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject(); var variants=json.getAsJsonArray("variants"); var disabled=variants.get(1).getAsJsonObject();
            h.assertTrue(disabled.get("id").getAsString().equals("pack") && !disabled.get("enabled").getAsBoolean(),"explosive pack must stay disabled");
            disabled.addProperty("enabled",true); var p=Presets.parse(json); var pack=p.variants().stream().filter(v->v.id().equals("pack")).findFirst().orElseThrow(); checkBox(h,pack.costB()); h.assertTrue(pack.result().is(Items.SHULKER_BOX),"explosive pack result must be plain shulker box");
        }
        var terracotta=preset("terracotta_kit"); h.assertTrue(terracotta.costA().is(Items.DIAMOND) && terracotta.costA().getCount()==16 && terracotta.costB().is(Items.SHULKER_BOX) && terracotta.costB().getCount()==1,"base terracotta payment changed");
        var shell=preset("shulker_shell_supply"); h.assertTrue(shell.result().is(Items.SHULKER_SHELL) && shell.result().getCount()==2,"shell supply result changed");
        var offer=new MerchantOffer(expedition.costA().copy(),expedition.costB().copy(),expedition.result().copy(),expedition.maxUses(),expedition.xp(),expedition.multiplier());
        h.assertTrue(offer.satisfiedBy(expedition.costA().copy(),new ItemStack(Items.SHULKER_BOX)),"empty box must match");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void filledBoxPaymentReturnsContents(GameTestHelper h) {
        var v=h.spawn(net.minecraft.world.entity.EntityType.VILLAGER,new net.minecraft.core.BlockPos(1,1,1));
        v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.CLERIC)); v.setVillagerXp(1);
        var kit=preset("potion_kit"); v.getOffers().clear();
        v.getOffers().add(new MerchantOffer(kit.costA().copy(),kit.costB().copy(),kit.result().copy(),4,kit.xp(),kit.multiplier()));
        var player=h.makeMockSurvivalPlayer(); var menu=new net.minecraft.world.inventory.MerchantMenu(1,player.getInventory(),v);
        player.containerMenu=menu; v.setTradingPlayer(player);
        try {
            var filled=new ItemStack(Items.SHULKER_BOX); var items=new net.minecraft.nbt.ListTag();
            var diamonds=new ItemStack(Items.DIAMOND,5).save(new net.minecraft.nbt.CompoundTag()); diamonds.putByte("Slot",(byte)0); items.add(diamonds);
            var block=new net.minecraft.nbt.CompoundTag(); block.putString("id","minecraft:shulker_box"); block.put("Items",items); filled.getOrCreateTag().put("BlockEntityTag",block);
            menu.getSlot(0).set(kit.costA().copy()); menu.getSlot(1).set(filled);
            // Premise: vanilla matching ignores the payment's extra NBT, so the filled box is accepted.
            h.assertTrue(menu.getSlot(2).getItem().is(Items.SHULKER_BOX),"vanilla no longer accepts a filled box; revisit ContainerPayments");
            menu.getSlot(2).onTake(player,menu.getSlot(2).getItem().copy());
            h.assertTrue(menu.getSlot(1).getItem().isEmpty(),"box payment must be consumed");
            h.assertTrue(player.getInventory().countItem(Items.DIAMOND)==5,"box contents must be returned to the player");
            menu.getSlot(0).set(kit.costA().copy()); menu.getSlot(1).set(new ItemStack(Items.SHULKER_BOX));
            menu.getSlot(2).onTake(player,menu.getSlot(2).getItem().copy());
            h.assertTrue(player.getInventory().countItem(Items.DIAMOND)==5,"an empty box must not refund anything again");
            h.assertTrue(player.getInventory().countItem(Items.SHULKER_BOX)==0 && v.getOffers().get(0).getUses()==2,"both trades must complete");
        } finally { v.setTradingPlayer(null); player.containerMenu=player.inventoryMenu; }
        h.succeed();
    }
}
