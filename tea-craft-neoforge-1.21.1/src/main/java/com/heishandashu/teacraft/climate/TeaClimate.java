package com.heishandashu.teacraft.climate;

import com.heishandashu.teacraft.config.TeaConfig;
import com.heishandashu.teacraft.core.TeaOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

/**
 * 群系气候相关的判断<b>全部集中在这里</b>。
 *
 * <p>跨版本时，原版这几处 API 最容易变（温度取值方式、群系 id 的取法），
 * 所以本模组只在这一个类里碰它们，其它代码一律调用这里的方法 ——
 * 将来升级只需改本文件。</p>
 *
 * <h2>为什么「禁止种植」不能按温度判（26.3 实测教训）</h2>
 *
 * <p>最初按「温度 &gt; 1.5 就太热」来挡沙漠与恶地。自检一跑就红了：
 * <b>26.3 里热带草原（savanna）的温度也是 2.0</b>，和沙漠、恶地完全一样。
 * 也就是说温度<b>无法</b>区分「炎热的沙漠」和「炎热的草原」——
 * 而需求恰恰是「沙漠与恶地不能种，其它群系都能种」。</p>
 *
 * <p>所以改成按<b>群系身份</b>判：恶地走原版标签 {@code #minecraft:is_badlands}
 * （自动覆盖 badlands / eroded_badlands / wooded_badlands，将来加入变体也自动生效），
 * 沙漠走配置里的 id 名单（26.3 没有 {@code #minecraft:is_desert} 这个标签，
 * 只有 {@code is_badlands}）。</p>
 */
public final class TeaClimate {

    /** 原版恶地标签。26.3 里包含 badlands / eroded_badlands / wooded_badlands。 */
    public static final TagKey<Biome> BADLANDS =
            TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("is_badlands"));

    private TeaClimate() {
    }

    /** 某个位置的群系基准温度。 */
    public static float temperature(LevelReader level, BlockPos pos) {
        return level.getBiome(pos).value().getBaseTemperature();
    }

    /** 该群系产出的鲜叶产地档（决定采摘到的茶叶名字）。 */
    public static TeaOrigin originAt(LevelReader level, BlockPos pos) {
        return TeaOrigin.fromTemperature(temperature(level, pos));
    }

    /**
     * 群系 id。
     *
     * <p>26.3 的群系是<b>动态注册表</b>，不在 {@code BuiltInRegistries} 里，
     * 必须从世界的 {@code registryAccess()} 取。</p>
     */
    public static ResourceLocation biomeId(LevelReader level, BlockPos pos) {
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        return registry.getKey(level.getBiome(pos).value());
    }

    /** 该位置能不能种茶树。 */
    public static boolean canPlant(LevelReader level, BlockPos pos) {
        return canPlant(level.getBiome(pos), biomeId(level, pos));
    }

    /**
     * 种植判定的核心（纯逻辑，不碰世界状态）。
     *
     * @param holder 群系 holder —— 用来查 {@code #minecraft:is_badlands} 标签
     * @param id     群系 id —— 用来查配置名单；可为 {@code null}
     */
    public static boolean canPlant(Holder<Biome> holder, ResourceLocation id) {
        if (holder.is(BADLANDS)) return false;
        return canPlantById(id);
    }

    /** 只按 id 名单判定（查不了标签时用，例如自检里只拿到 id）。 */
    public static boolean canPlantById(ResourceLocation id) {
        return id == null || !TeaConfig.get().bannedBiomes.contains(id.toString());
    }

    /** 在此位置成熟需要的 tick 数（按该群系温度插值）。 */
    public static long growthTicks(LevelReader level, BlockPos pos) {
        return TeaConfig.get().growthTicks(temperature(level, pos));
    }

    /**
     * 随机刻生长的「选中概率分母」—— 仿原版小麦的 {@code nextInt(25 / growthSpeed + 1) == 0}。
     *
     * <p>小麦在 growthSpeed=1 时是 1/26。这里把分母 N 与该群系<b>需要的成熟天数</b>挂钩：</p>
     * <pre>
     * 每游戏日随机刻数 = 24000 × randomTickSpeed / 4096 ≈ 17.58（默认 speed=3）
     * 两阶（幼体→生长期→成树） ⇒ N = 目标天数 × 17.58 / 2
     *   3 天  → 26   （= 小麦的 25/1+1，纯属巧合但很好记）
     *   8 天  → 70
     * </pre>
     *
     * <p>这样「越热越快」与「概率仿小麦」是同一套机制；改 {@code randomTickSpeed}
     * 会像原版作物一样按比例影响所有茶树。</p>
     */
    public static int growthRollBound(LevelReader level, BlockPos pos) {
        return growthRollBound(growthTicks(level, pos));
    }

    /** 纯函数版本：给定「成熟所需 tick」，算出随机刻概率分母。自检直接用它。 */
    public static int growthRollBound(long requiredTicks) {
        double days = requiredTicks / (double) TeaConfig.TICKS_PER_DAY;
        double randomTicksPerDay = TeaConfig.TICKS_PER_DAY * 3.0 / 4096.0;   // ≈ 17.578
        double perStage = days * randomTicksPerDay / 2.0;
        return (int) Math.max(1, Math.round(perStage));
    }

    /** 人类可读的温度，用于提示与调试输出。 */
    public static String describe(LevelReader level, BlockPos pos) {
        float t = temperature(level, pos);
        ResourceLocation id = biomeId(level, pos);
        return String.format("%s (%.2f)", id == null ? "?" : id, t);
    }
}
