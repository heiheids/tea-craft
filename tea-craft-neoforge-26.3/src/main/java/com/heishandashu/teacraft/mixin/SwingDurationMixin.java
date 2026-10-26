package com.heishandashu.teacraft.mixin;

import com.heishandashu.teacraft.effect.TeaEffects;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修「急迫等级太高时镐子完全没有挥动动画」。
 *
 * <h2>根因（反编译 {@code LivingEntity#getModifiedSwingDuration} 得到）</h2>
 * <pre>
 *   duration = anim.duration();                       // 默认 6 tick
 *   if (hasDigSpeed(this)) {
 *       return duration - (1 + digSpeedAmplification); // 急迫每级再扣 1
 *   }
 * </pre>
 * <p>急迫 IX 的 amplification 是 8 → 扣掉 <b>9</b>：{@code 6 - 9 = -3}。
 * 挥动时长成了<b>负数</b>，动画自然一帧都播不出来 —— 看起来就是镐子完全不动。</p>
 *
 * <p>这和"一击破深板岩"是<b>直接冲突</b>的：一击破需要急迫 IX，
 * 而急迫 IX 本身就会把挥动动画压成 0。</p>
 *
 * <h2>修法</h2>
 * <p>在 {@code getModifiedSwingDuration} 返回时兜底：<b>只要玩家身上有单丛效果</b>，
 * 挥动时长不得低于 {@link #MIN_SWING_TICKS} tick。</p>
 *
 * <p>只在有单丛效果时生效，不影响原版正常的挖掘/攻击手感
 * （普通玩家、其他生物、其它效果一律不动）。</p>
 */
@Mixin(LivingEntity.class)
public class SwingDurationMixin {

    /** 挥动动画的最短时长（tick）。4 tick ≈ 0.2 秒，肉眼能看清一下挥动。 */
    private static final int MIN_SWING_TICKS = 4;

    @Inject(method = "getModifiedSwingDuration", at = @At("RETURN"), cancellable = true)
    private void teacraft$keepSwingVisible(SwingAnimation animation,
                                           CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (TeaEffects.DANCONG == null) return;
        if (!self.hasEffect(TeaEffects.DANCONG)) return;

        int modified = cir.getReturnValueI();
        if (modified < MIN_SWING_TICKS) {
            cir.setReturnValue(MIN_SWING_TICKS);
        }
    }
}
