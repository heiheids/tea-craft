package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.core.TeaBrew;
import com.heishandashu.teacraft.core.TeaComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Consumer;

/**
 * 茶盘物品 —— 拿在手里时显示盘上有什么。
 *
 * <h2>为什么要单独一个类</h2>
 * <p>茶盘是合成出来的<b>空盘</b>，茶具靠玩家自己放上去。拿起整只盘时，
 * 盘上内容记在物品组件里（{@code set_gaiwan} / {@code set_cup_*} /
 * {@code BLOCK_ENTITY_DATA}）。如果只当普通 BlockItem，鼠标悬停只能看到
 * 一行名字，玩家根本不知道盘里带了什么。</p>
 *
 * <p>这里把「有几只茶具、盖碗里有没有干茶/茶汤、每只杯子里有没有茶」
 * 都列出来。</p>
 */
public class TeaSetItem extends BlockItem {

    public TeaSetItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * 放置时的初始状态 —— 朝向在这里定。
     *
     * <h2>为什么必须重写</h2>
     * <p>26.3 的物品放置流程走的是
     * {@code BlockItem.getPlacementState} → {@code Block.getStateForPlacement}，
     * 但实测这条链上拿到的 {@code BlockPlaceContext} 并不总能落到
     * 我们要的那个重载上。烧水壶当初也是同一个坑：{@code getStateForPlacement}
     * 加了日志一次都没打印，最后靠重写物品的 {@code getPlacementState} 才生效。</p>
     *
     * <h2>朝向的优先级（这里踩过坑）</h2>
     * <ol>
     *   <li><b>物品组件里存了朝向</b>（从盘上拿起来过）→ 用存的那个。
     *       玩家把茶盘拿起来再放下，朝向应该保持，不该突然转向自己。</li>
     *   <li>没有存值（合成出来、创造栏拿的）→ 才按玩家朝向定。</li>
     * </ol>
     * <p>第一版无条件按玩家朝向覆盖，结果「带茶具的茶盘拿起再放下，
     * 朝向全变成玩家面向的那一边」—— 因为这些盘子的朝向记在组件里，
     * 被这里直接抹掉了。</p>
     */
    @Override
    protected BlockState getPlacementState(
            net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState base = super.getPlacementState(context);
        if (base == null) return base;
        if (!base.hasProperty(com.heishandashu.teacraft.block.TeaSetBlock.FACING)) return base;

        // 朝向一律看玩家，不读物品里的 set_facing。
        //
        // 曾经这里「优先用保存的朝向」，结果：茶盘被拿起过一次之后，
        // 物品上就永远带着那个朝向，之后无论站在哪边放下都还原成同一个
        // 方向，玩家再也转不动它。朝向是摆放时决定的，不该被物品记住。
        net.minecraft.core.Direction want =
                context.getHorizontalDirection().getOpposite();
        if (!want.getAxis().isHorizontal()) {
            want = net.minecraft.core.Direction.NORTH;
        }
        return base.setValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING, want);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);

        // ① 盖碗在不在
        Boolean gaiwan = stack.get(TeaComponents.SET_GAIWAN);
        boolean hasGaiwan = Boolean.TRUE.equals(gaiwan);

        // ② 三只杯子的状态
        String a = stack.get(TeaComponents.SET_CUP_A);
        String b = stack.get(TeaComponents.SET_CUP_B);
        String c = stack.get(TeaComponents.SET_CUP_C);
        int emptyCups = 0;
        int fullCups = 0;
        for (String s : new String[]{a, b, c}) {
            if ("empty".equals(s)) emptyCups++;
            else if ("full".equals(s)) fullCups++;
        }
        int cups = emptyCups + fullCups;

        // ③ 全空就直接说"空盘"
        if (!hasGaiwan && cups == 0) {
            tooltip.accept(Component.translatable("teacraft.tooltip.set_empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("teacraft.tooltip.set_usage")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        // ④ 茶具清单
        tooltip.accept(Component.translatable("teacraft.tooltip.set_vessels",
                        hasGaiwan ? Component.translatable("item.teacraft.gaiwan")
                                : Component.literal("—"),
                        cups)
                .withStyle(ChatFormatting.GRAY));

        // ⑤ 盖碗里的干茶 / 茶汤
        //    这两项存在方块实体的 NBT 里，用 BLOCK_ENTITY_DATA 读出来
        var data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        String dryTea = "";
        String gaiwanBrew = "";
        String[] cupBrews = {"", "", ""};
        if (data != null) {
            var tag = data.copyTagWithoutId();
            if (tag.contains("dry_tea")) dryTea = tag.getStringOr("dry_tea", "");
            if (tag.contains("gaiwan")) gaiwanBrew = tag.getStringOr("gaiwan", "");
            String[] keys = {"cup0", "cup1", "cup2"};
            for (int i = 0; i < 3; i++) {
                if (tag.contains(keys[i])) cupBrews[i] = tag.getStringOr(keys[i], "");
            }
        }

        if (hasGaiwan) {
            if (!dryTea.isEmpty()) {
                TeaBrew dry = TeaBrew.decode(dryTea);
                if (dry != null) {
                    tooltip.accept(Component.translatable("teacraft.tooltip.set_dry_tea",
                                    Component.translatable(dry.kind().translationKey()), dry.quality(), dry.servings())
                            .withStyle(ChatFormatting.YELLOW));
                }
            } else if (!gaiwanBrew.isEmpty()) {
                TeaBrew brew = TeaBrew.decode(gaiwanBrew);
                if (brew != null) {
                    tooltip.accept(Component.translatable("teacraft.tooltip.set_gaiwan_brew",
                                    Component.translatable(brew.kind().translationKey()), brew.quality(), brew.servings())
                            .withStyle(ChatFormatting.GOLD));
                }
            } else {
                tooltip.accept(Component.translatable("teacraft.tooltip.set_gaiwan_empty")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        // ⑥ 杯子里的茶
        String[] letters = {"A", "B", "C"};
        for (int i = 0; i < 3; i++) {
            if (cupBrews[i].isEmpty()) continue;
            TeaBrew brew = TeaBrew.decode(cupBrews[i]);
            if (brew == null) continue;
            tooltip.accept(Component.translatable("teacraft.tooltip.set_cup_brew",
                            letters[i], Component.translatable(brew.kind().translationKey()), brew.quality())
                    .withStyle(ChatFormatting.GOLD));
        }

        tooltip.accept(Component.translatable("teacraft.tooltip.set_usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
