package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.block.ClayKilnBlockEntity;
import com.heishandashu.teacraft.core.CeramicPatterns;
import com.heishandashu.teacraft.menu.ClayKilnMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 窑炉界面。
 *
 * <h2>绘制分工（26.3 的 Api 拆得比较细，别混用）</h2>
 * <ul>
 *   <li>{@code extractBackground} —— 面板底、格子凹槽、火焰条。</li>
 *   <li>{@code extractContents} —— 格子里的物品（原版画）。</li>
 *   <li>{@code extractLabels} —— 文字。</li>
 * </ul>
 * <p>把文字塞进 {@code extractBackground} 会编译不过：{@code GuiGraphicsExtractor}
 * 本身只有 {@code fill}/{@code blit} 这类几何绘制，没有 {@code drawString}。</p>
 */
public class ClayKilnScreen extends AbstractContainerScreen<ClayKilnMenu> {

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 208;

    private static final int SLOT = 18;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    private static final int CANCEL_SIZE = 7;
    private static final int CANCEL_BG = 0xFFC04040;
    private static final int CANCEL_HOVER = 0xFFE06060;

    private static final int FLAME_BG = 0xFF3A3A3A;
    private static final int FLAME_FG = 0xFFE07020;
    private static final int ARROW_BG = 0xFF8B8B8B;
    private static final int ARROW_FG = 0xFFFFFFFF;

    private int hoveredCancel = -1;

    public ClayKilnScreen(ClayKilnMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, PANEL_W, PANEL_H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 114;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        hoveredCancel = cancelIndexAt(mouseX, mouseY);
        drawPanel(g);
        drawSlotWells(g);
        drawCancelButtons(g);
        drawFuelProgress(g);
    }

    private void drawPanel(GuiGraphicsExtractor g) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFFC6C6C6);
        g.fill(x, y, x + PANEL_W, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + PANEL_H, 0xFFFFFFFF);
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, 0xFF555555);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, 0xFF555555);

        int midX = x + 92;
        g.fill(midX, y + 16, midX + 1, y + 74, 0xFF888888);
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

    private void drawCancelButtons(GuiGraphicsExtractor g) {
        var kiln = this.menu.kiln();
        for (int i = 0; i < ClayKilnBlockEntity.BLANK_COUNT; i++) {
            boolean has = kiln != null && !kiln.blankAt(i).isEmpty();
            if (!has) continue;
            var slot = this.menu.slots.get(i);
            int bx = this.leftPos + slot.x + 10;
            int by = this.topPos + slot.y - 1;
            g.fill(bx, by, bx + CANCEL_SIZE, by + CANCEL_SIZE,
                    (i == hoveredCancel) ? CANCEL_HOVER : CANCEL_BG);
            for (int k = 0; k < CANCEL_SIZE - 2; k++) {
                g.fill(bx + 1 + k, by + 1 + k, bx + 2 + k, by + 2 + k, 0xFFFFFFFF);
                g.fill(bx + CANCEL_SIZE - 2 - k, by + 1 + k,
                        bx + CANCEL_SIZE - 1 - k, by + 2 + k, 0xFFFFFFFF);
            }
        }
    }

    private void drawFuelProgress(GuiGraphicsExtractor g) {
        var kiln = this.menu.kiln();
        boolean burning = kiln != null && kiln.isLit();
        int x = this.leftPos;
        int y = this.topPos;

        // 火焰：烧制中画成跳动的橙红块，没烧就是灰底。
        // 右侧那条灰色横条已经去掉了 —— 20 分钟一炉，条子一格要走很久，
        // 看着像"没用"，倒计时文字反而更直观。
        int fx = x + 50;
        int fy = y + 74;
        int fw = 14;
        int fh = 16;
        if (burning) {
            g.fill(fx, fy, fx + fw, fy + fh, FLAME_BG);
            // 底部实心
            g.fill(fx + 1, fy + 6, fx + fw - 1, fy + fh - 1, FLAME_FG);
            // 跳动的火舌：每 120ms 换一种高度，让玩家一眼看出"在烧"
            int flicker = (int) ((System.currentTimeMillis() / 120) % 3);
            int tongueH = 5 + flicker;
            g.fill(fx + 4, fy + 6 - tongueH + 5, fx + fw - 4, fy + fh - 1, FLAME_FG);
        } else {
            g.fill(fx, fy, fx + fw, fy + fh, 0xFF5A5A5A);
        }
    }

    /**
     * 文字。
     *
     * <p><b>这里的坐标是「面板相对坐标」</b>（和 {@code titleLabelY}、
     * {@code inventoryLabelY} 一样，父类已经平移过了）。之前我又加了一次
     * {@code leftPos}，于是文字被整体推到了面板右侧外面。</p>
     *
     * <p>进度取的是菜单同步过来的 DataSlot，而不是直接读方块实体 ——
     * 直接读 BE 在「打开界面」这个场景下拿不到实时更新，倒计时会一直停着。</p>
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        var kiln = this.menu.kiln();
        if (kiln == null) return;

        // 判定「在烧」用剩余 tick > 0，不再单独依赖 lit 那个 DataSlot。
        //
        // 两个槽分开同步时，只要有一个没拿到值（客户端侧初始是 0），
        // 界面就会走进 else 分支、整块倒计时直接不画 —— 表现就是「倒计时没了」。
        // 剩余 tick 本身就能说明在不在烧，少一个依赖就少一处出错。
        // 取剩余时间：优先问方块实体，其次用菜单同步值。
        //
        // 客户端那份方块实体本身是有数据的（打开界面时 getUpdateTag 会写一次），
        // 而且现在客户端也挂了 ticker 做本地递减，所以它是可靠的实时来源。
        // 之前只信 DataSlot，一旦同步通道没把值送过来就是 0，界面判定"没在烧"，
        // 整块倒计时都不画 —— 表现就是"完全没有数字"。
        int leftTicks = kiln.remainingTicks();
        if (leftTicks <= 0) leftTicks = this.menu.getSyncedRemainingTicks();

        if (leftTicks > 0) {
            // 烧制中：剩余时间紧贴在火焰图标右侧（火焰在 50~64，留 4px 间隙）
            Component left = Component.translatable("teacraft.kiln.time_left",
                    clockText(leftTicks));
            g.text(this.font, left, 68, 78, 0xFFD06000);
        } else {
            // 没在烧：状态说明放在燃料槽下方那行空白里
            g.text(this.font, kiln.statusText(), 8, 96, 0xFF404040);
        }
    }

    /** 是否在烧（走菜单同步的 DataSlot，保证每 tick 都在变）。 */
    private boolean isBurning() {
        return this.menu.isSyncedBurning();
    }

    /**
     * 把剩余 tick 折成 {@code 时:分}（24 小时制）。
     *
     * <p>一炉 = 1 个游戏日 = 24000 tick，正好折算成 24:00。
     * 一小时 = 1000 tick、一分钟 ≈ 16.7 tick，所以数字大约每 17 tick
     * （不到 1 秒）就动一次，能明显看出在走。</p>
     *
     * <p>如果以后把 {@code BURN_TICKS} 调短，这里会等比例压缩，
     * 不会出现"显示 24:00 却 30 秒就烧完"的错位。</p>
     */
    private static String clockText(int left) {
        if (left <= 0) return "00:00";
        int totalTicks = ClayKilnBlockEntity.BURN_TICKS;
        // 把剩余 tick 映射到 0~1440 分钟
        int totalMinutes = (int) Math.ceil(left * 1440.0 / totalTicks);
        return String.format("%02d:%02d", totalMinutes / 60, totalMinutes % 60);
    }

    private int cancelIndexAt(int mouseX, int mouseY) {
        var kiln = this.menu.kiln();
        if (kiln == null) return -1;
        for (int i = 0; i < ClayKilnBlockEntity.BLANK_COUNT; i++) {
            if (kiln.blankAt(i).isEmpty()) continue;
            var slot = this.menu.slots.get(i);
            int bx = this.leftPos + slot.x + 10;
            int by = this.topPos + slot.y - 1;
            if (mouseX >= bx && mouseX < bx + CANCEL_SIZE
                    && mouseY >= by && mouseY < by + CANCEL_SIZE) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                                boolean doubleClick) {
        int idx = cancelIndexAt((int) event.x(), (int) event.y());
        if (idx >= 0) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(
                        this.menu.containerId, ClayKilnMenu.BUTTON_CANCEL_BASE + idx);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (hoveredCancel >= 0) {
            var kiln = this.menu.kiln();
            var blank = kiln == null ? net.minecraft.world.item.ItemStack.EMPTY
                    : kiln.blankAt(hoveredCancel);
            var pattern = blank.isEmpty() ? null : CeramicPatterns.forItem(blank.getItem());
            int clay = pattern == null ? 1 : pattern.clay();
            g.setTooltipForNextFrame(this.font,
                    Component.translatable("teacraft.kiln.cancel_hint", clay),
                    mouseX, mouseY);
            return;
        }
        super.extractTooltip(g, mouseX, mouseY);
    }
}
