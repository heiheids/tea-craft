package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.CeramicPatterns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 窑炉的方块实体 —— 烧制陶瓷。
 *
 * <h2>槽位（12 格）</h2>
 * <pre>
 *   0        燃料位       煤炭 / 木炭，满 8 个才能点火
 *   1 ~ 6    坯料位 ×6    由拉坯机写入；玩家不能手动放，只能「撤销」退回黏土
 *   7 ~ 12   成品位 ×6    烧好后从坯料位移过来
 * </pre>
 *
 * <h2>烧制规则（需求原文）</h2>
 * <ul>
 *   <li>「一次消耗 8 个，低于 8 个不开始工作，开始后一次性消耗掉 8 个」——
 *       所以是<b>批量点火</b>：点火那一刻直接扣掉 8 个，不是一点点烧。</li>
 *   <li>「输出物品有 6 个栏位，一次可以有 6 种不同东西烧出来」——
 *       坯料位 6 个各自独立计时，同时烧。</li>
 *   <li>烧一炉 200 tick（10 秒），够短，不让人干等。</li>
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
        // 注意这条锁放在 addBlank 里而不是 setItem 里：拉坯机是通过
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
        // 必须主动推给客户端，否则界面上的「正在烧制 / 还要 N 分钟」
        // 和火焰进度条都不会变 —— 玩家根本不知道有没有开始烧。
        syncToClient();
        return true;
    }

    /** 把当前状态推给客户端（lit / 进度 / 槽位物品都靠它）。 */
    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            var state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state,
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    /**
     * 客户端本地倒计时。
     *
     * <p>只递减 burnLeft，不产出物品、不改世界状态。
     * 服务端推来新值时会覆盖这里的结果（以服务端为准）。</p>
     *
     * <p>为什么需要它：客户端那份方块实体没有任何代码去推进 burnLeft，
     * 而服务端的持续同步实测拿不到，结果就是倒计时停在起始值、
     * 或者因为值恒为 0 而根本显示不出来。</p>
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
            // 不要每 tick 都发同步包 —— 一炉 20 分钟就是 24000 次，
            // 纯属浪费带宽。改成每秒一次，界面上看不出差别。
            if (++syncCounter >= 20) {
                syncCounter = 0;
                syncToClient();
            }
            // 烧完 / 状态切换要立刻同步，否则界面还停在"正在烧制"
            if (justFinished) {
                justFinished = false;
                syncCounter = 0;
                syncToClient();
            }
        }
    }

    /** 同步节流计数器（每 20 tick = 1 秒推一次）。 */
    private int syncCounter = 0;

    /** 刚有东西烧完 / 火烧完了，需要立刻同步。 */
    private boolean justFinished = false;

    /**
     * 把第 i 个坯料位的产物挪到成品位。
     *
     * <p><b>不合并同种物品</b>：每一件烧出来单独占一格。
     * 需求是「输出物品有 6 个栏位，一次可以有 6 种不同东西烧出来」——
     * 如果自动合并，烧 3 个茶杯就只占 1 格，另外 5 格的意义就没了，
     * 而且玩家看不出这炉到底出了几件。原版熔炉的成品槽也是不合并的。</p>
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
        int best = 0;
        for (int i = 0; i < BLANK_COUNT; i++) {
            int left = burnLeft[i];
            if (left > 0 && left > best) best = left;
        }
        if (best <= 0) return 0f;
        return 1f - (float) best / BURN_TICKS;
    }

    /**
     * 还要烧多少分钟（向上取整）。
     *
     * <p>20 分钟一炉，界面上必须看得到剩余时间 —— 进度条只有一根细箭头，
     * 说不了"还要等 17 分钟"。</p>
     */
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
        // 24000 tick 对应 24:00 → 每 tick 折算成的"游戏分钟"
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
        // 否则会出现"槽是空的但还在烧"的残留状态：burnLeft 仍是正数、
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
        //   ② 在界面里手动拖进燃料槽 → 走的是 setItem，之前没有触发，
        //      于是「燃料放上去了但一直不烧」。
        // 两条路都要能自动点火，所以放在这里兜底。
        if (slot == SLOT_FUEL && hasEnoughFuel()) {
            tryStartBurning();
        }
    }

    /**
     * 只有燃料位接受玩家手动放入。
     *
     * <p>需求：「左侧坯料窗口…不能在这里手动放入任何东西」。
     * 玩家能塞东西的路径只有手动点击和 shift 点击，都走这里，挡掉就够了。</p>
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
    }

    /* ---------------- 存档 / 同步 ---------------- */

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putBoolean("lit", lit);
        for (int i = 0; i < BLANK_COUNT; i++) {
            if (burnLeft[i] > 0) out.putInt("burn" + i, burnLeft[i]);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items.clear();
        // 先按容器格式读（存档路径）
        ContainerHelper.loadAllItems(in, items);
        // 再按同步格式读（getUpdateTag 走的是 slot_N + ItemStack.CODEC）
        for (int i = 0; i < SIZE; i++) {
            if (items.get(i).isEmpty()) {
                var opt = in.read("slot_" + i, net.minecraft.world.item.ItemStack.CODEC);
                if (opt.isPresent()) items.set(i, opt.get());
            }
        }
        lit = in.getBooleanOr("lit", false);
        for (int i = 0; i < BLANK_COUNT; i++) {
            burnLeft[i] = Math.max(0, in.getIntOr("burn" + i, 0));
        }
    }

    /**
     * 同步给客户端的数据。
     *
     * <p>界面要显示坯料 / 成品 / 燃料三区的物品，还要画燃烧进度，
     * 所以<b>槽位物品必须完整同步</b>（不能只发 id+数量，否则客户端画不出图标）。</p>
     */
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(
            net.minecraft.core.HolderLookup.Provider registries) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("lit", lit);
        // 逐件发剩余 tick —— 客户端要按它算进度和倒计时。
        // 之前发的是汇总 burnMax，而 loadAdditional 读 "burn"+i，
        // 键名对不上 → 客户端永远读到 0 → 倒计时和进度条不动。
        for (int k = 0; k < BLANK_COUNT; k++) {
            tag.putInt("burn" + k, burnLeft[k]);
        }
        tag.putInt("burnTicks", BURN_TICKS);
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, registries);
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty()) {
                output.store("slot_" + i, net.minecraft.world.item.ItemStack.CODEC, s);
            }
        }
        tag.merge(output.buildResult());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
            getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
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
     * <p>一炉现在是 20 分钟（24000 tick），自检里不能真的循环两万多次。</p>
     */
    public void finishBurningForTest() {
        for (int i = 0; i < BLANK_COUNT; i++) {
            if (burnLeft[i] > 0) burnLeft[i] = 1;
        }
        tickBurning();
    }
}
