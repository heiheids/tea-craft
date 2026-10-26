package com.heishandashu.teacraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 烧水茶桌 —— 嵌入岩浆块的茶桌变体（NeoForge 1.21.1）。
 *
 * <h2>它做什么</h2>
 * <p>桌子中心嵌了一块岩浆，所以把烧水壶放在桌面上就能像放在岩浆块上一样烧水。</p>
 *
 * <h2>怎么接上烧水壶的</h2>
 * <p>{@code TeaKettleBlock.isHeatSource()} 判断正下方那一格，这里让本方块
 * 也被那个方法接受即可。</p>
 *
 * <h2>没有伤害</h2>
 * <p>需求明确「没有玩家烫伤效果」。所以这里<b>不继承</b> {@code MagmaBlock}，
 * 也没有 {@code stepOn} / {@code entityInside} 钩子 —— 它只是一张桌子。</p>
 */
public class TeaTableHotBlock extends Block {

    public static final MapCodec<TeaTableHotBlock> CODEC = simpleCodec(TeaTableHotBlock::new);

    /** 满格碰撞箱（和普通茶桌一致，茶盘放上去无缝）。 */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public TeaTableHotBlock(Properties properties) {
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
