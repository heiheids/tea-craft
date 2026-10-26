package com.heishandashu.teacraft.menu;

import com.heishandashu.teacraft.block.TeaCharcoalStoveBlockEntity;
import com.heishandashu.teacraft.core.TeaProcessing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 碳焙框的界面逻辑（3 个槽：燃料 / 待焙茶 / 成品）。
 *
 * <p>需求：「右键打开，里面只能放木炭和发酵过的茶叶，和熔炉工作类似」。</p>
 *
 * <h2>槽位</h2>
 * <ol>
 *   <li>0 燃料 —— <b>只能是木炭</b>（{@link Items#CHARCOAL}）；</li>
 *   <li>1 待焙 —— <b>只能是发酵好的茶</b>（{@link TeaProcessing#canBake}）；</li>
 *   <li>2 成品 —— 只出不进。</li>
 * </ol>
 *
 * <p><b>为什么成品槽可以只出不进：</b>玩家能往槽里放东西只有 shift 点击和手动点击两条路，
 * 两条都过 {@link Slot#mayPlace}。把槽 2 的 {@code mayPlace} 写成 false 就挡住了。</p>
 */
public class TeaCharcoalStoveMenu extends AbstractContainerMenu {

    /** 槽位索引（界面按这些下标取坐标）。 */
    public static final int SLOT_FUEL_INDEX = 0;
    public static final int SLOT_INPUT_INDEX = 1;
    public static final int SLOT_OUTPUT_INDEX = 2;

    /** 客户端用：只为了建界面，不带真实容器。 */
    public static final int CLIENT_MARKER = -1;

    private final Container container;
    private final TeaCharcoalStoveBlockEntity stove;

    /** 服务端构造（真实方块实体）。 */
    public TeaCharcoalStoveMenu(int id, Inventory playerInv, TeaCharcoalStoveBlockEntity stove) {
        this(id, playerInv, stove, stove);
    }

    /** 客户端构造：只需要方块实体的引用，数据由它的 getUpdateTag 同步。 */
    public TeaCharcoalStoveMenu(int id, Inventory playerInv, Container container, TeaCharcoalStoveBlockEntity stove) {
        super(TeaMenus.CHARCOAL_STOVE, id);
        this.container = container;
        this.stove = stove;

        // 三个功能槽 —— 坐标完全照抄原版熔炉（输入 56,17 / 燃料 56,53 / 输出 116,35），\n        // 这样贴图、格子、物品三者自然对齐，不会再"偏左上"。
        this.addSlot(new Slot(container, TeaCharcoalStoveBlockEntity.SLOT_FUEL, 56, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.CHARCOAL);      // 只能放木炭
            }
        });
        this.addSlot(new Slot(container, TeaCharcoalStoveBlockEntity.SLOT_INPUT, 56, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TeaProcessing.canBake(idOf(stack));   // 只能放发酵好的茶
            }
        });
        this.addSlot(new Slot(container, TeaCharcoalStoveBlockEntity.SLOT_OUTPUT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;                          // 成品槽只出不进
            }
        });

        // 玩家背包 3x9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    /** 从物品栈取阶段 id。 */
    public static String idOf(ItemStack stack) {
        if (stack.getItem() instanceof com.heishandashu.teacraft.item.TeaStageItem s) {
            return s.stage().id();
        }
        return "";
    }

    public TeaCharcoalStoveBlockEntity stove() {
        return stove;
    }

    @Override
    public boolean stillValid(Player player) {
        // 距离太远就关掉界面（和熔炉一致）
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int machineSlots = 3;
        if (index < machineSlots) {
            // 机器 → 背包
            if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 机器：木炭进燃料槽，发酵茶进待焙槽，其余不放
            if (stack.is(Items.CHARCOAL)) {
                if (!this.moveItemStackTo(stack, TeaCharcoalStoveBlockEntity.SLOT_FUEL,
                        TeaCharcoalStoveBlockEntity.SLOT_FUEL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (TeaProcessing.canBake(idOf(stack))) {
                if (!this.moveItemStackTo(stack, TeaCharcoalStoveBlockEntity.SLOT_INPUT,
                        TeaCharcoalStoveBlockEntity.SLOT_INPUT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return result;
    }

    /** 客户端空容器（仅用于构造界面，真正的数据从方块实体同步过来）。 */
    public static Container clientContainer() {
        return new SimpleContainer(TeaCharcoalStoveBlockEntity.SLOT_COUNT);
    }

    /** 需要把方块实体的位置带过去，客户端才能找到对应方块实体。 */
    public record OpeningData(net.minecraft.core.BlockPos pos) {
        public static final net.minecraft.network.codec.StreamCodec<
                RegistryFriendlyByteBuf, OpeningData> CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.core.BlockPos.STREAM_CODEC, OpeningData::pos,
                        OpeningData::new);
    }
}
