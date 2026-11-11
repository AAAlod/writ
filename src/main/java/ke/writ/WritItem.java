package ke.writ;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

final class WritItem extends Item {
    private static final int MAX_DETAIL_LINES = 12;
    WritItem(Properties properties) { super(properties); }

    /** A trade writ is named after the trade it carries and tinted by its tier, so a held stack is identifiable at a glance. */
    @Override public Component getName(ItemStack stack) {
        if (!stack.is(Writ.NORMAL.get())) return super.getName(stack);
        CompoundTag root = stack.getTag();
        SnapshotView.View view = SnapshotView.parse(root);
        Component preset = presetName(root, view);
        if (preset == null) return super.getName(stack);
        MutableComponent name = Component.translatable(getDescriptionId(stack) + ".named", preset);
        return previewable(view) ? name.withStyle(Texts.tierColor(view.poolId(), view.rarity())) : name;
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        if (stack.is(Writ.CHAOS.get())) {
            lines.add(Component.translatable("writ.tooltip.chaos.use").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("writ.tooltip.chaos.notice").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (!stack.is(Writ.NORMAL.get())) return;
        CompoundTag root = stack.getTag();
        SnapshotView.View view = SnapshotView.parse(root);
        if (presetId(root).isEmpty() && !previewable(view) && view.kind() != SnapshotView.Kind.LEGACY) {
            lines.add(Component.translatable("writ.tooltip.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        // An anvil name hides the trade's name from the title, so repeat it here.
        if (stack.hasCustomHoverName()) lines.add(getName(stack));
        if (previewable(view)) preview(view, lines);
        else if (view.kind() == SnapshotView.Kind.LEGACY) for (String value : view.legacy()) lines.add(Component.literal(value).withStyle(ChatFormatting.GRAY));
        else lines.add(Component.translatable(view.kind() == SnapshotView.Kind.UNSUPPORTED ? "writ.tooltip.unsupported" : "writ.tooltip.no_preview").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("writ.tooltip.use").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("writ.tooltip.notice").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static void preview(SnapshotView.View view, List<Component> lines) {
        Component tier = !view.poolTranslationKey().isEmpty() ? Component.translatableWithFallback(view.poolTranslationKey(), view.poolLabel().isEmpty() ? view.rarity() : view.poolLabel())
                : !view.poolLabel().isEmpty() ? Component.literal(view.poolLabel())
                : Component.translatableWithFallback("writ.rarity." + view.rarity(), view.rarity());
        // Tiers speak for themselves; any other kind of pool is labelled as the writ's purpose.
        if (view.poolKind().isEmpty() || view.poolKind().equals("rarity")) lines.add(tier.copy().withStyle(Texts.tierColor(view.poolId(), view.rarity())));
        else lines.add(Component.translatable("writ.tooltip.purpose", tier).withStyle(ChatFormatting.GRAY));
        List<ResourceLocation> professions = new ArrayList<>();
        for (String value : view.professions()) { ResourceLocation id = ResourceLocation.tryParse(value); if (id != null) professions.add(id); }
        lines.add(Component.translatable("writ.tooltip.professions", Texts.professions(professions).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        lines.add(Texts.trade(item(view.costA()), view.costB() == null ? null : item(view.costB()), item(view.result())).withStyle(ChatFormatting.WHITE));
        details(view.result(), lines);
        if (view.variantTotal() >= 2 && view.variantIndex() >= 1 && view.variantIndex() <= view.variantTotal()) {
            String fallback = !view.variantLabel().isEmpty() ? view.variantLabel() : view.variantId();
            Component choice = !view.variantKey().isEmpty() ? Component.translatableWithFallback(view.variantKey(), fallback.isEmpty() ? view.variantKey() : fallback)
                    : !view.variantLabel().isEmpty() ? Component.literal(view.variantLabel())
                    : name(view.result());
            lines.add(Component.translatable("writ.tooltip.choice", choice.copy().withStyle(ChatFormatting.WHITE), view.variantIndex(), view.variantTotal()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("writ.tooltip.choice_hint", Component.keybind("key.use"), Component.keybind("key.sneak")).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Enchantments, potion effects or box contents of the offered item: shown on demand, because the item's bare name hides them. */
    private static void details(SnapshotView.Stack value, List<Component> lines) {
        if (value.serialized() == null || !value.serialized().contains("tag", 10)) return;
        try {
            ItemStack stack = ItemStack.of(value.serialized().copy());
            if (stack.isEmpty()) return;
            List<Component> detail = stack.getTooltipLines(Texts.viewer.get(), TooltipFlag.NORMAL);
            // Some tags (an axolotl's colour, say) add nothing to read; only offer details that exist.
            if (detail.size() <= 1) return;
            if (!Texts.detailsHeld.getAsBoolean()) {
                lines.add(Component.translatable("writ.tooltip.details_hint").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            for (int i = 1; i < detail.size() && i <= MAX_DETAIL_LINES; i++) lines.add(Component.literal("  ").append(detail.get(i)));
            if (detail.size() > MAX_DETAIL_LINES + 1) lines.add(Component.literal("  …").withStyle(ChatFormatting.DARK_GRAY));
        } catch (RuntimeException ignored) { }
    }

    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && !player.isSpectator() && VariantRules.canRequest(stack.is(Writ.NORMAL.get()), stack.getTag(), stack.getCount()) && VariantBridge.request(player, hand, stack)) {
            return net.minecraft.world.InteractionResultHolder.success(stack);
        }
        return net.minecraft.world.InteractionResultHolder.pass(stack);
    }

    private static boolean previewable(SnapshotView.View view) { return view.kind() == SnapshotView.Kind.V2 || view.kind() == SnapshotView.Kind.V3; }
    private static String presetId(@Nullable CompoundTag root) { return root != null && root.contains("preset_id", 8) ? root.getString("preset_id") : ""; }
    /** The pack's translation key, then its plain name, then the conventional {@code <namespace>.preset.<path>} key; the raw ID is the last resort. */
    @Nullable private static Component presetName(@Nullable CompoundTag root, SnapshotView.View view) {
        String presetId = presetId(root);
        if (!view.presetTranslationKey().isEmpty()) return Component.translatableWithFallback(view.presetTranslationKey(), !view.presetName().isEmpty() ? view.presetName() : presetId.isEmpty() ? view.presetTranslationKey() : presetId);
        if (!view.presetName().isEmpty()) return Component.literal(view.presetName());
        if (presetId.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(presetId);
        return id == null ? Component.literal(presetId) : Component.translatableWithFallback(id.getNamespace() + ".preset." + id.getPath(), presetId);
    }
    private static Component item(SnapshotView.Stack value) { return Texts.counted(value.count(), name(value)); }
    private static Component name(SnapshotView.Stack value) {
        if (value.serialized() != null) {
            try {
                ItemStack stack = ItemStack.of(value.serialized().copy());
                if (!stack.isEmpty()) return stack.getHoverName();
            } catch (RuntimeException ignored) { }
            return Component.literal(value.id());
        }
        ResourceLocation id = ResourceLocation.tryParse(value.id());
        Item found = id == null || !ForgeRegistries.ITEMS.containsKey(id) ? null : ForgeRegistries.ITEMS.getValue(id);
        return found == null || new ItemStack(found).isEmpty() ? Component.literal(value.id()) : new ItemStack(found).getHoverName();
    }
}
