package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.TeaProcessing;
import com.heishandashu.teacraft.menu.TeaWokMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 炒茶锅 —— 外形取自原版炼药锅（Y 轴压到 2/3），带界面。
 *
 * <h2>交互</h2>
 * <table border="1">
 *   <tr><th>操作</th><th>结果</th></tr>
 *   <tr><td>右键（不潜行）</td><td><b>打开界面</b>：放茶叶 + 木炭</td></tr>
 *   <tr><td><b>潜行 + 右键</b></td><td><b>炒一次</b>（共 3 次；第 3 次结算并消耗 1 块木炭）</td></tr>
 * </table>
 *
 * <p>需求：「右键打开炒茶锅，界面类似碳焙……不自动烧，需要玩家退出界面，
 * 蹲下右键炒茶锅炒制 3 次，一次完成全部茶叶炒制，消耗一个木炭」。</p>
 */
public class TeaWokBlock extends Block implements EntityBlock {

    /** 锅里有没有茶（换模型用）。 */
    public static final BooleanProperty HAS_TEA = BooleanProperty.create("has_tea");

    /** 锅身比一格矮：顶部在 10.67px。 */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 10.67, 16.0);

    public TeaWokBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HAS_TEA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_TEA);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeaWokBlockEntity(pos, state);
    }

    public static TeaWokBlockEntity wokAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TeaWokBlockEntity w ? w : null;
    }

    /* ---------------- 交互 ---------------- */

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                         Player player, InteractionHand hand, BlockHitResult hit) {
        // 一律交给 useWithoutItem —— 那里按"是否潜行"分流成「炒制」或「开界面」。
        // 必须回 TRY_WITH_EMPTY_HAND，原版才会接着调 useWithoutItem。
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        TeaWokBlockEntity wok = wokAt(level, pos);
        if (wok == null) return InteractionResult.PASS;

        // 潜行 = 炒一次
        if (player.isShiftKeyDown()) {
            return stir(state, level, pos, player, wok);
        }

        // 不潜行 = 开界面
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new ExtendedMenuProvider<TeaWokMenu.OpeningData>() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("block.teacraft.tea_wok");
                }

                @Override
                public TeaWokMenu.OpeningData getScreenOpeningData(ServerPlayer p) {
                    return new TeaWokMenu.OpeningData(pos);
                }

                @Override
                public TeaWokMenu createMenu(int id, Inventory inv, Player p) {
                    TeaWokBlockEntity be = wokAt(level, pos);
                    return be == null
                            ? new TeaWokMenu(id, inv, TeaWokMenu.clientContainer(), null)
                            : new TeaWokMenu(id, inv, be);
                }
            });
        }
        return InteractionResult.SUCCESS;
    }

    /** 蹲下右键：炒一次，按结果给不同提示。 */
    private InteractionResult stir(BlockState state, Level level, BlockPos pos, Player player, TeaWokBlockEntity wok) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        TeaWokBlockEntity.StirResult result = wok.stir();
        sync(level, pos, state);

        switch (result) {
            case PROGRESS -> {
                hint(level, player, "teacraft.msg.wok_stir_progress");
                level.playSound(null, pos, SoundEvents.BAMBOO_STEP, SoundSource.BLOCKS, 0.6F, 1.1F);
            }
            case DONE -> {
                hint(level, player, "teacraft.msg.wok_stirred");
                level.playSound(null, pos, SoundEvents.BAMBOO_PLACE, SoundSource.BLOCKS, 0.9F, 0.9F);
            }
            case NO_FUEL -> hint(level, player, "teacraft.msg.wok_no_fuel");
            case NO_INPUT -> hint(level, player, "teacraft.msg.wok_needs_rolled");
            case OUTPUT_FULL -> hint(level, player, "teacraft.msg.wok_full");
            case OVER_CAPACITY -> hint(level, player, "teacraft.msg.wok_over_capacity");
            // 冷却中的连发直接忽略，不提示（否则按住右键会刷屏）
            case COOLDOWN -> { }
        }
        return InteractionResult.SUCCESS;
    }

    /* ---------------- 掉落 ---------------- */

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!level.getBlockState(pos).is(this)) {
            TeaWokBlockEntity wok = wokAt(level, pos);
            if (wok != null) {
                for (int i = 0; i < TeaWokBlockEntity.SLOT_COUNT; i++) {
                    ItemStack s = wok.getItem(i);
                    if (!s.isEmpty()) popResource(level, pos, s.copy());
                }
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /* ---------------- 工具 ---------------- */

    public static void sync(Level level, BlockPos pos, BlockState state) {
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static void hint(Level level, Player player, String key) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable(key), true);
        }
    }

    /** 供界面判断：这个物品能不能进锅。 */
    public static boolean accepts(String stageId) {
        return TeaProcessing.canStir(stageId);
    }
}
