package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.core.TeaStages;
import com.heishandashu.teacraft.effect.TeaEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 鎴愬搧鑼讹細鍙互銆屽枬銆嶏紝鎸夊搧璐ㄧ粰鏁堟灉銆?
 *
 * <p>闇€姹傦細</p>
 * <ul>
 *   <li>鍝佽川 1~5锛堢儹甯︽渶鍐烽€掑锛夛紝缁存寔鏃堕棿 <b>20 / 40 / 60 / 80 / 100 鍒嗛挓</b>锛?/li>
 *   <li>鍑ゅ嚢鍗曚笡锛氫笉鐢熸垚骞荤考銆佷笉琚够缈兼敾鍑伙紝鎬ヨ揩 III锛?/li>
 *   <li>绾㈣尪锛氶ケ椋熷害娑堣€?-90%锛岀敓鍛芥仮澶?II锛?/li>
 *   <li>缁胯尪锛氳繀鎹?II锛岃嫤鍔涙€曚笌浜＄伒涓嶄富鍔ㄦ敾鍑汇€?/li>
 * </ul>
 *
 * <p>鍝佽川涓嶆槸鍙﹀瓨瀛楁锛岃€屾槸<b>浠庝骇鍦扮粍浠舵帹鍑烘潵</b>鐨?鈥斺€?
 * 浜у湴涓€璺粠椴滃彾甯﹀埌鎴愬搧锛屽搧璐ㄦ槸瀹冪殑鍑芥暟锛屼笉闇€瑕侀澶栧悓姝ャ€?/p>
 *
 * <h2>1.21.1 杩佺Щ璇存槑</h2>
 * <p>{@code use} 杩斿洖 {@link InteractionResultHolder}锛寋@code appendHoverText} 鐢?
 * {@code (ItemStack, TooltipContext, List<Component>, TooltipFlag)}锛涙暟鎹粍浠惰缁?
 * {@code TeaComponents.XXX.get()} 鍙栧€硷紙{@link TeaComponents#originOf} 鍐呴儴宸插鐞嗭級銆?/p>
 */
public class TeaDrinkItem extends TeaStageItem {

    public TeaDrinkItem(Properties properties, TeaStages.Stage stage) {
        super(properties, stage);
    }

    /** 杩欎竴鏉殑鍝佽川 1~5銆?*/
    public static int qualityOf(ItemStack stack) {
        TeaOrigin origin = TeaComponents.originOf(stack, TeaOrigin.TEMPERATE);
        return origin.quality();
    }

    /** 杩欎竴鏉兘缁存寔澶氬皯鍒嗛挓銆?*/
    public static int minutesOf(ItemStack stack) {
        return TeaEffects.minutesFor(qualityOf(stack));
    }

    /**
     * 鎴愬搧鑼?b>涓嶈兘鐩存帴鍚?/b>銆?
     *
     * <h2>涓轰粈涔堟敼鎺?/h2>
     * <p>浠ュ墠鍙抽敭灏辫兘鎶婃垚鍝佽尪鍠濇帀锛堢洿鎺ョ粰鏁堟灉锛夈€備絾閭ｆ牱浼氱粫杩囨暣涓场鑼舵祦绋?鈥斺€?
     * 鎶曡尪銆佹敞姘淬€佸嚭姹ゃ€佸€掓澂鍏ㄩ兘鍙互璺宠繃锛岃尪鍏峰氨娌℃湁鎰忎箟浜嗐€?/p>
     *
     * <p>鐜板湪鍙抽敭鍙粰涓€鍙ユ彁绀恒€傝鍠濊锛氱洊纰楅噷鎶曡尪 鈫?娉ㄥ叆寮€姘?鈫?鍊掕繘鏉瓙 鈫?鍠濇澂瀛愩€?/p>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("teacraft.msg.cannot_eat_raw")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        // 杩斿洖 SUCCESS 鍙负鎵嬭噦鎽嗗姩锛屽疄闄呬笉娑堣€椼€佷笉缁欐晥鏋?
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        // 产地：茶叶在哪个群系种的，决定等级和效果时长。
        // 这条信息对玩家判断「这罐茶值不值得留」很关键，必须显示出来。
        //
        // 【曾经漏过】tooltip.origin 这个语言键早就存在（"产地 %1$s"），
        // 但没有任何地方用它，于是成品茶上看不到产地。
        TeaOrigin origin = TeaComponents.originOf(stack, TeaOrigin.TEMPERATE);
        tooltip.add(Component.translatable("teacraft.tooltip.origin",
                        Component.translatable(origin.translationKey()))
                .withStyle(ChatFormatting.DARK_GRAY));

        int q = qualityOf(stack);
        tooltip.add(Component.translatable("teacraft.tooltip.quality", q)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("teacraft.tooltip.duration", minutesOf(stack))
                .withStyle(ChatFormatting.GRAY));
        // 不再显示「右键饮用」—— 玩家已经知道茶能喝，那行只占空间。
    }
}
