package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.block.TeaBushBlock;
import com.heishandashu.teacraft.block.TeaBushBlockEntity;
import com.heishandashu.teacraft.block.TeaRollingTrayBlock;
import com.heishandashu.teacraft.block.TeaTrayBlock;
import com.heishandashu.teacraft.block.TeaTrayBlockEntity;
import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.item.TeaItems;
import com.heishandashu.teacraft.item.TeaTrayItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 晒盘的信息面板：准星对着晒盘、或手持晒盘正在摇青时显示。
 *
 * <h2>两行文字必须不同</h2>
 * <p>第一版两行都用了 {@code tray_stage.N}，看起来一模一样。现在：</p>
 * <ul>
 *   <li>第一行 = <b>工艺步骤</b>（晒盘 · 已萎凋）</li>
 *   <li>第二行 = <b>内容物</b>（热带 · 3 / 4 份）</li>
 * </ul>
 * <p>面板宽度按两行文字的实测宽度算（{@code font.width}），所以不会超出底框。</p>
 *
 * <h2>两个显示场景</h2>
 * <ol>
 *   <li>准星指向方块 → 阶段、内容物、数量、萎凋进度；</li>
 *   <li>手持晒盘长按摇青 → 「摇青中 N/3」与这一下的 3 秒进度。</li>
 * </ol>
 *
 * <p>整体包在 try/catch 里：面板只是附加信息，异常最多让这一帧不显示。</p>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <ul>
 *   <li>不再实现 Fabric 的 {@code HudElement}，改成静态的
 *       {@link #onRenderGui(RenderGuiEvent.Post)} 监听器，
 *       由 {@code TeaCraftClient} 挂到 {@code NeoForge.EVENT_BUS}；</li>
 *   <li>{@code GuiGraphicsExtractor} → {@link GuiGraphics}；</li>
 *   <li>{@code g.text(...)} → {@code g.drawString(...)}；</li>
 *   <li>数据组件要写 {@code TeaComponents.TRAY_SHAKES.get()}（DeferredHolder）；</li>
 *   <li>方块/物品是 {@code DeferredBlock / DeferredItem}，比较时要 {@code .get()}。</li>
 * </ul>
 */
public final class TeaTrayHud {

    private static final int PAD = 7;
    private static final int LINE = 11;
    private static final int MIN_W = 118;
    private static final int MAX_W = 190;
    private static final int BG = 0xC0101010;
    private static final int BORDER = 0xFF9AA0A6;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFBFC7CF;
    private static final int BAR_BG = 0xFF2A2A2A;

    private TeaTrayHud() {
    }

    /**
     * HUD 渲染回调（{@code RenderGuiEvent.Post}）。
     *
     * <p>整个方法包在 try/catch 里：面板只是附加信息，异常最多让这一帧不显示。</p>
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

        // 场景二：正在摇青（优先，此时准星可能指向任何地方）
        if (mc.player.isUsingItem() && mc.player.getUseItem().is(TeaItems.TEA_TRAY_ITEM.get())) {
            renderShaking(g, mc, mc.player.getUseItem());
            return;
        }

        // 场景一：准星指向晒盘 / 揉茶匾
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        // 茶树：太冷时提示放篝火
        if (state.is(TeaItems.TEA_BUSH.get())) {
            renderColdBushHint(g, mc, pos);
            return;
        }

        if (state.is(TeaItems.TEA_ROLLING_TRAY.get())) {
            renderRollingTray(g, mc, pos, state);
            return;
        }
        if (!state.is(TeaItems.TEA_TRAY.get())) return;

        int count = state.getValue(TeaTrayBlock.COUNT);
        int stage = state.getValue(TeaTrayBlock.STAGE);
        TeaTrayBlockEntity tray = mc.level.getBlockEntity(pos) instanceof TeaTrayBlockEntity be ? be : null;
        TeaOrigin origin = tray == null ? TeaOrigin.TEMPERATE : tray.origin();
        long now = mc.level.getGameTime();

        Component line1 = Component.translatable("teacraft.hud.tray_step",
                Component.translatable("teacraft.tray_stage." + stage));
        Component line2 = count > 0
                ? Component.translatable("teacraft.hud.tray_content",
                Component.translatable(origin.translationKey()), count, TeaTrayBlock.MAX_LEAVES)
                : Component.translatable("teacraft.hud.tray_empty_hint");

        // 各阶段的进度：新茶数萎凋、摇青数杀青，已完成的两段画满
        float progress = -1f;
        Component barLabel = Component.empty();
        if (stage == TeaTrayBlock.STAGE_FRESH) {
            progress = tray == null ? -1f : tray.stageProgress(now, TeaTrayBlock.WITHER_TICKS);
            barLabel = Component.translatable("teacraft.hud.withering", percent(progress));
        } else if (stage == TeaTrayBlock.STAGE_SHAKEN) {
            // 摇青盘放下后要再等 5 分钟杀青 —— 这一段以前完全没有反馈，只能干等
            progress = tray == null ? -1f : tray.stageProgress(now, TeaTrayBlock.KILLGREEN_TICKS);
            barLabel = Component.translatable("teacraft.hud.killgreening", percent(progress));
        } else if (stage == TeaTrayBlock.STAGE_WITHERED) {
            progress = 1f;
            barLabel = Component.translatable("teacraft.hud.withering", 100);
        } else if (stage == TeaTrayBlock.STAGE_KILLGREEN) {
            progress = 1f;
            barLabel = Component.translatable("teacraft.hud.killgreening", 100);
        }

        drawPanel(g, mc, line1, line2, barLabel, progress, 0xFF6FBF4F);
    }

    /**
     * 准星对着「太冷的茶树」时，在准星下方提示放篝火。
     *
     * <p>需求：「缺少篝火增温的茶树，玩家鼠标放上去会有提示词：
     * 它太冷了，放一个篝火在旁边吧。」</p>
     *
     * <p>方块本身没有 tooltip，所以只能画在 HUD 上 ——
     * 和晒盘面板同一套机制：读 {@code mc.hitResult} 判断准星指向哪个方块。</p>
     */
    private static void renderColdBushHint(GuiGraphics g, Minecraft mc, BlockPos pos) {
        renderBushPanel(g, mc, pos);
    }

    /**
     * 茶树面板：成熟倒计时 + 进度条 + 暂停提示。
     *
     * <p>需求：「增加茶树成熟倒计时显示。太冷暂停生长，有取暖后恢复生长。」
     * 所以这里除了倒计时，还要在暂停时明确写出「生长暂停」的提示。</p>
     *
     * <h2>为什么能显示倒计时</h2>
     * <p>茶树现在带 {@link TeaBushBlockEntity}，里面存 {@code remainingTicks}。
     * 客户端通过 {@code level.getBlockEntity(pos)} 直接读到它（值随
     * {@code getUpdateTag} 同步过来），不需要服务端每 tick 发包。</p>
     *
     * <h2>三种状态</h2>
     * <ol>
     *   <li><b>已成熟</b>（age 3）→ 显示「可以采茶了」；</li>
     *   <li><b>暂停</b>（太冷、缺篝火）→ 显示剩余时间 + 「太冷，生长暂停」；</li>
     *   <li><b>生长中</b> → 显示剩余时间与百分比。</li>
     * </ol>
     */
    private static void renderBushPanel(GuiGraphics g, Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.is(TeaItems.TEA_BUSH.get())) return;

        int age = state.getValue(TeaBushBlock.AGE);
        boolean cold = TeaBushBlock.isTooCold(mc.level, pos);

        Component line1;
        Component line2;
        float progress;

        if (age >= TeaBushBlock.AGE_RIPE) {
            line1 = Component.translatable("teacraft.hud.bush_ripe");
            line2 = Component.translatable("teacraft.hud.bush_ripe_hint");
            progress = 1f;
        } else if (age < TeaBushBlock.AGE_TREE) {
            // 还没成树：随机刻在长，没有确定的倒计时
            line1 = Component.translatable("teacraft.hud.bush_growing_stage",
                    Component.translatable("teacraft.bush_age." + age));
            line2 = cold
                    ? Component.translatable("teacraft.hud.bush_too_cold")
                    : Component.translatable("teacraft.hud.bush_growing_hint");
            progress = -1f;
        } else {
            // 成树，等时间到点
            TeaBushBlockEntity be = TeaBushBlock.bushEntityAt(mc.level, pos);
            long remain = be == null ? -1 : be.remainingTicks();
            float prog = be == null ? -1f : be.progress();

            line1 = remain > 0
                    ? Component.translatable("teacraft.hud.bush_countdown", formatDuration(remain))
                    : Component.translatable("teacraft.hud.bush_growing_stage",
                    Component.translatable("teacraft.bush_age." + age));
            line2 = cold
                    ? Component.translatable("teacraft.hud.bush_paused")
                    : Component.translatable("teacraft.hud.bush_waiting");
            progress = prog;
        }

        drawSimplePanel(g, mc, line1, line2, progress,
                cold ? 0xFF4FC3F7 : 0xFF7BC67B);
    }

    /**
     * 把 tick 数变成「1 天 3 小时」这样的可读文本。
     *
     * <p>一个游戏日 = 24000 tick = 20 分钟现实时间；一小时 = 1000 tick。</p>
     */
    static String formatDuration(long ticks) {
        if (ticks <= 0) return "0";
        long totalMinutes = ticks / 1200L;              // 24000 tick = 20 min ⇒ 1200 tick/min
        long days = totalMinutes / 20L;
        long hours = (totalMinutes % 20L) * 24L / 20L;
        long minutes = totalMinutes % (20L * 60L) % 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(Component.translatable("teacraft.unit.day").getString());
        }
        if (hours > 0) {
            sb.append(hours).append(Component.translatable("teacraft.unit.hour").getString());
        }
        if (days == 0) {
            sb.append(minutes).append(Component.translatable("teacraft.unit.minute").getString());
        }
        if (sb.length() == 0) {
            sb.append(Component.translatable("teacraft.unit.less_than_minute").getString());
        }
        return sb.toString();
    }

    /** 两行文字 + 可选进度条的小面板（茶树用）。 */
    private static void drawSimplePanel(GuiGraphics g, Minecraft mc,
                                        Component line1, Component line2,
                                        float progress, int barColor) {
        Font font = mc.font;
        int textW = Math.max(font.width(line1), font.width(line2));
        int panelW = Math.max(130, Math.min(210, textW + PAD * 2));
        int panelH = PAD * 2 + LINE * 2 + 4;

        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        int x = (guiW - panelW) / 2;
        int y = guiH - 132;

        g.fill(x, y, x + panelW, y + panelH, BG);
        g.fill(x, y, x + panelW, y + 1, BORDER);
        g.fill(x, y + panelH - 1, x + panelW, y + panelH, BORDER);
        g.fill(x, y, x + 1, y + panelH, BORDER);
        g.fill(x + panelW - 1, y, x + panelW, y + panelH, BORDER);

        int maxTextW = panelW - PAD * 2;
        int row1 = y + PAD;
        int row2 = row1 + LINE;

        g.drawString(font, fit(font, line1, maxTextW), x + PAD, row1, TEXT, true);
        g.drawString(font, fit(font, line2, maxTextW), x + PAD, row2, TEXT_DIM, true);

        if (progress >= 0f) {
            int barW = maxTextW;
            int barY = row2 + LINE + 1;
            g.fill(x + PAD, barY, x + PAD + barW, barY + 3, BAR_BG);
            int fillW = (int) (barW * Math.max(0f, Math.min(1f, progress)));
            if (fillW > 0) {
                g.fill(x + PAD, barY, x + PAD + fillW, barY + 3, barColor);
            }
        }
    }

    /**
     * 揉茶匾的信息面板：揉捻进度（x/3）与发酵进度。
     *
     * <p>需求：「发酵进度条也没有」—— 揉捻完成进入发酵后，面板要显示发酵百分比。</p>
     */
    private static void renderRollingTray(GuiGraphics g, Minecraft mc, BlockPos pos, BlockState state) {
        int count = state.getValue(TeaRollingTrayBlock.COUNT);
        int stage = state.getValue(TeaRollingTrayBlock.STAGE);
        TeaTrayBlockEntity be = TeaRollingTrayBlock.trayAt(mc.level, pos);
        TeaOrigin origin = be == null ? TeaOrigin.TEMPERATE : be.origin();
        long now = mc.level.getGameTime();

        Component line1 = Component.translatable("teacraft.hud.rolling_step",
                Component.translatable("teacraft.rolling_stage." + stage));
        Component line2 = count > 0
                ? Component.translatable("teacraft.hud.tray_content",
                Component.translatable(origin.translationKey()), count, TeaRollingTrayBlock.MAX_LEAVES)
                : Component.translatable("teacraft.hud.rolling_empty_hint");

        float progress = -1f;
        Component barLabel = Component.empty();
        switch (stage) {
            case TeaRollingTrayBlock.STAGE_KILLGREEN -> {
                int rolls = be == null ? 0 : be.rolls();
                float rolling = be == null ? -1f
                        : be.rollProgress(now, TeaRollingTrayBlock.ROLL_TICKS);
                if (rolling >= 0f) {
                    // 正在揉这一下：显示这一下的 3 秒进度
                    progress = rolling;
                    barLabel = Component.translatable("teacraft.hud.rolling_now",
                            Math.min(rolls + 1, TeaRollingTrayBlock.ROLLS_NEEDED),
                            TeaRollingTrayBlock.ROLLS_NEEDED, percent(rolling));
                } else {
                    progress = Math.min(1f, rolls / (float) TeaRollingTrayBlock.ROLLS_NEEDED);
                    barLabel = Component.translatable("teacraft.hud.rolling_progress_label",
                            Math.min(rolls, TeaRollingTrayBlock.ROLLS_NEEDED), TeaRollingTrayBlock.ROLLS_NEEDED);
                }
            }
            case TeaRollingTrayBlock.STAGE_ROLLED -> {
                progress = be == null ? -1f : be.stageProgress(now, TeaRollingTrayBlock.FERMENT_TICKS);
                barLabel = Component.translatable("teacraft.hud.fermenting", percent(progress));
            }
            case TeaRollingTrayBlock.STAGE_FERMENTED -> {
                progress = 1f;
                barLabel = Component.translatable("teacraft.hud.fermenting", 100);
            }
            default -> { }
        }

        drawPanel(g, mc, line1, line2, barLabel, progress, 0xFFC8733C);
    }

    private static int percent(float progress) {
        return progress < 0f ? 0 : Math.round(progress * 100f);
    }

    private static void renderShaking(GuiGraphics g, Minecraft mc, ItemStack stack) {
        int shakes = stack.getOrDefault(TeaComponents.TRAY_SHAKES.get(), 0);
        int total = TeaTrayItem.SHAKES_NEEDED;

        // 连续摇青：总时长是 CONTINUOUS_USE_TICKS，这一下的进度要按
        // elapsed % SHAKE_TICKS 算，否则进度条会一路涨到 100% 再不动。
        int remaining = mc.player.getUseItemRemainingTicks();
        int elapsed = TeaTrayItem.CONTINUOUS_USE_TICKS - remaining;
        if (elapsed < 0) elapsed = 0;
        float progress = (elapsed % TeaTrayItem.SHAKE_TICKS) / (float) TeaTrayItem.SHAKE_TICKS;

        Component line1 = Component.translatable("teacraft.hud.shake_title");
        Component line2 = Component.translatable("teacraft.hud.shake_count", shakes + 1, total);
        Component barLabel = Component.translatable("teacraft.hud.shake_bar", percent(progress));

        drawPanel(g, mc, line1, line2, barLabel, progress, 0xFFD8A03C);

        // 绿色操作提示画在面板「上方」。
        // 以前这句是 sendSystemMessage(..., true) 走原版动作栏，正好落在面板的绘制区域内，
        // 被面板挡住看不见；现在由 HUD 自己画，位置可控（并在 use() 里去掉了重复的动作栏提示）。
        Component hint = Component.translatable("teacraft.hud.shake_hint");
        int hintW = mc.font.width(hint);
        int hintX = (mc.getWindow().getGuiScaledWidth() - hintW) / 2;
        int hintY = mc.getWindow().getGuiScaledHeight() - 132 - LINE - 6;
        g.drawString(mc.font, hint, hintX, hintY, 0xFF5BE05B);
    }

    /** 面板宽度按实际文字宽度算，保证不会超出底框。 */
    private static void drawPanel(GuiGraphics g, Minecraft mc,
                                  Component line1, Component line2, Component barLabel,
                                  float progress, int barColor) {
        Font font = mc.font;
        int textW = Math.max(font.width(line1), font.width(line2));
        boolean hasLabel = barLabel != null && !barLabel.getString().isEmpty();
        if (hasLabel) {
            textW = Math.max(textW, font.width(barLabel));
        }
        int panelW = Math.max(MIN_W, Math.min(MAX_W, textW + PAD * 2));
        // 行数固定为 3 行文字 + 1 条进度条，高度必须跟着算，否则最后一行的文字会被画到框外
        int panelH = PAD * 2 + LINE * 3 + 3 + 2;

        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        int x = (guiW - panelW) / 2;
        // 面板整体上移：原版动作栏提示文字在 guiH-72 一带，面板占到那里就会把它盖住
        int y = guiH - 132;

        g.fill(x, y, x + panelW, y + panelH, BG);
        g.fill(x, y, x + panelW, y + 1, BORDER);
        g.fill(x, y + panelH - 1, x + panelW, y + panelH, BORDER);
        g.fill(x, y, x + 1, y + panelH, BORDER);
        g.fill(x + panelW - 1, y, x + panelW, y + panelH, BORDER);

        // 固定行距：每一行都算出来，绝不靠 panelH 反推（第一版就是那样重叠的）
        int maxTextW = panelW - PAD * 2;
        int row1 = y + PAD;
        int row2 = row1 + LINE;
        int row3 = row2 + LINE;
        int barY = row3 + LINE;

        g.drawString(font, fit(font, line1, maxTextW), x + PAD, row1, TEXT);
        g.drawString(font, fit(font, line2, maxTextW), x + PAD, row2, TEXT_DIM);

        if (hasLabel) {
            g.drawString(font, fit(font, barLabel, maxTextW), x + PAD, row3, TEXT_DIM);
        }
        int barW = maxTextW;
        g.fill(x + PAD, barY, x + PAD + barW, barY + 3, BAR_BG);
        if (progress > 0f) {
            int filled = Math.round(barW * Math.max(0f, Math.min(1f, progress)));
            if (filled > 0) g.fill(x + PAD, barY, x + PAD + filled, barY + 3, barColor);
        }
    }

    /**
     * 超宽就截断加省略号。
     *
     * <p>{@code font.width} 之外还留一点余量，避免中文字形比测量值略宽时压到边框。</p>
     */
    private static Component fit(Font font, Component text, int maxWidth) {
        if (text == null || font.width(text) <= maxWidth) return text;
        String s = text.getString();
        String ellipsis = "…";
        int ellW = font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String next = sb.toString() + s.charAt(i);
            if (font.width(next) + ellW > maxWidth) break;
            sb.append(s.charAt(i));
        }
        return Component.literal(sb + ellipsis);
    }
}
