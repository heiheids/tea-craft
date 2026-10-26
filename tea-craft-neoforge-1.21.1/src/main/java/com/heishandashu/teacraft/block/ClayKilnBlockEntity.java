package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.CeramicPatterns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 窑炉的方块实体 —— 烧制陶瓷。
 *
 * <h2>槽位（13 格）</h2>
 * <pre>
 *   0        燃料位       煤炭 / 木炭，满 8 个才能点火
 *   1 ~ 6    坯料位 ×6    由拉坯机写入；玩家不能手动放、也不能取出，只能「撤销」退回黏土
 *   7 ~ 12   成品位 ×6    烧好后从坯料位移过来
 * </pre>
 *
 * <h2>烧制规则（需求原文）</h2>
 * <ul>
 *   <li>「一次消耗 8 个，低于 8 个不开始工作，开始后一次性消耗掉 8 个」——
 *       所以是<b>批量点火</b>：点火那一刻直接扣掉 8 个，不是一点点烧。</li>
 *   <li>「输出物品有 6 个栏位，一次可以有 6 种不同东西烧出来」——
 *       坯料位 6 个各自独立计时，同时烧。</li>
 *   <li>一炉 = 1 个游戏日 = 24000 tick。</li>
 *   <li>开始烧制后锁定：不能再放新坯料、不能中途撤销。</li>
 * </ul>
 *
 * <h2>1.21.1 迁移要点</h2>
 * <ul>
 *   <li>序列化是 {@code saveAdditional(CompoundTag, HolderLookup.Provider)}，
 *       不是 26.3 的 {@code ValueOutput}。</li>
 *   <li>{@code ContainerHelper.saveAllItems/loadAllItems} 多一个 registries 参数。</li>
 *   <li>方块实体类型用静态字段回填，调用方写 {@code TYPE}。</li>
 * </ul>
 */
public class ClayKilnBlockEntity extends BlockEntity implements Container {

    /** 由 TeaItems 在注册后回填。 */
    public static BlockEntityType<ClayKilnBlockEntity> TYPE;

    public static final int SLOT_FUEL = 0;
    public static final int BLANK_COUNT = 6;
    public static final int OUTPUT_COUNT = 6;
    public static final int SLOT_BLANK_START = 1;
    public static final int SLOT_OUTPUT_START = SLOT_BLANK_START + BLANK_COUNT;
    public static final int SIZE = 1 + BLANK_COUNT + OUTPUT_COUNT;

    /** 一次点火要吃的燃料数量。 */
    public static final int FUEL_PER_BURN = 8;

    /**
     * 烧一炉要多少 tick —— <b>1 个游戏日</b>。
     *
     * <p>Minecraft 一个游戏日 = 24000 tick（= 20 分钟现实时间）。
     * 进度只随游戏刻推进，所以玩家下线 / 区块卸载时不会烧完，
     * 这一点和原版熔炉一致。</p>
     */
    public static final int BURN_TICKS = 24000;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    /** 每件坯料各自的剩余 tick；0 表示不在烧。 */
    private final int[] burnLeft = new int[BLANK_COUNT];

    /** 有没有正在烧（模型 / 界面据此显示火焰）。 */
    private boolean lit = false;

    /** 同步节流计数器（每 20 tick = 1 秒推一次）。 */
    private int syncCounter = 0;

    /** 刚有东西烧完 / 火烧完了，需要立刻同步。 */
    private boolean justFinished = false;

    public ClayKilnBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    /* ---------------- 燃料 ---------------- */

    /** 煤炭或木炭都能当燃料。 */
    public static boolean isFuel(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    /** 燃料够不够点一次火。 */
    public boolean hasEnoughFuel() {
        return items.get(SLOT_FUEL).getCount() >= FUEL_PER_BURN;
    }

    /* ---------------- 坯料 / 成品 ---------------- */

    /** 坯料位里有没有东西。 */
    public boolean hasBlanks() {
        for (int i = 0; i < BLANK_COUNT; i++) {
            if (!items.get(SLOT_BLANK_START + i).isEmpty()) return true;
        }
        return false;
    }

    /** 读某个坯料位的成品物品（用于图样还原）。 */
    public ItemStack blankAt(int index) {
        if (index < 0 || index >= BLANK_COUNT) return ItemStack.EMPTY;
        return items.get(SLOT_BLANK_START + index);
    }

    /**
     * 拉坯机往里写一件坯料。成功返回 true。
     *
     * <p>找第一个空坯料位放进去；6 个都满了就失败（需求没要求排队）。</p>
     */
    public boolean addBlank(ItemStack blank) {
        if (blank.isEmpty()) return false;
        // 正在烧的时候锁定，不能再塞新的坯料。
        //
        // 这条锁放在 addBlank 里而不是 setItem 里：拉坯机是通过
        // addBlank 送坯料的，锁这里正好挡住「烧制中又下单」。
        if (lit) return false;
        for (int i = 0; i < BLANK_COUNT; i++) {
            int slot = SLOT_BLANK_START + i;
            if (items.get(slot).isEmpty()) {
                items.set(slot, blank.copy());
                setChanged();
                return true;
            }
        }
        return false;
    }

    /**
     * 撤销某个坯料位，返回应该退给玩家的物品（黏土）。
     *
     * <p>需求：「这里只能点击撤销本次制作，返还黏土到玩家手里」。
     * 所以退的是<b>黏土</b>，不是那件坯体。</p>
     */
    public ItemStack cancelBlank(int index) {
        if (index < 0 || index >= BLANK_COUNT) return ItemStack.EMPTY;
        // 正在烧的时候锁定：烧到一半抽走坯料会让进度和成品对不上
        if (lit) return ItemStack.EMPTY;
        int slot = SLOT_BLANK_START + index;
        ItemStack blank = items.get(slot);
        if (blank.isEmpty()) return ItemStack.EMPTY;

        var pattern = CeramicPatterns.forItem(blank.getItem());
        int clay = pattern == null ? 1 : pattern.clay();
        items.set(slot, ItemStack.EMPTY);
        burnLeft[index] = 0;
        setChanged();
        refreshLit();
        return new ItemStack(Items.CLAY_BALL, clay);
    }

    /* ---------------- 烧制 ---------------- */

    /**
     * 尝试点火：燃料够、有坯料、且当前没在烧。
     *
     * <p>「开始后一次性消耗掉 8 个」—— 这里直接扣，不等烧完。</p>
     */
    public boolean tryStartBurning() {
        if (!hasEnoughFuel()) return false;
        // 已经有在烧的就不重复点火
        for (int i = 0; i < BLANK_COUNT; i++) {
            if (burnLeft[i] > 0) return false;
        }
        boolean any = false;
        for (int i = 0; i < BLANK_COUNT; i++) {
            if (!items.get(SLOT_BLANK_START + i).isEmpty()) {
                burnLeft[i] = BURN_TICKS;
                any = true;
            }
        }
        if (!any) return false;

        // 一次性扣 8 个
        ItemStack fuel = items.get(SLOT_FUEL);
        fuel.shrink(FUEL_PER_BURN);
        if (fuel.isEmpty()) items.set(SLOT_FUEL, ItemStack.EMPTY);

        lit = true;
        setChanged();
        // 必须主动推给客户端，否则界面上的燃烧状态不会变。
        syncToClient();
        return true;
    }

    /** 把当前状态推给客户端（lit / 进度 / 槽位物品都靠它）。 */
    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            var state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    /**
     * 客户端本地倒计时。
     *
     * <p>只递减 burnLeft，不产出物品、不改世界状态。
     * 服务端推来新值时会覆盖这里的结果（以服务端为准）。</p>
     *
     * <p>为什么需要它：客户端那份方块实体没有任何代码去推进 burnLeft，
     * 而服务端的持续同步在「界面开着」这个场景下并不可靠，
     * 结果就是倒计时停在起始值、或者因为值恒为 0 而根本显示不出来。</p>
     */
    public void tickBurningClient() {
        if (level == null || !level.isClientSide()) return;
        for (int i = 0; i < BLANK_COUNT; i++) {
            if (burnLeft[i] > 0) burnLeft[i]--;
        }
    }

    /** 每 tick 调一次（服务端）。 */
    public void tickBurning() {
        if (level == null || level.isClientSide()) return;

        boolean changed = false;
        boolean stillBurning = false;
        for (int i = 0; i < BLANK_COUNT; i++) {
            if (burnLeft[i] <= 0) continue;
            burnLeft[i]--;
            changed = true;
            if (burnLeft[i] <= 0) {
                // 这一件烧好了：坯料 → 成品
                burnLeft[i] = 0;
                moveToOutput(i);
                justFinished = true;
            } else {
                stillBurning = true;
            }
        }
        if (!stillBurning && lit) {
            lit = false;
            justFinished = true;
        }

        if (changed) {
            setChanged();
            // 不要每 tick 都发同步包 —— 一炉 24000 次纯属浪费带宽。
            // 改成每秒一次，界面上看不出差别（客户端自己也在本地递减）。
            if (++syncCounter >= 20) {
                syncCounter = 0;
                syncToClient();
            }
            // 烧完 / 状态切换要立刻同步，否则界面还停在「正在烧制」
            if (justFinished) {
                justFinished = false;
                syncCounter = 0;
                syncToClient();
            }
        }
    }

    /**
     * 把第 i 个坯料位的产物挪到成品位。
     *
     * <p><b>不合并同种物品</b>：每一件烧出来单独占一格。
     * 需求是「输出物品有 6 个栏位，一次可以有 6 种不同东西烧出来」——
     * 如果自动合并，烧 3 个茶杯就只占 1 格，另外 5 格的意义就没了。</p>
     */
    private void moveToOutput(int i) {
        int blankSlot = SLOT_BLANK_START + i;
        ItemStack blank = items.get(blankSlot);
        if (blank.isEmpty()) return;

        // 只找空位，不找同种堆
        int target = -1;
        for (int k = 0; k < OUTPUT_COUNT; k++) {
            int slot = SLOT_OUTPUT_START + k;
            if (items.get(slot).isEmpty()) {
                target = slot;
                break;
            }
        }

        if (target < 0) {
            // 成品位满了：坯料留在原地（进度已重置），等玩家取走成品再来
            return;
        }
        items.set(blankSlot, ItemStack.EMPTY);
        items.set(target, blank.copy());
    }

    /** 有没有在烧。 */
    public boolean isLit() {
        return lit;
    }

    /** 当前烧制进度 0~1（取最靠前的那个坯料）。 */
    public float burnProgress() {
        int best = remainingTicks();
        if (best <= 0) return 0f;
        return 1f - (float) best / BURN_TICKS;
    }

    /** 还要烧多少分钟（向上取整）。 */
    public int minutesLeft() {
        int best = remainingTicks();
        if (best <= 0) return 0;
        // 20 tick = 1 秒，60 秒 = 1 分 → 1200 tick = 1 分
        return (best + 1199) / 1200;
    }

    /** 烧得最久的那件还剩多少 tick（0 = 没在烧）。 */
    public int remainingTicks() {
        int best = 0;
        for (int i = 0; i < BLANK_COUNT; i++) {
            int left = burnLeft[i];
            if (left > 0 && left > best) best = left;
        }
        return best;
    }

    /**
     * 剩余时间，格式化成 {@code 12:34}（游戏时:分）。
     *
     * <p>一炉 = 1 个游戏日 = 24000 tick。按 24000 tick 折成 24 小时制，
     * 一小时 = 1000 tick、一分钟 ≈ 16.7 tick，这样数字<b>每 17 tick 就动一次</b>，
     * 不会像「N 分钟」那样盯着 60 秒都不变，让人以为卡住了。</p>
     */
    public String remainingClock() {
        int left = remainingTicks();
        if (left <= 0) return "00:00";
        int totalMinutes = (int) Math.ceil(left / (BURN_TICKS / 1440.0));
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        return String.format("%02d:%02d", h, m);
    }

    private void refreshLit() {
        boolean any = false;
        for (int i = 0; i < BLANK_COUNT; i++) {
            if (burnLeft[i] > 0) {
                any = true;
                break;
            }
        }
        lit = any;
    }

    /* ---------------- Container ---------------- */

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) return false;
        }
        return true;
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

        // 坯料位被清空时，要把这一件的燃烧进度一并归零。
        //
        // 否则会出现「槽是空的但还在烧」的残留状态：burnLeft 仍是正数、
        // lit 仍是 true，于是 addBlank 被锁定逻辑挡住，窑炉彻底卡死。
        if (slot >= SLOT_BLANK_START && slot < SLOT_OUTPUT_START && stack.isEmpty()) {
            int index = slot - SLOT_BLANK_START;
            if (burnLeft[index] > 0) {
                burnLeft[index] = 0;
                refreshLit();
            }
        }

        items.set(slot, stack);
        setChanged();

        // 燃料位刚被填满 → 立刻尝试点火。
        //
        // 玩家往燃料槽里放燃料有两条路：
        //   ① 手持燃料右键方块 → ClayKilnBlock 里已经调了 tryStartBurning；
        //   ② 在界面里手动拖进燃料槽 → 走的是 setItem，必须在这里兜底，
        //      否则「燃料放上去了但一直不烧」。
        if (slot == SLOT_FUEL && hasEnoughFuel()) {
            tryStartBurning();
        }
    }

    /**
     * 只有燃料位接受玩家手动放入。
     *
     * <p>需求：「左侧坯料窗口…不能在这里手动放入任何东西」。</p>
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_FUEL && isFuel(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        java.util.Arrays.fill(burnLeft, 0);
        lit = false;
        setChanged();
    }

    /* ---------------- 存档 / 同步 ---------------- */

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putBoolean("lit", lit);
        for (int i = 0; i < BLANK_COUNT; i++) {
            if (burnLeft[i] > 0) tag.putInt("burn" + i, burnLeft[i]);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        lit = tag.getBoolean("lit");
        for (int i = 0; i < BLANK_COUNT; i++) {
            burnLeft[i] = Math.max(0, tag.getInt("burn" + i));
        }
    }

    /**
     * 同步给客户端的数据。
     *
     * <p>界面要显示坯料 / 成品 / 燃料三区的物品，还要画燃烧进度，
     * 所以<b>完整槽位与剩余 tick 都要带上</b>。</p>
     *
     * <p><b>必须包含槽位内容</b>：服务端烧制时每秒调一次
     * {@code sendBlockUpdated}，客户端收到后会用这个 tag 走
     * {@link #loadAdditional} 覆盖自身状态。如果 tag 里没有物品，
     * {@code items.clear()} 之后就是空的 —— 表现正是「开始燃烧后内容物
     * 消失了，重开界面能看到，几秒后又消失」。</p>
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("lit", lit);
        for (int k = 0; k < BLANK_COUNT; k++) {
            tag.putInt("burn" + k, burnLeft[k]);
        }
        tag.putInt("burnTicks", BURN_TICKS);
        // 槽位内容（和 saveAdditional 用同一套 key，loadAdditional 才能读回来）
        ContainerHelper.saveAllItems(tag, items, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 界面顶部的状态提示。 */
    public Component statusText() {
        if (lit) return Component.translatable("teacraft.kiln.burning");
        if (!hasBlanks()) return Component.translatable("teacraft.kiln.no_blank");
        if (!hasEnoughFuel()) {
            return Component.translatable("teacraft.kiln.need_fuel", FUEL_PER_BURN);
        }
        return Component.translatable("teacraft.kiln.ready");
    }

    /**
     * 测试专用：把燃烧进度直接推到最后 1 tick 并跑完。
     *
     * <p>一炉是 24000 tick，自检里不能真的循环两万多次。</p>
     */
    public void finishBurningForTest() {
        for (int i = 0; i < BLANK_COUNT; i++) {
            if (burnLeft[i] > 0) burnLeft[i] = 1;
        }
        tickBurning();
    }
}
