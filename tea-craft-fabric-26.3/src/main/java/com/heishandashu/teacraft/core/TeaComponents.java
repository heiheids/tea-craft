package com.heishandashu.teacraft.core;

import com.mojang.serialization.Codec;
import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.core.TeaOrigin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * 本模组的自定义数据组件（1.20.5 起 NBT 被组件取代）。
 *
 * <p>只为一件事存在：<b>晒盘被「抱起」时要把盘里装了什么带走</b>。
 * 方块状态能表达「盘里 3 份、已萎凋」，但物品一旦离开世界就只剩 id，
 * 必须靠组件把「份数 / 阶段 / 摇了几次」存进物品栈。</p>
 *
 * <p>三个组件都是 <b>persistent（会写进存档）+ networkSynchronized（会同步到客户端）</b>，
 * 少了后者客户端看到的永远是默认值，工具提示与图标就会对不上。</p>
 */
public final class TeaComponents {

    /** 盘里的茶叶份数（0~4）。 */
    public static final DataComponentType<Integer> TRAY_COUNT = register("tray_count",
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** 茶叶状态：0 空 / 1 新茶 / 2 萎凋 / 3 摇青。 */
    public static final DataComponentType<Integer> TRAY_STAGE = register("tray_stage",
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** 盘里茶叶的产地 id（例如 {@code tropical}）。 */
    public static final DataComponentType<String> TRAY_ORIGIN = register("tray_origin",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /**
     * 茶叶物品的产地（例如 {@code tropical}）。
     *
     * <p>需求：「将来产自不同地区的茶叶做出来的成茶，饮用效果是有区别的」。
     * 为了不把物品数量乘 5，阶段物品只注册一份，产地记在组件里 —— 显示名与
     * 后续工序都读它（与药水、山羊角的做法一致）。</p>
     */
    public static final DataComponentType<String> TEA_ORIGIN = register("tea_origin",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** 已经摇了几次（0~3）。 */
    public static final DataComponentType<Integer> TRAY_SHAKES = register("tray_shakes",
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * 盖碗 / 茶杯里的茶汤。
     *
     * <p>编码为 {@code "kind|quality|servings"}，例如 {@code "green|4|3"}。
     * 用字符串而不是 Codec 记录：字段只有三个，字符串便于直接在 NBT 里看懂。</p>
     */
    public static final DataComponentType<String> TEA_BREW = register("tea_brew",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** 烧水壶里是不是已烧开的热水（存在时为 true）。 */
    public static final DataComponentType<Boolean> HOT_WATER = register("hot_water",
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /**
     * 盖碗里尚未注水的干茶，编码 {@code "kind|quality|count"}。
     *
     * <p>与 {@link #TEA_BREW} 分开存：一个盖碗可以「有干茶但还没注水」，
     * 也可以「已经泡成茶汤」，两者不会同时存在，但需要区分。</p>
     */
    public static final DataComponentType<String> DRY_TEA = register("dry_tea",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /* ---------------- 茶盘「整只拿起」要带走的状态 ---------------- */

    /**
     * 茶盘上有没有盖碗。
     *
     * <p>拿起茶盘时，盘上的东西必须记在物品上，否则放下就全空了。
     * 方块状态带不走，所以这几项单独存组件。</p>
     */
    public static final DataComponentType<Boolean> SET_GAIWAN = register("set_gaiwan",
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /** 茶盘 A 位杯子状态：{@code none / empty / full}。 */
    public static final DataComponentType<String> SET_CUP_A = register("set_cup_a",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** 茶盘 B 位杯子状态。 */
    public static final DataComponentType<String> SET_CUP_B = register("set_cup_b",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** 茶盘 C 位杯子状态。 */
    public static final DataComponentType<String> SET_CUP_C = register("set_cup_c",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** 茶盘朝向（{@code north/east/south/west}），放回去时保持原朝向。 */
    public static final DataComponentType<String> SET_FACING = register("set_facing",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** 烧水壶朝向，拿起再放下时保持壶嘴方向。 */
    public static final DataComponentType<String> KETTLE_FACING = register("kettle_facing",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    private TeaComponents() {
    }

    private static <T> DataComponentType<T> register(String path, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, TeaCraft.id(path), type);
    }

    /** 触发类加载（组件必须在任何物品用到它之前注册）。 */
    public static void register() {
        if (TRAY_COUNT == null || TRAY_STAGE == null || TRAY_SHAKES == null || TRAY_ORIGIN == null
                || TEA_ORIGIN == null) {
            throw new IllegalStateException("teacraft: 组件注册失败");
        }
        TeaCraft.LOGGER.info("[teacraft] 组件就绪：tray_count / tray_stage / tray_shakes / tray_origin / tea_origin");
    }
    /* ---------------- 读茶叶产地的统一入口 ---------------- */

    /**
     * 读一个茶叶物品的产地。
     *
     * @param fallback 没有产地组件时返回什么（创造栏新拿到的物品就是这种）
     */
    public static TeaOrigin originOf(ItemStack stack, TeaOrigin fallback) {
        String id = stack.getOrDefault(TEA_ORIGIN, "");
        return id.isEmpty() ? fallback : TeaOrigin.byId(id);
    }

    /** 这个物品栈是否带产地。 */
    public static boolean hasOrigin(ItemStack stack) {
        return !stack.getOrDefault(TEA_ORIGIN, "").isEmpty();
    }

    /** 给物品栈打上产地。 */
    public static void setOrigin(ItemStack stack, TeaOrigin origin) {
        stack.set(TEA_ORIGIN, origin.id());
    }
}