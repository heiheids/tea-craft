package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.item.TeaGuideItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 《茶工艺说明》的分页界面（NeoForge 1.21.1）。
 *
 * <h2>与 26.3 的差别</h2>
 * <ul>
 *   <li>绘制入口是 {@code render(GuiGraphics, int, int, float)}（26.3 拆成了
 *       {@code extractBackground} + {@code extractRenderState} + {@code GuiGraphicsExtractor}）。</li>
 *   <li>画文字用 {@code g.drawString(font, ...)}（26.3 是 {@code g.text(...)}）。</li>
 *   <li>输入事件是原始参数：{@code keyPressed(int,int,int)}、
 *       {@code mouseClicked(double,double,int)}（26.3 是 {@code KeyEvent}/{@code MouseButtonEvent} 记录类）。</li>
 * </ul>
 */
public class TeaGuideScreen extends Screen {

    /** 书本面板尺寸。 */
    private static final int W = 300;
    private static final int H = 200;

    /** 正文内边距。 */
    private static final int PAD = 14;

    /** 每页最多几行。 */
    private static final int MAX_LINES = 13;

    private static final int PAPER = 0xFFF5EEDC;
    private static final int PAPER_EDGE = 0xFFC9B98F;
    private static final int BORDER = 0xFF8A7A55;
    private static final int TEXT = 0xFF3A2C1A;
    private static final int TITLE = 0xFF6B4A1F;
    private static final int ARROW = 0xFF8A7A55;
    private static final int ARROW_OFF = 0xFFD8CCB0;

    /** 当前页（0 起）。 */
    private int page = 0;

    public TeaGuideScreen() {
        super(Component.translatable("item.teacraft.tea_guide"));
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int x = left();
        int y = top();

        // 纸张与边框
        g.fill(x, y, x + W, y + H, PAPER);
        g.fill(x, y, x + W, y + 2, BORDER);
        g.fill(x, y + H - 2, x + W, y + H, BORDER);
        g.fill(x, y, x + 2, y + H, BORDER);
        g.fill(x + W - 2, y, x + W, y + H, BORDER);
        g.fill(x + 5, y + 5, x + W - 5, y + 6, PAPER_EDGE);
        g.fill(x + 5, y + H - 6, x + W - 5, y + H - 5, PAPER_EDGE);

        // 翻页箭头
        boolean hasPrev = page > 0;
        boolean hasNext = page < TeaGuideItem.PAGE_COUNT - 1;
        drawArrow(g, x - 22, y + H / 2, true, hasPrev);
        drawArrow(g, x + W + 8, y + H / 2, false, hasNext);

        // 页码
        Component pageText = Component.translatable("teacraft.guide.page",
                page + 1, TeaGuideItem.PAGE_COUNT);
        int pw = this.font.width(pageText);
        g.drawString(this.font, pageText, x + (W - pw) / 2, y + H - 20, TITLE);

        // 标题：首页画大标题，其它页画页眉
        int tx = x + PAD;
        int ty = y + PAD;
        if (page == 0) {
            g.drawString(this.font, Component.translatable("teacraft.guide.title"),
                    tx, ty, TITLE);
            ty += 18;
        } else {
            g.drawString(this.font, Component.translatable(pageKey() + ".head"),
                    tx, ty, TITLE);
            ty += 16;
        }

        // 正文
        String body = Component.translatable(pageKey()).getString();
        drawWrapped(g, body, tx, ty, W - PAD * 2);
    }

    /** 画一个三角形箭头（逐行 fill 拼出来，不依赖贴图）。 */
    private void drawArrow(GuiGraphics g, int ax, int ay, boolean left, boolean enabled) {
        int color = enabled ? ARROW : ARROW_OFF;
        int size = 9;
        for (int i = 0; i < size; i++) {
            int w = size - i;
            int yy = ay - size / 2 + i;
            if (left) {
                g.fill(ax + i, yy, ax + i + w, yy + 1, color);
            } else {
                g.fill(ax + size - i - w, yy, ax + size - i, yy + 1, color);
            }
        }
    }

    /** 画自动折行的正文。 */
    private void drawWrapped(GuiGraphics g, String body, int x, int y, int maxWidth) {
        int lineHeight = 10;
        int lines = 0;
        for (String raw : body.split("\n")) {
            for (String piece : wrap(raw, maxWidth)) {
                if (lines >= MAX_LINES) return;
                g.drawString(this.font, Component.literal(piece), x, y, TEXT);
                y += lineHeight;
                lines++;
            }
        }
    }

    /** 按像素宽度把一行切成多行。 */
    private java.util.List<String> wrap(String line, int maxWidth) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (line.isEmpty()) {
            out.add("");
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            cur.append(c);
            if (this.font.width(cur.toString()) > maxWidth) {
                cur.setLength(cur.length() - 1);
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** 第 page 页正文的语言键。 */
    private String pageKey() {
        return "teacraft.guide.p" + page;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left();
        int y = top();
        int my = (int) mouseY;

        if (my >= y + 20 && my <= y + H - 20) {
            if (mouseX < x - 4) {
                return turn(-1);
            }
            if (mouseX > x + W + 4) {
                return turn(1);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                double scrollX, double scrollY) {
        if (scrollY != 0) {
            return turn(scrollY > 0 ? -1 : 1);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 左右方向键 / A、D 翻页
        if (keyCode == 263 || keyCode == 65) {
            return turn(-1);
        }
        if (keyCode == 262 || keyCode == 68) {
            return turn(1);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 翻页，越界就不动。 */
    private boolean turn(int delta) {
        int next = page + delta;
        if (next < 0 || next >= TeaGuideItem.PAGE_COUNT) return true;
        page = next;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
