package com.heishandashu.teacraft.core;

import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 拉坯机能做的陶瓷图样表。
 *
 * <h2>为什么单独一���表</h2>
 * <p>需求：「已经有的陶瓷物品这里自动出现图样」。所以这张表不是写死给界面用的，
 * 而是<b>由注册的陶瓷物品反推出来</b>——以后再加一种陶瓷，只要在
 * {@link #register} 里补一行，拉坯机菜单、黏土消耗、窑炉输出全都跟着走。</p>
 *
 * <h2>黏土消耗规则（需求原文）</h2>
 * <ul>
 *   <li>1 黏土 → 1 个茶杯</li>
 *   <li>2 黏土 → 1 个盖碗</li>
 *   <li>4 黏土 → 1 个烧水壶</li>
 * </ul>
 */
public final class CeramicPatterns {

    /**
     * 一个图样。
     *
     * @param id      用于存档 / 网络同步的稳定字符串
     * @param result  做出来的物品
     * @param clay    需要几个黏土
     */
    public record Pattern(String id, Item result, int clay) {

        /** 显示名 —— 直接复用物品自己的名字，不额外写语言键。 */
        public net.minecraft.network.chat.Component displayName() {
            return net.minecraft.network.chat.Component.translatable(result.getDescriptionId());
        }

        public ItemStack makeStack() {
            return new ItemStack(result);
        }
    }

    /** 全部图样，注册顺序 = 界面里的顺序。 */
    private static final List<Pattern> ALL = new ArrayList<>();

    private CeramicPatterns() {
    }

    /**
     * 注册图样。
     *
     * @param id     稳定 id（存档用，改了就丢数据）
     * @param result 成品物品
     * @param clay   需要几个黏土
     */
    public static void register(String id, Item result, int clay) {
        if (clay <= 0) throw new IllegalArgumentException("clay must be > 0");
        for (Pattern p : ALL) {
            if (p.id().equals(id)) return;   // 重复注册忽略，避免热重载时翻倍
        }
        ALL.add(new Pattern(id, result, clay));
    }

    /** 模组初始化时调用一次。物品此时已注册完毕。 */
    public static void bootstrap() {
        register("tea_cup", TeaItems.TEA_CUP.get(), 1);
        register("gaiwan", TeaItems.GAIWAN.get(), 2);
        register("kettle", TeaItems.TEA_KETTLE_ITEM.get(), 4);
    }

    /** 所有图样（只读）。 */
    public static List<Pattern> all() {
        return List.copyOf(ALL);
    }

    /** 按 id 找图样，找不到返回 null。 */
    public static Pattern byId(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Pattern p : ALL) {
            if (p.id().equals(id)) return p;
        }
        return null;
    }

    /** 这个物品能不能当图纸做出来（用于「自动出现图样」的判断）。 */
    public static Pattern forItem(Item item) {
        for (Pattern p : ALL) {
            if (p.result() == item) return p;
        }
        return null;
    }

    /** 手上的东西是不是黏土。 */
    public static boolean isClay(ItemStack stack) {
        return stack.is(Items.CLAY_BALL);
    }
}
