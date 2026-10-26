package com.heishandashu.teacraft.block;

import com.mojang.serialization.MapCodec;
import com.heishandashu.teacraft.menu.TeaBoxMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 茶盒（NeoForge 1.21.1）—— 扁平木盒，内部 27 个槽位，每槽可存 999 个同种成品茶。
 *
 * <h2>交互</h2>
 * <ul>
 *   <li><b>手持成品茶右键</b> → 直接存入（不用开界面）</li>
 *   <li><b>右键</b> → 打开界面取茶</li>
 *   <li><b>潜行右键</b> → 拿起整个茶盒（茶叶跟着走）</li>
 * </ul>
 *
 * <h2>1.21.1 与 26.3 的差异</h2>
 * <ul>
 *   <li>物品交互是 {@code useItemOn}（返回 {@code ItemInteractionResult}），
 *       空手是 {@code useWithoutItem}；</li>
 *   <li>菜单用 {@link SimpleMenuProvider}，客户端自己按坐标取方块实体；</li>
 *   <li>{@code saveAdditional} 收 {@code CompoundTag} 而不是 {@code ValueOutput}。</li>
 * </ul>
 */
public class TeaBoxBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<TeaBoxBlock> CODEC = simpleCodec(TeaBoxBlock::new);

    /** 朝向：装饰性（盒盖木纹有方向）。 */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 碰撞箱：只到盒盖顶部（7 像素），和模型一致。 */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);

    public TeaBoxBlock(Properties properties) {
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
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TeaBoxBlockEntity box)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 潜行右键：拿起整个茶盒（不管手上拿什么，茶叶跟着走）
        if (player.isShiftKeyDown()) {
            takeWholeBox(level, pos, player, box);
            return ItemInteractionResult.SUCCESS;
        }

        // 手上有成品茶 → 直接存入
        if (!stack.isEmpty() && stack.getItem() instanceof com.heishandashu.teacraft.item.TeaDrinkItem) {
            int left = box.insert(stack.getItem(), stack.getCount());
            int moved = stack.getCount() - left;
            if (moved > 0) {
                stack.shrink(moved);
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable(
                            "teacraft.msg.tea_box_stored", moved).withStyle(
                            net.minecraft.ChatFormatting.GRAY), true);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }

        // 手上拿着别的东西 → 也要能开界面
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TeaBoxBlockEntity box)) {
            return InteractionResult.PASS;
        }

        // 潜行右键 = 拿起整个茶盒
        if (player.isShiftKeyDown()) {
            takeWholeBox(level, pos, player, box);
            return InteractionResult.SUCCESS;
        }
        // 普通右键 = 开界面
        //
        // 【必须传第二个参数 buf -> writeBlockPos】
        // MenuType 是用 IMenuTypeExtension.create 注册的，它的工厂签名一定会去读
        //   buf.readBlockPos()
        // 如果 openMenu 时不给数据写入器，客户端拿到的 buf 是 null，直接崩：
        //   NullPointerException: Cannot invoke "RegistryFriendlyByteBuf.readBlockPos()"
        //   because "buf" is null
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new TeaBoxMenu(id, inv, box),
                            Component.translatable("container.teacraft.tea_box")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 潜行右键 —— 把整个茶盒拿起来放进背包，<b>里面的茶叶跟着盒子走</b>。
     *
     * <p>背包满了就什么都不做 —— 绝不能先移除方块再发现塞不进去。</p>
     */
    private void takeWholeBox(Level level, BlockPos pos, Player player, TeaBoxBlockEntity box) {
        ItemStack stack = new ItemStack(com.heishandashu.teacraft.item.TeaItems.TEA_BOX_ITEM.get());

        // 把方块实体的全部数据存进物品
        CompoundTag tag = box.saveWithoutMetadata(level.registryAccess());
        if (tag == null) return;
        net.minecraft.world.item.BlockItem.setBlockEntityData(stack, box.getType(), tag);

        if (!player.getInventory().add(stack)) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("teacraft.msg.tea_box_full")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return;
        }

        level.removeBlock(pos, false);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOD_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    /** 放置时把物品里的茶叶数据恢复进方块实体。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        if (!(level.getBlockEntity(pos) instanceof TeaBoxBlockEntity box)) return;

        var data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        if (tag == null) return;

        box.loadAdditional(tag, level.registryAccess());
        box.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    /**
     * 玩家破坏时掉落「带数据」的茶盒，保证里面的茶叶不丢。
     *
     * <h2>为什么覆写 playerDestroy</h2>
     * <p>它是唯一同时满足三个条件的钩子：</p>
     * <ul>
     *   <li>能拿到 {@code BlockEntity} 参数（此时数据还在）；</li>
     *   <li>是实例方法，可以覆写；</li>
     *   <li>掉什么由我们自己决定。</li>
     * </ul>
     *
     * <p>{@code getDrops} 是静态方法，不能覆写；
     * {@code onRemove} 触发太晚，方块实体可能已经没了。</p>
     *
     * <p>原版默认会掉一个空茶盒，这里<b>不调 super</b>，改为掉带数据的那个。</p>
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                             BlockEntity be, ItemStack tool) {
        // 创造模式：原版不给掉落，保持一致
        if (player.isCreative()) return;

        ItemStack drop = new ItemStack(com.heishandashu.teacraft.item.TeaItems.TEA_BOX_ITEM.get());
        if (be instanceof TeaBoxBlockEntity box && box.hasAny()) {
            CompoundTag tag = box.saveWithoutMetadata(level.registryAccess());
            if (tag != null) {
                net.minecraft.world.item.BlockItem.setBlockEntityData(drop, box.getType(), tag);
            }
        }
        // 掉在方块中心
        Block.popResource(level, pos, drop);
    }
}
