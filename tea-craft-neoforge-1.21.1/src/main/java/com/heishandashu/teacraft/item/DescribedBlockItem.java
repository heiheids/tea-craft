package com.heishandashu.teacraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 带「简介」的方块物品（NeoForge 1.21.1）。
 *
 * <h2>为什么单独一个类</h2>
 * <p>{@link PairedBlockItem} 是「必须紧贴另一台机器」的专用提示（金色一行）。
 * 茶桌、茶椅这类需要<b>多行</b>说明，并且要区分主次：</p>
 * <ul>
 *   <li>第一行：一句话功能简介（灰色）</li>
 *   <li>第二行：操作提示（深灰，视觉上更弱）</li>
 * </ul>
 *
 * <h2>1.21.1 差异</h2>
 * <p>tooltip 收集器是 {@code List<Component>}，用 {@code add()}；
 * 26.3 是 {@code Consumer<Component>}，用 {@code accept()}。</p>
 */
public class DescribedBlockItem extends BlockItem {

    /** 简介的语言键（例如 {@code teacraft.tooltip.tea_table}）。 */
    private final String descKey;

    /** 操作提示的语言键；不需要就传 null。 */
    private final String hintKey;

    public DescribedBlockItem(Block block, Properties properties, String descKey) {
        this(block, properties, descKey, null);
    }

    public DescribedBlockItem(Block block, Properties properties,
                              String descKey, String hintKey) {
        super(block, properties);
        this.descKey = descKey;
        this.hintKey = hintKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        if (descKey != null) {
            tooltip.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
        }
        if (hintKey != null) {
            tooltip.add(Component.translatable(hintKey).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
