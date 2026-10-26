package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.core.TeaStages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 某个茶某个阶段的物品（2 阶段以后，含成品）。
 *
 * <p>需求：「物品描述一定要写清楚是某个茶叶的第几阶段」。所以 tooltip 固定三行：</p>
 * <ol>
 *   <li>哪一种茶 · <b>第 N / M 阶段</b></li>
 *   <li>这道工序叫什么 · 要用哪台机器</li>
 *   <li>这一阶段叶片的状态（取自流程说明原文）</li>
 * </ol>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <p>{@code appendHoverText} 的签名是
 * {@code (ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)}。</p>
 */
public class TeaStageItem extends Item {

    private final TeaStages.Stage stage;

    public TeaStageItem(Properties properties, TeaStages.Stage stage) {
        super(properties);
        this.stage = stage;
    }

    public TeaStages.Stage stage() { return stage; }

    /**
     * 带产地的显示名。
     *
     * <p>物品只注册一份，靠组件区分产地（否则 3 种茶 × 5 产地 × 各阶段会把物品表撑爆）。
     * 有产地组件时显示成「热带 单丛·做青叶」，没有时（例如创造栏直接拿的）保持原名。</p>
     */
    @Override
    public Component getName(ItemStack stack) {
        if (!TeaComponents.hasOrigin(stack)) {
            return super.getName(stack);
        }
        TeaOrigin origin = TeaComponents.originOf(stack, TeaOrigin.TEMPERATE);
        return Component.translatable("teacraft.stage_name",
                Component.translatable(origin.translationKey()),
                super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        // 共享的第 2 阶段不属于任何一条路线（kind == null），
        // 所以不能说"第 N 步 / 共 M 步"，只说它是个岔路口。
        if (stage.isShared()) {
            tooltip.add(Component.translatable("teacraft.tooltip.shared_stage")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("teacraft.tooltip.stage",
                    Component.translatable(stage.kind().translationKey()),
                    stage.number(),
                    stage.kind().lastStage()).withStyle(ChatFormatting.GOLD));
        }

        tooltip.add(Component.translatable("teacraft.tooltip.process",
                Component.translatable(stage.processKey())).withStyle(ChatFormatting.GRAY));

        if (TeaComponents.hasOrigin(stack)) {
            tooltip.add(Component.translatable("teacraft.tooltip.origin",
                    Component.translatable(TeaComponents.originOf(stack, TeaOrigin.TEMPERATE).translationKey()))
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("teacraft.flavor." + stage.id())
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
