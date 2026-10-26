package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.CeramicPatterns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 拉坯机 —— 用黏土做陶瓷坯体，直接送进旁边的窑炉。
 *
 * <h2>流程</h2>
 * <ol>
 *   <li>往方块里放黏土球</li>
 *   <li>在界面的图样列表里点一个（茶杯 1 黏土 / 盖碗 2 / 烧水壶 4）</li>
 *   <li>坯体直接进窑炉排队，玩家不用在两个方块之间搬东西</li>
 * </ol>
 *
 * <h2>找窑炉的规则</h2>
 * <p><b>必须紧贴</b>：只看上下左右前后 6 个面。
 * 隔着格子放是不算的 —— 需求要求两台机器相邻摆放。</p>
 *
 * <h2>1.21.1 迁移要点</h2>
 * <p>序列化是 {@code saveAdditional(CompoundTag, HolderLookup.Provider)}；
 * {@code ContainerHelper} 的读写方法多一个 registries 参数。</p>
 */
public class PotterWheelBlockEntity extends BlockEntity implements Container {

    /** 由 TeaItems 在注册后回填。 */
    public static BlockEntityType<PotterWheelBlockEntity> TYPE;

    public static final int SLOT_CLAY = 0;
    public static final int SIZE = 1;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public PotterWheelBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    /**
     * 找一台能接单的窑炉。
     *
     * <p>只认紧贴的 6 个面（需求：必须相邻放置）。
     * 之前还会往 3x3x3、半径 4 去搜，导致隔着几格也能连上，
     * 玩家看不出「必须贴在一起」，所以收掉了远处搜索。</p>
     */
    public ClayKilnBlockEntity findKiln() {
        if (level == null) return null;
        for (Direction d : Direction.values()) {
            var be = level.getBlockEntity(worldPosition.relative(d));
            if (be instanceof ClayKilnBlockEntity kiln) return kiln;
        }
        return null;
    }

    /* ---------------- 制作坯体 ---------------- */

    /** 当前有多少黏土。 */
    public int clayCount() {
        return items.get(SLOT_CLAY).getCount();
    }

    /** 这个图样现在做得出来吗（黏土够）。 */
    public boolean canMake(CeramicPatterns.Pattern pattern) {
        return pattern != null && clayCount() >= pattern.clay();
    }

    /**
     * 按图样做一件坯体，塞进旁边窑炉。
     *
     * @return 结果码：0=成功，1=黏土不够，2=找不到窑炉，3=窑炉坯料位满了
     */
    public int make(CeramicPatterns.Pattern pattern) {
        if (pattern == null) return 1;
        if (clayCount() < pattern.clay()) return 1;

        var kiln = findKiln();
        if (kiln == null) return 2;

        if (!kiln.addBlank(pattern.makeStack())) return 3;

        items.get(SLOT_CLAY).shrink(pattern.clay());
        if (items.get(SLOT_CLAY).isEmpty()) items.set(SLOT_CLAY, ItemStack.EMPTY);
        setChanged();
        return 0;
    }

    /* ---------------- Container ---------------- */

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT_CLAY).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SIZE ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack got = ContainerHelper.removeItem(items, slot, count);
        if (!got.isEmpty()) setChanged();
        return got;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) return;
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_CLAY && stack.is(Items.CLAY_BALL);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /* ---------------- 存档 / 同步 ---------------- */

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    /**
     * 同步给客户端的数据 —— <b>必须带上槽里的黏土</b>。
     *
     * <h2>为什么少了这个界面就报「黏土不够」</h2>
     * <p>界面上的「黏土 N」和「能不能做」都是客户端直接读方块实体的
     * {@link #clayCount()}。不写 {@code getUpdateTag} 的话客户端那份
     * {@code items} 永远是空的 —— <b>明明塞了黏土也一直显示 0，图样全部变灰</b>。</p>
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // 1.21.1 的容器序列化直接写进 CompoundTag 即可
        ContainerHelper.saveAllItems(tag, items, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
