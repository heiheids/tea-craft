package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.core.TeaProcessing;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 炒茶锅 —— 带界面的炒制方块。
 *
 * <h2>绿茶流程（本类服务的两条）</h2>
 * <pre>
 *   晒青 →[炒茶锅，最多 32 份，炒 3 次]→ 杀青
 *   揉捻 →[炒茶锅，最多 64 份，炒 3 次]→ 炒干 →[碳焙筐]→ 成品
 * </pre>
 *
 * <h2>与碳焙筐的区别（关键）</h2>
 * <table border="1">
 *   <tr><th></th><th>碳焙筐</th><th>炒茶锅</th></tr>
 *   <tr><td>自动烧</td><td>放入即自动</td><td><b>不自动</b></td></tr>
 *   <tr><td>触发</td><td>界面里等待</td><td><b>退出界面，蹲下右键 3 次</b></td></tr>
 *   <tr><td>木炭</td><td>每块 1600 tick</td><td><b>炒完一批消耗 1 块</b></td></tr>
 * </table>
 */
public class TeaWokBlockEntity extends BlockEntity implements Container {

    /** 由 TeaItems 在注册后回填。 */
    public static BlockEntityType<TeaWokBlockEntity> TYPE;

    public static final int SLOT_FUEL = 0;
    public static final int SLOT_INPUT = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    /** 需要炒几次。 */
    public static final int STIRS_NEEDED = 3;

    /**
     * 两次炒制之间至少隔多少 tick。
     *
     * <p><b>为什么需要它</b>：原版 {@code Minecraft.startUseItem} 在交互成功后
     * 会把 {@code rightClickDelay} 设为 4（字节码里的 {@code iconst_4}），
     * 也就是<b>按住右键不放会每 4 tick 触发一次交互</b>。
     * 没有冷却时，按住 0.6 秒就能把 3 次炒制全触发掉 —— 看起来就是「瞬间完成」，
     * 玩家根本没有"炒了三次"的操作感。</p>
     */
    public static final int STIR_COOLDOWN_TICKS = 10;
    /** 槽位硬上限。 */
    /** 一次最多放 32 份（需求：所有工作方块统一 32）。 */
    public static final int MAX_ITEMS = 32;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    /** 已经炒了几次（0~3）。 */
    private int stirs = 0;
    /** 上一次被接受的炒制时刻，用于冷却判定。 */
    private long lastStirTick = -100L;
    private TeaOrigin origin = TeaOrigin.TEMPERATE;

    public TeaWokBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    /* ---------------- 查询 ---------------- */

    public int stirs() { return stirs; }
    public TeaOrigin origin() { return origin; }

    /** 0~1 的炒制进度。 */
    public float stirProgress() {
        return Math.min(1f, stirs / (float) STIRS_NEEDED);
    }

    public static String idOf(ItemStack stack) {
        if (stack.getItem() instanceof com.heishandashu.teacraft.item.TeaStageItem s) {
            return s.stage().id();
        }
        return "";
    }

    /** 当前输入物品允许的最大份数（晒青叶 32 / 揉捻叶 64）。 */
    public int capacity() {
        return TeaProcessing.stirCapacity(idOf(items.get(SLOT_INPUT)));
    }

    /* ---------------- Container ---------------- */

    @Override
    public int getContainerSize() { return SLOT_COUNT; }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) setChanged();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize(stack)) stack.setCount(getMaxStackSize(stack));
        setChanged();
    }

    /** 燃料槽只收木炭；输入槽只收可炒的茶；成品槽不收。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_FUEL -> stack.is(Items.CHARCOAL);
            case SLOT_INPUT -> TeaProcessing.canStir(idOf(stack));
            default -> false;
        };
    }

    @Override
    public int getMaxStackSize() { return MAX_ITEMS; }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        stirs = 0;
        setChanged();
    }

    /* ---------------- 炒制 ---------------- */

    /**
     * 炒一次（蹲下右键）。
     *
     * <p>炒满 {@link #STIRS_NEEDED} 次时<b>一次性把锅里所有茶叶</b>转成下一阶段，
     * 并消耗 <b>1 块木炭</b>；木炭不够则拒绝且回退计数。</p>
     */
    public StirResult stir() {
        // 冷却：防止"按住右键"把 3 次连成一次（见 STIR_COOLDOWN_TICKS 的说明）
        long now = level == null ? 0L : level.getGameTime();
        if (now - lastStirTick < STIR_COOLDOWN_TICKS) {
            return StirResult.COOLDOWN;
        }

        ItemStack input = items.get(SLOT_INPUT);
        if (input.isEmpty() || !TeaProcessing.canStir(idOf(input))) {
            return StirResult.NO_INPUT;
        }
        if (input.getCount() > capacity()) {
            return StirResult.OVER_CAPACITY;
        }
        String outputId = TeaProcessing.stirOutput(idOf(input));
        if (outputId == null) return StirResult.NO_INPUT;

        Room room = outputRoom(outputId);
        if (!room.canTake()) return StirResult.OUTPUT_FULL;

        lastStirTick = now;
        stirs++;
        if (stirs < STIRS_NEEDED) {
            setChanged();
            return StirResult.PROGRESS;
        }

        // 第 3 次：结算前先检查木炭
        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.isEmpty() || !fuel.is(Items.CHARCOAL)) {
            stirs = STIRS_NEEDED - 1;
            setChanged();
            return StirResult.NO_FUEL;
        }

        int n = Math.min(input.getCount(), room.room());
        if (n <= 0) {
            stirs = STIRS_NEEDED - 1;
            setChanged();
            return StirResult.OUTPUT_FULL;
        }

        origin = TeaComponents.originOf(input, TeaOrigin.TEMPERATE);
        input.shrink(n);
        if (input.isEmpty()) items.set(SLOT_INPUT, ItemStack.EMPTY);

        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) {
            ItemStack created = new ItemStack(TeaItems.stage(outputId), n);
            // 产地跟着成品走
            TeaComponents.setOrigin(created, origin);
            items.set(SLOT_OUTPUT, created);
        } else {
            out.grow(n);
        }

        fuel.shrink(1);
        stirs = 0;
        setChanged();
        return StirResult.DONE;
    }

    /** 成品槽可用空间。 */
    public record Room(boolean canTake, int room) { }

    private Room outputRoom(String outputId) {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) return new Room(true, MAX_ITEMS);
        if (!(out.getItem() instanceof com.heishandashu.teacraft.item.TeaStageItem s)) {
            return new Room(false, 0);
        }
        if (!s.stage().id().equals(outputId)) return new Room(false, 0);
        int left = MAX_ITEMS - out.getCount();
        return new Room(left > 0, Math.max(0, left));
    }

    /** 炒制结果。 */
    public enum StirResult {
        /** 又炒了一次，还没到 3 次。 */
        PROGRESS,
        /** 第 3 次完成，已产出。 */
        DONE,
        /** 锅里没有可炒的茶。 */
        NO_INPUT,
        /** 没有木炭。 */
        NO_FUEL,
        /** 成品槽满了或装了别的产物。 */
        OUTPUT_FULL,
        /** 一次放太多，超过本轮容量。 */
        OVER_CAPACITY,
        /** 离上次炒制太近（按住右键的连发被挡掉）。 */
        COOLDOWN
    }

    /* ---------------- 存档 ---------------- */

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("stirs", stirs);
        out.putString("origin", origin.id());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items.clear();
        // 先按存档格式读
        ContainerHelper.loadAllItems(in, items);
        // 再按同步格式读：getUpdateTag 写的是 slot_N + ItemStack.CODEC，
        // 少了这一段，客户端那份 items 永远是空的 → 界面里看不到放进去的物品。
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (items.get(i).isEmpty()) {
                var opt = in.read("slot_" + i, ItemStack.CODEC);
                if (opt.isPresent()) items.set(i, opt.get());
            }
        }
        stirs = in.getIntOr("stirs", 0);
        origin = TeaOrigin.byId(in.getStringOr("origin", TeaOrigin.TEMPERATE.id()));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stirs", stirs);
        tag.putString("origin", origin.id());
        // 完整 ItemStack：界面靠它画图标，只发 id/数量会导致界面里看不见物品
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty()) output.store("slot_" + i, ItemStack.CODEC, s);
        }
        tag.merge(output.buildResult());
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 界面标题。 */
    public Component title() {
        return Component.translatable("block.teacraft.tea_wok");
    }
}
