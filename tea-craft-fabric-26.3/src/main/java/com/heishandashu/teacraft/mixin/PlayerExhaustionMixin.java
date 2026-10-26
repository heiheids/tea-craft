package com.heishandashu.teacraft.mixin;

import com.heishandashu.teacraft.effect.TeaEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 红茶的「饱食度消耗速度降低 90%」。
 *
 * <p>为什么不放在药水效果的 {@code applyEffectTick} 里：
 * 饥饿消耗是<b>被动发生</b>的（挖方块、跑步、回血都会调
 * {@link Player#causeFoodExhaustion(float)}），效果 tick 里没法拦截。</p>
 *
 * <p>用 {@code @ModifyVariable} 把入参乘以 0.1，比 {@code @Inject(cancellable)}
 * 再手动调用一次安全 —— 后者容易写成无限递归。</p>
 */
@Mixin(Player.class)
public class PlayerExhaustionMixin {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float teacraft$reduceExhaustion(float amount) {
        Player self = (Player) (Object) this;
        if (TeaEffects.BLACK != null && self.hasEffect(TeaEffects.BLACK)) {
            return amount * 0.1F;   // 降低 90%
        }
        return amount;
    }
}
