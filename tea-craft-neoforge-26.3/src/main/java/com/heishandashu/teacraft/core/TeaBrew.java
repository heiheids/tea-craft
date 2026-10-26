package com.heishandashu.teacraft.core;

/**
 * 一份「泡好的茶汤」：什么茶、什么品质、还能倒几杯。
 *
 * <p>存在盖碗 / 茶杯物品的数据组件里（{@code teacraft:tea_brew}），
 * 用字符串编码 {@code "green|4|3"}，与项目里其它组件（{@code tea_origin}）保持一致的做法。</p>
 *
 * <p>为什么不用 Codec 记录类型：这份数据要频繁读（提示框、倒水、喝），
 * 而且字段就三个；字符串编码够用、也便于调试时直接看懂 NBT。</p>
 */
public record TeaBrew(TeaKind kind, int quality, int servings) {

    /** 分隔符。 */
    public static final String SEP = "|";

    /** 一杯的量：一次倒水消耗 1 份。 */
    public static final int PER_CUP = 1;

    public static TeaBrew of(TeaKind kind, int quality, int servings) {
        return new TeaBrew(kind, clampQuality(quality), Math.max(0, servings));
    }

    private static int clampQuality(int q) {
        return Math.max(1, Math.min(5, q));
    }

    /** 编码成组件字符串。 */
    public String encode() {
        return kind.id() + SEP + quality + SEP + servings;
    }

    /**
     * 从组件字符串还原；格式不对就返回 {@code null}（调用方按"没有茶汤"处理）。
     */
    public static TeaBrew decode(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split("\\" + SEP);
        if (parts.length < 3) return null;
        TeaKind kind = TeaKind.byId(parts[0]);
        if (kind == null) return null;
        try {
            int quality = clampQuality(Integer.parseInt(parts[1]));
            int servings = Math.max(0, Integer.parseInt(parts[2]));
            return new TeaBrew(kind, quality, servings);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isEmpty() {
        return servings <= 0;
    }

    /** 倒出一杯：份数 -1。 */
    public TeaBrew pourOne() {
        return new TeaBrew(kind, quality, Math.max(0, servings - 1));
    }

    /** 品质对应的效果时长（分钟），提示框用。 */
    public int minutes() {
        return 20 * quality;
    }
}
