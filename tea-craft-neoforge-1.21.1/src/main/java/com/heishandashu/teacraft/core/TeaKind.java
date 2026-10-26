package com.heishandashu.teacraft.core;

/**
 * 茶的种类（工艺路线）。
 *
 * <p>每种茶是一条加工链：鲜叶 → 第 2 阶段 → … → 成品。阶段总数各不相同
 * （绿茶/红茶 6 段，凤凰单丛 8 段），因为单丛多了「做青 / 初制」两道。</p>
 *
 * <p>这里的名字只是回退值，实际显示走语言文件
 * （{@code teacraft.kind.green} 等），保证中英双语都能翻。</p>
 */
public enum TeaKind {

    GREEN("green", "绿茶", "Green Tea", 6),
    BLACK("black", "红茶", "Black Tea", 6),
    DANCONG("dancong", "凤凰单丛", "Phoenix Dancong", 6);   // 摇青→杀青→发酵→碳焙，共 5 段

    private final String id;
    private final String zhName;
    private final String enName;
    private final int lastStage;

    TeaKind(String id, String zhName, String enName, int lastStage) {
        this.id = id;
        this.zhName = zhName;
        this.enName = enName;
        this.lastStage = lastStage;
    }

    public String id() { return id; }

    /** 成品所在阶段号（也是这条链的总阶段数）。 */
    public int lastStage() { return lastStage; }

    /** 语言键：{@code teacraft.kind.<id>}。 */
    public String translationKey() { return "teacraft.kind." + id; }

    public String zhName() { return zhName; }

    public String enName() { return enName; }

    public static TeaKind byId(String id) {
        for (TeaKind k : values()) {
            if (k.id.equals(id)) return k;
        }
        return GREEN;
    }
}
