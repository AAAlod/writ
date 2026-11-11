package ke.writ;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

/** Shared wording for everything a player reads: tooltips and action-bar feedback build their text here. */
final class Texts {
    private Texts() { }
    /** Installed by the client: whether the "show details" key is held, and who is looking at the tooltip. */
    static volatile BooleanSupplier detailsHeld = () -> false;
    static volatile Supplier<Player> viewer = () -> null;

    static MutableComponent counted(int count, Component name) { return Component.translatable("writ.trade.count", count, name); }
    static MutableComponent stack(ItemStack stack) { return counted(stack.getCount(), stack.getHoverName()); }
    static MutableComponent trade(Component costA, @Nullable Component costB, Component result) {
        return Component.translatable("writ.trade.arrow", costB == null ? costA : Component.translatable("writ.trade.plus", costA, costB), result);
    }
    static MutableComponent trade(MerchantOffer offer) {
        ItemStack second = offer.getCostB();
        return trade(stack(offer.getBaseCostA()), second.isEmpty() ? null : stack(second), stack(offer.getResult()));
    }
    /** Same key vanilla and Forge use for a villager's profession name; the ID is shown if no language file knows it. */
    static Component profession(ResourceLocation id) {
        String key = "entity.minecraft.villager." + (id.getNamespace().equals("minecraft") ? "" : id.getNamespace() + ".") + id.getPath();
        return Component.translatableWithFallback(key, id.toString());
    }
    static MutableComponent professions(List<ResourceLocation> ids) {
        MutableComponent text = Component.empty();
        if (ids.isEmpty()) return text.append(Component.translatable("writ.profession.any"));
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) text.append(Component.translatable("writ.trade.list_separator"));
            text.append(profession(ids.get(i)));
        }
        return text;
    }
    /** The five built-in tiers follow the familiar white-green-blue-purple-gold ladder; any other pool stays neutral. */
    static ChatFormatting tierColor(String poolId, String rarity) {
        if (!poolId.isEmpty() && !poolId.startsWith(Writ.ID + ":")) return ChatFormatting.GRAY;
        return switch (rarity) {
            case "n" -> ChatFormatting.WHITE;
            case "r" -> ChatFormatting.GREEN;
            case "sr" -> ChatFormatting.AQUA;
            case "ssr" -> ChatFormatting.LIGHT_PURPLE;
            case "ur" -> ChatFormatting.GOLD;
            default -> ChatFormatting.GRAY;
        };
    }
}
