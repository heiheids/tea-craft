package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.core.TeaBrew;
import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.effect.TeaEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 茶杯 —— 从茶盘上拿起来、端在手里喝的那一只。
 *
 * <p>杯里的茶汤（茶类 + 品质）记在 {@code teacraft:tea_brew} 组件里；
 * 右键喝掉：给对应的茶效果、杯子变回空杯（这里直接消耗掉，简化处理）。</p>
 *
 * <p>喝下去的效果完全复用 {@link TeaEffects}，所以和直接喝成品茶
 * 走的是同一套数值，不会出现两套不一致的效果。</p>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <ul>
 *   <li>{@code Item#use} 在 1.21.1 返回 {@link InteractionResultHolder InteractionResultHolder&lt;ItemStack&gt;}
 *       （26.3 才返回裸的 {@code InteractionResult}）；</li>
 *   <li>数据组件要写 {@code TeaComponents.XXX.get()}；</li>
 *   <li>{@code appendHoverText} 用 1.21.1 的 {@code (ItemStack, TooltipContext, List<Component>, TooltipFlag)}。</li>
 * </ul>
 */
public class TeaCupItem extends Item {

    public TeaCupItem(Properties properties) {
        super(properties);
    }

    public static TeaBrew brewOf(ItemStack stack) {
        return TeaBrew.decode(stack.get(TeaComponents.TEA_BREW.get()));
    }

    public static void setBrew(ItemStack stack, TeaBrew brew) {
        if (brew == null) {
            stack.remove(TeaComponents.TEA_BREW.get());
        } else {
            stack.set(TeaComponents.TEA_BREW.get(), brew.encode());
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        TeaBrew brew = brewOf(stack);
        if (brew == null || brew.isEmpty()) {
            return InteractionResultHolder.pass(stack);      // 空杯子：不喝水
        }

        if (level.isClientSide()) {
            // 客户端只播动作，服务端结算
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            TeaEffects.drink(sp, brew.kind(), brew.quality());
            sp.sendSystemMessage(Component.translatable("teacraft.msg.tea_drunk",
                    Component.translatable(brew.kind().translationKey()),
                    brew.quality(),
                    brew.minutes()), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F, 1.0F);
            // 喝完变成空杯留在手上，而不是消失 —— 杯子是器物，不该消耗掉。
            setBrew(stack, null);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        TeaBrew brew = brewOf(stack);
        if (brew == null || brew.isEmpty()) {
            tooltip.add(Component.translatable("teacraft.tooltip.cup_empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("teacraft.tooltip.brew_kind",
                        Component.translatable(brew.kind().translationKey()))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("teacraft.tooltip.quality", brew.quality())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("teacraft.tooltip.duration", brew.minutes())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("teacraft.tooltip.cup_drink")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
