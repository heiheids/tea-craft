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
 * <p>正确的位置是 {@link LivingEntity#canAttack(LivingEntity)} ——
 * 所有索敌目标在「能不能把某人当目标」时都会问它
 * （{@code TargetingConditions#test} 里就有一句 {@code attacker.canAttack(target)}）。
 * 在这里返回 {@code false}，生物就<b>根本不会选中</b>你。</p>
 *
 * <table border="1">
 *   <tr><th>条件</th><th>拦截对象</th></tr>
 *   <tr><td>喝了绿茶</td><td>苦力怕 + 所有亡灵（{@code #minecraft:undead}）</td></tr>
 *   <tr><td>喝了单丛</td><td>幻翼</td></tr>
 * </table>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <p><b>目标类从 {@code Mob} 改成了 {@code LivingEntity}。</b>
 * 26.3 里 {@code Mob} 自己重写了 {@code canAttack(LivingEntity)}，所以 @Mixin(Mob.class)
 * 能注入到那个重写方法上；1.21.1 的 {@code Mob} <b>没有</b>这个重写
 * （只剩 {@code canAttackType(EntityType)}），
 * {@code canAttack(LivingEntity)} 只有 {@code LivingEntity} 里的一份实现。
 * Mixin 无法给「未被子类重写的继承方法」单独打补丁，所以只能把 Mixin 挂到
 * {@code LivingEntity} 上。语义不变：下面两处判断都要求 {@code self} 是
 * {@code Phantom} / {@code Creeper}（都是 Mob 的子类），其它实体一律不受影响。</p>
 */
@Mixin(LivingEntity.class)
public class MobTargetingMixin {

    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void teacraft$teaDrinkersAreIgnored(LivingEntity target,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (!(target instanceof Player player)) return;

        LivingEntity self = (LivingEntity) (Object) this;
        // 只管生物，别碰玩家之间/其它实体的判定
        if (!(self instanceof Mob)) return;

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
