package com.heishandashu.teacraft.mixin;

import com.heishandashu.teacraft.effect.TeaEffects;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让喝了茶的玩家「不会成为某些敌对生物的目标」。
 *
 * <h2>为什么必须用 Mixin</h2>
 * <p>第一版是在药水效果 tick 里<b>清除已有目标</b>（{@code mob.setTarget(null)}），
 * 实测无效 —— 因为 AI 的索敌目标（{@code NearestAttackableTargetGoal}）
 * 每几 tick 就重新扫一次，清掉后立刻又锁上，
 * 而且已经开始的攻击动画/爆炸倒计时并不会被清除。</p>
 *
 * <p>正确的位置是 {@link Mob#canAttack(LivingEntity)} ——
 * 所有索敌目标在「能不能把某人当目标」时都会问它。
 * 在这里返回 {@code false}，生物就<b>根本不会选中</b>你。</p>
 *
 * <table border="1">
 *   <tr><th>条件</th><th>拦截对象</th></tr>
 *   <tr><td>喝了绿茶</td><td>苦力怕 + 所有亡灵（{@code #minecraft:undead}）</td></tr>
 *   <tr><td>喝了单丛</td><td>幻翼</td></tr>
 * </table>
 */
@Mixin(Mob.class)
public class MobTargetingMixin {

    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void teacraft$teaDrinkersAreIgnored(LivingEntity target,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (!(target instanceof Player player)) return;

        Mob self = (Mob) (Object) this;

        // 单丛：幻翼不打喝茶的人
        if (self instanceof Phantom
                && TeaEffects.DANCONG != null && player.hasEffect(TeaEffects.DANCONG)) {
            cir.setReturnValue(false);
            return;
        }

        // 绿茶：苦力怕与亡灵不主动攻击
        if (TeaEffects.GREEN != null && player.hasEffect(TeaEffects.GREEN)) {
            boolean hostile = self instanceof Creeper
                    || self.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
            if (hostile) {
                cir.setReturnValue(false);
            }
        }
    }
}
