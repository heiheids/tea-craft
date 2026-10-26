package com.heishandashu.teacraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * 窑炉 / 拉坯机这类「必须成对摆放」的方块物品，统一带上配对说明。
 *
 * <h2>为什么要写在物品信息里</h2>
 * <p>玩家在创造栏或背包里看到这个方块时，界面还没打开，看不到里面那句
 * 「必须紧贴…」。等打开了界面才发现做不了，得先去找原因。
 * 所以配对规则直接写进物品说明，拿在手里就知道要先摆好另一台。</p>
 */
public class PairedBlockItem extends BlockItem {

    /** 说明的翻译键（例如 {@code teacraft.tooltip.clay_kiln_pair}）。 */
    private final String pairKey;

    public PairedBlockItem(Block block, Properties properties, String pairKey) {
        super(block, properties);
        this.pairKey = pairKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        // 配对要求：醒目一点，用金色
        tooltip.accept(Component.translatable(pairKey)
                .withStyle(ChatFormatting.GOLD));
    }
}
