package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.block.TeaWokBlockEntity;
import com.heishandashu.teacraft.menu.TeaWokMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 炒茶锅的界面 —— 和碳焙筐同一套画法（贴图 + 沿 Slot 坐标补画格子）。
 *
 * <p>绘制经验（踩过的坑都写在碳焙筐那边）：</p>
 * <ol>
 *   <li>先让原版铺底，再画自己的 —— 顺序反了会被深色遮罩压暗
 *       （1.21.1 里原版的底在 {@code renderBackground()} 里、{@code renderBg} 之前就铺好了）；</li>
 *   <li>格子要画在 {@code slot.x-1, slot.y-1}（原版槽位底是 18×18，比物品四周多 1px）；</li>
 *   <li>装饰（炒制进度条）只画在槽位之间的空隙。</li>
 * </ol>
 *
 * <h2>1.21.1（NeoForge）迁移说明</h2>
 * <ul>
 *   <li>{@code GuiGraphicsExtractor} → {@link GuiGraphics}；</li>
 *   <li>{@code extractBackground(g, mouseX, mouseY, partialTick)}（public）→
 *       {@code protected renderBg(g, partialTick, mouseX, mouseY)}；</li>
 *   <li>构造只有三参，面板尺寸给 {@code imageWidth / imageHeight} 赋值。</li>
 * </ul>
 */
public class TeaWokScreen extends AbstractContainerScreen<TeaWokMenu> {

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 166;

    private static final int SLOT = 18;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;

    /** 炒制进度条：放在输入/输出两槽之间的空隙。 */
    private static final int BAR_BG = 0xFF8B8B8B;
    private static final int BAR_FG = 0xFFD8A03C;

    public TeaWokScreen(TeaWokMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 73;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        try {
            drawPanel(g);
            drawAllSlotWells(g);
            drawStirBar(g);
        } catch (Throwable ignored) {
            // 界面装饰失败不该影响游戏
        }
    }

    private void drawPanel(GuiGraphics g) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + PANEL_W, y + PANEL_H, PANEL_BG);
        g.fill(x, y, x + PANEL_W, y + 1, PANEL_LIGHT);
        g.fill(x, y, x + 1, y + PANEL_H, PANEL_LIGHT);
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, PANEL_DARK);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, PANEL_DARK);
    }

    /** 沿真实槽位坐标画格子底（原点在 slot-1）。 */
    private void drawAllSlotWells(GuiGraphics g) {
        for (var slot : this.menu.slots) {
            int sx = this.leftPos + slot.x - 1;
            int sy = this.topPos + slot.y - 1;
            g.fill(sx, sy, sx + SLOT, sy + SLOT, SLOT_BG);
            g.fill(sx, sy, sx + SLOT, sy + 1, SLOT_SHADOW);
            g.fill(sx, sy, sx + 1, sy + SLOT, SLOT_SHADOW);
        }
    }

    /**
     * 炒制进度条：三段（炒 3 次）。
     *
     * <p>放在输入槽与输出槽之间的空隙，不压任何槽位。</p>
     */
    private void drawStirBar(GuiGraphics g) {
        var inSlot = this.menu.slots.get(TeaWokMenu.SLOT_INPUT_INDEX);
        var outSlot = this.menu.slots.get(TeaWokMenu.SLOT_OUTPUT_INDEX);

        int barX = this.leftPos + inSlot.x + 22;
        int barY = this.topPos + outSlot.y + 6;
        int barW = Math.max(12, (outSlot.x - inSlot.x) - 44);
        int seg = barW / TeaWokBlockEntity.STIRS_NEEDED;

        TeaWokBlockEntity wok = this.menu.wok();
        int stirs = wok == null ? 0 : wok.stirs();

        for (int i = 0; i < TeaWokBlockEntity.STIRS_NEEDED; i++) {
            int sx = barX + i * seg;
            g.fill(sx, barY, sx + seg - 1, barY + 6, BAR_BG);
            if (i < stirs) {
                g.fill(sx, barY, sx + seg - 1, barY + 6, BAR_FG);
            }
        }
    }
}
