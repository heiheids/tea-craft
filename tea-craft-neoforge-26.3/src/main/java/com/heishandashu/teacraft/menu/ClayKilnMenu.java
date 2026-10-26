package com.heishandashu.teacraft.menu;

import com.heishandashu.teacraft.block.ClayKilnBlockEntity;
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
 * 窑炉界面：左侧 6 坯料 / 右侧 6 成品 / 下方燃料。
 *
 * <h2>槽位规则（对着需求一条条落实）</h2>
 * <ul>
 *   <li><b>坯料位 ×6</b>：玩家只能「撤销」不能放东西 —— {@link Slot#mayPlace} 返回 false，
 *       撤销走 {@code clickMenuButton}。</li>
 *   <li><b>成品位 ×6</b>：只出不进。</li>
 *   <li><b>燃料位</b>：只收煤炭 / 木炭。</li>
 * </ul>
 *
 * <h2>撤销怎么走</h2>
 * <p>原版槽位被 {@code mayPlace=false} 挡住后，玩家点它不会有反应。所以撤销做成
 * <b>菜单按钮</b>：界面在坯料格上画一个「×」，点它发 button id = 200 + 槽序号，
 * 服务端收到后调 {@link ClayKilnBlockEntity#cancelBlank(int)} 把黏土退给玩家。</p>
 */
public class ClayKilnMenu extends AbstractContainerMenu {

    /** 撤销按钮的 id 基数（200~205 对应 6 个坯料位）。 */
    public static final int BUTTON_CANCEL_BASE = 200;

    /** 点火按钮。 */
    public static final int BUTTON_IGNITE = 100;

    private final Container container;
    private final ClayKilnBlockEntity kiln;

    /** 同步给客户端的两个只读槽：剩余 tick、是否在烧。 */
    /** 同步用：服务端读方块实体、客户端收同步包写回来。 */
    private net.minecraft.world.inventory.ContainerData data;
    /** 客户端缓存：从同步包收到的值。 */
    private int clientTicks = 0;
    private int clientLit = 0;

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
                 * 坯料位禁止玩家取出。
                 *
                 * <p><b>这条以前漏了，是个真 bug：</b>格子里存的是成品物品
                 * （{@code tea_cup} 等），{@code mayPlace=false} 只挡住"放进去"，
                 * 原版 {@code Slot} 默认 {@code mayPickup=true}，于是玩家
                 * <b>点火后能直接把坯料当成品拿走</b>。</p>
                 *
                 * <p>唯一正确的取出方式是界面上的「×」撤销，它走
                 * {@code clickMenuButton}，退的是黏土。</p>
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
        //
        // 【踩过的两个坑】
        // ① 自己 new DataSlot 重写 get() 去读方块实体 —— 客户端 get() 读的是
        //    「客户端那个不跑 tick 的方块实体」，永远返回打开界面时的初始值。
        // ② DataSlot.standalone() —— 它只是普通缓存，用在容器菜单里框架不会
        //    把它跟同步包连起来，客户端还是拿不到值（倒计时直接不显示）。
        //
        // 正确做法就是原版熔炉那套：给菜单一个 ContainerData，
        // 用 addDataSlots(data) 注册。菜单框架会调 DataSlot.forContainer(data, i)，
        // 服务端读 data.get(i) 拿真实进度、客户端把收到的值写回 data.set(i, v)，
        // 界面再读同一个 data 就是同步后的值。
        this.data = new net.minecraft.world.inventory.ContainerData() {
            @Override
            public int get(int index) {
                // 服务端：读方块实体的真实进度（权威来源）
                // 客户端：读同步包写进来的缓存
                //
                // 必须区分两端。若客户端也走 kiln.xxx，读到的是「客户端那个
                // 不跑 tick 的方块实体」，永远是打开界面那一刻的初始值 ——
                // 倒计时就会卡死不动。
                if (isServerSide()) {
                    return switch (index) {
                        case 0 -> kiln.remainingTicks();
                        case 1 -> kiln.isLit() ? 1 : 0;
                        default -> 0;
                    };
                }
                return index == 0 ? clientTicks : clientLit;
            }

            @Override
            public void set(int index, int value) {
                // 客户端收到同步包时会走到这里。
                // 记到本地字段，界面就能读到服务端的值。
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

    /**
     * 按钮：点火 / 撤销某件坯料。
     */
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
            player.getInventory().placeItemBackInInventory(
                    refund, net.minecraft.util.Prediction.SERVER_ONLY);
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

    /** 客户端空容器。 */
    public static Container clientContainer() {
        return new SimpleContainer(ClayKilnBlockEntity.SIZE);
    }

    public record OpeningData(net.minecraft.core.BlockPos pos) {
        public static final net.minecraft.network.codec.StreamCodec<
                RegistryFriendlyByteBuf, OpeningData> CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.core.BlockPos.STREAM_CODEC, OpeningData::pos,
                        OpeningData::new);
    }
}
