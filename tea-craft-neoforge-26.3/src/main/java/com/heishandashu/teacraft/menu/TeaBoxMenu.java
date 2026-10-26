package com.heishandashu.teacraft.menu;

import com.heishandashu.teacraft.block.TeaBoxBlockEntity;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 茶盒界面 —— 27 格，每格最多 999 个同种成品茶。
 *
 * <h2>槽位为什么是「虚拟」的</h2>
 * <p>原版 {@code ItemStack} 数量上限 99，存不下 999。所以这里每个槽位只是
 * <b>一个显示位</b>：它不含真实 {@code ItemStack}，数据在
 * {@link TeaBoxBlockEntity} 里（物品 id + 数量）。</p>
 *
 * <p>代价是 click 处理要全部自己写（见 {@link #clicked}），但好处是数量可以
 * 任意大，而且能精确实现「左键取 3 个 / 右键取 64 个」这种原版没有的语义。</p>
 */
public class TeaBoxMenu extends AbstractContainerMenu {

    /** 盒子区一共 27 格（3 行 x 9 列）。 */
    public static final int BOX_SLOTS = TeaBoxBlockEntity.SLOT_COUNT;

    /** 界面每行格数。 */
    public static final int COLS = 9;

    /** 盒子区在界面里的左上角。 */
    public static final int BOX_X = 8;
    public static final int BOX_Y = 18;

    /** 玩家背包区起点。 */
    public static final int INV_Y = 84;

    private final TeaBoxBlockEntity box;

    /** 客户端构造用的空盒子（只为建界面）。 */
    public TeaBoxMenu(int id, Inventory playerInv) {
        this(id, playerInv, new TeaBoxBlockEntity(net.minecraft.core.BlockPos.ZERO,
                TeaItems.TEA_BOX.get().defaultBlockState()));
    }

    public TeaBoxMenu(int id, Inventory playerInv, TeaBoxBlockEntity box) {
        super(TeaMenus.TEA_BOX.get(), id);
        this.box = box;

        // 27 个虚拟槽位：只占位，不让原版碰。
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = col + row * COLS;
                this.addSlot(new BoxSlot(this, box, idx,
                        BOX_X + col * 18, BOX_Y + row * 18));
            }
        }

        // 玩家背包 3x9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, INV_Y + 58));
        }
    }

    public TeaBoxBlockEntity box() {
        return box;
    }

    /**
     * 点击处理。
     *
     * <p>规则（按需求）：</p>
     * <ul>
     *   <li>左键盒子槽 -> 取 TAKE_ON_LEFT（3 个）</li>
     *   <li>右键盒子槽 -> 取 TAKE_ON_RIGHT（64 个）</li>
     *   <li>Shift 点击 -> 该槽全部取出</li>
     *   <li>玩家背包槽 -> 原版行为</li>
     * </ul>
     */
    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        // 玩家背包区域：交回原版
        if (slotId < 0 || slotId >= BOX_SLOTS) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        // 取多少
        int want;
        if (clickType == ContainerInput.QUICK_MOVE) {
            want = TeaBoxBlockEntity.MAX_PER_SLOT;          // Shift：全取
        } else if (clickType == ContainerInput.PICKUP || clickType == ContainerInput.PICKUP_ALL) {
            want = (button == 1)
                    ? TeaBoxBlockEntity.TAKE_ON_RIGHT           // 右键 64
                    : TeaBoxBlockEntity.TAKE_ON_LEFT;           // 左键 3
        } else {
            return;
        }

        if (box.isEmpty(slotId)) return;

        ItemStack got = box.extract(slotId, want);
        if (got.isEmpty()) return;

        // 先试着叠进鼠标拖着的物品，再放进背包
        ItemStack carried = getCarried();
        if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, got)) {
            int canAdd = Math.min(got.getCount(), carried.getMaxStackSize() - carried.getCount());
            if (canAdd > 0) {
                carried.grow(canAdd);
                got.shrink(canAdd);
                setCarried(carried);
            }
        }
        if (!got.isEmpty()) {
            if (!player.getInventory().add(got)) {
                // 背包满了：塞回盒子，别吞掉玩家的茶
                box.insert(got.getItem(), got.getCount());
            }
        }

        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 盒子槽的 Shift 行为已在 clicked() 处理，这里只处理背包 -> 盒子
        if (index >= 0 && index < BOX_SLOTS) return ItemStack.EMPTY;

        Slot slot = getSlot(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (!(stack.getItem() instanceof com.heishandashu.teacraft.item.TeaDrinkItem)) {
            return ItemStack.EMPTY;
        }
        int left = box.insert(stack.getItem(), stack.getCount());
        int moved = stack.getCount() - left;
        if (moved > 0) {
            stack.shrink(moved);
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** 拾取全部时跳过虚拟槽位。 */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        if (slot instanceof BoxSlot) return false;
        return super.canTakeItemForPickAll(stack, slot);
    }

    /**
     * 虚拟槽位：显示用，数据在方块实体里。
     *
     * <p>{@code getItem} 返回「只有 1 个」的展示栈，因为真实数量可能有 999，
     * 塞进 ItemStack 会被裁剪。数量由界面自己画（小号字）。</p>
     */
    public static class BoxSlot extends Slot {

        private final TeaBoxBlockEntity box;
        private final int idx;

        public BoxSlot(TeaBoxMenu menu, TeaBoxBlockEntity box, int idx, int x, int y) {
            super(new net.minecraft.world.SimpleContainer(1), 0, x, y);
            this.box = box;
            this.idx = idx;
        }

        public int index() {
            return idx;
        }

        /** 展示用的物品：只放 1 个，避免超过原版上限。 */
        @Override
        public ItemStack getItem() {
            if (box.isEmpty(idx)) return ItemStack.EMPTY;
            return new ItemStack(box.itemIn(idx), 1);
        }

        /** 真实数量（界面用来画数字）。 */
        public int realCount() {
            return box.countIn(idx);
        }

        @Override
        public boolean hasItem() {
            return !box.isEmpty(idx);
        }

        @Override
        public void set(ItemStack stack) {
            // 拒绝原版写入 —— 数据只能通过 TeaBoxBlockEntity 改
        }

        @Override
        public void setChanged() {
            // 同上
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;      // 不放原版物品进来，存入走方块实体
        }

        @Override
        public boolean mayPickup(Player player) {
            return !box.isEmpty(idx);
        }
    }

    /** 打开界面时只带方块坐标，客户端自己去 level 里取方块实体。 */
    public record OpenData(net.minecraft.core.BlockPos pos) {
        public static final net.minecraft.network.codec.StreamCodec<
                RegistryFriendlyByteBuf, OpenData> CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.core.BlockPos.STREAM_CODEC, OpenData::pos,
                        OpenData::new);
    }
}
