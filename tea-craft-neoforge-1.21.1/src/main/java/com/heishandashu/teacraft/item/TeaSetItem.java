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

import java.util.List;

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
 *
 * <h2>1.21.1 迁移要点</h2>
 * <p>tooltip 签名和 26.3 不同：没有 {@code TooltipDisplay} 参数，
 * 收集容器是 {@code List<Component>}，用 {@code tooltip.add(...)}。</p>
 *
 * <p><b>还没迁移的部分</b>：茶盘朝向（{@code getPlacementState} 里那段）。
 * 1.21.1 这边的 {@code TeaSetBlock} 目前还没有 {@code FACING} 属性，
 * 朝向要连方块状态、预旋转模型、blockstate 一起改，单独作为一整块处理。</p>
 */
public class TeaSetItem extends BlockItem {

    public TeaSetItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        // ① 盖碗在不在
        Boolean gaiwan = stack.get(TeaComponents.SET_GAIWAN.get());
        boolean hasGaiwan = Boolean.TRUE.equals(gaiwan);

        // ② 三只杯子的状态
        String a = stack.get(TeaComponents.SET_CUP_A.get());
        String b = stack.get(TeaComponents.SET_CUP_B.get());
        String c = stack.get(TeaComponents.SET_CUP_C.get());
        int emptyCups = 0;
        int fullCups = 0;
        for (String s : new String[]{a, b, c}) {
            if ("empty".equals(s)) emptyCups++;
            else if ("full".equals(s)) fullCups++;
        }
        int cups = emptyCups + fullCups;

        // ③ 全空就直接说「空盘」
        if (!hasGaiwan && cups == 0) {
            tooltip.add(Component.translatable("teacraft.tooltip.set_empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("teacraft.tooltip.set_usage")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        // ④ 茶具清单
        tooltip.add(Component.translatable("teacraft.tooltip.set_vessels",
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
            var tag = data.getUnsafe();
            if (tag.contains("dry_tea")) dryTea = tag.getString("dry_tea");
            if (tag.contains("gaiwan")) gaiwanBrew = tag.getString("gaiwan");
            String[] keys = {"cup0", "cup1", "cup2"};
            for (int i = 0; i < 3; i++) {
                if (tag.contains(keys[i])) cupBrews[i] = tag.getString(keys[i]);
            }
        }

        if (hasGaiwan) {
            if (!dryTea.isEmpty()) {
                TeaBrew dry = TeaBrew.decode(dryTea);
                if (dry != null) {
                    tooltip.add(Component.translatable("teacraft.tooltip.set_dry_tea",
                                    Component.translatable(dry.kind().translationKey()),
                                    dry.quality(), dry.servings())
                            .withStyle(ChatFormatting.YELLOW));
                }
            } else if (!gaiwanBrew.isEmpty()) {
                TeaBrew brew = TeaBrew.decode(gaiwanBrew);
                if (brew != null) {
                    tooltip.add(Component.translatable("teacraft.tooltip.set_gaiwan_brew",
                                    Component.translatable(brew.kind().translationKey()),
                                    brew.quality(), brew.servings())
                            .withStyle(ChatFormatting.GOLD));
                }
            } else {
                tooltip.add(Component.translatable("teacraft.tooltip.set_gaiwan_empty")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        // ⑥ 杯子里的茶
        String[] letters = {"A", "B", "C"};
        for (int i = 0; i < 3; i++) {
            if (cupBrews[i].isEmpty()) continue;
            TeaBrew brew = TeaBrew.decode(cupBrews[i]);
            if (brew == null) continue;
            tooltip.add(Component.translatable("teacraft.tooltip.set_cup_brew",
                            letters[i], Component.translatable(brew.kind().translationKey()),
                            brew.quality())
                    .withStyle(ChatFormatting.GOLD));
        }

        tooltip.add(Component.translatable("teacraft.tooltip.set_usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
