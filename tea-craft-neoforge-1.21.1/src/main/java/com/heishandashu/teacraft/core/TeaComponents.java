package com.heishandashu.teacraft.core;

import com.mojang.serialization.Codec;
import com.heishandashu.teacraft.TeaCraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的自定义数据组件（1.20.5 起 NBT 被组件取代）。
 *
 * <p>只为一件事存在：<b>晒盘被「抱起」时要把盘里装了什么带走</b>。
 * 方块状态能表达「盘里 3 份、已萎凋」，但物品一旦离开世界就只剩 id，
 * 必须靠组件把「份数 / 阶段 / 摇了几次」存进物品栈。</p>
 *
 * <p>所有组件都是 <b>persistent（会写进存档）+ networkSynchronized（会同步到客户端）</b>，
 * 少了后者客户端看到的永远是默认值，工具提示与图标就会对不上。</p>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <p>26.3 用的是 {@code Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ...)} 直接注册；
 * NeoForge 1.21.1 改成 {@link DeferredRegister}，在模组构造时挂到事件总线上。</p>
 */
public final class TeaComponents {

    /** NeoForge 的延迟注册器（数据组件）。 */
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TeaCraft.MOD_ID);

    /** 盘里的茶叶份数（0~4）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TRAY_COUNT =
            COMPONENTS.register("tray_count", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** 茶叶状态：0 空 / 1 新茶 / 2 萎凋 / 3 摇青。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TRAY_STAGE =
            COMPONENTS.register("tray_stage", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** 盘里茶叶的产地 id（例如 {@code tropical}）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TRAY_ORIGIN =
            COMPONENTS.register("tray_origin", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /**
     * 茶叶物品的产地（例如 {@code tropical}）。
     *
     * <p>需求：「将来产自不同地区的茶叶做出来的成茶，饮用效果是有区别的」。
     * 为了不把物品数量乘 5，阶段物品只注册一份，产地记在组件里。</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TEA_ORIGIN =
            COMPONENTS.register("tea_origin", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** 已经摇了几次（0~3）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TRAY_SHAKES =
            COMPONENTS.register("tray_shakes", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * 盖碗 / 茶杯里的茶汤。
     *
     * <p>编码为 {@code "kind|quality|servings"}，例如 {@code "green|4|3"}。</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TEA_BREW =
            COMPONENTS.register("tea_brew", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** 烧水壶里是不是已烧开的热水（存在时为 true）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> HOT_WATER =
            COMPONENTS.register("hot_water", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /**
     * 盖碗里尚未注水的干茶，编码 {@code "kind|quality|count"}。
     *
     * <p>与 {@link #TEA_BREW} 分开存：一个盖碗可以「有干茶但还没注水」，
     * 也可以「已经泡成茶汤」，两者不会同时存在，但需要区分。</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DRY_TEA =
            COMPONENTS.register("dry_tea", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /* ---------------- 茶盘：盘上有哪些茶具 ---------------- */

    /**
     * 盘上有没有盖碗。
     *
     * <p>茶盘物品要显示「盘里带了什么」，所以这些状态必须记在物品组件上，
     * 而不是只存在于方块实体（方块被拆掉后物品上就没有了）。</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SET_GAIWAN =
            COMPONENTS.register("set_gaiwan", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /** 杯子 A 的状态：空 / 满 / 没有这只杯子。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SET_CUP_A =
            COMPONENTS.register("set_cup_a", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** 杯子 B 的状态。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SET_CUP_B =
            COMPONENTS.register("set_cup_b", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** 杯子 C 的状态。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SET_CUP_C =
            COMPONENTS.register("set_cup_c", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /**
     * 茶盘朝向（存 {@link net.minecraft.core.Direction#getName()}）。
     *
     * <p>搬起整只盘再放下时用它保留朝向。
     * <b>注意</b>：1.21.1 这边的 {@code TeaSetBlock} 还没有 FACING 属性，
     * 这个组件先注册着，等朝向那一块迁移完再用。</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SET_FACING =
            COMPONENTS.register("set_facing", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    private TeaComponents() {
    }

    /** 把注册器挂到模组事件总线。 */
    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        TeaCraft.LOGGER.info("[teacraft] 组件注册器已挂载");
    }

    /* ---------------- 读茶叶产地的统一入口 ---------------- */

    /**
     * 读一个茶叶物品的产地。
     *
     * @param fallback 没有产地组件时返回什么（创造栏新拿到的物品就是这种）
     */
    public static TeaOrigin originOf(ItemStack stack, TeaOrigin fallback) {
        String id = stack.getOrDefault(TEA_ORIGIN.get(), "");
        return id.isEmpty() ? fallback : TeaOrigin.byId(id);
    }

    /** 这个物品栈是否带产地。 */
    public static boolean hasOrigin(ItemStack stack) {
        return !stack.getOrDefault(TEA_ORIGIN.get(), "").isEmpty();
    }

    /** 给物品栈打上产地。 */
    public static void setOrigin(ItemStack stack, TeaOrigin origin) {
        stack.set(TEA_ORIGIN.get(), origin.id());
    }
}
