package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.block.ClayKilnBlockEntity;
import com.heishandashu.teacraft.menu.ClayKilnMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 窑炉界面：左侧 6 坯料 / 右侧 6 成品 / 下方燃料。
 *
 * <h2>布局（面板 176 × 208）</h2>
 * <pre>
 *   0~6      标题「窑炉」
 *   20~74    坯料（左两列三行）/ 成品（右两列三行）
 *   74~92    火焰图标 + 倒计时文字
 *   96       状态说明
 *   114      物品栏标签
 *   124~178  背包 3 行
 *   182~200  快捷栏
 * </pre>
 *
 * <h2>1.21.1 与 26.3 的差异</h2>
 * <ul>
 *   <li>渲染是 {@code renderBg(GuiGraphics, float, int, int)}，不是 26.3 的
 *       {@code extractBackground}/{@code extractContents}/{@code extractLabels} 三段式。</li>
 *   <li>画文字用 {@code drawString}，不是 {@code text}。</li>
 *   <li>画物品用 {@code renderItem}，不是 {@code item}。</li>
 *   <li>提示用 {@code renderTooltip}，不是 {@code setTooltipForNextFrame}。</li>
 * </ul>
 */
public class ClayKilnScreen extends AbstractContainerScreen<ClayKilnMenu> {

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 208;

    private static final int SLOT = 18;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    private static final int FLAME_BG = 0xFF3A3A3A;
    private static final int FLAME_FG = 0xFFE07020;

    /** 撤销按钮（画在坯料格右上角的「×」）。 */
    private static final int CANCEL_SIZE = 7;
    private static final int CANCEL_RED = 0xFFD02020;
    private static final int CANCEL_RED_HOVER = 0xFFFF4040;

    private int hoveredCancel = -1;

    public ClayKilnScreen(ClayKilnMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // 1.21.1 的构造只有三个参数，尺寸靠继承来的字段设
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 114;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // 面板
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFFC6C6C6);
        g.fill(x, y, x + PANEL_W, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + PANEL_H, 0xFFFFFFFF);
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, 0xFF555555);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, 0xFF555555);

        // 槽位凹槽（坐标一律从 Slot.x/y 取，不写死）
        for (var slot : this.menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            g.fill(sx, sy, sx + SLOT, sy + SLOT, SLOT_BG);
            g.fill(sx, sy, sx + SLOT, sy + 1, SLOT_SHADOW);
            g.fill(sx, sy, sx + 1, sy + SLOT, SLOT_SHADOW);
        }

        drawFlame(g);
        drawCancelButtons(g, mouseX, mouseY);
        drawStatusText(g);
    }

    /** 火焰 + 倒计时：火焰在 50~64，倒计时从 68 起，竖直居中对齐。 */
    private void drawFlame(GuiGraphics g) {
        var kiln = this.menu.kiln();
        boolean burning = kiln != null && kiln.isLit();
        int x = this.leftPos;
        int y = this.topPos;

        int fx = x + 50;
        int fy = y + 74;
        int fw = 14;
        int fh = 16;

        if (burning) {
            g.fill(fx, fy, fx + fw, fy + fh, FLAME_BG);
            g.fill(fx + 1, fy + 6, fx + fw - 1, fy + fh - 1, FLAME_FG);
            // 跳动的火舌：每 120ms 换一种高度，让玩家一眼看出在烧
            int flicker = (int) ((System.currentTimeMillis() / 120) % 3);
            int tongueH = 5 + flicker;
            g.fill(fx + 4, fy + 6 - tongueH + 5, fx + fw - 4, fy + fh - 1, FLAME_FG);
        } else {
            g.fill(fx, fy, fx + fw, fy + fh, 0xFF5A5A5A);
        }

        // 倒计时：烧制中才有
        int leftTicks = this.menu.getSyncedRemainingTicks();
        if (leftTicks <= 0) return;
        Component text = Component.translatable("teacraft.kiln.time_left", clockText(leftTicks));
        g.drawString(this.font, text, x + 68, y + 78, 0xFFD06000, false);
    }

    /** 状态说明：放在燃料槽下方那行空白里。 */
    private void drawStatusText(GuiGraphics g) {
        var kiln = this.menu.kiln();
        if (kiln == null) return;
        if (this.menu.getSyncedRemainingTicks() > 0) return;   // 烧制中由倒计时占据
        g.drawString(this.font, kiln.statusText(),
                this.leftPos + 8, this.topPos + 96, 0xFF404040, false);
    }

    /**
     * 坯料格右上角画一个红色「×」，点它撤销本次制作（退黏土）。
     *
     * <p>坯料格本身是 {@code mayPickup=false}，玩家点格子没反应，
     * 所以撤销必须做成独立的可点区域。</p>
     */
    private void drawCancelButtons(GuiGraphics g, int mouseX, int mouseY) {
        var kiln = this.menu.kiln();
        if (kiln == null) return;
        hoveredCancel = -1;

        for (int i = 0; i < ClayKilnBlockEntity.BLANK_COUNT; i++) {
            if (kiln.blankAt(i).isEmpty()) continue;
            var slot = this.menu.slots.get(i);
            int bx = this.leftPos + slot.x + SLOT - CANCEL_SIZE - 1;
            int by = this.topPos + slot.y - 1;

            boolean hover = mouseX >= bx && mouseX < bx + CANCEL_SIZE
                    && mouseY >= by && mouseY < by + CANCEL_SIZE;
            if (hover) hoveredCancel = i;
            g.fill(bx, by, bx + CANCEL_SIZE, by + CANCEL_SIZE,
                    hover ? CANCEL_RED_HOVER : CANCEL_RED);
        }
    }

    /**
     * 鼠标点击：先判断有没有点中「×」。
     *
     * <p>1.21.1 的签名是 {@code mouseClicked(double, double, int)}。</p>
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredCancel >= 0) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(
                        this.menu.containerId,
                        ClayKilnMenu.BUTTON_CANCEL_BASE + hoveredCancel);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 悬停在「×」上时给出说明。 */
    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (hoveredCancel >= 0) {
            g.renderTooltip(this.font,
                    Component.translatable("teacraft.kiln.cancel_hint")
                            .withStyle(ChatFormatting.GRAY),
                    mouseX, mouseY);
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    /**
     * 把剩余 tick 折成「时:分」（24 小时制）。
     *
     * <p>一炉 = 1 个游戏日 = 24000 tick，正好折算成 24:00。
     * 一小时 = 1000 tick、一分钟 ≈ 16.7 tick，所以数字大约每 17 tick
     * （不到 1 秒）就动一次，能明显看出在走。</p>
     */
    private static String clockText(int left) {
        if (left <= 0) return "00:00";
        int totalTicks = ClayKilnBlockEntity.BURN_TICKS;
        int totalMinutes = (int) Math.ceil(left * 1440.0 / totalTicks);
        return String.format("%02d:%02d", totalMinutes / 60, totalMinutes % 60);
    }
}
