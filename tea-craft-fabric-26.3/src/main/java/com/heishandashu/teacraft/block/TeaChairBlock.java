package com.heishandashu.teacraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 茶椅 —— 配套茶桌的椅子。
 *
 * <h2>碰撞箱只到座面</h2>
 * <p>需求：「高度只做到椅子面高度，椅背不做碰撞箱」。所以碰撞箱是
 * {@code 0 ~ 9.5}（约 0.6 格），而不是满格：</p>
 * <ul>
 *   <li>玩家能走上去、能跳上座面；</li>
 *   <li>椅背（{@code y 9.5~16}）完全无碰撞，可以从背后走过去。</li>
 * </ul>
 *
 * <h2>朝向</h2>
 * <p>用 {@link HorizontalDirectionalBlock} 自带的 {@link #FACING}，放置时朝向玩家
 * （玩家面对我 → 椅背在玩家身后，坐下时面朝外）。blockstate 里用 {@code y}
 * 旋转模型，26.3 的 {@code variants} + {@code y} 是有效的。</p>
 *
 * <h2>坐下</h2>
 * <p>空手右键生成一个不可见的 {@link TeaSeatEntity} 并骑上去。
 * 方块本身当不了载具，这是原版机制决定的。</p>
 */
public class TeaChairBlock extends HorizontalDirectionalBlock {

    /**
     * 碰撞箱高度。
     *
     * <p>模型座面上表面在 {@code y=9.5}（16 分之 9.5 格）。碰撞箱取同一个值，
     * 玩家站上去脚正好在座面上。</p>
     */
    public static final double SEAT_HEIGHT = 9.5;

    /** 站立/碰撞用的形状：只到座面。 */
    private static final VoxelShape SHAPE = Block.box(0.5, 0.0, 0.5, 15.5, SEAT_HEIGHT, 15.5);

    public TeaChairBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    /**
     * 放置时朝向玩家。
     *
     * <p>{@code getHorizontalDirection()} 是玩家看的方向，取反就是
     * 「从椅子指向玩家」的方向，也就是椅背所在方向。</p>
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!facing.getAxis().isHorizontal()) {
            facing = Direction.NORTH;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    /**
     * 右键坐下 / 起身。
     *
     * <p>潜行右键不处理（留给别的交互）。</p>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) return InteractionResult.PASS;

        // 已经坐在别处：先站起来，避免叠坐
        if (player.isPassenger()) {
            player.stopRiding();
        }

        // 座位点：方块中心，高度抬到座面
        double seatY = pos.getY() + SEAT_HEIGHT / 16.0;
        TeaSeatEntity seat = TeaSeatEntity.create(level, pos, seatY);
        if (seat == null) return InteractionResult.PASS;

        level.addFreshEntity(seat);
        // 26.3 是三参数：startRiding(载具, 强制, 显示提示)
        if (!player.startRiding(seat, true, true)) {
            seat.discard();
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 椅子被破坏时把坐着的人放下来。
     *
     * <p>不处理的话玩家会一直「坐」在已经消失的椅子上，只能重进世界。</p>
     *
     * <p>26.3 把旧的 {@code onRemove} 拆成了
     * {@code affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)}。</p>
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state,
                                               net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean movedByPiston) {
        TeaSeatEntity.ejectAt(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
