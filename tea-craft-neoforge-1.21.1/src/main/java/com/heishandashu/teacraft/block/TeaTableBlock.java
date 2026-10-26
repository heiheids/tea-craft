package com.heishandashu.teacraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 茶桌 —— 满格方块，桌面顶在 {@code y=16}（NeoForge 1.21.1）。
 *
 * <h2>为什么做成满格</h2>
 * <p>需求是「把茶盘放到茶桌上要紧密贴合」。茶盘是 3px 高的方块，放在方块顶部
 * （{@code y=16~19}）。只要茶桌是完整的一格、桌面正好在 {@code y=16}，
 * 茶盘落上去就是严丝合缝的 —— 不需要渲染偏移。</p>
 *
 * <h2>朝向</h2>
 * <p>不分朝向：参考图是方桌，四面对称。</p>
 */
public class TeaTableBlock extends Block {

    public static final MapCodec<TeaTableBlock> CODEC = simpleCodec(TeaTableBlock::new);

    /** 满格碰撞箱。 */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public TeaTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }
}
