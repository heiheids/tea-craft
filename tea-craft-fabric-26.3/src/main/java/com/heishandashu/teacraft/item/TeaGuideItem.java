package com.heishandashu.teacraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * 《茶工艺说明》—— 模组的说明书。
 *
 * <h2>怎么用</h2>
 * <p>拿在手上右键打开分页界面。这本书只是「打开界面的钥匙」，没有耐久、不会被消耗。</p>
 *
 * <h2>为什么不直接 new 界面</h2>
 * <p>界面类（{@code TeaGuideScreen}）继承客户端专属的 {@code Screen}，而物品类是
 * 两端共用的。如果这里直接引用它，<b>专用服务器</b>加载本类时可能因为找不到
 * 客户端类而抛 {@code NoClassDefFoundError}。</p>
 *
 * <p>所以用一个「打开器」间接调用：默认是空操作，客户端初始化时把它替换成
 * 真正开界面的 lambda。这样本类永远不引用任何客户端类。</p>
 */
public class TeaGuideItem extends Item {

    /** 总页数 —— 界面和翻页逻辑都读这个值。 */
    public static final int PAGE_COUNT = 11;

    /**
     * 打开说明书的动作。默认什么都不做（服务端 / 未初始化时）。
     *
     * <p>由 {@code TeaCraftClient} 在客户端初始化时替换成真正开界面的实现。</p>
     */
    private static Runnable opener = () -> {
    };

    /** 客户端注册真正的打开器（只会被客户端调用）。 */
    public static void setOpener(Runnable impl) {
        opener = impl == null ? () -> {
        } : impl;
    }

    public TeaGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            try {
                opener.run();
            } catch (Throwable t) {
                com.heishandashu.teacraft.TeaCraft.LOGGER.warn(
                        "[teacraft] 打开《茶工艺说明》失败", t);
            }
        }
        // 两端都返回 SUCCESS：服务端这样才会让客户端走完右键动画
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("teacraft.tooltip.guide_open")
                .withStyle(ChatFormatting.GRAY));
    }
}
