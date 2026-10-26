package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaKind;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.core.TeaStages;
import com.heishandashu.teacraft.effect.TeaEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * 成品茶：可以「喝」，按品质给效果。
 *
 * <p>需求：</p>
 * <ul>
 *   <li>品质 1~5（热带最冷递增），维持时间 <b>20 / 40 / 60 / 80 / 100 分钟</b>；</li>
 *   <li>凤凰单丛：不生成幻翼、不被幻翼攻击，急迫 III；</li>
 *   <li>红茶：饱食度消耗 -90%，生命恢复 II；</li>
 *   <li>绿茶：迅捷 II，苦力怕与亡灵不主动攻击。</li>
 * </ul>
 *
 * <p>品质不是另存字段，而是<b>从产地组件推出来</b>的 ——
 * 产地一路从鲜叶带到成品，品质是它的函数，不需要额外同步。</p>
 */
public class TeaDrinkItem extends TeaStageItem {

    public TeaDrinkItem(Properties properties, TeaStages.Stage stage) {
        super(properties, stage);
    }

    /** 这一杯的品质 1~5。 */
    public static int qualityOf(ItemStack stack) {
        TeaOrigin origin = TeaComponents.originOf(stack, TeaOrigin.TEMPERATE);
        return origin.quality();
    }

    /** 这一杯能维持多少分钟。 */
    public static int minutesOf(ItemStack stack) {
        return TeaEffects.minutesFor(qualityOf(stack));
    }

    /**
     * 成品茶<b>不能直接吃</b>。
     *
     * <h2>为什么改掉</h2>
     * <p>以前右键就能把成品茶喝掉（直接给效果）。但那样会绕过整个泡茶流程 ——
     * 投茶、注水、出汤、倒杯全都可以跳过，茶具就没有意义了。</p>
     *
     * <p>现在右键只给一句提示。要喝请：盖碗里投茶 → 注入开水 → 倒进杯子 → 喝杯子。</p>
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("teacraft.msg.cannot_eat_raw")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        // 返回 SUCCESS 只为手臂摆动，实际不消耗、不给效果
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);

        // 产地：茶叶在哪个群系种的，决定等级和效果时长。
        // 这条信息对玩家判断「这罐茶值不值得留」很关键，必须显示出来。
        //
        // 【曾经漏过】tooltip.origin 这个语言键早就存在（"产地 %1$s"），
        // 但没有任何地方用它，于是成品茶上看不到产地。
        TeaOrigin origin = TeaComponents.originOf(stack, TeaOrigin.TEMPERATE);
        tooltip.accept(Component.translatable("teacraft.tooltip.origin",
                        Component.translatable(origin.translationKey()))
                .withStyle(ChatFormatting.DARK_GRAY));

        int q = qualityOf(stack);
        tooltip.accept(Component.translatable("teacraft.tooltip.quality", q)
                .withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("teacraft.tooltip.duration", minutesOf(stack))
                .withStyle(ChatFormatting.GRAY));
        // 不再显示「右键饮用」—— 玩家已经知道茶能喝，
        // 这行占了宝贵的 tooltip 空间却不提供新信息。
    }
}
