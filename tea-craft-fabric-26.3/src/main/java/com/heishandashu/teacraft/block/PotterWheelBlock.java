package com.heishandashu.teacraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 拉坯机 —— 把黏土做成各种陶瓷坯体。
 *
 * <h2>交互</h2>
 * <table border="1">
 *   <tr><th>操作</th><th>结果</th></tr>
 *   <tr><td>空手右键</td><td>打开界面：上面是黏土格，下面是图样列表</td></tr>
 *   <tr><td>手持黏土右键</td><td>塞进黏土格</td></tr>
 * </table>
 */
public class PotterWheelBlock extends Block implements EntityBlock {

    public PotterWheelBlock(Properties properties) {
        super(properties);
    }

    /**
     * 不算作光照遮挡。
     *
     * <p>四条腿之间是空的，桌面也探出方块边界；按满方块算遮挡会剔除贴面，
     * 看起来像缺面或透明。</p>
     */
    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PotterWheelBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PotterWheelBlockEntity wheel)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<
                    com.heishandashu.teacraft.menu.ClayPotterWheelMenu.OpeningData>() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("block.teacraft.potter_wheel");
                }

                @Override
                public com.heishandashu.teacraft.menu.ClayPotterWheelMenu.OpeningData
                        getScreenOpeningData(ServerPlayer p) {
                    return new com.heishandashu.teacraft.menu.ClayPotterWheelMenu.OpeningData(pos);
                }

                @Override
                public com.heishandashu.teacraft.menu.ClayPotterWheelMenu createMenu(
                        int id, net.minecraft.world.entity.player.Inventory inv,
                        Player p) {
                    var be = p.level().getBlockEntity(pos);
                    return be instanceof PotterWheelBlockEntity w
                            ? new com.heishandashu.teacraft.menu.ClayPotterWheelMenu(id, inv, w)
                            : new com.heishandashu.teacraft.menu.ClayPotterWheelMenu(id, inv,
                                    com.heishandashu.teacraft.menu.ClayPotterWheelMenu.clientContainer(),
                                    null);
                }
            });
        }
        return InteractionResult.SUCCESS;
    }

    /** 手持黏土右键：直接塞进黏土格。 */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
                                          Level level, BlockPos pos, Player player,
                                          net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(net.minecraft.world.item.Items.CLAY_BALL)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PotterWheelBlockEntity wheel)) {
            return InteractionResult.PASS;
        }

        var clay = wheel.getItem(PotterWheelBlockEntity.SLOT_CLAY);
        int space = 64 - clay.getCount();
        if (space <= 0) {
            player.sendSystemMessage(Component.translatable("teacraft.potter_wheel.clay_full"));
            return InteractionResult.SUCCESS;
        }
        int move = Math.min(space, stack.getCount());
        if (clay.isEmpty()) {
            wheel.setItem(PotterWheelBlockEntity.SLOT_CLAY,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CLAY_BALL, move));
        } else {
            clay.grow(move);
            wheel.setChanged();
        }
        stack.shrink(move);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }
}
