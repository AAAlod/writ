package ke.writ;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class ContentBatch1GameTests {
    @GameTest(template = "empty")
    public static void loadedPresetsAndSources(GameTestHelper h) {
        var snapshot = Presets.INSTANCE.currentSnapshot();
        for (String name : java.util.List.of("sponge_supply", "echo_shard_supply", "netherite_upgrade_template_supply", "elytra_upgrade_service", "gunpowder_supply", "slime_ball_supply", "blaze_rod_supply", "ghast_tear_supply", "shulker_shell_supply", "wither_skull_supply", "totem_supply", "nether_star_supply", "dragon_breath_supply")) {
            h.assertTrue(snapshot.presets().containsKey(new ResourceLocation("writ", name)), "missing or invalid preset " + name);
            h.assertTrue(snapshot.candidates().stream().anyMatch(c -> c.id().equals("writ:" + name)), "preset not a natural candidate " + name);
        }
        h.assertTrue(snapshot.invalidPresets().stream().allMatch(id -> id.getPath().startsWith("test_")), "built-in presets failed to load: " + snapshot.invalidPresets());
        for (String table : java.util.List.of("simple_dungeon", "abandoned_mineshaft", "desert_pyramid", "pillager_outpost", "shipwreck_supply", "shipwreck_map", "shipwreck_treasure", "underwater_ruin_small", "underwater_ruin_big", "buried_treasure", "nether_bridge", "bastion_other", "bastion_bridge", "bastion_hoglin_stable", "bastion_treasure", "stronghold_library", "stronghold_corridor", "stronghold_crossing", "woodland_mansion", "ancient_city", "end_city_treasure"))
            h.assertTrue(snapshot.sources().containsKey("minecraft:chests/" + table), "missing or conflicting source " + table);
        for (String entity : java.util.List.of("minecraft:elder_guardian", "minecraft:wither", "minecraft:ender_dragon", "minecraft:warden"))
            h.assertTrue(snapshot.bosses().containsKey(entity), "missing or conflicting boss source " + entity);
        // Every built-in natural candidate must be producible by at least one enabled source.
        var all = new java.util.ArrayList<>(snapshot.sources().values()); all.addAll(snapshot.bosses().values());
        var builtIn = snapshot.candidates().stream().filter(c -> !c.sources().isEmpty()).toList();
        h.assertTrue(builtIn.size() > 100, "built-in candidates missing: " + builtIn.size());
        h.assertTrue(LootRules.unreachable(builtIn, snapshot.rarities(), all).isEmpty(), "unreachable built-in presets " + LootRules.unreachable(builtIn, snapshot.rarities(), all));
        h.assertTrue(snapshot.presets().get(new ResourceLocation("writ:shulker_shell_supply")).result().is(Items.SHULKER_SHELL), "merged shell preset absent");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void elytraVanillaMatching(GameTestHelper h) {
        var p = Presets.INSTANCE.currentSnapshot().presets().get(new ResourceLocation("writ:elytra_upgrade_service"));
        h.assertTrue(p != null && p.costB().getDamageValue() == 0 && p.maxUses() == 1, "elytra template invalid");
        var offer = new net.minecraft.world.item.trading.MerchantOffer(p.costA().copy(), p.costB().copy(), p.result().copy(), p.maxUses(), p.xp(), p.multiplier());
        var damaged = new ItemStack(Items.ELYTRA); damaged.setDamageValue(1);
        h.assertTrue(offer.satisfiedBy(p.costA().copy(), new ItemStack(Items.ELYTRA)), "new elytra rejected");
        h.assertFalse(offer.satisfiedBy(p.costA().copy(), damaged), "damaged elytra accepted");
        var custom = new ItemStack(Items.ELYTRA); custom.getOrCreateTag().putString("custom_test", "present");
        h.assertTrue(offer.satisfiedBy(p.costA().copy(), custom), "vanilla NBT subset behavior changed: investigate before enabling");
        h.assertTrue(p.result().getTag().getList("Enchantments", 10).size() == 2, "elytra enchantments absent");
        h.succeed();
    }
}
