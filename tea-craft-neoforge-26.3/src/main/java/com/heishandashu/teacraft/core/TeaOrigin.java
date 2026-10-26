package com.heishandashu.teacraft.core;

/**
 * 鲜叶的产地（由采摘时所在群系的温度决定）。
 *
 * <p>需求：「不同的群系采摘的茶叶要赋予不同的名字，例如热带产茶叶、极寒地区产茶叶」。
 * 所以鲜叶这一个阶段按温度分 5 个变体，<b>贴图完全相同</b>（需求：每种茶 1 阶段的新鲜茶叶
 * 都一样，对应 1.png），只有名字与描述不同。</p>
 *
 * <p>温度分档（原版群系基准温度 {@code Biome#getBaseTemperature}）：</p>
 * <table border="1">
 *   <tr><th>档</th><th>温度区间</th><th>典型群系</th></tr>
 *   <tr><td>TROPICAL 热带</td><td>≥ 1.10</td><td>热带草原 (1.2)</td></tr>
 *   <tr><td>SUBTROPICAL 亚热带</td><td>0.85 – 1.10</td><td>丛林 (0.95)、竹林 (0.95)</td></tr>
 *   <tr><td>TEMPERATE 温带</td><td>0.55 – 0.85</td><td>平原 (0.8)、森林 (0.7)、沼泽 (0.8)</td></tr>
 *   <tr><td>BOREAL 寒带</td><td>0.15 – 0.55</td><td>针叶林 (0.25)、草甸 (0.5)</td></tr>
 *   <tr><td>POLAR 极寒</td><td>&lt; 0.15</td><td>雪原 (0.0)、积雪山坡 (-0.5)</td></tr>
 * </table>
 */
public enum TeaOrigin {

    TROPICAL("tropical", "热带", "Tropical", 1.10f),
    SUBTROPICAL("subtropical", "亚热带", "Subtropical", 0.85f),
    TEMPERATE("temperate", "温带", "Temperate", 0.55f),
    BOREAL("boreal", "寒带", "Boreal", 0.15f),
    POLAR("polar", "极寒", "Polar", Float.NEGATIVE_INFINITY);

    private final String id;
    private final String zhName;
    private final String enName;
    /** 这一档的温度下限（含）。 */
    private final float minTemperature;

    TeaOrigin(String id, String zhName, String enName, float minTemperature) {
        this.id = id;
        this.zhName = zhName;
        this.enName = enName;
        this.minTemperature = minTemperature;
    }

    public String id() { return id; }

    public String zhName() { return zhName; }

    public String enName() { return enName; }

    public float minTemperature() { return minTemperature; }

    /** 语言键：{@code teacraft.origin.<id>}。 */
    public String translationKey() { return "teacraft.origin." + id; }

    /**
     * 品质评级 1~5。
     *
     * <p>需求：「从最热带种植的茶，到最冷地种植的茶，品质从 1 至 5」。</p>
     * <pre>
     *   热带 1 → 亚热带 2 → 温带 3 → 寒带 4 → 极寒 5
     * </pre>
     * <p>越冷品质越高：生长慢、产量少，但内含物积累更充分。</p>
     */
    public int quality() {
        return switch (this) {
            case TROPICAL -> 1;
            case SUBTROPICAL -> 2;
            case TEMPERATE -> 3;
            case BOREAL -> 4;
            case POLAR -> 5;
        };
    }

    /**
     * 这个产地是否需要篝火保温才生长。
     *
     * <p>需求：「寒带和极寒带的茶树必须半径 2 格之内有篝火维持温度，否则不生长」。</p>
     */
    public boolean needsCampfire() {
        return this == BOREAL || this == POLAR;
    }

    /** 物品 id：{@code fresh_leaf_<id>}。 */
    public String itemId() { return "fresh_leaf_" + id; }

    /** 按群系基准温度取档，从热到冷依次匹配。 */
    public static TeaOrigin fromTemperature(float temperature) {
        for (TeaOrigin o : values()) {
            if (temperature >= o.minTemperature) return o;
        }
        return POLAR;
    }

    public static TeaOrigin byId(String id) {
        for (TeaOrigin o : values()) {
            if (o.id.equals(id)) return o;
        }
        return TEMPERATE;
    }
}
