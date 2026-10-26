package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.menu.TeaBoxMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 茶盒 —— 一个扁平木盒，内部 27 个槽位，每槽可存 999 个同种成品茶。
 *
 * <h2>交互</h2>
 * <ul>
 *   <li><b>右键</b> → 打开界面（27 格）</li>
 *   <li><b>潜行右键</b> → 拿起整个茶盒（里面的茶叶跟着走）</li>
 * </ul>
 *
 * <h2>放茶叶</h2>
 * <p>手持成品茶右键盒子 → 存入（同样走界面里的手动放置）。
 * 只接受成品茶，其余一律拒收。</p>
 */
public class TeaBoxBlock extends HorizontalDirectionalBlock implements EntityBlock {


    /** 朝向：装饰性（盒盖木纹有方向）。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * 碰撞箱：只到盒盖顶部（7 像素）。
     *
     * <p>刻意和模型一致 —— 写成满格会像一个隐形方块柱，玩家站在旁边
     * 会被莫名推开，而且俯视时射线会打在"空气"上。</p>
     */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);

    public TeaBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // 朝向一律看玩家（和茶壶一样，不读物品上记的朝向）
        Direction want = context.getHorizontalDirection().getOpposite();
        if (!want.getAxis().isHorizontal()) want = Direction.NORTH;
        return defaultBlockState().setValue(FACING, want);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeaBoxBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TeaBoxBlockEntity box)) {
            return InteractionResult.PASS;
        }

        // 潜行右键：拿起整个茶盒（不管手上拿什么，茶叶跟着走）
        if (player.isShiftKeyDown()) {
            return takeWholeBox(level, pos, player, box);
        }

        // 手上有成品茶 → 直接存入
        if (!stack.isEmpty() && stack.getItem() instanceof com.heishandashu.teacraft.item.TeaDrinkItem) {
            int left = box.insert(stack.getItem(), stack.getCount());
            int moved = stack.getCount() - left;
            if (moved > 0) {
                stack.shrink(moved);
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.translatable(
                            "teacraft.msg.tea_box_stored", moved).withStyle(
                            net.minecraft.ChatFormatting.GRAY), true);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }

        // 手上拿着别的东西 → 也要能开界面。
        //
        // 【必须返回 TRY_WITH_EMPTY_HAND】之前这里返回 PASS，结果原版就不再调
        // useWithoutItem 了 —— 手上随便拿个方块右键茶盒，界面根本打不开。
        // 窑炉那边早就踩过这个坑并写了注释，我这里又踩了一次。
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TeaBoxBlockEntity box)) {
            return InteractionResult.PASS;
        }

        // 潜行右键 = 拿起整个茶盒（茶叶跟着走）
        if (player.isShiftKeyDown()) {
            return takeWholeBox(level, pos, player, box);
        }

        // 普通右键 = 开界面。
        //
        // 【必须用 ExtendedMenuProvider】普通 SimpleMenuProvider 不会把方块坐标
        // 传给客户端，客户端 openMenu 时拿不到方块实体，界面里就是 27 个空格
        // （甚至直接崩）。窑炉用的是同一套，照抄。
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<
                    TeaBoxMenu.OpenData>() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("container.teacraft.tea_box");
                }

                @Override
                public TeaBoxMenu.OpenData getScreenOpeningData(ServerPlayer p) {
                    return new TeaBoxMenu.OpenData(pos);
                }

                @Override
                public TeaBoxMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv,
                                             Player p) {
                    TeaBoxBlockEntity be = level.getBlockEntity(pos) instanceof TeaBoxBlockEntity b
                            ? b : box;
                    return new TeaBoxMenu(id, inv, be);
                }
            });
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 潜行右键 —— 把整个茶盒拿起来放进背包，<b>里面的茶叶跟着盒子走</b>。
     *
     * <h2>为什么不用 drainAll</h2>
     * <p>以前是「把茶叶全倒进背包、盒子留在原地」，但需求改成了「拿起盒子」。
     * 所以现在要把方块实体的完整数据（27 个槽位）写进物品的
     * {@code BLOCK_ENTITY_DATA}，再移除方块。放下时靠
     * {@code TeaBoxBlockItem} / 这里的 {@code setPlacedBy} 恢复。</p>
     *
     * <h2>背包满了怎么办</h2>
     * <p>先判断能不能放下，放不下就<b>什么都不做</b> —— 绝不能先移除方块再发现
     * 塞不进去，那会把玩家的茶叶弄丢。</p>
     */
    private InteractionResult takeWholeBox(Level level, BlockPos pos, Player player,
                                          TeaBoxBlockEntity box) {
        ItemStack stack = new ItemStack(TeaItems.TEA_BOX_ITEM);

        // 把方块实体的全部数据存进物品（saveWithId 会带上 id，供放置时识别类型）
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        box.saveWithId(out);
        net.minecraft.world.item.BlockItem.setBlockEntityData(stack, box.getType(), out);

        // 先试着放进背包；放不下就取消，不动方块
        if (!player.getInventory().add(stack)) {
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("teacraft.msg.tea_box_full")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return InteractionResult.SUCCESS;
        }

        level.removeBlock(pos, false);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOD_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    /**
     * 放置时把物品里的茶叶数据恢复进方块实体。
     *
     * <p>原版的 {@code setPlacedBy} 之后才会调 {@code setBlockEntityData}，
     * 但那条路在 26.3 上不总可靠（茶壶那边实测过）。这里主动兜底一次，
     * 保证"拿起再放下"茶叶不丢。</p>
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        if (!(level.getBlockEntity(pos) instanceof TeaBoxBlockEntity box)) return;

        var data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return;
        var tag = data.copyTagWithoutId();
        if (tag == null) return;

        // 用 ValueInput 包一层，复用方块实体自己的读取逻辑
        var in = net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag);
        box.loadAdditional(in);
        box.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    /**
     * 玩家破坏时掉落「带数据」的茶盒，保证里面的茶叶不丢。
     *
     * <h2>为什么用 playerDestroy</h2>
     * <p>它是唯一同时满足三个条件的钩子：</p>
     * <ul>
     *   <li>能拿到 {@code BlockEntity} 参数（此时数据还在）；</li>
     *   <li>是实例方法，可以覆写；</li>
     *   <li>掉什么由我们自己决定。</li>
     * </ul>
     *
     * <p>{@code getDrops} 是静态方法，不能覆写；
     * {@code affectNeighborsAfterRemoval} 触发太晚，方块实体可能已经没了。</p>
     *
     * <p>原版默认会掉一个空茶盒，这里<b>不调 super</b>，改为掉带数据的那个。</p>
     */
    @Override
    public void playerDestroy(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.server.level.ServerPlayer player,
                              BlockPos pos, BlockState state, BlockEntity be, ItemStack tool) {
        // 创造模式：原版不给掉落，保持一致
        if (player.isCreative()) return;

        ItemStack drop = new ItemStack(TeaItems.TEA_BOX_ITEM);
        if (be instanceof TeaBoxBlockEntity box && box.hasAny()) {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            box.saveWithId(out);
            net.minecraft.world.item.BlockItem.setBlockEntityData(drop, box.getType(), out);
        }
        // 掉在方块中心
        net.minecraft.world.level.block.Block.popResource(level, pos, drop);
    }
}
