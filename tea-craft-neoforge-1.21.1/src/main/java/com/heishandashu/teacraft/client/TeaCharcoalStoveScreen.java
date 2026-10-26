package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.block.TeaCharcoalStoveBlockEntity;
import com.heishandashu.teacraft.menu.TeaCharcoalStoveMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 碳焙筐的界面，按原版熔炉的布局做。
 *
 * <h2>三次踩坑记录（都是真 bug，别再犯）</h2>
 * <ol>
 *   <li>覆盖「内容层」并先画不透明底板 → <b>把槽位和物品全盖住</b>；</li>
 *   <li>覆盖背景钩子但<b>先画自己、后调 super</b> → 原版的深色遮罩
 *       盖在我们的底板上，界面变成深灰；</li>
 *   <li>不提供 GUI 贴图 → 原版槽位的背景是画在贴图上的，<b>背包槽只看得见物品、
 *       看不见格子</b>。</li>
 * </ol>
 *
 * <h2>现在的顺序</h2>
 * <pre>
 *   renderBg(...)      // 原版在 renderBackground 里先铺它的底，我们再画自己的
 *   drawPanel(g)       // 浅灰面板（盖住原版遮罩）
 *   drawAllSlotWells() // 3 个机器槽 + 27 背包槽 + 9 快捷栏，自己画凹槽
 *   drawProgress(g)    // 火焰与箭头，只占槽位之间的空隙
 * </pre>
 *
 * <h2>1.21.1（NeoForge）迁移说明</h2>
 * <ul>
 *   <li>没有 {@code GuiGraphicsExtractor}，统一用 {@link GuiGraphics}；</li>
 *   <li>26.3 的 {@code extractBackground(g, mouseX, mouseY, partialTick)}（public）
 *       对应 1.21.1 的 {@code protected void renderBg(g, partialTick, mouseX, mouseY)}
 *       —— 注意<b>参数顺序不同</b>，而且它是 protected；</li>
 *   <li>绘制时机不变：{@code render()} 里先调 {@code renderBackground()}（内部会调
 *       {@code renderBg}），之后才画槽位、物品与标题。所以写在 {@code renderBg} 里的
 *       东西一定在槽位<b>下面</b>，正是原来 {@code extractBackground} 的语义；</li>
 *   <li>{@code blit} 的签名变了：1.21.1 是
 *       {@code blit(ResourceLocation, x, y, u, v, w, h, texW, texH)}，
 *       没有 {@code RenderPipelines.GUI_TEXTURED} 这个首参；</li>
 *   <li>{@code ResourceLocation} → {@link ResourceLocation}；</li>
 *   <li>1.21.1 的 {@code AbstractContainerScreen} 构造只有三个参数，
 *       面板尺寸改成给继承来的 {@code imageWidth / imageHeight} 赋值。</li>
 * </ul>
 */
public class TeaCharcoalStoveScreen extends AbstractContainerScreen<TeaCharcoalStoveMenu> {

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 166;

    /** 槽位边长（原版固定 18）。坐标一律从 Slot.x/y 取，不要写死。 */
    private static final int SLOT = 18;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    /** 界面贴图（由 tools/gen_stove_gui.ps1 生成，槽位底已画在里面）。 */
    private static final ResourceLocation TEXTURE =
            com.heishandashu.teacraft.TeaCraft.id("textures/gui/tea_charcoal_stove.png");

    private static final int ARROW_BG = 0xFF8B8B8B;
    private static final int ARROW_FG = 0xFFFFFFFF;
    private static final int FLAME_BG = 0xFF3A3A3A;
    private static final int FLAME_FG = 0xFFE07020;

    public TeaCharcoalStoveScreen(TeaCharcoalStoveMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // 1.21.1 的构造只有三参，尺寸靠继承来的字段
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 73;
    }

    /**
     * 背景层。1.21.1 会在 {@code renderBackground()} 里先铺原版遮罩再调本方法，
     * 因此这里直接画，不需要（也不能）再调 {@code super.renderBg} 去重复铺底。
     */
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // 两层保险，任一条路径失效都还能看见格子：
        //   1) 贴图里已经画好槽位底（最省事，坐标一个来源）
        //   2) 再用 fill 沿 Slot.x/y 画一遍格子（不依赖贴图能否加载）
        // 之前只靠贴图，一旦 blit 出问题（贴图没加载/参数不对）界面就整个"没有格子"，
        // 而且异常被我吞掉了，看不到原因。现在异常会记日志。
        try {
            // 1.21.1：blit(ResourceLocation, x, y, u, v, w, h, texW, texH)
            g.blit(TEXTURE, this.leftPos, this.topPos, 0f, 0f,
                    PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        } catch (Throwable t) {
            com.heishandashu.teacraft.TeaCraft.LOGGER.warn(
                    "[teacraft] 碳焙筐界面贴图绘制失败，改用纯色回退", t);
        }

        try {
            drawPanelFallback(g);
            drawAllSlotWells(g);
            drawProgress(g);
        } catch (Throwable t) {
            com.heishandashu.teacraft.TeaCraft.LOGGER.warn("[teacraft] 碳焙筐界面绘制失败", t);
        }
    }

    /** 纯色面板回退：即使贴图没加载，界面也不会是空的。 */
    private void drawPanelFallback(GuiGraphics g) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFFC6C6C6);
        g.fill(x, y, x + PANEL_W, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + PANEL_H, 0xFFFFFFFF);
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, 0xFF555555);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, 0xFF555555);
    }

    /**
     * 沿真实槽位坐标画格子底。
     *
     * <p><b>关键：格子要画在 {@code slot.x - 1, slot.y - 1}。</b>
     * 原版的槽位底贴图是 18×18，比 16×16 的物品图标四周各多 1px 边框。
     * 我先前画在 {@code slot.x, slot.y}，于是格子整体比物品<b>偏右下 1px</b>，
     * 看起来就是「物品整体偏左上」—— 根子在格子的原点，不在物品。</p>
     */
    private void drawAllSlotWells(GuiGraphics g) {
        for (var slot : this.menu.slots) {
            int sx = this.leftPos + slot.x - 1;
            int sy = this.topPos + slot.y - 1;
            g.fill(sx, sy, sx + SLOT, sy + SLOT, SLOT_BG);
            // 左上 1px 阴影，模拟原版凹槽
            g.fill(sx, sy, sx + SLOT, sy + 1, SLOT_SHADOW);
            g.fill(sx, sy, sx + 1, sy + SLOT, SLOT_SHADOW);
        }
    }

    /**
     * 火焰与箭头 —— 坐标照抄原版熔炉的贴图位置。
     *
     * <p>熔炉里：火焰在 {@code (56, 36)} 14×14（燃料槽上方），
     * 箭头在 {@code (79, 34)} 24×16（输入槽与输出槽之间）。
     * 我们的槽位布局已经和熔炉一致，所以直接用这两组数字，
     * 燃烧条自然就居中在两槽之间了。</p>
     */
    private void drawProgress(GuiGraphics g) {
        int x = this.leftPos;
        int y = this.topPos;

        TeaCharcoalStoveBlockEntity stove = this.menu.stove();
        float bakeP = stove == null ? 0f : stove.bakeProgress();
        float burnP = stove == null ? 0f : stove.burnProgress();

        // 火焰：熔炉位置 (56,36)，14×14
        int flameX = x + 56;
        int flameY = y + 36;
        int flameW = 14;
        int flameH = 14;
        g.fill(flameX, flameY, flameX + flameW, flameY + flameH, FLAME_BG);
        int lit = Math.round(flameH * Math.max(0f, Math.min(1f, burnP)));
        if (lit > 0) {
            g.fill(flameX, flameY + (flameH - lit), flameX + flameW, flameY + flameH, FLAME_FG);
        }

        // 箭头：熔炉位置 (79,34)，24×16
        int ax = x + 79;
        int ay = y + 34;
        int arrowW = 24;
        int arrowH = 16;
        g.fill(ax, ay, ax + arrowW, ay + arrowH, ARROW_BG);
        int fill = Math.round(arrowW * Math.max(0f, Math.min(1f, bakeP)));
        if (fill > 0) {
            g.fill(ax, ay + 5, ax + fill, ay + arrowH - 5, ARROW_FG);
        }
    }
}
