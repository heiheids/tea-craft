package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.item.TeaDrinkItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * 茶盒的方块实体 —— 27 个槽位，每槽最多 999 个同种成品茶。
 *
 * <h2>为什么不用原版 Container</h2>
 * <p>原版 {@code ItemStack} 的数量上限是 99，而需求是 <b>999</b>。硬塞进
 * ItemStack 会在 {@code setCount} 被裁剪，或者在保存/同步时溢出。
 * 所以这里<b>自己管理</b>：</p>
 *
 * <pre>槽位 = 物品 id + 数量(int)</pre>
 *
 * <p>界面上的图标由「物品 id」渲染，数量由界面自己画（小号字）。
 * 取物品时才真正生成 {@code ItemStack}（每次不超过 64，符合原版语义）。</p>
 *
 * <h2>只收成品茶</h2>
 * <p>判定统一走 {@link TeaDrinkItem} —— 它只被三个阶段链的<b>最后</b>一个阶段使用。
 * 鲜叶、萎凋叶、揉捻叶、茶具、任何别的方块都进不来。</p>
 */
public class TeaBoxBlockEntity extends BlockEntity {

    /** 由 TeaItems 在注册后回填。 */
    public static BlockEntityType<TeaBoxBlockEntity> TYPE;

    /** 槽位数量：3 行 x 9 列。 */
    public static final int SLOT_COUNT = 27;

    /** 每个槽位的数量上限。 */
    public static final int MAX_PER_SLOT = 999;

    /** 左键一次取几个。 */
    public static final int TAKE_ON_LEFT = 3;

    /** 右键一次取几个（原版一组）。 */
    public static final int TAKE_ON_RIGHT = 64;

    /** 每个槽位存的物品；null 表示空槽。 */
    private final Item[] slotItem = new Item[SLOT_COUNT];

    /** 每个槽位的数量。 */
    private final int[] slotCount = new int[SLOT_COUNT];

    public TeaBoxBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    /* ---------------- 查询 ---------------- */

    /** 槽位里的物品；空槽返回 AIR。 */
    public Item itemIn(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return net.minecraft.world.item.Items.AIR;
        Item it = slotItem[slot];
        return it == null ? net.minecraft.world.item.Items.AIR : it;
    }

    /** 槽位里的数量。 */
    public int countIn(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return 0;
        return slotCount[slot];
    }

    public boolean isEmpty(int slot) {
        return countIn(slot) <= 0 || itemIn(slot) == net.minecraft.world.item.Items.AIR;
    }

    /** 盒子里有没有东西。 */
    public boolean hasAny() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!isEmpty(i)) return true;
        }
        return false;
    }

    /* ---------------- 存入 ---------------- */

    /**
     * 尝试存入一批成品茶。
     *
     * <p>规则：同种茶优先叠进已有槽位，叠满 999 之后再开新槽。
     * 返回<b>没存进去的剩余数量</b>（0 表示全放进去了）。</p>
     */
    public int insert(Item item, int amount) {
        if (amount <= 0 || item == null) return amount;
        if (!(item instanceof TeaDrinkItem)) return amount;    // 只收成品茶

        int left = amount;

        // 第一轮：叠进已有的同种槽位
        for (int i = 0; i < SLOT_COUNT && left > 0; i++) {
            if (slotItem[i] != item) continue;
            int space = MAX_PER_SLOT - slotCount[i];
            if (space <= 0) continue;
            int put = Math.min(space, left);
            slotCount[i] += put;
            left -= put;
        }

        // 第二轮：开新槽
        for (int i = 0; i < SLOT_COUNT && left > 0; i++) {
            if (slotItem[i] != null && slotCount[i] > 0) continue;
            int put = Math.min(MAX_PER_SLOT, left);
            slotItem[i] = item;
            slotCount[i] = put;
            left -= put;
        }

        if (left != amount) {
            setChanged();
            sync();
        }
        return left;
    }

    /* ---------------- 取出 ---------------- */

    /**
     * 从某个槽位取出若干个。
     *
     * @param want 想取多少（左键 3 / 右键 64）
     * @return 实际取出的物品栈（可能比 want 少，也可能为空）
     */
    public ItemStack extract(int slot, int want) {
        if (isEmpty(slot) || want <= 0) return ItemStack.EMPTY;
        int take = Math.min(want, slotCount[slot]);
        ItemStack out = new ItemStack(slotItem[slot], take);

        slotCount[slot] -= take;
        if (slotCount[slot] <= 0) {
            slotCount[slot] = 0;
            slotItem[slot] = null;
        }
        setChanged();
        sync();
        return out;
    }

    /** 把整盒内容清空并返回列表（潜行右键收进背包时用）。 */
    public java.util.List<ItemStack> drainAll() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (isEmpty(i)) continue;
            Item it = slotItem[i];
            int n = slotCount[i];
            // 大数量拆成多个 64 的栈，避免超过原版上限
            while (n > 0) {
                int take = Math.min(64, n);
                out.add(new ItemStack(it, take));
                n -= take;
            }
            slotItem[i] = null;
            slotCount[i] = 0;
        }
        if (!out.isEmpty()) {
            setChanged();
            sync();
        }
        return out;
    }

    /* ---------------- 存档 ---------------- */

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ValueOutput.ValueOutputList list = out.childrenList("slots");
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (isEmpty(i)) continue;
            ValueOutput child = list.addChild();
            child.putInt("slot", i);
            child.putString("item", BuiltInRegistries.ITEM.getKey(slotItem[i]).toString());
            child.putInt("count", slotCount[i]);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        java.util.Arrays.fill(slotItem, null);
        java.util.Arrays.fill(slotCount, 0);

        for (ValueInput child : in.childrenListOrEmpty("slots")) {
            int slot = child.getIntOr("slot", -1);
            if (slot < 0 || slot >= SLOT_COUNT) continue;
            String id = child.getStringOr("item", "");
            if (id.isEmpty()) continue;
            Identifier rl;
            try {
                rl = Identifier.parse(id);
            } catch (Exception ex) {
                continue;
            }
            Item it = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            int count = Math.min(MAX_PER_SLOT, Math.max(0, child.getIntOr("count", 0)));
            if (it == null || count <= 0) continue;
            slotItem[slot] = it;
            slotCount[slot] = count;
        }
    }

    /* ---------------- 同步 ---------------- */

    /**
     * 同步给客户端。
     *
     * <p>界面渲染直接读方块实体里的「物品 id + 数量」，所以<b>完整槽位数据</b>
     * 必须带上，否则打开界面看到的是空的。</p>
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (isEmpty(i)) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("slot", i);
            e.putString("item", BuiltInRegistries.ITEM.getKey(slotItem[i]).toString());
            e.putInt("count", slotCount[i]);
            list.add(e);
        }
        tag.put("slots", list);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 主动把变化推给客户端。 */
    private void sync() {
        if (level == null || level.isClientSide()) return;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }
}
