package com.heishandashu.teacraft.block;

import com.mojang.serialization.MapCodec;
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
 * 茶椅 —— 配套茶桌的椅子（NeoForge 1.21.1）。
 *
 * <h2>碰撞箱只到座面</h2>
 * <p>需求：「高度只做到椅子面高度，椅背不做碰撞箱」。碰撞箱是 {@code 0 ~ 9.5}
 * （约 0.6 格）：玩家能走上去、能跳上座面，椅背完全无碰撞、可以从背后走过。</p>
 *
 * <h2>朝向</h2>
 * <p>用 {@link HorizontalDirectionalBlock} 的 {@link #FACING}，放置时朝向玩家。
 * blockstate 里用 {@code y} 旋转模型 —— 1.21.1 的 {@code variants} + {@code y}
 * 是有效的（原生有 287 个用例）。</p>
 *
 * <h2>1.21.1 与 26.3 的差异</h2>
 * <ul>
 *   <li>{@code FACING} 是 {@code DirectionProperty}（26.3 是 {@code EnumProperty}）；</li>
 *   <li>必须实现 {@code codec()}；</li>
 *   <li>{@code onRemove} 存在（26.3 换成了 {@code affectNeighborsAfterRemoval}）；</li>
 *   <li>{@code startRiding} 是两参数。</li>
 * </ul>
 */
public class TeaChairBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<TeaChairBlock> CODEC = simpleCodec(TeaChairBlock::new);

    /**
     * 碰撞箱高度（像素）。
     *
     * <p>模型座面上表面在 {@code y=9.5}。碰撞箱取同一个值，
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
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
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

    /** 放置时朝向玩家（椅背在玩家身后）。 */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!facing.getAxis().isHorizontal()) {
            facing = Direction.NORTH;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    /** 右键坐下 / 起身。潜行右键不处理。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) return InteractionResult.PASS;

        if (player.isPassenger()) {
            player.stopRiding();
        }

        double seatY = pos.getY() + SEAT_HEIGHT / 16.0;
        TeaSeatEntity seat = TeaSeatEntity.create(level, pos, seatY);
        if (seat == null) return InteractionResult.PASS;

        level.addFreshEntity(seat);
        // 1.21.1 是两参数：startRiding(载具, 强制)
        if (!player.startRiding(seat, true)) {
            seat.discard();
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    /** 椅子被破坏时把坐着的人放下来，否则玩家会卡在已消失的椅子上。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            TeaSeatEntity.ejectAt(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
