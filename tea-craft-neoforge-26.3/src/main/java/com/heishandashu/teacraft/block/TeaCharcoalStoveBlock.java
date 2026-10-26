package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.core.TeaProcessing;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 碳焙框 —— 用木炭焙茶的箱子，行为类似熔炉。
 *
 * <h2>需求</h2>
 * <ul>
 *   <li>「类似熔炉，必须使用木炭」→ 木炭放燃料位，一块烧 1600 tick，最多存 64 块；</li>
 *   <li>「一次可放入最多 64 个同一种茶叶」→ 换品种会被拒绝；</li>
 *   <li>「必须是发酵好的茶才能放进去」→ 收什么由 {@link TeaProcessing#canBake} 决定，
 *       绿茶没有发酵工序，所以绿茶进不来。</li>
 * </ul>
 *
 * <h2>交互</h2>
 * <table border="1">
 *   <tr><th>手上</th><th>右键</th><th>结果</th></tr>
 *   <tr><td>木炭</td><td>普通</td><td>加燃料</td></tr>
 *   <tr><td>发酵好的茶</td><td>普通</td><td>放进去（≤64）</td></tr>
 *   <tr><td>空手</td><td>普通</td><td>把茶取出来</td></tr>
 * </table>
 */
public class TeaCharcoalStoveBlock extends Block implements EntityBlock {

    /** 有没有在烧（模型据此换成炭火发红的顶面）。 */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public TeaCharcoalStoveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeaCharcoalStoveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof TeaCharcoalStoveBlockEntity stove && lvl instanceof ServerLevel sl) {
                stove.serverTick(sl, pos, st);
            }
        };
    }

    public static TeaCharcoalStoveBlockEntity stoveAt(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TeaCharcoalStoveBlockEntity s ? s : null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                         Player player, InteractionHand hand, BlockHitResult hit) {
        // 一律交给 useWithoutItem 去开界面。
        // 这里必须回 TRY_WITH_EMPTY_HAND，原版才会接着调 useWithoutItem
        // （29 轮踩过：返回 PASS 会把这条路整条堵死）。
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    /**
     * 空手 / 非目标物品右键 → 打开碳焙框界面。
     *
     * <p>需求：「右键打开，里面只能放木炭和发酵过的茶叶」。所以交互改成开界面，
     * 燃料与茶叶都通过槽位放（槽位规则见方块实体的 {@code canPlaceItem}）。</p>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inv, p) -> {
                                TeaCharcoalStoveBlockEntity be = stoveAt(level, pos);
                                return be == null
                                        ? new com.heishandashu.teacraft.menu.TeaCharcoalStoveMenu(id, inv,
                                                com.heishandashu.teacraft.menu.TeaCharcoalStoveMenu.clientContainer(), null)
                                        : new com.heishandashu.teacraft.menu.TeaCharcoalStoveMenu(id, inv, be);
                            },
                            Component.translatable("block.teacraft.tea_charcoal_stove")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
    /** 从物品栈反查阶段 id（StageItem 自带）。 */
    public static String stageIdOf(ItemStack stack) {
        if (stack.getItem() instanceof com.heishandashu.teacraft.item.TeaStageItem s) {
            return s.stage().id();
        }
        return "";
    }

    // 26.3 没有 onRemove 了，换成 affectNeighborsAfterRemoval（只有服务端会调）
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!level.getBlockState(pos).is(this)) {
            TeaCharcoalStoveBlockEntity stove = stoveAt(level, pos);
            if (stove != null) {
                // 把三个槽里的东西全掉出来（燃料也算），别吞玩家物品
                for (int i = 0; i < TeaCharcoalStoveBlockEntity.SLOT_COUNT; i++) {
                    ItemStack s = stove.getItem(i);
                    if (!s.isEmpty()) popResource(level, pos, s.copy());
                }
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    private static void sync(Level level, BlockPos pos, BlockState state) {
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static void hint(Level level, Player player, String key) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable(key), true);
        }
    }
}
