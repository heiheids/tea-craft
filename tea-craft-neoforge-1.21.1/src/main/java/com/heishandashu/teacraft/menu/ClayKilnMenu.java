package com.heishandashu.teacraft.menu;

import com.heishandashu.teacraft.block.ClayKilnBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 窑炉界面：左侧 6 坯料 / 右侧 6 成品 / 下方燃料。
 *
 * <h2>槽位规则（对着需求一条条落实）</h2>
 * <ul>
 *   <li><b>坯料位 ×6</b>：玩家既不能放、也不能拿 —— {@code mayPlace=false} +
 *       {@code mayPickup=false}，撤销走 {@code clickMenuButton}。</li>
 *   <li><b>成品位 ×6</b>：只出不进。</li>
 *   <li><b>燃料位</b>：只收煤炭 / 木炭。</li>
 * </ul>
 *
 * <h2>撤销怎么走</h2>
 * <p>原版槽位被 {@code mayPlace=false} 挡住后，玩家点它不会有反应。所以撤销做成
 * <b>菜单按钮</b>：界面在坯料格上画一个「×」，点它发 button id = 200 + 槽序号，
 * 服务端收到后调 {@link ClayKilnBlockEntity#cancelBlank(int)} 把黏土退给玩家。</p>
 *
 * <h2>倒计时怎么同步（踩过很多坑）</h2>
 * <p>用 {@link ContainerData} + {@code addDataSlots}（原版熔炉同款）。
 * 服务端 {@code get()} 读方块实体拿真实进度，客户端把收到的值写进
 * {@code clientTicks} 再从 {@code get()} 读出来。</p>
 *
 * <p><b>注意</b>：客户端分支不能也去读 {@code kiln.xxx} —— 那是客户端
 * 不跑 tick 的方块实体，永远是打开界面那一刻的初始值，倒计时会卡死。</p>
 */
public class ClayKilnMenu extends AbstractContainerMenu {

    /** 撤销按钮的 id 基数（200~205 对应 6 个坯料位）。 */
    public static final int BUTTON_CANCEL_BASE = 200;

    /** 点火按钮。 */
    public static final int BUTTON_IGNITE = 100;

    private final Container container;
    private final ClayKilnBlockEntity kiln;

    /** 同步用：服务端读方块实体、客户端收同步包写回来。 */
    private final ContainerData data;

    /** 客户端缓存：从同步包收到的值。 */
    private int clientTicks = 0;
    private int clientLit = 0;

    /** 服务端构造：直接从方块实体建。 */
    public ClayKilnMenu(int id, Inventory playerInv, ClayKilnBlockEntity kiln) {
        this(id, playerInv, kiln, kiln);
    }

    public ClayKilnMenu(int id, Inventory playerInv, Container container, ClayKilnBlockEntity kiln) {
        super(TeaMenus.CLAY_KILN.get(), id);
        this.container = container;
        this.kiln = kiln;

        // 坯料位 6 个：左侧两列三行
        for (int i = 0; i < ClayKilnBlockEntity.BLANK_COUNT; i++) {
            int col = i % 2;
            int row = i / 2;
            int x = 26 + col * 18;
            int y = 20 + row * 18;
            this.addSlot(new Slot(container, ClayKilnBlockEntity.SLOT_BLANK_START + i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;   // 坯料位只由拉坯机写入，玩家放不进来
                }

                /**
                 * 坯料位也禁止玩家取出。
                 *
                 * <p>格子里存的是成品物品（tea_cup 等），{@code mayPlace=false}
                 * 只挡住「放进去」，原版 Slot 默认 {@code mayPickup=true}，
                 * 于是玩家点火后能直接把坯料当成品拿走。</p>
                 */
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }

        // 成品位 6 个：右侧两列三行
        for (int i = 0; i < ClayKilnBlockEntity.OUTPUT_COUNT; i++) {
            int col = i % 2;
            int row = i / 2;
            int x = 116 + col * 18;
            int y = 20 + row * 18;
            this.addSlot(new Slot(container, ClayKilnBlockEntity.SLOT_OUTPUT_START + i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;   // 成品只出不进
                }
            });
        }

        // 燃料位
        this.addSlot(new Slot(container, ClayKilnBlockEntity.SLOT_FUEL, 26, 78) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ClayKilnBlockEntity.isFuel(stack);
            }
        });

        // 把「剩余 tick」和「是否在烧」同步给客户端。
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (isServerSide()) {
                    return switch (index) {
                        case 0 -> kiln == null ? 0 : kiln.remainingTicks();
                        case 1 -> kiln != null && kiln.isLit() ? 1 : 0;
                        default -> 0;
                    };
                }
                return index == 0 ? clientTicks : clientLit;
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) clientTicks = value;
                else if (index == 1) clientLit = value;
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
        this.addDataSlots(this.data);

        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 124 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 182));
        }
    }

    public ClayKilnBlockEntity kiln() {
        return kiln;
    }

    /**
     * 客户端读：剩余 tick。
     *
     * <p>服务端直接读方块实体；客户端读同步包写进 {@code clientTicks} 的值。</p>
     */
    public int getSyncedRemainingTicks() {
        if (isServerSide()) return this.kiln == null ? 0 : this.kiln.remainingTicks();
        return this.clientTicks;
    }

    /** 客户端读：是否在烧。 */
    public boolean isSyncedBurning() {
        if (isServerSide()) return this.kiln != null && this.kiln.isLit();
        return this.clientLit != 0;
    }

    /** 菜单挂在服务端还是客户端。 */
    private boolean isServerSide() {
        return this.kiln != null && this.kiln.getLevel() != null
                && !this.kiln.getLevel().isClientSide();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /** 按钮：点火 / 撤销某件坯料。 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (kiln == null) return false;

        if (id == BUTTON_IGNITE) {
            return kiln.tryStartBurning();
        }
        if (id >= BUTTON_CANCEL_BASE && id < BUTTON_CANCEL_BASE + ClayKilnBlockEntity.BLANK_COUNT) {
            int index = id - BUTTON_CANCEL_BASE;
            ItemStack refund = kiln.cancelBlank(index);
            if (refund.isEmpty()) return false;
            // 退黏土给玩家：placeItemBackInInventory 会先塞背包，塞不下自动掉在脚边
            player.getInventory().placeItemBackInInventory(refund, true);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        int machineSlots = ClayKilnBlockEntity.SIZE;

        // shift 点击也不许把坯料搬走。
        //
        // 坯料位里的东西是「待烧的坯」，玩家唯一能拿走的方式是点「×」撤销
        // （退黏土）。bulk 搬走会绕过 mayPickup，所以在这里再挡一道。
        if (index >= ClayKilnBlockEntity.SLOT_BLANK_START
                && index < ClayKilnBlockEntity.SLOT_OUTPUT_START) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (index < machineSlots) {
            // 机器 → 背包（只剩成品位和燃料位能走这里）
            if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 机器：只有燃料能进
            if (!ClayKilnBlockEntity.isFuel(stack)) return ItemStack.EMPTY;
            if (!this.moveItemStackTo(stack, ClayKilnBlockEntity.SLOT_FUEL,
                    ClayKilnBlockEntity.SLOT_FUEL + 1, false)) {
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
        return new SimpleContainer(ClayKilnBlockEntity.SIZE);
    }
}
