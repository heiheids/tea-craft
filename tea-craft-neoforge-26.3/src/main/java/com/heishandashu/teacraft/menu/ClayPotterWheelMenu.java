package com.heishandashu.teacraft.menu;

import com.heishandashu.teacraft.block.PotterWheelBlockEntity;
import com.heishandashu.teacraft.core.CeramicPatterns;
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
 * 拉坯机界面：黏土格 + 图样列表。
 *
 * <h2>图样怎么来的</h2>
 * <p>「已经有的陶瓷物品这里自动出现图样」—— 列表直接取
 * {@link CeramicPatterns#all()}，不写死。界面按按钮 id = 图样下标 发点击，
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

    /** 当前这台机器做某个图样会返回什么结果码（界面用来显示能不能点）。 */
    public int previewResult(CeramicPatterns.Pattern p) {
        if (wheel == null) return 1;
        if (!wheel.canMake(p)) return 1;
        return wheel.findKiln() == null ? 2 : 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * 点图样：做一件坯体送进窑炉。
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (wheel == null) return false;
        var patterns = CeramicPatterns.all();
        int index = id - BUTTON_PATTERN_BASE;
        if (index < 0 || index >= patterns.size()) return false;

        var pattern = patterns.get(index);
        int code = wheel.make(pattern);
        if (code != 0) {
            String key = switch (code) {
                case 1 -> "teacraft.potter_wheel.no_clay";
                case 2 -> "teacraft.potter_wheel.no_kiln";
                default -> "teacraft.potter_wheel.kiln_full";
            };
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key));
            return false;
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "teacraft.potter_wheel.made", pattern.displayName()));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int machineSlots = PotterWheelBlockEntity.SIZE;

        if (index < machineSlots) {
            if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
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

    public static Container clientContainer() {
        return new SimpleContainer(PotterWheelBlockEntity.SIZE);
    }

    public record OpeningData(net.minecraft.core.BlockPos pos) {
        public static final net.minecraft.network.codec.StreamCodec<
                RegistryFriendlyByteBuf, OpeningData> CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.core.BlockPos.STREAM_CODEC, OpeningData::pos,
                        OpeningData::new);
    }
}
