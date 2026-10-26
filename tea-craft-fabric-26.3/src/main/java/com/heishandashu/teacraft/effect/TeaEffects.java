package com.heishandashu.teacraft.effect;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.core.TeaKind;
import com.heishandashu.teacraft.core.TeaOrigin;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;

/**
 * 三种成品茶的效果。
 *
 * <h2>需求</h2>
 * <table border="1">
 *   <tr><th>茶</th><th>效果</th></tr>
 *   <tr><td>凤凰单丛</td><td>不生成幻翼、不被幻翼攻击；<b>急迫 III</b></td></tr>
 *   <tr><td>红茶</td><td>饱食度消耗降低 90%；<b>生命恢复 II</b></td></tr>
 *   <tr><td>绿茶</td><td><b>迅捷 II</b>；苦力怕与亡灵不主动攻击</td></tr>
 * </table>
 *
 * <p>三种茶的维持时间都按品质走：<b>20 / 40 / 60 / 80 / 100 分钟</b>（品质 1~5）。</p>
 *
 * <h2>实现方式</h2>
 * <p>每种茶注册一个<b>自定义药水效果</b>作为「标记」，玩家身上有它就说明正在喝茶的效果期。
 * 好处：</p>
 * <ul>
 *   <li>HUD / 物品栏能直接看到剩余时间；</li>
 *   <li>那些"不能靠原版效果表达"的行为（幻翼、苦力怕、饱食度）有地方写；</li>
 *   <li>不会和玩家从别处获得的急迫/迅捷混淆。</li>
 * </ul>
 * <p>急迫/恢复/迅捷这些<b>能</b>用原版效果表达的部分，由标记效果每隔 20 tick 刷新一次。</p>
 */
public final class TeaEffects {

    /** 凤凰单丛：急迫 III + 幻翼免疫。 */
    public static Holder<MobEffect> DANCONG;
    /** 红茶：生命恢复 II + 饱食度消耗 -90%。 */
    public static Holder<MobEffect> BLACK;
    /** 绿茶：迅捷 II + 苦力怕/亡灵不主动攻击。 */
    public static Holder<MobEffect> GREEN;

    /**
     * 单丛的急迫等级：amplifier 8 = <b>急迫 IX</b>。
     *
     * <h2>为什么不用「挖掘速度属性」而只用急迫等级</h2>
     * <p>曾经用 {@code Attributes.BLOCK_BREAK_SPEED} 修饰符（+70% / +100%）来补速度，
     * 实测表现是：<b>镐子疯狂空挥但方块不掉</b>。这是典型的双端不同步 ——
     * 客户端算出的挖掘速度足够、于是每 tick 完成一次"挖掘"并重新挥动，
     * 而服务端不认，方块永远不碎。</p>
     *
     * <p>急迫是原版效果，<b>客户端与服务端用的是同一套数值</b>，
     * 不存在这类不同步，所以改成单纯提高急迫等级。</p>
     *
     * <h2>为什么是 IX</h2>
     * <p>按 26.3 的 {@code Player.getDestroySpeed} 字节码还原：</p>
     * <pre>
     *   speed = (工具速度 + 效率加成) × (1 + (急迫等级+1) × 0.2)
     * </pre>
     * <p>效率 V 加成 {@code 5²+1 = 26}，钻石镐基础 8 → 34。
     * 深板岩硬度 3.0，一击破需要 {@code speed ≥ 30 × 3.0 = 90}：</p>
     * <pre>
     *   急迫 III (a=2)：(8+26) × 1.6 = 54.4   ← 不够
     *   急迫 IV  (a=3)：(8+26) × 1.8 = 61.2   ← 不够（就是你说的"加了 20% 还是不行"）
     *   急迫 IX  (a=8)：(8+26) × 2.8 = 95.2   ← 够
     * </pre>
     * <p>所以要让「效率 V 钻石镐」真的一击破深板岩，急迫必须到 IX。
     * 想降低等级就得动效率附魔，那是另一条路。</p>
     */
    public static final int HASTE_AMPLIFIER = 8;

    /** 品质 1~5 → 分钟 20/40/60/80/100。 */
    public static int minutesFor(int quality) {
        int q = Math.max(1, Math.min(5, quality));
        return 20 * q;
    }

    /** 品质 1~5 → tick 数。 */
    public static long ticksFor(int quality) {
        return (long) minutesFor(quality) * 60L * 20L;
    }

    /** 由物品 id 前缀判断是哪一种茶。 */
    public static TeaKind kindOf(String stageId) {
        if (stageId == null) return null;
        if (stageId.startsWith("dancong_")) return TeaKind.DANCONG;
        if (stageId.startsWith("green_")) return TeaKind.GREEN;
        if (stageId.startsWith("black_")) return TeaKind.BLACK;
        return null;
    }

    /** 注册三个自定义效果。在 TeaCraft 主入口调用。 */
    public static void register() {
        DANCONG = reg("dancong_tea", new TeaEffect(MobEffectCategory.BENEFICIAL, 0xD8A03C, TeaKind.DANCONG));
        BLACK = reg("black_tea", new TeaEffect(MobEffectCategory.BENEFICIAL, 0x8B3A1E, TeaKind.BLACK));
        GREEN = reg("green_tea", new TeaEffect(MobEffectCategory.BENEFICIAL, 0x4C8B2B, TeaKind.GREEN));
        TeaCraft.LOGGER.info("[teacraft] 茶效果就绪：dancong_tea / black_tea / green_tea");
    }

    private static Holder<MobEffect> reg(String path, MobEffect effect) {
        Identifier id = TeaCraft.id(path);
        Registry.register(BuiltInRegistries.MOB_EFFECT, id, effect);
        // 26.3 的 Registry 没有 getHolder(id)，用 wrapAsHolder 拿 Holder
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    /** 某类茶对应的标记效果。 */
    public static Holder<MobEffect> markerFor(TeaKind kind) {
        return switch (kind) {
            case DANCONG -> DANCONG;
            case BLACK -> BLACK;
            case GREEN -> GREEN;
        };
    }

    /** 喝下一杯茶：按茶类与品质给效果。 */
    public static void drink(ServerPlayer player, TeaKind kind, TeaOrigin origin) {
        drink(player, kind, origin.quality());
    }

    /**
     * 喝下一杯已知品质的茶（茶盘上倒出来的那一杯）。
     *
     * <p>与按产地算品质的版本共用同一套数值，避免出现"两种喝法效果不同"。</p>
     */
    public static void drink(ServerPlayer player, TeaKind kind, int quality) {
        int q = Math.max(1, Math.min(5, quality));
        int ticks = (int) Math.min(Integer.MAX_VALUE, ticksFor(q));
        Holder<MobEffect> marker = markerFor(kind);
        if (marker == null) return;
        // amplifier 0 = I 级；标记效果本身不叠等级，只用来计时
        player.addEffect(new MobEffectInstance(marker, ticks, 0, false, true, true));
        // 立刻给一次原版效果，时长与标记效果一致，避免第一秒空窗
        refreshVanilla(player, kind, ticks);
    }

    /** 把「能用原版效果表达」的部分挂上去。 */
    private static void refreshVanilla(ServerPlayer player, TeaKind kind, int ticks) {
        // ⚠ 不要截断成短时长：原版效果图标在剩余时间 < 200 tick（10 秒）时会**闪烁**。
        // 之前这里写死 min(ticks, 100)，于是每 20 tick 刷新一次、图标几乎一直在闪。
        int t = Math.max(1, ticks);
        switch (kind) {
            // 急迫 IV = amplifier 3（需求：在急迫 III 基础上再加 20%）
            case DANCONG -> player.addEffect(new MobEffectInstance(MobEffects.HASTE, t, HASTE_AMPLIFIER, false, false, true));
            // 生命恢复 II = amplifier 1
            case BLACK -> player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, t, 1, false, false, true));
            // 迅捷 II = amplifier 1
            case GREEN -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, t, 1, false, false, true));
        }
    }

    private TeaEffects() {
    }

    /* ---------------- 自定义效果本体 ---------------- */

    /**
     * 茶效果：作为「正在生效」的标记，同时负责原版效果刷新与特殊行为。
     */
    public static class TeaEffect extends MobEffect {

        private final TeaKind kind;

        TeaEffect(MobEffectCategory category, int color, TeaKind kind) {
            super(category, color);
            this.kind = kind;

        }

        public TeaKind kind() { return kind; }

        /** 每 20 tick 跑一次就够，不必每 tick。 */
        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        @Override
        public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
            if (!(entity instanceof ServerPlayer player)) return true;

            // 用「标记效果自己还剩多久」来续原版效果：
            // 这样两者同时结束，且时长始终 > 200 tick，图标不会闪。
            var self = player.getEffect(markerFor(kind));
            int remaining = self == null ? 200 : self.getDuration();
            refreshVanilla(player, kind, remaining);

            switch (kind) {
                case DANCONG -> keepPhantomsAway(player);
                case GREEN -> calmCreepersAndUndead(player);
                case BLACK -> {
                    // 饱食度消耗 -90% 由 PlayerExhaustionMixin 处理
                }
            }
            return true;
        }

        /**
         * 幻翼：既不受攻击，也不生成。
         *
         * <p>「不生成」的做法是把玩家的 {@code TIME_SINCE_REST} 归零 ——
         * 原版正是靠这个统计值决定是否在雷雨天夜里刷幻翼，
         * 归零后等同于"刚睡过觉"，不会触发。</p>
         */
        private static void keepPhantomsAway(ServerPlayer player) {
            player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 0);
            // 已经在场上的幻翼：让它们忘掉目标
            for (Phantom phantom : player.level().getEntitiesOfClass(
                    Phantom.class, player.getBoundingBox().inflate(48.0))) {
                if (phantom.getTarget() == player) {
                    phantom.setTarget(null);
                }
            }
        }

        /** 苦力怕与亡灵不主动攻击：清掉以该玩家为目标的目标锁定。 */
        private static void calmCreepersAndUndead(ServerPlayer player) {
            for (Mob mob : player.level().getEntitiesOfClass(
                    Mob.class, player.getBoundingBox().inflate(32.0))) {
                if (mob.getTarget() != player) continue;
                boolean hostile = mob instanceof Creeper
                        || mob.getType().builtInRegistryHolder()
                                .is(net.minecraft.tags.EntityTypeTags.UNDEAD);
                if (hostile) mob.setTarget(null);
            }
        }
    }
}
