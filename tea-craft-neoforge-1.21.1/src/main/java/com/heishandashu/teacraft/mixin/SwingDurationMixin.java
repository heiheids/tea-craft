package com.heishandashu.teacraft.mixin;

import com.heishandashu.teacraft.effect.TeaEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修「急迫等级太高时镐子完全没有挥动动画」。
 *
 * <h2>根因（反编译 1.21.1 的 {@code LivingEntity#getCurrentSwingDuration} 得到）</h2>
 * <pre>
 *   public int getCurrentSwingDuration() {
 *       if (MobEffectUtil.hasDigSpeed(this))
 *           return 6 - (1 + MobEffectUtil.getDigSpeedAmplification(this));
 *       return hasEffect(DIG_SLOWDOWN) ? 6 + (1 + amp) * 2 : 6;
 *   }
 * </pre>
 * <p>急迫 IX 的 amplification 是 8 → 扣掉 <b>9</b>：{@code 6 - 9 = -3}。
 * 挥动时长成了<b>负数</b>，动画自然一帧都播不出来 —— 看起来就是镐子完全不动。</p>
 *
 * <p>这和"一击破深板岩"是<b>直接冲突</b>的：一击破需要急迫 IX，
 * 而急迫 IX 本身就会把挥动动画压成 0。</p>
 *
 * <h2>修法</h2>
 * <p>在返回时兜底：<b>只要玩家身上有单丛效果</b>，
 * 挥动时长不得低于 {@link #MIN_SWING_TICKS} tick。</p>
 *
 * <p>只在有单丛效果时生效，不影响原版正常的挖掘/攻击手感
 * （普通玩家、其他生物、其它效果一律不动）。</p>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <ul>
 *   <li>方法名变了：26.3 叫 {@code getModifiedSwingDuration(SwingAnimation)}，
 *       1.21.1 叫 <b>{@code getCurrentSwingDuration()}</b>，<b>不带参数</b>
 *       （{@code SwingAnimation} 这个类在 1.21.1 整个不存在），
 *       所以注入回调也随之去掉了那个入参；</li>
 *   <li>目标类与方法可见性不变（{@code LivingEntity}，public，被子类继承使用），
 *       用 {@code @At("RETURN")} 注入仍然有效。</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class SwingDurationMixin {

    /** 挥动动画的最短时长（tick）。4 tick ≈ 0.2 秒，肉眼能看清一下挥动。 */
    private static final int MIN_SWING_TICKS = 4;

    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void teacraft$keepSwingVisible(CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (TeaEffects.DANCONG == null) return;
        if (!self.hasEffect(TeaEffects.DANCONG)) return;

        int modified = cir.getReturnValueI();
        if (modified < MIN_SWING_TICKS) {
            cir.setReturnValue(MIN_SWING_TICKS);
        }
    }
}
