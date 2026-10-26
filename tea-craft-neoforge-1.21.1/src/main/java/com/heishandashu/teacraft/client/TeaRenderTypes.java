package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;

/**
 * 方块渲染层注册（NeoForge 1.21.1）。
 *
 * <h2>为什么必须有这个类</h2>
 * <p>26.3 的方块默认走 cutout 渲染，带 alpha 的贴图透明处正常镂空。
 * 而 <b>1.21.1 的 NeoForge 方块默认是 SOLID</b>：贴图里的透明像素
 * 不会被镂空，而是按 RGB 直接画成黑色。</p>
 *
 * <p>表现就是：茶树、晒盘、揉捻匾、炭窑上那些本该透空的地方全变成黑块。
 * 尤其是往晒盘里放了茶叶之后，茶叶层的透明边缘会糊成一片黑边。</p>
 *
 * <h2>怎么区分 cutout 和 translucent</h2>
 * <ul>
 *   <li><b>cutout</b>：要么完全不透明、要么完全透明（镂空）。树叶、茶苗、
 *       晒盘上的茶叶图案都属于这类 —— 像素 alpha 只有 0 或 255。</li>
 *   <li><b>translucent</b>：有半透明像素（alpha 在 1~254 之间），
 *       需要按深度排序混色。玻璃、水属于这类。</li>
 * </ul>
 *
 * <p>本模组的贴图全部是 cutout 语义（镂空图案），所以统一用
 * {@link RenderType#cutout()}。用 translucent 反而会引入排序问题
 * （半透明面互相遮挡时闪烁）。</p>
 */
public final class TeaRenderTypes {

    private TeaRenderTypes() {
    }

    /**
     * 给需要用 cutout 的方块登记渲染层。
     *
     * <p>调用时机：客户端构造里直接调即可（1.21.1 的
     * {@code ItemBlockRenderTypes.setRenderLayer} 是静态表，
     * 在客户端初始化阶段写入，模型烘焙时读取）。</p>
     */
    public static void register() {
        // 茶树：叶片有大片镂空
        cutout(TeaItems.TEA_BUSH.get());

        // 晒盘三态（空 / 新叶 / 萎凋 / 摇青 / 杀青）：顶面是画的茶叶，四周镂空
        cutout(TeaItems.TEA_TRAY.get());

        // 揉捻匾：同上，另有发酵用的覆布图案
        cutout(TeaItems.TEA_ROLLING_TRAY.get());

        // 炭窑：侧面与顶面的通风孔是镂空的
        cutout(TeaItems.TEA_CHARCOAL_STOVE.get());

        TeaCraft.LOGGER.info("[teacraft] 方块渲染层已登记：茶树 / 晒盘 / 揉捻匾 / 炭窑（cutout）");
    }

    private static void cutout(net.minecraft.world.level.block.Block block) {
        ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout());
    }
}
