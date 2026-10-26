package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.menu.ClayPotterWheelMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 拉坯机 —— 黏土在这里做成陶瓷坯体，直接送进紧贴的窑炉。
 *
 * <h2>交互</h2>
 * <table border="1">
 *   <tr><th>操作</th><th>结果</th></tr>
 *   <tr><td>手持黏土球右键</td><td>塞进黏土格（累计）</td></tr>
 *   <tr><td>空手右键</td><td>打开界面（图样列表 + 黏土格）</td></tr>
 * </table>
 *
 * <h2>1.21.1 迁移要点</h2>
 * <ul>
 *   <li>{@code useItemOn} 返回 {@link ItemInteractionResult}。</li>
 *   <li>打开菜单用 {@link SimpleMenuProvider} + {@code openMenu(provider, writer)}。</li>
 * </ul>
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

    /** 空手右键：打开界面。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PotterWheelBlockEntity wheel)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(
                    new SimpleMenuProvider(
                            (id, inv, p) -> new ClayPotterWheelMenu(id, inv, wheel),
                            Component.translatable("block.teacraft.potter_wheel")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** 手持黏土右键：直接塞进黏土格。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
                                              Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.CLAY_BALL)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PotterWheelBlockEntity wheel)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        var clay = wheel.getItem(PotterWheelBlockEntity.SLOT_CLAY);
        int space = 64 - clay.getCount();
        if (space <= 0) {
            player.sendSystemMessage(Component.translatable("teacraft.potter_wheel.clay_full"));
            return ItemInteractionResult.SUCCESS;
        }
        int move = Math.min(space, stack.getCount());
        if (clay.isEmpty()) {
            wheel.setItem(PotterWheelBlockEntity.SLOT_CLAY, new ItemStack(Items.CLAY_BALL, move));
        } else {
            clay.grow(move);
            wheel.setChanged();
        }
        stack.shrink(move);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return ItemInteractionResult.SUCCESS;
    }
}
