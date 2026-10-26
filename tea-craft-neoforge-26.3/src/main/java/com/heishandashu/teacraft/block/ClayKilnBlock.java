package com.heishandashu.teacraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 窑炉 —— 烧制陶瓷的砖窑。
 *
 * <h2>外观</h2>
 * <p>砖砌方炉，正面一扇米色瓷门、右侧一根金属门把，顶上一块浅色石板加小烟囱。
 * 门朝着玩家，所以带 {@link #FACING}。</p>
 *
 * <h2>交互</h2>
 * <table border="1">
 *   <tr><th>操作</th><th>结果</th></tr>
 *   <tr><td>手持煤炭 / 木炭右键</td><td>塞进燃料位（累计，够 8 个自动点火）</td></tr>
 *   <tr><td>空手右键</td><td>打开界面（左侧 6 坯料 / 右侧 6 成品 / 下方燃料）</td></tr>
 * </table>
 */
public class ClayKilnBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public ClayKilnBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * 不算作光照遮挡。
     *
     * <p>模型不是满方块（四脚之间有缝、烟囱探出顶面），若按满方块算遮挡，
     * 相邻面和自身贴面都会被剔除，表现为「底部透明」。</p>
     */
    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    /** 门朝向玩家。 */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!facing.getAxis().isHorizontal()) facing = Direction.NORTH;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClayKilnBlockEntity(pos, state);
    }

    /** 每 tick 推进烧制。 */
    /**
     * 每 tick 推进烧制。
     *
     * <p><b>客户端也要挂 ticker</b>：客户端那份方块实体上的 {@code burnLeft}
     * 只会被同步包在打开界面时写一次，之后没有任何代码递减它；而服务端的
     * 持续推送在「界面开着」这个场景下并不可靠（实测拿不到）。
     * 结果就是倒计时永远停在起始值，甚至因为值一直是 0 而完全显示不出来。</p>
     *
     * <p>所以客户端也跑一个 {@code tickBurningClient()}：只做本地递减，
     * 不产出物品、不改世界状态。服务端一旦推来新值，客户端会被覆盖并以
     * 服务端为准。</p>
     */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof ClayKilnBlockEntity kiln) {
                if (lvl.isClientSide()) kiln.tickBurningClient();
                else kiln.tickBurning();
            }
        };
    }

    /**
     * 右键：手持燃料就塞燃料，否则开界面。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ClayKilnBlockEntity kiln)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inv, p) -> {
                                var be = p.level().getBlockEntity(pos);
                                return be instanceof ClayKilnBlockEntity k
                                        ? new com.heishandashu.teacraft.menu.ClayKilnMenu(id, inv, k)
                                        : new com.heishandashu.teacraft.menu.ClayKilnMenu(id, inv,
                                                com.heishandashu.teacraft.menu.ClayKilnMenu.clientContainer(), null);
                            },
                            Component.translatable("block.teacraft.clay_kiln")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** 手持燃料右键：直接塞进燃料位。 */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
                                          Level level, BlockPos pos, Player player,
                                          net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!ClayKilnBlockEntity.isFuel(stack)) {
            // 不是燃料 → 交给 useWithoutItem 开界面
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ClayKilnBlockEntity kiln)) {
            return InteractionResult.PASS;
        }

        var fuel = kiln.getItem(ClayKilnBlockEntity.SLOT_FUEL);
        int space = 64 - fuel.getCount();
        if (space <= 0) {
            player.sendSystemMessage(Component.translatable("teacraft.kiln.fuel_full"));
            return InteractionResult.SUCCESS;
        }
        int move = Math.min(space, stack.getCount());
        if (fuel.isEmpty()) {
            kiln.setItem(ClayKilnBlockEntity.SLOT_FUEL,
                    new net.minecraft.world.item.ItemStack(stack.getItem(), move));
        } else {
            fuel.grow(move);
            kiln.setChanged();
        }
        stack.shrink(move);

        // 燃料够了就自动点火，省得玩家还要开界面按一下
        kiln.tryStartBurning();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }
}
