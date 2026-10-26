package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.effect.TeaEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 急迫等级很高时的「镐子挥手」驱动。
 *
 * <h2>问题</h2>
 * <p>原版的挥手动画是在<b>连续挖掘的每一跳</b>里发出的。
 * 急迫 IX + 效率 V 挖深板岩是<b>第一跳就挖完</b>，
 * 客户端根本走不到"挥一下"的分支，方块就没了 —— 表现是<b>镐子完全不动</b>。</p>
 *
 * <h2>做法</h2>
 * <p>每客户端 tick 检查一次：如果玩家有单丛效果、按着左键、准星对着一个方块，
 * 而当前<b>没有正在播的挥手动画</b>，就补一次 {@code swing}。</p>
 *
 * <p>为什么放在客户端：挥手是<b>纯视觉</b>，客户端自己播就够了，
 * 不必依赖服务端广播（服务端那条路在 {@code InstantBreakSwingMixin} 里，
 * 但实测它对"按住不放连续瞬间破坏"覆盖不全）。</p>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <ul>
 *   <li>{@code LivingEntity#swing(InteractionHand, SwingAnimation, boolean)} 在 1.21.1
 *       <b>不存在</b>（{@code net.minecraft.world.item.component.SwingAnimation} 整个类都没有）：
 *       1.21.1 只有 {@code swing(InteractionHand)} 与 {@code swing(InteractionHand, boolean)}，
 *       后者第二参是「是否广播给周围客户端」（true = 服务端广播）；</li>
 *   <li>{@code LivingEntity#isSwinging()} 在 1.21.1 也没有，
 *       直接用 public 字段 {@code swinging}（客户端本地状态）；</li>
 *   <li>每 tick 的驱动由 {@code TeaCraftClient} 挂在
 *       {@code ClientTickEvent.Post}（对应 26.3 的 {@code ClientTickEvents.END_CLIENT_TICK}）。</li>
 * </ul>
 */
public final class TeaMiningSwing {

    private TeaMiningSwing() {
    }

    /**
     * 这一下是不是「一击即碎」。
     *
     * <p>原版每 tick 的挖掘进度 = {@code speed / (硬度 × 30)}，
     * 达到 1 就当场挖完 —— 此时原版会跳过挥手动画，需要我们自己补。</p>
     *
     * <p>抽成静态纯函数是为了让自检能直接验证阈值，不用造一个客户端玩家。</p>
     */
    public static boolean isInstantBreak(float speed, float hardness) {
        if (hardness <= 0.0F) return false;      // 负数=不可破坏
        return speed >= hardness * 30.0F;
    }

    /** 每客户端 tick 调一次。 */
    public static void tick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return;
        if (TeaEffects.DANCONG == null) return;
        if (!mc.player.hasEffect(TeaEffects.DANCONG)) return;

        // 必须真的按着攻击键
        if (mc.options == null || mc.options.keyAttack == null) return;
        if (!mc.options.keyAttack.isDown()) return;

        // 准星要对着一个还没被挖掉的方块
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;
        var pos = hit.getBlockPos();
        var state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        // 只在「一击即碎」时补帧：
        //   挖掘进度/tick = speed / (硬度 × 30)，够到 1 就是一跳挖完。
        // 普通方块本来就有原版动画，这里不插手，避免出现"多挥一下"。
        float hardness = state.getDestroySpeed(mc.level, pos);
        if (hardness < 0.0F) return;                 // 不可破坏（基岩等）
        float speed = mc.player.getDestroySpeed(state);
        if (!isInstantBreak(speed, hardness)) return;   // 不是一击破，交回原版

        // 正在挥手就让它播完，别打断（否则动画会卡在第 0 帧）
        // 1.21.1 没有 isSwinging()，用 public 字段 swinging
        if (mc.player.swinging) return;

        // 1.21.1：没有 SwingAnimation 参数，单参版本就是「本地播放、不广播」
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
}
