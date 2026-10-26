package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.block.TeaTrayBlock;
import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaOrigin;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * 晒盘的物品形态 —— 也就是「抱在手上」的那只盘。
 *
 * <h2>三种右键含义</h2>
 * <ol>
 *   <li><b>对着方块右键</b> → 把盘放下去（连同里面的茶叶一起，靠 {@link TeaComponents} 带状态）；</li>
 *   <li><b>潜行 + 右键</b> → 摇青：长按 3 秒算摇一次，摇满 3 次茶叶变「摇青」。
 *       潜行时原版会跳过方块交互，所以这个动作不会误触「放下去」；</li>
 *   <li>盘是空的或还没萎凋 → 只提示，不摇。</li>
 * </ol>
 *
 * <h2>关于「抱起摇晃」的动画</h2>
 * <p>摇的时候用原版 {@link ItemUseAnimation#BLOCK} 姿态（把盘捧在胸前），
 * 并且每 5 tick 主动挥一次手（{@code Player#swing}）＋ 竹篾摩擦声 ＋ 叶片碎屑粒子。
 * 这套动作全部由服务端驱动，能同步给所有玩家，也不需要客户端 mixin。</p>
 *
 * <p>真正「把盘端起来左右晃」的手臂位移动画需要客户端渲染改写（{@code ItemInHandRenderer}），
 * 留作后续单独一步再做 —— 现在先保证动作、时长与状态机是对的。</p>
 */
public class TeaTrayItem extends BlockItem {

    /** 一次摇动用时：3 秒 = 60 tick。 */
    public static final int SHAKE_TICKS = 3 * 20;

    /** 摇几次才算摇青完成。 */
    public static final int SHAKES_NEEDED = 3;

    /** 连续摇青时的"总时长"上限（10 分钟，实际上够摇完 3 次）。 */
    public static final int CONTINUOUS_USE_TICKS = 10 * 60 * 20;

    public TeaTrayItem(Block block, Properties properties) {
        super(block, properties);
    }

    /* ---------------- 放置：把份数与状态带上去 ---------------- */

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        // 先记下来：super.useOn 会把物品消耗/数量减一
        int count = stack.getOrDefault(TeaComponents.TRAY_COUNT, 0);
        int stage = stack.getOrDefault(TeaComponents.TRAY_STAGE, 0);
        // 【产地也要记下来】晒盘的产地存在方块实体的 origin 字段里，
        // 物品上则存在 tray_origin 组件。放下时方块实体是全新构造的，
        // 如果不在这里还原，origin 就退回默认的温带 —— 表现是
        // 「极寒茶潜行端起摇青、放下之后就变温带了」。
        String originId = stack.get(TeaComponents.TRAY_ORIGIN);

        InteractionResult result = super.useOn(context);
        if (!result.consumesAction() || context.getLevel().isClientSide()) {
            return result;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        BlockState placed = level.getBlockState(pos);
        if (placed.is(this.getBlock())) {
            BlockState next = placed
                    .setValue(TeaTrayBlock.COUNT, Math.min(TeaTrayBlock.MAX_LEAVES, count))
                    // 上限要跟着阶段数走：原来写死 3，会把「已杀青(4)」的盘夹回摇青状态
                    .setValue(TeaTrayBlock.STAGE,
                            Math.max(0, Math.min(TeaTrayBlock.STAGE_KILLGREEN, stage)));
            level.setBlock(pos, next, 3);
            // 关键：setBlock 是在 onPlace 之后才带正确的 stage，onPlace 里因为
            // 「oldState 已经是本方块」提前返回了，所以计时必须在这里补排一次。
            // 少了这一行，摇青盘放回去永远不会变成杀青。
            ((TeaTrayBlock) this.getBlock()).armTimer(level, pos, next);

            // 产地还原：这一步必须放在 setBlock 之后（方块实体这时才建好）。
            if (originId != null && !originId.isEmpty()
                    && level.getBlockEntity(pos) instanceof com.heishandashu.teacraft.block.TeaTrayBlockEntity tray) {
                tray.setOrigin(TeaOrigin.byId(originId));
                level.sendBlockUpdated(pos, next, next, 3);
            }
        }
        return result;
    }

    /* ---------------- 摇青 ---------------- */

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int count = stack.getOrDefault(TeaComponents.TRAY_COUNT, 0);
        int stage = stack.getOrDefault(TeaComponents.TRAY_STAGE, 0);

        if (count <= 0) {
            hint(level, player, "teacraft.msg.tray_empty");
            return InteractionResult.PASS;
        }
        if (stage == TeaTrayBlock.STAGE_FRESH) {
            hint(level, player, "teacraft.msg.tray_not_withered");
            return InteractionResult.PASS;
        }
        if (stage == TeaTrayBlock.STAGE_SHAKEN) {
            hint(level, player, "teacraft.msg.tray_already_shaken");
            return InteractionResult.PASS;
        }

        player.startUsingItem(hand);
        // 不再走动作栏：那句提示会被信息面板挡住。改由 TeaTrayHud 画在面板上方。
        return InteractionResult.CONSUME;
    }

    /**
     * <b>连续摇青</b>：把总时长设得很长，按住就一直摇。
     *
     * <p>需求：「摇青过程改为连续，不要一次结束之后必须重新按一次右键」。</p>
     *
     * <p>以前这里返回 {@code SHAKE_TICKS}（3 秒），所以按满 3 秒就自动结束，
     * 玩家必须松手再按一次。现在返回一个足够长的值（10 分钟），
     * 每次"摇一下"由 {@link #onUseTick} 里的 {@code elapsed % SHAKE_TICKS} 推进，
     * 按住不放就会一下接一下地摇，直到摇满 3 次或松手。</p>
     */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return CONTINUOUS_USE_TICKS;
    }

    /** 和弓一样：松手时由服务端结算。 */
    @Override
    public boolean useOnRelease(ItemStack stack) {
        return true;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BLOCK;          // 捧在胸前
    }

    /**
     * 连续摇青：每 {@link #SHAKE_TICKS} 结算一次，同时每 5 tick 给手感反馈。
     *
     * <p>关键点：判断"这一下摇完了吗"不能再用 {@code SHAKE_TICKS - remaining}
     * （那是一次性的），而要用 {@code elapsed = 总时长 - remaining} 对
     * {@code SHAKE_TICKS} 取模 —— 这样按住就一直摇。</p>
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity == null) return;

        int elapsed = CONTINUOUS_USE_TICKS - remaining;
        if (elapsed < 0) return;

        // 每满 3 秒算摇了一下（只在服务端结算，避免双端各算一次）
        if (elapsed > 0 && elapsed % SHAKE_TICKS == 0 && !level.isClientSide()) {
            settleOneShake(level, entity, stack);
        }

        // 手感反馈每 5 tick 一次（客户端也要，否则看着不连贯）
        if (elapsed % 5 == 0) {
            if (entity instanceof Player player) {
                player.swing(InteractionHand.MAIN_HAND,
                        net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            }
            float phase = (elapsed % SHAKE_TICKS) / (float) SHAKE_TICKS;
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.BAMBOO_STEP, SoundSource.PLAYERS, 0.35F, 0.7F + 0.5F * phase);
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.COMPOSTER,
                        entity.getX(), entity.getY() + 1.0, entity.getZ(), 2, 0.25, 0.15, 0.25, 0.0);
            }
        }
    }

    /**
     * 摇完一下。
     *
     * @return true 表示已经是第三次（摇青完成）
     */
    public static boolean settleOneShake(Level level, LivingEntity entity, ItemStack stack) {
        // entity 允许为 null（自检直接调这个方法时没有玩家实体）——
        // 所以下面凡是 entity.xxx() 都必须先判空。
        if (stack.getOrDefault(TeaComponents.TRAY_STAGE, 0) != TeaTrayBlock.STAGE_WITHERED) {
            return false;                        // 已经摇青过，不再累计
        }
        int shakes = stack.getOrDefault(TeaComponents.TRAY_SHAKES, 0) + 1;

        if (shakes >= SHAKES_NEEDED) {
            stack.set(TeaComponents.TRAY_STAGE, TeaTrayBlock.STAGE_SHAKEN);
            stack.set(TeaComponents.TRAY_SHAKES, 0);
            if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("teacraft.msg.tray_shaken"), true);
                sp.stopUsingItem();             // 摇满了就自动停手
            }
            if (entity != null) {
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.BAMBOO_PLACE, SoundSource.PLAYERS, 0.9F, 0.8F);
            }
            return true;
        }

        stack.set(TeaComponents.TRAY_SHAKES, shakes);
        if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("teacraft.msg.tray_shake_progress",
                    shakes, SHAKES_NEEDED), true);
        }
        return false;
    }

    /**
     * 松手时不再结算。
     *
     * <p>连续摇青的每一次都在 {@link #onUseTick} 里按 {@code elapsed % SHAKE_TICKS}
     * 结算过了；这里再算一遍会导致「松手瞬间多摇一次」。</p>
     */
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        return true;
    }

    /* ---------------- 提示与描述 ---------------- */

    private static void hint(Level level, Player player, String key) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable(key), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int count = stack.getOrDefault(TeaComponents.TRAY_COUNT, 0);
        int stage = stack.getOrDefault(TeaComponents.TRAY_STAGE, 0);

        tooltip.accept(Component.translatable("teacraft.tooltip.tray_count", count, TeaTrayBlock.MAX_LEAVES)
                .withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("teacraft.tray_stage." + stage)
                .withStyle(ChatFormatting.GREEN));
        if (stage == TeaTrayBlock.STAGE_WITHERED) {
            tooltip.accept(Component.translatable("teacraft.tooltip.tray_shake_hint")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("teacraft.tooltip.tray_usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
