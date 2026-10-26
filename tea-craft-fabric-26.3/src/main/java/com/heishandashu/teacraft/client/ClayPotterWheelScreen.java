package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.core.CeramicPatterns;
import com.heishandashu.teacraft.menu.ClayPotterWheelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 拉坯机界面：上面黏土格，下面图样列表。
 *
 * <h2>图样列表</h2>
 * <p>直接读 {@link CeramicPatterns#all()}，不在界面里写死。以后加一种陶瓷，
 * 这里自动多一行。列表可滚动：右侧有滚动条，滚轮或拖动都能翻。</p>
 *
 * <h2>每行内容</h2>
 * <pre>
 *   [图标] 名字                    需要 N 黏土
 * </pre>
 * <p>消耗写在右侧固定对齐，鼠标悬停只提示「点击制作并添加进窑炉」，
 * 不再重复黏土数量 —— 数字已经在行里了。</p>
 *
 * <h2>26.3 绘制分工</h2>
 * <ul>
 *   <li>{@code extractBackground} —— 面板、行底、滚动条、格子凹槽</li>
 *   <li>{@code extractContents} —— 先让原版画槽位物品，再补行图标</li>
 *   <li>{@code extractLabels} —— 文字</li>
 * </ul>
 */
public class ClayPotterWheelScreen extends AbstractContainerScreen<ClayPotterWheelMenu> {

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 200;

    private static final int SLOT = 18;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    /** 行高：原来 22，降低 20% → 18。 */
    private static final int ROW_H = 18;
    /** 行与行之间留 1px 空隙，视觉上不粘在一起。 */
    private static final int ROW_GAP = 1;

    private static final int ROW_BG = 0xFFB8B8B8;
    private static final int ROW_HOVER = 0xFFD8D8D8;
    private static final int ROW_DISABLED = 0xFF9A9A9A;

    private static final int LIST_X = 8;
    private static final int LIST_Y = 46;
    /** 列表可视高度（约 5 行）。 */
    private static final int LIST_H = 92;
    /** 滚动条宽度。 */
    private static final int SCROLL_W = 6;

    /** 滚动条颜色。 */
    private static final int SCROLL_TRACK = 0xFF8A8A8A;
    private static final int SCROLL_THUMB = 0xFF4A4A4A;
    private static final int SCROLL_THUMB_HOVER = 0xFF2A2A2A;

    /** 当前滚动偏移（像素）。 */
    private int scroll = 0;
    /** 拖动滚动条时的抓取偏移；-1 表示没在拖。 */
    private int draggingGrab = -1;

    private int hoveredRow = -1;

    public ClayPotterWheelScreen(ClayPotterWheelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, PANEL_W, PANEL_H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 108;
    }

    /* ---------------- 滚动计算 ---------------- */

    /** 内容总高度。 */
    private int contentHeight() {
        return CeramicPatterns.all().size() * (ROW_H + ROW_GAP);
    }

    /** 最大滚动量。 */
    private int maxScroll() {
        return Math.max(0, contentHeight() - LIST_H);
    }

    /** 滚动条轨道的 x。 */
    private int trackX() {
        return this.leftPos + PANEL_W - 8 - SCROLL_W;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    /** 滚动条滑块的高度与位置。 */
    private int thumbHeight() {
        int total = contentHeight();
        if (total <= LIST_H) return LIST_H;
        return Math.max(12, LIST_H * LIST_H / total);
    }

    private int thumbY() {
        int max = maxScroll();
        if (max <= 0) return this.topPos + LIST_Y;
        int travel = LIST_H - thumbHeight();
        return this.topPos + LIST_Y + (int) ((float) scroll / max * travel);
    }

    /** 这个图样行在屏幕上的 y（已含滚动）。 */
    private int rowY(int index) {
        return this.topPos + LIST_Y + index * (ROW_H + ROW_GAP) - scroll;
    }

    /* ---------------- 绘制 ---------------- */

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        clampScroll();
        hoveredRow = rowAt(mouseX, mouseY);
        drawPanel(g);
        drawSlotWells(g);
        drawPatternRows(g);
        drawScrollbar(g, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphicsExtractor g) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFFC6C6C6);
        g.fill(x, y, x + PANEL_W, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + PANEL_H, 0xFFFFFFFF);
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, 0xFF555555);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, 0xFF555555);
    }

    private void drawSlotWells(GuiGraphicsExtractor g) {
        for (var slot : this.menu.slots) {
            int sx = this.leftPos + slot.x - 1;
            int sy = this.topPos + slot.y - 1;
            g.fill(sx, sy, sx + SLOT, sy + SLOT, SLOT_BG);
            g.fill(sx, sy, sx + SLOT, sy + 1, SLOT_SHADOW);
            g.fill(sx, sy, sx + 1, sy + SLOT, SLOT_SHADOW);
        }
    }

    /** 图样行的底（图标在 extractContents 里补）。 */
    private void drawPatternRows(GuiGraphicsExtractor g) {
        var patterns = CeramicPatterns.all();
        int rowW = PANEL_W - LIST_X - 8 - SCROLL_W - 4;

        for (int i = 0; i < patterns.size(); i++) {
            int ry = rowY(i);
            // 裁掉超出可视区的行
            if (ry + ROW_H < this.topPos + LIST_Y) continue;
            if (ry > this.topPos + LIST_Y + LIST_H) continue;

            var p = patterns.get(i);
            boolean enabled = this.menu.previewResult(p) == 0;
            int bg = enabled ? (i == hoveredRow ? ROW_HOVER : ROW_BG) : ROW_DISABLED;
            g.fill(this.leftPos + LIST_X, ry,
                    this.leftPos + LIST_X + rowW, ry + ROW_H, bg);
        }
    }

    /** 右侧滚动条：只在内容超出时显示。 */
    private void drawScrollbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (maxScroll() <= 0) return;
        int tx = trackX();
        int ty = this.topPos + LIST_Y;
        g.fill(tx, ty, tx + SCROLL_W, ty + LIST_H, SCROLL_TRACK);

        int th = thumbHeight();
        int thy = thumbY();
        boolean hover = mouseX >= tx && mouseX < tx + SCROLL_W
                && mouseY >= thy && mouseY < thy + th;
        g.fill(tx, thy, tx + SCROLL_W, thy + th,
                (hover || draggingGrab >= 0) ? SCROLL_THUMB_HOVER : SCROLL_THUMB);
    }

    /** 行图标 —— 必须在原版画完槽位物品之后。 */
    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractContents(g, mouseX, mouseY, partialTick);
        var patterns = CeramicPatterns.all();
        for (int i = 0; i < patterns.size(); i++) {
            int ry = rowY(i);
            if (ry + ROW_H < this.topPos + LIST_Y) continue;
            if (ry > this.topPos + LIST_Y + LIST_H) continue;
            // 行高 18 → 图标 16x16 居中留 1px
            g.item(patterns.get(i).makeStack(), this.leftPos + LIST_X + 1, ry + 1);
        }
    }

    /** 文字。 */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);

        var wheel = this.menu.wheel();
        int clay = wheel == null ? 0 : wheel.clayCount();
        g.text(this.font,
                Component.translatable("teacraft.potter_wheel.clay_amount", clay),
                this.leftPos + 50, this.topPos + 26, 0xFF404040);

        var patterns = CeramicPatterns.all();
        int costRight = this.leftPos + PANEL_W - 8 - SCROLL_W - 6;

        for (int i = 0; i < patterns.size(); i++) {
            int ry = rowY(i);
            if (ry + ROW_H < this.topPos + LIST_Y) continue;
            if (ry > this.topPos + LIST_Y + LIST_H) continue;

            var p = patterns.get(i);
            // 名字：图标右侧
            g.text(this.font, p.displayName(),
                    this.leftPos + LIST_X + 20, ry + 5, 0xFF202020);

            // 消耗：右对齐贴到滚动条左侧
            Component cost = Component.translatable("teacraft.potter_wheel.cost_short", p.clay());
            int w = this.font.width(cost);
            g.text(this.font, cost, costRight - w, ry + 5, 0xFF505050);
        }
    }

    /** 鼠标落在哪个图样行上（考虑滚动）。 */
    private int rowAt(int mouseX, int mouseY) {
        int listLeft = this.leftPos + LIST_X;
        int rowW = PANEL_W - LIST_X - 8 - SCROLL_W - 4;
        if (mouseX < listLeft || mouseX >= listLeft + rowW) return -1;
        if (mouseY < this.topPos + LIST_Y || mouseY >= this.topPos + LIST_Y + LIST_H) return -1;

        var patterns = CeramicPatterns.all();
        for (int i = 0; i < patterns.size(); i++) {
            int ry = rowY(i);
            if (mouseY >= ry && mouseY < ry + ROW_H) return i;
        }
        return -1;
    }

    /** 鼠标是否在滚动条滑块上。 */
    private boolean onThumb(double mx, double my) {
        if (maxScroll() <= 0) return false;
        int tx = trackX();
        int thy = thumbY();
        return mx >= tx && mx < tx + SCROLL_W && my >= thy && my < thy + thumbHeight();
    }

    /** 鼠标是否在滚动条区域内。 */
    private boolean onTrack(double mx, double my) {
        if (maxScroll() <= 0) return false;
        int tx = trackX();
        return mx >= tx && mx < tx + SCROLL_W
                && my >= this.topPos + LIST_Y && my < this.topPos + LIST_Y + LIST_H;
    }

    /* ---------------- 输入 ---------------- */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                                boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        // 点滚动条：开始拖动；点轨道空白：跳一页
        if (onThumb(mx, my)) {
            draggingGrab = (int) my - thumbY();
            return true;
        }
        if (onTrack(mx, my)) {
            scroll += (my < thumbY()) ? -LIST_H / 2 : LIST_H / 2;
            clampScroll();
            return true;
        }

        int idx = rowAt((int) mx, (int) my);
        if (idx >= 0) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(
                        this.menu.containerId,
                        ClayPotterWheelMenu.BUTTON_PATTERN_BASE + idx);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingGrab = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
                                double dragX, double dragY) {
        if (draggingGrab >= 0) {
            int ty = this.topPos + LIST_Y;
            int travel = LIST_H - thumbHeight();
            if (travel <= 0) return true;
            int rel = (int) event.y() - ty - draggingGrab;
            rel = Math.max(0, Math.min(travel, rel));
            scroll = (int) ((float) rel / travel * maxScroll());
            clampScroll();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        // 鼠标在列表区域内才滚列表
        if (mouseX >= this.leftPos + LIST_X && mouseX < this.leftPos + PANEL_W - 8
                && mouseY >= this.topPos + LIST_Y
                && mouseY < this.topPos + LIST_Y + LIST_H) {
            scroll -= (int) (scrollY * (ROW_H + ROW_GAP));
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** 悬停提示：只说"点击制作并添加进窑炉"。 */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (hoveredRow >= 0) {
            var patterns = CeramicPatterns.all();
            if (hoveredRow < patterns.size()) {
                var p = patterns.get(hoveredRow);
                int code = this.menu.previewResult(p);
                Component hint = switch (code) {
                    case 1 -> Component.translatable("teacraft.potter_wheel.no_clay");
                    case 2 -> Component.translatable("teacraft.potter_wheel.no_kiln");
                    default -> Component.translatable("teacraft.potter_wheel.hint_click");
                };
                g.setTooltipForNextFrame(this.font, hint, mouseX, mouseY);
                return;
            }
        }
        super.extractTooltip(g, mouseX, mouseY);
    }
}
