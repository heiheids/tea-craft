package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.block.TeaKettleBlock;
import com.heishandashu.teacraft.block.TeaKettleBlockEntity;
import com.heishandashu.teacraft.block.TeaSetBlock;
import com.heishandashu.teacraft.block.TeaSetBlockEntity;
import com.heishandashu.teacraft.core.TeaBrew;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 准星指向<b>盖碗 / 烧水壶</b>时，在准星下方显示内容提示。
 *
 * <h2>需求</h2>
 * <p>「鼠标指向盖碗和烧水壶有提示文字，写明茶叶数量、茶水几份」。
 * 这是<b>世界内提示</b>（HUD），不是物品栏 tooltip ——
 * 玩家把盖碗放在茶盘上，鼠标指过去就能看到里面有什么，不必拿起来。</p>
 *
 * <h3>盖碗</h3>
 * <ul>
 *   <li>空 → “盖碗 · 空”</li>
 *   <li>有干茶 → “盖碗 · 含茶叶：绿茶 品质4 · 2 份”</li>
 *   <li>有茶汤 → “盖碗 · 含茶汤：绿茶 品质4 · 2 杯”</li>
 * </ul>
 *
 * <h3>烧水壶</h3>
 * <ul>
 *   <li>空壶 → “烧水壶 · 空壶”</li>
 *   <li>有水未开 → “烧水壶 · 正在烧水 43%”</li>
 *   <li>已烧开 → “烧水壶 · 开水”</li>
 * </ul>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <ul>
 *   <li>Fabric 的 {@code HudElement} / {@code HudElementRegistry} 不存在，
 *       改成 NeoForge 的 {@link RenderGuiEvent.Post} 监听，
 *       {@code g} 从 {@code event.getGuiGraphics()} 取，类型是 {@link GuiGraphics}；</li>
 *   <li>{@code GuiGraphicsExtractor} 不存在，画字用 {@code g.drawString(...)}
 *       （26.3 叫 {@code g.text(...)}）；</li>
 *   <li>{@code g.guiWidth() / g.guiHeight()} 在 1.21.1 里<b>存在</b>（NeoForge 保留），
 *       但为了和项目里其它 HUD 统一，这里统一走 {@code mc.getWindow().getGuiScaled*()}；</li>
 *   <li>{@code DeferredBlock} 不是 {@code Block}，判断方块要 {@code state.is(TeaItems.TEA_SET.get())}。</li>
 * </ul>
 */
public final class TeaSetHud {

    /** 提示文字与准星的垂直偏移。 */
    private static final int OFFSET_Y = 20;

    private TeaSetHud() {
    }

    /**
     * HUD 渲染回调（在 {@code RenderGuiEvent.Post} 里被调用）。
     *
     * <p>整个方法包在 try/catch 里：面板只是附加信息，
     * 异常最多让这一帧不显示，绝不能影响游戏。</p>
     */
    static void onRenderGui(RenderGuiEvent.Post event) {
        try {
            render(event.getGuiGraphics());
        } catch (Throwable ignored) {
            // 附加信息面板永远不该影响游戏本身
        }
    }

    private static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.font == null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        Component line = null;

        // ① 茶盘上的盖碗
        if (state.is(TeaItems.TEA_SET.get()) && state.getValue(TeaSetBlock.GAIWAN)
                && mc.level.getBlockEntity(pos) instanceof TeaSetBlockEntity be) {
            line = gaiwanLine(be);
        }
        // ② 烧水壶
        else if (state.is(TeaItems.TEA_KETTLE.get())
                && mc.level.getBlockEntity(pos) instanceof TeaKettleBlockEntity kettle) {
            line = kettleLine(state, kettle);
        }

        if (line == null) return;

        int w = mc.font.width(line);
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        int x = (guiW - w) / 2;
        int y = guiH / 2 + OFFSET_Y;
        // 半透明底衬，避免压在明亮方块上看不清
        g.fill(x - 3, y - 2, x + w + 3, y + 10, 0x90000000);
        g.drawString(mc.font, line, x, y, 0xFFFFFFFF, true);
    }

    /** 盖碗那一行：空 / 干茶几份 / 茶汤几杯。 */
    private static Component gaiwanLine(TeaSetBlockEntity be) {
        TeaBrew brew = TeaBrew.decode(be.gaiwanBrew());
        if (brew != null) {
            return Component.translatable("teacraft.hud.gaiwan_brew",
                    Component.translatable(brew.kind().translationKey()),
                    brew.quality(), brew.servings());
        }
        TeaBrew dry = TeaBrew.decode(be.dryTea());
        if (dry != null) {
            return Component.translatable("teacraft.hud.gaiwan_dry",
                    Component.translatable(dry.kind().translationKey()),
                    dry.quality(), dry.servings());
        }
        return Component.translatable("teacraft.hud.gaiwan_empty");
    }

    /**
     * 烧水壶那一行：空壶 / 正在烧（百分比）/ 开水。
     *
     * <h2>为什么"已烧开"读方块状态而不是方块实体</h2>
     * <p>方块状态的同步是原版内置的、必定到达客户端；
     * 方块实体要服务端显式发包才同步，一旦漏发或时序不对，
     * 客户端就会一直显示旧值 —— 用户截图里"烧开了还显示正在烧水 0%"
     * 就是这个原因。</p>
     *
     * <p>所以：<b>烧开与否看 state（可靠）</b>，
     * 加热百分比的进度仍然需要方块实体（那个只影响数字，不影响结论）。</p>
     */
    private static Component kettleLine(BlockState state, TeaKettleBlockEntity kettle) {
        boolean boiled = state.hasProperty(TeaKettleBlock.BOILED)
                ? state.getValue(TeaKettleBlock.BOILED)
                : kettle.boiled();
        if (boiled) {
            return Component.translatable("teacraft.hud.kettle_boiled");
        }
        if (kettle.hasWater()) {
            int percent = Math.min(99,
                    kettle.heatTicks() * 100 / TeaKettleBlockEntity.BOIL_TICKS);
            return Component.translatable("teacraft.hud.kettle_heating", percent);
        }
        return Component.translatable("teacraft.hud.kettle_empty");
    }
}
