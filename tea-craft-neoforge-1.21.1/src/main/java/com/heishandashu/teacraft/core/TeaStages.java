package com.heishandashu.teacraft.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加工链的<b>唯一数据源</b>：每种茶每个阶段「叫什么、第几阶段」。
 *
 * <h2>共享前两步 + 第三步分化（2026-10-22 需求）</h2>
 * <pre>
 *   第 1 步  鲜叶（三类共享，按产地分 5 种物品）
 *   第 2 步  摊晾萎凋（三类共享，<b>同一个物品</b> spread_2_withered）
 *           ┌── 摇青 ──→ 凤凰单丛路线
 *   第 3 步 ─┼── 炒茶 ──→ 绿茶路线
 *           └── 揉捻 ──→ 红茶路线
 * </pre>
 *
 * <p>关键点：第 2 阶段是<b>一个共享物品</b>，不属于任何单一茶类。
 * {@link Stage#kind()} 对它返回 {@code null}；{@link #chain(TeaKind)} 把同一个
 * Stage 实例放进三条链的首位，保证「看起来是一条线，分叉在第三步」。</p>
 *
 * <p>三条路线各自的完整链：</p>
 * <pre>
 * 绿茶 鲜叶 → 摊晾萎凋 → 炒茶(杀青) → 揉捻 → 炒干 → 碳焙(成品)
 * 红茶 鲜叶 → 摊晾萎凋 → 揉捻 → 发酵 → 毛火 → 碳焙(成品)
 * 单丛 鲜叶 → 摊晾萎凋 → 摇青 → 杀青 → 揉捻 → 发酵焙火 → 碳焙(成品)
 * </pre>
 */
public final class TeaStages {

    /**
     * 一个加工阶段。
     *
     * @param kind    所属茶类；<b>共享阶段为 null</b>
     * @param number  阶段号（1 = 鲜叶，最后一个号 = 成品）
     * @param id      物品 id / 贴图名，例如 {@code green_3_killgreen}
     * @param process 工序名（语言键后缀，例如 {@code killgreen}）
     */
    public record Stage(TeaKind kind, int number, String id, String process) {

        /** 是否是三条路线共享的阶段（第 2 步）。 */
        public boolean isShared() { return kind == null; }

        /** 是否成品（链上最后阶段）。共享阶段永远不是成品。 */
        public boolean isFinished() { return kind != null && number == kind.lastStage(); }

        /** 工序名的语言键：{@code teacraft.process.<process>}。 */
        public String processKey() { return "teacraft.process." + process; }
    }

    /**
     * 三条路线共享的第 2 阶段：摊晾萎凋。
     *
     * <p>就是这一个物品 —— 鲜叶萎凋之后还不知道要走哪条路，
     * 第三步选了工具（摇青/炒茶/揉捻）才定型。</p>
     */
    public static final String SHARED_WITHER_ID = "spread_2_withered";

    private static final Stage SHARED_WITHER =
            new Stage(null, 2, SHARED_WITHER_ID, "withering");

    private static final List<Stage> ALL = new ArrayList<>();
    private static final Map<String, Stage> BY_ID = new LinkedHashMap<>();
    /** 每种茶的链，按阶段号升序，首位是共享的第 2 阶段。 */
    private static final Map<TeaKind, List<Stage>> CHAINS = new LinkedHashMap<>();

    static {
        ALL.add(SHARED_WITHER);
        BY_ID.put(SHARED_WITHER.id(), SHARED_WITHER);

        // ---------------- 绿茶：炒茶起步 ----------------
        chain(TeaKind.GREEN,
                SHARED_WITHER,
                stage(TeaKind.GREEN, 3, "green_3_killgreen", "killgreen"),
                stage(TeaKind.GREEN, 4, "green_4_rolling", "rolling"),
                stage(TeaKind.GREEN, 5, "green_5_drying", "drying"),
                stage(TeaKind.GREEN, 6, "green_6_finished", "finished"));

        // ---------------- 红茶：揉捻起步 ----------------
        chain(TeaKind.BLACK,
                SHARED_WITHER,
                stage(TeaKind.BLACK, 3, "black_3_rolling", "rolling"),
                stage(TeaKind.BLACK, 4, "black_4_fermenting", "fermenting"),
                stage(TeaKind.BLACK, 5, "black_5_firstbake", "firstbake"),
                stage(TeaKind.BLACK, 6, "black_6_finished", "finished"));

        // ---------------- 凤凰单丛：摇青起步，且<b>不揉捻</b> ----------------
        // 需求：鲜叶→萎凋→摇青→杀青→发酵(揉茶匾)→碳焙→成品。
        // 单丛没有揉捻工序，杀青叶直接进揉茶匾发酵。
        chain(TeaKind.DANCONG,
                SHARED_WITHER,
                stage(TeaKind.DANCONG, 3, "dancong_3_shaking", "shaking"),
                stage(TeaKind.DANCONG, 4, "dancong_4_killgreen", "killgreen"),
                // 发酵叶沿用 dancong_6_roasting 这个 id（贴图/语言都已存在），
                // 但含义明确为"发酵完成的单丛叶"，编号顺延为 5。
                stage(TeaKind.DANCONG, 5, "dancong_6_roasting", "fermenting"),
                stage(TeaKind.DANCONG, 6, "dancong_8_finished", "finished"));

        // ---------------- 旧版各自的第 2 阶段：保留注册但不再进链 ----------------
        // 删掉它们会让老存档里的这些物品变成空气，所以仍然注册，只是不在创造栏、
        // 也不在任何加工链上。玩家拿不到新的了。
        legacy("green_2_spreading", "spreading", TeaKind.GREEN);
        legacy("black_2_withering", "withering", TeaKind.BLACK);
        legacy("dancong_2_sunwither", "sunwither", TeaKind.DANCONG);
        legacy("dancong_5_rolling", "rolling", TeaKind.DANCONG);
    }

    private TeaStages() {
    }

    private static Stage stage(TeaKind kind, int number, String id, String process) {
        Stage s = new Stage(kind, number, id, process);
        ALL.add(s);
        BY_ID.put(id, s);
        return s;
    }

    /** 只注册、不进任何链的旧物品。 */
    private static void legacy(String id, String process, TeaKind kind) {
        Stage s = new Stage(kind, 2, id, process);
        ALL.add(s);
        BY_ID.put(id, s);
    }

    private static void chain(TeaKind kind, Stage... stages) {
        CHAINS.put(kind, List.of(stages));
    }

    /** 所有阶段（共享阶段 + 各链阶段 + 旧物品）。 */
    public static List<Stage> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** 某种茶的加工链（含共享的第 2 阶段，按阶段号升序）。 */
    public static List<Stage> chain(TeaKind kind) {
        return CHAINS.getOrDefault(kind, List.of());
    }

    public static Stage byId(String id) {
        return BY_ID.get(id);
    }

    /** 共享的第 2 阶段。 */
    public static Stage sharedWither() {
        return SHARED_WITHER;
    }
}
