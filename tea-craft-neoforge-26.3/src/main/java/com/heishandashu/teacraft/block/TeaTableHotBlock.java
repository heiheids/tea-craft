package com.heishandashu.teacraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 烧水茶桌 —— 嵌入岩浆块的茶桌变体。
 *
 * <h2>它做什么</h2>
 * <p>桌子中心嵌了一块岩浆，所以把烧水壶放在桌面上就能像放在岩浆块上一样烧水。</p>
 *
 * <h2>怎么接上烧水壶的</h2>
 * <p>{@code TeaKettleBlock.isHeatSource()} 用 {@code level.getBlockState(pos.below())}
 * 判断正下方那一格是不是热源，认得 {@code MagmaBlock} / 点燃的营火 / 点燃的熔炉。
 * 这里让本方块<b>也</b>被那个方法接受（见 {@link TeaKettleBlock#isHeatSource}），
 * 壶就能在桌面上烧水了。</p>
 *
 * <h2>没有伤害</h2>
 * <p>需求明确「没有玩家烫伤效果」。所以这里<b>不继承</b> {@code MagmaBlock}，
 * 也不加 {@code entityInside} / {@code stepOn} 之类的伤害钩子 —— 它就是一张桌子，
 * 只是内部嵌了岩浆。踩上去、坐旁边都不会掉血。</p>
 */
public class TeaTableHotBlock extends Block {

    /** 满格碰撞箱（和普通茶桌一致，茶盘放上去无缝）。 */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public TeaTableHotBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }
}
