package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.core.TeaBrew;
import com.heishandashu.teacraft.core.TeaComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 盖碗 —— 泡茶用的容器，可以拿在手上。
 *
 * <h2>两种内容都跟着物品走</h2>
 * <ul>
 *   <li>{@code teacraft:tea_brew} —— 泡好的茶汤（茶类 + 品质 + 几杯）</li>
 *   <li>{@code teacraft:dry_tea} —— 还没注水的干茶（茶类 + 品质 + 几份）</li>
 * </ul>
 *
 * <p>之前拿起盖碗时<b>只带走了茶汤、把干茶丢了</b>
 * （方块实体里 {@code setDryTea("")} 清空且没写进物品），
 * 所以「有茶叶的盖碗拿起来茶叶就消失了」。现在两者都序列化到物品上。</p>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <ul>
 *   <li>数据组件改成 {@code DeferredHolder}，取值要写 {@code TeaComponents.XXX.get()}；</li>
 *   <li>{@code appendHoverText} 的 1.21.1 签名是
 *       {@code (ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)}——
 *       没有 {@code TooltipDisplay}，也不是 {@code Consumer<Component>}。</li>
 * </ul>
 */
public class GaiwanItem extends Item {

    public GaiwanItem(Properties properties) {
        super(properties);
    }

    /** 读出这份盖碗里的茶汤；没有就返回 {@code null}。 */
    public static TeaBrew brewOf(ItemStack stack) {
        return TeaBrew.decode(stack.get(TeaComponents.TEA_BREW.get()));
    }

    /** 写回茶汤（{@code null} 表示倒空了）。 */
    public static void setBrew(ItemStack stack, TeaBrew brew) {
        if (brew == null) {
            stack.remove(TeaComponents.TEA_BREW.get());
        } else {
            stack.set(TeaComponents.TEA_BREW.get(), brew.encode());
        }
    }

    /**
     * 读出盖碗里的<b>干茶</b>（未注水的茶叶份数）；没有返回 {@code null}。
     *
     * <p>复用 {@link TeaBrew} 的结构：{@code kind|quality|count}，
     * 其中 {@code count} 是份数。</p>
     */
    public static TeaBrew dryTeaOf(ItemStack stack) {
        return TeaBrew.decode(stack.get(TeaComponents.DRY_TEA.get()));
    }

    public static void setDryTea(ItemStack stack, TeaBrew tea) {
        if (tea == null) {
            stack.remove(TeaComponents.DRY_TEA.get());
        } else {
            stack.set(TeaComponents.DRY_TEA.get(), tea.encode());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        TeaBrew dry = dryTeaOf(stack);
        TeaBrew brew = brewOf(stack);

        // 空：既没有干茶也没有茶汤
        if (dry == null && brew == null) {
            tooltip.add(Component.translatable("teacraft.tooltip.gaiwan_state_empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("teacraft.tooltip.gaiwan_put_leaves")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        // 干茶：还没注水
        if (dry != null) {
            tooltip.add(Component.translatable("teacraft.tooltip.gaiwan_state_dry",
                            Component.translatable(dry.kind().translationKey()),
                            dry.quality(), dry.servings())
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("teacraft.tooltip.gaiwan_need_water")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        // 茶汤：已泡好，可以倒杯
        if (brew != null) {
            tooltip.add(Component.translatable("teacraft.tooltip.gaiwan_state_brew",
                            Component.translatable(brew.kind().translationKey()),
                            brew.quality(), brew.servings())
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("teacraft.tooltip.gaiwan_pour")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
