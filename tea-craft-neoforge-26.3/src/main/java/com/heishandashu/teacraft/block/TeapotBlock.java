package com.heishandashu.teacraft.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 茶壶（装饰用）—— 只是个摆件，<b>不能烧水</b>。
 *
 * <h2>为什么它也得是个方块</h2>
 * <p>最初 {@code teapot} 注册成纯物品，结果它完全放不到地上。
 * 现在两者都是方块，区别只在烧水壶能加热。</p>
 *
 * <h2>为什么要"右键给提示"</h2>
 * <p>它和烧水壶<b>用的是同一套模型</b>，肉眼完全分不出。
 * 用户实测反复「放上去、水桶右键没反应」，日志显示放下的其实是 {@code teacraft:teapot}。
 * 所以这里主动提示一句"这是装饰茶壶"，免得继续被当成烧水壶试。</p>
 */
public class TeapotBlock extends Block {

    /** 与烧水壶同一套几何，碰撞箱也保持一致：只包住壶身。 */
    private static final VoxelShape SHAPE =
            Block.box(4.6, 0.0, 3.6, 11.4, 7.9, 11.4);

    public TeapotBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    /** 右键提示：这是装饰茶壶，不能烧水。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("teacraft.msg.teapot_is_decor")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }
}
