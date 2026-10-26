package com.heishandashu.teacraft.mixin;

import com.heishandashu.teacraft.effect.TeaEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修「急迫太高时镐子不挥动」。
 *
 * <h2>为什么一击破就看不到挥手</h2>
 * <p>原版的挥手动画是在<b>连续挖掘</b>的每一跳里发出的。当挖掘速度高到
 * 「第一跳就挖完」（比如急迫 IX + 效率 V 打深板岩），方块当场消失，
 * 客户端还没走到"挥一下"的分支就结束了 —— 于是看起来像<b>镐子没有动画</b>。</p>
 *
 * <p>这里在服务端「破坏方块请求」处理完之后补一次挥手：
 * {@code swing(hand, true)} 的第二个参数 {@code true} 表示<b>广播给周围客户端</b>，
 * 所以第三人称和别人也能看到。</p>
 *
 * <p>只在<b>确实挖掉了方块</b>（该位置变成空气）且玩家身上有单丛效果时才补，
 * 不会影响正常挖掘的手感。</p>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <ul>
 *   <li>{@code handleBlockBreakAction(BlockPos, Action, Direction, int, int)} 在
 *       1.21.1 里签名<b>完全一致</b>，注入点不变（仍然没有 sequence 之外的差异）；</li>
 *   <li>26.3 的 {@code swing(InteractionHand, SwingAnimation, boolean)} 在 1.21.1
 *       不存在（{@code SwingAnimation} 整个类都没有），对应写法是
 *       {@code swing(InteractionHand, boolean)} —— 第二参语义同样是「是否广播」。</li>
 * </ul>
 */
@Mixin(ServerPlayerGameMode.class)
public class InstantBreakSwingMixin {

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "handleBlockBreakAction", at = @At("TAIL"))
    private void teacraft$swingAfterInstantBreak(BlockPos pos,
                                                 ServerboundPlayerActionPacket.Action action,
                                                 Direction direction,
                                                 int maxY, int sequence,
                                                 CallbackInfo ci) {
        // 只在"开始挖/停止挖"这两种请求上补，别的动作（换方向、丢弃物品）不管
        if (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                && action != ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
            return;
        }
        if (TeaEffects.DANCONG == null || !player.hasEffect(TeaEffects.DANCONG)) {
            return;
        }
        // 方块真的没了才补挥手（一击破的典型表现）
        if (!player.level().getBlockState(pos).isAir()) {
            return;
        }
        // 1.21.1：没有 SwingAnimation 参数，true = 广播给周围客户端
        player.swing(InteractionHand.MAIN_HAND, true);
    }
}
