package com.heishandashu.teacraft.core;

import java.util.Map;

/**
 * 「哪一步用什么工具、进去什么、出来什么」的<b>唯一映射表</b>。
 *
 * <p>放在一个类里而不是散在各个方块里：自检可以直接读这张表；改工艺只动这里一处，
 * 不会出现「方块改了、提示没改」的偏差。</p>
 *
 * <h2>2026-10-22 统一后的三条路线</h2>
 * <pre>
 * 绿茶  鲜叶 →[晒盘 萎凋]→ 萎凋叶 →[炒茶锅 杀青]→ 杀青叶
 *            →[揉茶匾 揉捻]→ 揉捻叶 →[炒茶锅 炒干]→ 炒干叶
 *            →[碳焙筐]→ 成品绿茶
 *
 * 红茶  鲜叶 →[晒盘 萎凋]→ 萎凋叶 →[揉茶匾 揉捻+发酵]→ 发酵叶
 *            →[炒茶锅 毛火]→ 毛火叶 →[碳焙筐]→ 成品红茶
 *
 * 单丛  鲜叶 →[晒盘 萎凋]→ 萎凋叶 →[晒盘 摇青]→ 做青叶
 *            →[晒盘 杀青]→ 杀青叶 →[揉茶匾 发酵]（<b>不揉捻</b>）
 *            →[碳焙筐]→ 成品凤凰单丛
 * </pre>
 *
 * <p>要点：</p>
 * <ul>
 *   <li>前两步（鲜叶、萎凋）三条路线共用同一个物品；</li>
 *   <li>第三步起分化：摇青→单丛、炒茶→绿茶、揉捻→红茶；</li>
 *   <li><b>单丛不揉捻</b>：杀青叶直接进揉茶匾发酵；</li>
 *   <li>所有工作方块一次最多 <b>32</b> 份。</li>
 * </ul>
 */
public final class TeaProcessing {

    /** 所有工作方块的统一容量上限。 */
    public static final int MAX_BATCH = 32;

    /**
     * 炒茶锅：收什么、出什么。
     *
     * <pre>
     *   萎凋叶        → green_3_killgreen   绿茶：炒茶（杀青）
     *   绿茶揉捻叶    → green_5_drying      绿茶：炒干
     *   红茶发酵叶    → black_5_firstbake   红茶：毛火
     * </pre>
     *
     * <p>单丛不进炒茶锅（杀青在晒盘完成，之后直接发酵、碳焙）。</p>
     */
    private static final Map<String, String> STIR = Map.of(
            "spread_2_withered", "green_3_killgreen",
            "green_4_rolling", "green_5_drying",
            "black_4_fermenting", "black_5_firstbake");

    /**
     * 碳焙筐：收什么、出什么（必须是走到最后一步的茶）。
     *
     * <pre>
     *   green_5_drying       → green_6_finished     成品绿茶
     *   black_5_firstbake    → black_6_finished     成品红茶
     *   dancong_6_roasting   → dancong_8_finished   成品凤凰单丛
     * </pre>
     */
    private static final Map<String, String> BAKE = Map.of(
            "green_5_drying", "green_6_finished",
            "black_5_firstbake", "black_6_finished",
            "dancong_6_roasting", "dancong_8_finished");

    private TeaProcessing() {
    }

    /* ---------------- 炒茶锅 ---------------- */

    /** 炒茶锅能不能收这个物品。 */
    public static boolean canStir(String inputId) {
        return inputId != null && STIR.containsKey(inputId);
    }

    /** 炒茶锅炒制后的产物 id；不能炒返回 null。 */
    public static String stirOutput(String inputId) {
        return inputId == null ? null : STIR.get(inputId);
    }

    /**
     * 这一批最多能放多少份。
     *
     * <p>需求：「所有工作方块都是一次可以最多放 32 个茶」。
     * 所以现在所有可炒的茶统一 32，不再分两档。不可炒返回 0。</p>
     */
    public static int stirCapacity(String inputId) {
        return canStir(inputId) ? MAX_BATCH : 0;
    }

    /* ---------------- 碳焙筐 ---------------- */

    /** 碳焙筐能不能收这个物品（必须是走到最后一步的茶）。 */
    public static boolean canBake(String inputId) {
        return inputId != null && BAKE.containsKey(inputId);
    }

    /** 碳焙筐焙完的产物 id；不能焙返回 null。 */
    public static String bakeOutput(String inputId) {
        return inputId == null ? null : BAKE.get(inputId);
    }

    /**
     * 碳焙产出哪一款<b>成品茶</b>。
     *
     * <p>判定依据是<b>这一份茶走过的工序</b>（物品 id 就编码了工序），不是猜：</p>
     * <ul>
     *   <li>单丛链（摇青 → 杀青 → 发酵）→ 凤凰单丛；</li>
     *   <li>绿茶链（炒茶 → 揉捻 → 炒干）→ 绿茶；</li>
     *   <li>红茶链（揉捻 → 发酵 → 毛火）→ 红茶。</li>
     * </ul>
     *
     * <p>成品上还带 {@code teacraft:tea_origin} 组件，产地差异留给将来的功效逻辑。</p>
     *
     * @param inputId 进入碳焙筐的物品 id
     * @param origin  这一批茶的产地（当前不参与款式判定）
     * @return 成品 id；不是可焙的茶则返回 {@code null}
     */
    public static String finishedFor(String inputId, TeaOrigin origin) {
        if (inputId == null) return null;
        // 先确认它确实是「能进碳焙筐的茶」
        if (!BAKE.containsKey(inputId)) return null;

        if (inputId.startsWith("dancong_")) return "dancong_8_finished";
        if (inputId.startsWith("green_")) return "green_6_finished";
        if (inputId.startsWith("black_")) return "black_6_finished";
        return null;
    }

    public static Map<String, String> stirTable() {
        return STIR;
    }

    public static Map<String, String> bakeTable() {
        return BAKE;
    }
}
