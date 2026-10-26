package com.heishandashu.teacraft.menu;

import com.heishandashu.teacraft.block.TeaWokBlockEntity;
import com.heishandashu.teacraft.core.TeaProcessing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 炒茶锅的界面逻辑（3 个槽：燃料 / 待炒茶 / 成品）。
 *
 * <p>布局照抄原版熔炉：燃料 (56,53) / 输入 (56,17) / 输出 (116,35)，
 * 背包 (8,84)、快捷栏 (8,142)。</p>
 *
 * <p><b>和碳焙筐一样不自动烧</b> —— 这里是纯手动：关掉界面后蹲下右键炒 3 次。</p>
 */
public class TeaWokMenu extends AbstractContainerMenu {

    public static final int SLOT_FUEL_INDEX = 0;
    public static final int SLOT_INPUT_INDEX = 1;
    public static final int SLOT_OUTPUT_INDEX = 2;

    private final Container container;
    private final TeaWokBlockEntity wok;

    /** 服务端构造（真实方块实体）。 */
    public TeaWokMenu(int id, Inventory playerInv, TeaWokBlockEntity wok) {
        this(id, playerInv, wok, wok);
    }

    /** 客户端构造。 */
    public TeaWokMenu(int id, Inventory playerInv, Container container, TeaWokBlockEntity wok) {
        super(TeaMenus.WOK, id);
        this.container = container;
        this.wok = wok;

        // 燃料槽：只收木炭
        this.addSlot(new Slot(container, TeaWokBlockEntity.SLOT_FUEL, 56, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.CHARCOAL);
            }
        });
        // 输入槽：只收可炒的茶；容量按类型（晒青叶 32 / 揉捻叶 64）
        this.addSlot(new Slot(container, TeaWokBlockEntity.SLOT_INPUT, 56, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TeaProcessing.canStir(TeaWokMenu.idOf(stack));
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                int cap = TeaProcessing.stirCapacity(TeaWokMenu.idOf(stack));
                return cap > 0 ? cap : 64;
            }
        });
        // 成品槽：只出不进
        this.addSlot(new Slot(container, TeaWokBlockEntity.SLOT_OUTPUT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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

    public static String idOf(ItemStack stack) {
        if (stack.getItem() instanceof com.heishandashu.teacraft.item.TeaStageItem s) {
            return s.stage().id();
        }
        return "";
    }

    public TeaWokBlockEntity wok() { return wok; }

    @Override
    public boolean stillValid(Player player) {
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
            if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.is(Items.CHARCOAL)) {
                if (!this.moveItemStackTo(stack, TeaWokBlockEntity.SLOT_FUEL,
                        TeaWokBlockEntity.SLOT_FUEL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (TeaProcessing.canStir(idOf(stack))) {
                if (!this.moveItemStackTo(stack, TeaWokBlockEntity.SLOT_INPUT,
                        TeaWokBlockEntity.SLOT_INPUT + 1, false)) {
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

    public static Container clientContainer() {
        return new SimpleContainer(TeaWokBlockEntity.SLOT_COUNT);
    }

    /** 把方块坐标带给客户端。 */
    public record OpeningData(net.minecraft.core.BlockPos pos) {
        public static final net.minecraft.network.codec.StreamCodec<
                RegistryFriendlyByteBuf, OpeningData> CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.core.BlockPos.STREAM_CODEC, OpeningData::pos,
                        OpeningData::new);
    }
}
