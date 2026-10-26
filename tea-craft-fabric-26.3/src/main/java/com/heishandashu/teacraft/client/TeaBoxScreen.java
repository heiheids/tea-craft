package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.menu.TeaBoxMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 茶盒界面（Fabric 26.3）—— 27 格，每格显示「物品图标 + 数量」。
 *
 * <h2>数量为什么要交给原版画</h2>
 * <p>茶盒每格可能有 <b>999</b> 个，而 {@code ItemStack} 上限只有 99，
 * 所以槽位对外只暴露「1 个」的展示栈。</p>
 *
 * <p>但这也导致原版不画数量（它只在 {@code count > 1} 时画）。我一开始自己
 * {@code text} 补画，结果<b>数字被物品图标盖住</b> —— 因为原版画物品时带着
 * z 抬升，我的文字在 z=0，天然在物品下面。</p>
 *
 * <p>正确的做法是调 {@code itemDecorations} 并传入<b>显示文本</b>：
 * 原版会在 {@code translate(0, 0, 200)} 之后画这个文本，所以它一定压在物品
 * 上方，而且位置、右对齐、阴影全部由原版负责。它还有一个判断：
 * {@code count == 1 && text == null} 才跳过 —— 传了 text 就一定会画。</p>
 *
 * <h2>坐标系</h2>
 * <p>{@code extractSlot} 由父类在坐标系偏移<b>之后</b>调用，
 * 所以这里直接用 {@code slot.x / slot.y}，不能再加 leftPos。</p>
 */
public class TeaBoxScreen extends AbstractContainerScreen<TeaBoxMenu> {

    private static final int PANEL_W = 176;

    /** 3 行格子（18px x 3）+ 间距 + 玩家背包 4 行。 */
    private static final int PANEL_H = 186;

    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    /** 面板配色：暖木色，和茶盒贴图呼应。 */
    private static final int PANEL_BORDER = 0xFF5A3016;
    private static final int PANEL_BG = 0xFFB89870;
    private static final int PANEL_INNER = 0xFFD8C0A0;

    public TeaBoxScreen(TeaBoxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, PANEL_W, PANEL_H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 74;
    }

    /** 画界面底板：外边框 + 底色 + 每个槽位的凹槽。 */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 先让原版铺它的深色遮罩，我们再把浅色面板盖上去（顺序反了会被压暗）
        super.extractBackground(g, mouseX, mouseY, partialTick);

        // 外边框
        g.fill(this.leftPos - 1, this.topPos - 1,
                this.leftPos + PANEL_W + 1, this.topPos + PANEL_H + 1, PANEL_BORDER);
        // 主底色
        g.fill(this.leftPos, this.topPos,
                this.leftPos + PANEL_W, this.topPos + PANEL_H, PANEL_BG);
        // 内描边（浅一档）
        g.fill(this.leftPos + 2, this.topPos + 2,
                this.leftPos + PANEL_W - 2, this.topPos + PANEL_H - 2, PANEL_INNER);

        // 每个槽位的凹槽（这里是全局坐标，要加 leftPos/topPos）
        for (Slot slot : this.menu.slots) {
            int x = this.leftPos + slot.x - 1;
            int y = this.topPos + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, SLOT_SHADOW);
            g.fill(x + 1, y + 1, x + 18, y + 18, SLOT_BG);
        }
    }

    /**
     * 画单个槽位的内容。
     *
     * <p>茶盒槽位：画图标，然后把真实数量作为「装饰文本」交给原版
     * （原版会 z 抬升 200，保证压在最上层）。</p>
     * <p>玩家背包槽位：走原版。</p>
     */
    @Override
    protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
        if (!(slot instanceof TeaBoxMenu.BoxSlot boxSlot)) {
            super.extractSlot(g, slot, mouseX, mouseY);
            return;
        }

        // 这里的坐标系已偏移，直接用 slot.x / slot.y
        int x = slot.x;
        int y = slot.y;

        var stack = boxSlot.getItem();
        if (stack.isEmpty()) return;

        // 画图标
        g.item(stack, x, y);

        // 数量：交给原版装饰绘制，传文本强制显示（含 1）
        int count = boxSlot.realCount();
        if (count > 0) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            g.itemDecorations(font, stack, x, y, String.valueOf(count));
        }
    }
}
