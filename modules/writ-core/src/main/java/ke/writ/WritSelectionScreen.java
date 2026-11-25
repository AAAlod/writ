package ke.writ;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraftforge.registries.ForgeRegistries;

final class WritSelectionScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation("writ", "textures/gui/preview_trade_sheet.png");
    private static final ResourceLocation ENCHANT_FONT = new ResourceLocation("minecraft", "alt");
    private static final int WIDTH = 256, HEIGHT = 192;
    private static final int LIST_X = 10, LIST_Y = 31, ROW_WIDTH = 73, ROW_HEIGHT = 21, ROW_COUNT = 7;
    private static final int SCROLL_X = 84, SCROLL_WIDTH = 4, THUMB_HEIGHT = 20;
    private static final int LIST_HEIGHT = ROW_HEIGHT * ROW_COUNT;
    private static final int PAPER_CENTER = 172;
    private final UUID session;
    private final List<VariantNet.Option> choices;
    private final Button[] rows = new Button[ROW_COUNT];
    private List<VariantNet.Option> matches;
    private EditBox search;
    private Button confirm;
    private int left, top, scroll;
    private VariantNet.Option selected;
    private boolean selectedByPlayer;
    private boolean dragging;

    WritSelectionScreen(UUID session, List<VariantNet.Option> choices, int selected) {
        super(Component.translatable("writ.gui.selection"));
        this.session = session;
        this.choices = choices;
        this.matches = choices;
        this.selected = choices.get(selected);
        this.scroll = Math.min(selected, maxScroll(choices));
    }

    @Override protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String previousSearch = search == null ? null : search.getValue();
        int previousScroll = scroll;
        search = new EditBox(font, left + 14, top + 14, 69, 10, Component.translatable("writ.gui.search"));
        search.setBordered(false);
        search.setMaxLength(80);
        search.setResponder(this::filter);
        if (previousSearch != null) search.setValue(previousSearch);
        scroll = Math.min(previousScroll, maxScroll(matches));
        addRenderableWidget(search);
        for (int i = 0; i < rows.length; i++) {
            final int row = i;
            rows[i] = addWidget(Button.builder(Component.empty(), button -> {
                int index = scroll + row;
                if (index < matches.size()) {
                    selected = matches.get(index);
                    selectedByPlayer = true;
                }
            }).bounds(left + LIST_X, top + LIST_Y + ROW_HEIGHT * i, ROW_WIDTH, ROW_HEIGHT).build());
        }
        confirm = addWidget(Button.builder(Component.translatable("gui.done"), button -> finish(false))
                .bounds(left + 203, top + 150, 19, 19).build());

    }

    private int maxScroll(List<VariantNet.Option> items) { return Math.max(0, items.size() - ROW_COUNT); }

    private void filter(String text) {
        String query = text.strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            matches = choices;
        } else {
            List<VariantNet.Option> results = new ArrayList<>();
            for (var option : choices) {
                if (contains(option.name().getString(), query) || contains(option.id(), query)
                        || matchesItem(option.result(), query)
                        || matchesItem(option.costA(), query) || matchesItem(option.costB(), query)) {
                    results.add(option);
                }
            }
            matches = results;
        }
        scroll = 0;
    }

    private static boolean matchesItem(ItemStack stack, String query) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return contains(stack.getHoverName().getString(), query) || (id != null && contains(id.toString(), query));
    }

    private static boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void finish(boolean cancel) {
        VariantNet.sendChoice(new VariantNet.Choice(session, selected.id(), cancel));
        super.onClose();
    }

    @Override public void onClose() {
        finish(true);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.blit(TEXTURE, left, top, WIDTH, HEIGHT, 0f, 0f, 512, 384, 512, 384);

        int maxScroll = maxScroll(matches);
        scroll = Math.min(scroll, maxScroll);
        drawInk(g, "pactum mercaturae", 39, 0x654022);
        drawInk(g, "inter partes conventum", 57, 0x80552e);
        drawInk(g, "merces pro pretio dato", 68, 0x80552e);
        drawInk(g, "ad pacta fide servanda", 79, 0x80552e);
        drawInk(g, "sub signo mercatoris", 90, 0x80552e);
        g.fill(left + 115, top + 106, left + 229, top + 107, 0xffa68159);

        drawTrade(g, selected, left + 131, top + 117, 21, 61, 0xff765034);

        g.drawString(font, ink("in fide"), left + 121, top + 154, 0x80552e, false);
        g.fill(left + 120, top + 165, left + 187, top + 166, 0xffa68159);
        boolean overSeal = confirm.isMouseOver(mouseX, mouseY);
        drawSeal(g, left + 212, top + 159, overSeal || confirm.isFocused());

        if (maxScroll > 0) {
            int trackX = left + SCROLL_X;
            int trackY = top + LIST_Y;
            int thumbY = trackY + scroll * (LIST_HEIGHT - THUMB_HEIGHT) / maxScroll;
            g.fill(trackX, trackY, trackX + SCROLL_WIDTH, trackY + LIST_HEIGHT, 0x60301309);
            g.fill(trackX, thumbY, trackX + SCROLL_WIDTH, thumbY + THUMB_HEIGHT, 0xffb68a55);
        }
        super.render(g, mouseX, mouseY, partialTick);

        ItemStack hovered = ItemStack.EMPTY;
        Component hoveredChoice = null;
        int rowX = left + LIST_X;
        for (int row = 0; row < ROW_COUNT; row++) {
            rows[row].visible = scroll + row < matches.size();
            if (!rows[row].visible) continue;
            int y = top + LIST_Y + row * ROW_HEIGHT;
            var option = matches.get(row + scroll);
            rows[row].setMessage(option.name());
            boolean overRow = mouseX >= rowX && mouseX < rowX + ROW_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (overRow || rows[row].isFocused()) g.fill(rowX, y, rowX + ROW_WIDTH, y + ROW_HEIGHT, 0x30f6d096);
            if (selectedByPlayer && option == selected) {
                g.renderOutline(rowX, y, ROW_WIDTH, ROW_HEIGHT, 0xffd5a66d);
            }

            drawTrade(g, option, rowX + 3, y + 2, 20, 51, 0xffdfbf95);
            if (overRow) {
                hoveredChoice = option.name();
                hovered = hoveredStack(option, mouseX - rowX - 3, mouseY - y - 2, 20, 51);
            }
        }
        if (hoveredChoice != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(hoveredChoice);
            if (!hovered.isEmpty()) {
                List<Component> itemLines = getTooltipFromItem(minecraft, hovered);
                int start = !itemLines.isEmpty() && itemLines.get(0).getString().equals(hoveredChoice.getString()) ? 1 : 0;
                tooltip.addAll(itemLines.subList(start, itemLines.size()));
            }
            g.renderTooltip(font, tooltip, hovered.getTooltipImage(), hovered, mouseX, mouseY);
        } else {
            hovered = hoveredStack(selected, mouseX - left - 131, mouseY - top - 117, 21, 61);
            if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private void drawTrade(GuiGraphics g, VariantNet.Option option, int x, int y,
                           int second, int result, int arrowColor) {
        drawStack(g, option.costA(), x, y);
        if (!option.costB().isEmpty()) drawStack(g, option.costB(), x + second, y);
        drawArrow(g, x + (second + 16 + result - 9) / 2, y + 5, arrowColor);
        drawStack(g, option.result(), x + result, y);
    }

    private static ItemStack hoveredStack(VariantNet.Option option, int x, int y, int second, int result) {
        if (y < 0 || y >= 16) return ItemStack.EMPTY;
        if (x >= 0 && x < 16) return option.costA();
        if (x >= second && x < second + 16) return option.costB();
        if (x >= result && x < result + 16) return option.result();
        return ItemStack.EMPTY;
    }

    private static Component ink(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(ENCHANT_FONT));
    }

    private void drawInk(GuiGraphics g, String text, int y, int color) {
        Component glyphs = ink(text);
        g.drawString(font, glyphs, left + PAPER_CENTER - font.width(glyphs) / 2, top + y, color, false);
    }

    private static void drawSeal(GuiGraphics g, int x, int y, boolean highlighted) {
        // A wax seal placeholder drawn as an octagon, with an inset check mark.
        g.fill(x - 5, y - 8, x + 6, y + 9, 0xff344323);
        g.fill(x - 8, y - 5, x + 9, y + 6, 0xff344323);
        g.fill(x - 6, y - 6, x + 7, y + 7, highlighted ? 0xff73834a : 0xff586432);
        g.fill(x - 4, y, x - 2, y + 2, 0xffc2c58a);
        g.fill(x - 2, y + 2, x, y + 4, 0xffc2c58a);
        for (int step = 0; step < 5; step++) {
            g.fill(x + step, y + 2 - step, x + step + 1, y + 4 - step, 0xffc2c58a);
        }
    }

    private void drawArrow(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 5, y, x + 6, y + 7, color);
        g.fill(x + 6, y + 1, x + 7, y + 6, color);
        g.fill(x + 7, y + 2, x + 8, y + 5, color);
        g.fill(x, y + 3, x + 9, y + 4, color);
    }

    private void drawStack(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (x >= left + LIST_X && x < left + SCROLL_X + SCROLL_WIDTH
                && y >= top + LIST_Y && y < top + LIST_Y + LIST_HEIGHT) {
            scroll = Math.max(0, Math.min(maxScroll(matches), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= left + SCROLL_X && x < left + SCROLL_X + SCROLL_WIDTH
                && y >= top + LIST_Y && y < top + LIST_Y + LIST_HEIGHT && maxScroll(matches) > 0) {
            dragging = true;
            return mouseDragged(x, y, button, 0, 0);
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) {
            int travel = LIST_HEIGHT - THUMB_HEIGHT;
            float fraction = (float) ((y - top - LIST_Y - THUMB_HEIGHT / 2.0) / travel);
            scroll = Math.max(0, Math.min(maxScroll(matches), Math.round(fraction * maxScroll(matches))));
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        dragging = false;
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!search.isFocused() && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
