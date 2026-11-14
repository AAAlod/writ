package ke.writ;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Writ.ID)
@PrefixGameTestTemplate(false)
public final class TooltipGameTests {
    private static List<String> tooltip(ItemStack stack, Player player, boolean shift) {
        BooleanSupplier oldDetails = Texts.detailsHeld;
        Supplier<Player> oldViewer = Texts.viewer;
        try {
            Texts.detailsHeld = () -> shift;
            Texts.viewer = () -> player;
            return stack.getTooltipLines(player, TooltipFlag.NORMAL).stream().map(c -> c.getString()).toList();
        } finally {
            Texts.detailsHeld = oldDetails;
            Texts.viewer = oldViewer;
        }
    }

    private static void has(GameTestHelper h, List<String> lines, String key, long count) {
        String translated = Language.getInstance().getOrDefault(key).split("%", 2)[0];
        h.assertTrue(lines.stream().filter(s -> s.contains(translated)).count() == count, key + " in " + lines);
    }

    private static void pair(GameTestHelper h, Player player, String label, ItemStack stack,
                             String summary, String use, String notice) {
        List<String> normal = tooltip(stack, player, false), shifted = tooltip(stack, player, true);
        has(h, normal, summary, 1); has(h, shifted, summary, 1);
        has(h, normal, use, 0); has(h, normal, notice, 0);
        has(h, shifted, use, 1); has(h, shifted, notice, 1);
        has(h, normal, "writ.tooltip.expand_hint", 1); has(h, shifted, "writ.tooltip.expand_hint", 0);
        has(h, shifted, "writ.tooltip.details_hint", 0);
        System.out.println("Tooltip lines " + label + ": default=" + normal.size() + ", shift=" + shifted.size());
    }

    @GameTest(template = "empty")
    public static void tooltipLayers(GameTestHelper h) {
        Player player = h.makeMockSurvivalPlayer();
        var snapshot = Presets.INSTANCE.currentSnapshot();
        ItemStack normal = Presets.INSTANCE.contractFor(snapshot, "writ:example");
        List<String> base = tooltip(normal, player, false), shifted = tooltip(normal, player, true);
        has(h, base, "writ.tooltip.use", 0); has(h, base, "writ.tooltip.notice", 0);
        has(h, base, "writ.tooltip.choice_hint", 0); has(h, base, "writ.tooltip.expand_hint", 1);
        has(h, shifted, "writ.tooltip.use", 1); has(h, shifted, "writ.tooltip.notice", 1);
        has(h, shifted, "writ.tooltip.expand_hint", 0); has(h, shifted, "writ.tooltip.details_hint", 0);
        System.out.println("Tooltip lines normal: default=" + base.size() + ", shift=" + shifted.size());

        ItemStack variant = Presets.INSTANCE.contractFor(snapshot, "writ:terracotta");
        base = tooltip(variant, player, false); shifted = tooltip(variant, player, true);
        has(h, base, "writ.tooltip.choice", 1); has(h, base, "writ.tooltip.choice_hint", 0);
        has(h, base, "writ.tooltip.expand_hint", 1); has(h, shifted, "writ.tooltip.choice_hint", 1);
        has(h, shifted, "writ.tooltip.expand_hint", 0);
        System.out.println("Tooltip lines terracotta: default=" + base.size() + ", shift=" + shifted.size());

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(Enchantments.SHARPNESS, 3));
        ItemStack detailed = new ItemStack(Writ.NORMAL.get());
        CompoundTag display = new CompoundTag(); display.putInt("format", 3); display.putString("rarity", "n");
        display.put("professions", new ListTag());
        display.put("cost_a", new ItemStack(Items.EMERALD).save(new CompoundTag()));
        display.put("result", book.save(new CompoundTag()));
        detailed.getOrCreateTag().put("display_snapshot", display);
        base = tooltip(detailed, player, false); shifted = tooltip(detailed, player, true);
        has(h, base, "writ.tooltip.details_hint", 1); has(h, base, "writ.tooltip.expand_hint", 0);
        has(h, shifted, "writ.tooltip.details_hint", 0); has(h, shifted, "writ.tooltip.expand_hint", 0);
        h.assertTrue(shifted.stream().anyMatch(s -> s.contains(Language.getInstance().getOrDefault("enchantment.minecraft.sharpness"))), "missing enchantment detail: " + shifted);
        System.out.println("Tooltip lines NBT: default=" + base.size() + ", shift=" + shifted.size());

        base = tooltip(new ItemStack(Writ.NORMAL.get()), player, false);
        has(h, base, "writ.tooltip.blank", 1);
        System.out.println("Tooltip lines blank: default=" + base.size() + ", shift=" + tooltip(new ItemStack(Writ.NORMAL.get()), player, true).size());
        pair(h, player, "random", new ItemStack(Writ.RANDOM.get()), "writ.tooltip.random.summary", "writ.tooltip.random.use", "writ.tooltip.random.notice");
        pair(h, player, "chaos", new ItemStack(Writ.CHAOS.get()), "writ.tooltip.chaos.summary", "writ.tooltip.chaos.use", "writ.tooltip.chaos.notice");
        h.succeed();
    }
}
