package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.core.TeaOrigin;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 第 1 阶段：鲜叶。同一种贴图（1.png）按采摘群系分 5 个产地变体。
 *
 * <p>需求：「不同的群系采摘的茶叶要赋予不同的名字」、
 * 「每种茶 1 阶段新鲜茶叶都是一样的，对应 1 图」—— 所以五个变体共用一个贴图，
 * 只有名字和描述里的产地不同。</p>
 *
 * <p>鲜叶是所有茶共同的起点：把它交给不同的第 2 阶段机器
 * （摊凉架 / 萎凋架 / 晒青席）就分别走上绿茶 / 红茶 / 单丛的路线。</p>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <p>{@code appendHoverText} 的签名是
 * {@code (ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)}：
 * 1.21.1 没有 {@code TooltipDisplay} 参数，工具提示是 {@code List} 而不是 {@code Consumer}。</p>
 */
public class TeaFreshLeafItem extends Item {

    private final TeaOrigin origin;

    public TeaFreshLeafItem(Properties properties, TeaOrigin origin) {
        super(properties);
        this.origin = origin;
    }

    public TeaOrigin origin() { return origin; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("teacraft.tooltip.stage",
                Component.translatable("teacraft.kind.fresh"),
                1,
                1).withStyle(ChatFormatting.GOLD));

        tooltip.add(Component.translatable("teacraft.tooltip.origin",
                Component.translatable(origin.translationKey())).withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("teacraft.flavor.fresh_leaf")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        tooltip.add(Component.translatable("teacraft.tooltip.fresh_routes")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
