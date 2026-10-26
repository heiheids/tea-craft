package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.item.TeaGuideItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 《茶工艺说明》的分页界面。
 *
 * <h2>为什么不用 Menu</h2>
 * <p>纯展示内容，没有物品槽位，所以直接继承 {@link Screen}：不需要 MenuType、
 * 不需要网络同步，客户端自己就能画。</p>
 *
 * <h2>内容从哪来</h2>
 * <p>正文全部放在语言文件里（{@code teacraft.guide.p0} ~ {@code p10}），
 * 这样中英文可以各写一份，代码里不出现硬编码文本。</p>
 *
 * <h2>26.3 绘制分工</h2>
 * <ul>
 *   <li>{@code extractBackground} —— 纸张、边框、页码、翻页箭头</li>
 *   <li>{@code extractLabels} —— 标题与正文</li>
 * </ul>
 */
public class TeaGuideScreen extends Screen {

    /** 书本面板尺寸。 */
    private static final int W = 300;
    private static final int H = 200;

    /** 正文起始位置（面板内）。 */
    private static final int PAD = 14;

    /** 每页最多几行（中文一行约 20 字，够放下所有条目）。 */
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
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();

        // 纸张
        g.fill(x, y, x + W, y + H, PAPER);
        // 外框
        g.fill(x, y, x + W, y + 2, BORDER);
        g.fill(x, y + H - 2, x + W, y + H, BORDER);
        g.fill(x, y, x + 2, y + H, BORDER);
        g.fill(x + W - 2, y, x + W, y + H, BORDER);
        // 内侧细线
        g.fill(x + 5, y + 5, x + W - 5, y + 6, PAPER_EDGE);
        g.fill(x + 5, y + H - 6, x + W - 5, y + H - 5, PAPER_EDGE);

        // 翻页箭头（画成三角形）
        boolean hasPrev = page > 0;
        boolean hasNext = page < TeaGuideItem.PAGE_COUNT - 1;
        drawArrow(g, x - 22, y + H / 2, true, hasPrev);
        drawArrow(g, x + W + 8, y + H / 2, false, hasNext);

        // 页码
        Component pageText = Component.translatable("teacraft.guide.page",
                page + 1, TeaGuideItem.PAGE_COUNT);
        int pw = this.font.width(pageText);
        g.text(this.font, pageText, x + (W - pw) / 2, y + H - 20, TITLE);
    }

    /** 画一个三角形箭头（用逐行 fill 拼出来，避免依赖贴图）。 */
    private void drawArrow(GuiGraphicsExtractor g, int ax, int ay, boolean left, boolean enabled) {
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                  float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int x = left() + PAD;
        int y = top() + PAD;

        // 第一页画大标题；其它页在顶部画该页的小标题
        if (page == 0) {
            g.text(this.font, Component.translatable("teacraft.guide.title"), x, y, TITLE);
            y += 18;
        } else {
            g.text(this.font, Component.translatable(pageKey() + ".head"), x, y, TITLE);
            y += 16;
        }

        // 正文（按行切分，超宽自动折行）
        String body = Component.translatable(pageKey()).getString();
        drawWrapped(g, body, x, y, W - PAD * 2);
    }

    /** 画自动折行的正文，返回绘制结束后的 y。 */
    private int drawWrapped(GuiGraphicsExtractor g, String body, int x, int y, int maxWidth) {
        int lineHeight = 10;
        int lines = 0;
        for (String raw : body.split("\n")) {
            for (String piece : wrap(raw, maxWidth)) {
                if (lines >= MAX_LINES) return y;
                g.text(this.font, Component.literal(piece), x, y, TEXT);
                y += lineHeight;
                lines++;
            }
        }
        return y;
    }

    /** 按像素宽度把一行切成多行（中文按字符切）。 */
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
                // 超宽了：退掉刚加的那个字，换行
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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int x = left();
        int y = top();
        int my = (int) event.y();

        // 点左箭头 / 点右箭头；也支持点整个面板左侧/右侧空白翻页
        if (my >= y + 20 && my <= y + H - 20) {
            if (event.x() < x - 4) {
                return turn(-1);
            }
            if (event.x() > x + W + 4) {
                return turn(1);
            }
        }
        return super.mouseClicked(event, doubleClick);
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        // 左右方向键 / A、D 翻页
        if (key == 263 || key == 65) {   // 左方向键 / A
            return turn(-1);
        }
        if (key == 262 || key == 68) {   // 右方向键 / D
            return turn(1);
        }
        return super.keyPressed(event);
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
        // 看书时不要暂停游戏（单人也照常跑时间）
        return false;
    }
}
