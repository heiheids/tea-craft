package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.core.TeaKind;
import com.heishandashu.teacraft.core.TeaStages;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * 本模组的创造模式物品栏：<b>茶工艺</b>。
 *
 * <p>需求：「创造模式物品栏增加茶工艺选项卡，我们的物品都放在这里」。
 * 栏内容直接读 {@link TeaItems#ALL_ITEMS}，所以「加了物品忘了放进栏」不可能发生。</p>
 *
 * <h2>1.21.1 迁移说明</h2>
 * <p>26.3 用的是 Fabric 的 {@code FabricCreativeModeTab.builder()} +
 * {@code Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ...)}；
 * NeoForge 1.21.1 用原版的 {@link CreativeModeTab#builder()}，
 * 并通过 {@link DeferredRegister} 登记（挂到模组事件总线上）。</p>
 *
 * <p>另外 {@code builder()} 必须调 {@code .title(...)}，否则 {@code build()} 会抛
 * {@code IllegalStateException: No display name set for CreativeModeTab}。</p>
 */
public final class TeaCreativeTab {

    /**
     * 创造栏的注册键。
     *
     * <p>保留这个常量纯粹是为了方便自检 / 其它地方按 key 查表：
     * {@code BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(TeaCreativeTab.KEY)}。
     * 真正的注册走下面的 {@link DeferredRegister}。</p>
     *
     * <p>注意 1.21.1 取 key 的路径是 {@code KEY.location()}，
     * 26.3 那个 {@code KEY.identifier()} 不存在。</p>
     */
    public static final ResourceKey<CreativeModeTab> KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, TeaCraft.id("tea_craft"));

    /** 创造栏注册器。 */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TeaCraft.MOD_ID);

    /** 茶工艺创造栏。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("tea_craft",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("teacraft.tab.main"))
                    .icon(() -> new ItemStack(iconItem()))
                    .displayItems((parameters, output) -> addAll(output))
                    .build());

    private TeaCreativeTab() {
    }

    /**
     * 栏图标：用一棵成品凤凰单丛，最有辨识度。
     *
     * <p>注意：图标 supplier 是<b>延迟</b>调用的（构建创造栏内容时才执行），
     * 那时物品早已注册完成，所以这里可以直接取 {@code Item}。</p>
     */
    private static Item iconItem() {
        List<TeaStages.Stage> dancong = TeaStages.chain(TeaKind.DANCONG);
        if (!dancong.isEmpty()) {
            Item finished = TeaItems.stage(dancong.get(dancong.size() - 1));
            if (finished != null) return finished;
        }
        List<Item> all = TeaItems.ALL_ITEMS;
        return all.isEmpty() ? net.minecraft.world.item.Items.AIR : all.get(0);
    }

    /**
     * 纯逻辑，不依赖加载器事件；自检可以传自己的 Output 来验证内容。
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

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
        TeaCraft.LOGGER.info("[teacraft] 创造栏注册器已挂载：tea_craft");
    }
}
