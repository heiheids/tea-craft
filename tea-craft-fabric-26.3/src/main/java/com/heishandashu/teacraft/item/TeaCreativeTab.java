package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.core.TeaKind;
import com.heishandashu.teacraft.core.TeaStages;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 本模组的创造模式物品栏：<b>茶工艺</b>。
 *
 * <p>需求：「创造模式物品栏增加茶工艺选项卡，我们的物品都放在这里」。
 * 栏内容直接读 {@link TeaItems#ALL_ITEMS}，所以「加了物品忘了放进栏」不可能发生。</p>
 *
 * <p><b>26.3 的坑：</b>Fabric 这套 API 现在叫 {@code creativetab}（包
 * {@code net.fabricmc.fabric.api.creativetab.v1}），不是老的 {@code itemgroup}。
 * 另外 {@code builder()} 必须调 {@code .title(...)}，否则 {@code build()} 会抛
 * {@code IllegalStateException: No display name set for CreativeModeTab}。</p>
 */
public final class TeaCreativeTab {

    public static final ResourceKey<CreativeModeTab> KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, TeaCraft.id("tea_craft"));

    private TeaCreativeTab() {
    }

    public static CreativeModeTab create() {
        return FabricCreativeModeTab.builder()
                .title(Component.translatable("teacraft.tab.main"))
                .icon(() -> new ItemStack(iconItem()))
                .displayItems((parameters, output) -> addAll(output))
                .build();
    }

    /** 栏图标：用一棵成品凤凰单丛，最有辨识度。 */
    private static Item iconItem() {
        List<TeaStages.Stage> dancong = TeaStages.chain(TeaKind.DANCONG);
        if (!dancong.isEmpty()) {
            Item finished = TeaItems.stage(dancong.get(dancong.size() - 1));
            if (finished != null) return finished;
        }
        return TeaItems.ALL_ITEMS.get(0);
    }

    /**
     * 纯逻辑，不依赖 Fabric 事件；自检可以传自己的 Output 来验证内容。
     *
     * <h2>为什么要在这里去重</h2>
     * <p>原版 {@code CreativeModeTab.ItemDisplayBuilder.accept} 遇到<b>同一个物品出现两次</b>
     * 会直接抛 {@code IllegalStateException: Accidentally adding the same item stack twice}，
     * 导致<b>整个游戏启动崩溃</b>（见过这个崩溃报告）。</p>
     *
     * <p>只靠"注册时别加重复"不够稳：一旦 {@code ALL_ITEMS} 里出现同一个 Item 两次，
     * 崩溃发生在客户端构建创造栏的时候，跟注册阶段隔了很远，很难查。
     * 所以在真正往栏里塞的这一步做兜底去重 —— 崩溃变成"少显示一个"，不会起不来。</p>
     */
    public static void addAll(CreativeModeTab.Output output) {
        java.util.Set<Item> added = new java.util.LinkedHashSet<>();
        for (Item item : TeaItems.ALL_ITEMS) {
            if (item == null || !added.add(item)) continue;
            output.accept(item);
        }
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, create());
        TeaCraft.LOGGER.info("[teacraft] 创造栏已注册：{} 个物品", TeaItems.ALL_ITEMS.size());
    }
}
