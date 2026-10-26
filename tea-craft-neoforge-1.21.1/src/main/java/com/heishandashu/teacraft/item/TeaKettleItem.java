package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.block.TeaKettleBlock;
import com.heishandashu.teacraft.core.TeaComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 烧水壶（物品形态）—— 从热源上拿起来的壶。
 *
 * <p><b>必须继承 {@link BlockItem}</b>：它既要能当方块放下，又要能携带
 * 「已烧开」状态。<b>如果注册成普通 BlockItem</b>，
 * 盖碗那边 {@code instanceof TeaKettleItem} 就永远为 false，
 * 开水倒不进去 —— 这正是最初版本的问题。</p>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <ul>
 *   <li>数据组件要写 {@code TeaComponents.HOT_WATER.get()}；</li>
 *   <li>{@code appendHoverText} 用 1.21.1 的
 *       {@code (ItemStack, TooltipContext, List<Component>, TooltipFlag)}。</li>
 * </ul>
 */
public class TeaKettleItem extends BlockItem {

    public TeaKettleItem(TeaKettleBlock block, Properties properties) {
        super(block, properties);
    }

    /**
     * 放置时按物品携带的热水状态决定方块状态。
     *
     * <h2>为什么不用 BLOCK_ENTITY_DATA</h2>
     * <p>实测发现放置流程里 {@code BLOCK_ENTITY_DATA} 在这条路径上
     * 根本到不了 {@code getPlacementState} / {@code setPlacedBy}
     *（加日志确认：两者收到的 stack 都显示"有数据=false"），
     * 结果就是"装了开水的壶放回去，水没了、也没烧开"。</p>
     *
     * <p>改用自己维护的 {@link TeaComponents#HOT_WATER} 组件 ——
     * 它是普通数据组件，跟着物品走，不受方块实体序列化流程影响。</p>
     */
    @Override
    protected net.minecraft.world.level.block.state.BlockState getPlacementState(
            net.minecraft.world.item.context.BlockPlaceContext context) {
        var base = super.getPlacementState(context);
        if (base == null) return null;

        boolean hot = hasHotWater(context.getItemInHand());
        return base
                .setValue(TeaKettleBlock.WATER, hot)
                .setValue(TeaKettleBlock.BOILED, hot);
    }

    /** 壶里有没有烧开的热水。 */
    public static boolean hasHotWater(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(TeaComponents.HOT_WATER.get()));
    }

    public static void setHotWater(ItemStack stack, boolean hot) {
        if (hot) {
            stack.set(TeaComponents.HOT_WATER.get(), Boolean.TRUE);
        } else {
            stack.remove(TeaComponents.HOT_WATER.get());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (hasHotWater(stack)) {
            tooltip.add(Component.translatable("teacraft.tooltip.kettle_hot")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("teacraft.tooltip.kettle_pour")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("teacraft.tooltip.kettle_empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("teacraft.tooltip.kettle_heat")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
