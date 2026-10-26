package com.heishandashu.teacraft.menu;

import com.heishandashu.teacraft.block.PotterWheelBlockEntity;
import com.heishandashu.teacraft.core.CeramicPatterns;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 拉坯机界面：黏土格 + 图样列表。
 *
 * <h2>图样怎么来的</h2>
 * <p>「已经有的陶瓷物品这里自动出现图样」—— 列表直接读
 * {@link CeramicPatterns#all()}，不写死。界面按「按钮 id = 图样下标」发点击，
 * 服务端收到后调 {@link PotterWheelBlockEntity#make}。</p>
 *
 * <h2>产物去哪</h2>
 * <p>不留在本机，直接进旁边窑炉的坯料位（需求：「然后窑炉左侧坯料窗口出现选择的图样」）。
 * 所以这个界面里<b>没有产物槽</b>。</p>
 */
public class ClayPotterWheelMenu extends AbstractContainerMenu {

    /** 图样按钮 id = 下标（0 起）。 */
    public static final int BUTTON_PATTERN_BASE = 0;

    private final Container container;
    private final PotterWheelBlockEntity wheel;

    public ClayPotterWheelMenu(int id, Inventory playerInv, PotterWheelBlockEntity wheel) {
        this(id, playerInv, wheel, wheel);
    }

    public ClayPotterWheelMenu(int id, Inventory playerInv, Container container,
                               PotterWheelBlockEntity wheel) {
        super(TeaMenus.POTTER_WHEEL.get(), id);
        this.container = container;
        this.wheel = wheel;

        // 黏土格
        this.addSlot(new Slot(container, PotterWheelBlockEntity.SLOT_CLAY, 26, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.CLAY_BALL);
            }
        });

        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 116 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 174));
        }
    }

    public PotterWheelBlockEntity wheel() {
        return wheel;
    }

    /**
     * 这个图样现在能不能做（用于界面把行画灰、把提示换成原因）。
     *
     * @return 0=可以，1=黏土不够，2=没找到紧邻的窑炉，3=窑炉坯料位满了
     */
    public int previewResult(CeramicPatterns.Pattern pattern) {
        if (wheel == null) return 2;
        if (!wheel.canMake(pattern)) return 1;
        var kiln = wheel.findKiln();
        if (kiln == null) return 2;
        // 窑炉正在烧制时也会拒绝（addBlank 里锁定）
        if (kiln.isLit()) return 3;
        for (int i = 0; i < com.heishandashu.teacraft.block.ClayKilnBlockEntity.BLANK_COUNT; i++) {
            if (kiln.blankAt(i).isEmpty()) return 0;
        }
        return 3;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (wheel == null) return false;
        var all = CeramicPatterns.all();
        int index = id - BUTTON_PATTERN_BASE;
        if (index < 0 || index >= all.size()) return false;

        int result = wheel.make(all.get(index));
        // 成交后要把窑炉的数据推过去，否则界面看不到新坯料
        var kiln = wheel.findKiln();
        if (result == 0 && kiln != null && kiln.getLevel() != null) {
            kiln.setChanged();
            kiln.getLevel().sendBlockUpdated(kiln.getBlockPos(),
                    kiln.getBlockState(), kiln.getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        return result == 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int machineSlots = PotterWheelBlockEntity.SIZE;

        if (index < machineSlots) {
            // 机器 → 背包
            if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 机器：只有黏土能进
            if (!stack.is(Items.CLAY_BALL)) return ItemStack.EMPTY;
            if (!this.moveItemStackTo(stack, PotterWheelBlockEntity.SLOT_CLAY,
                    PotterWheelBlockEntity.SLOT_CLAY + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return result;
    }

    /** 客户端空容器（找不到方块实体时兜底，避免界面崩）。 */
    public static Container clientContainer() {
        return new SimpleContainer(PotterWheelBlockEntity.SIZE);
    }
}
