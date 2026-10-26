package com.heishandashu.teacraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 茶树方块实体：记录「成熟进度」。
 *
 * <h2>为什么茶树需要 BlockEntity</h2>
 * <p>原先成熟完全靠计划刻（{@code level.scheduleTick}），到期直接 {@code ripen()}。
 * 但计划刻<b>没法反查</b>「这个坐标还有多少 tick 到期」。需求要显示倒计时，
 * 所以必须有个地方存进度。</p>
 *
 * <h2>为什么用时间戳而不是「每 tick 减 1」</h2>
 * <p>第一版写成「每 20 tick 调一次，每次 {@code remainingTicks--}」。
 * 这是个<b>速率错误</b>：</p>
 * <pre>
 *   实际经过 144000 tick（6 游戏日）
 *   但代码只被调用 144000 / 20 = 7200 次
 *   每次减 1 ⇒ 只推进了 7200 tick
 *   而 3 天成熟需要 72000 tick ⇒ 只走了 10%
 * </pre>
 * <p>表现就是：{@code /tick sprint 6D} 之后茶树依然不成熟。速率整整慢了 20 倍。</p>
 *
 * <p>正确做法是<b>记录已累积的游戏刻</b>，用 {@code gameTime} 的差值推进。
 * 这样无论每秒 tick 多少、是否 sprint，进度都严格等于「经过的游戏刻」，
 * 与游戏刻完全同步（也就能被 {@code /tick sprint}、{@code /tick rate} 加速）。</p>
 *
 * <h2>暂停 / 恢复的语义</h2>
 * <p>太冷时把当前 {@code gameTime} 记进 {@link #anchor} 前先结算，
 * 收住已累积的量；取暖后重新取锚点，从那一刻继续累加 ——
 * 暂停期间游戏刻照走，但不计入进度。</p>
 */
public class TeaBushBlockEntity extends BlockEntity {

    /** 由 TeaItems 在注册后回填（与 TeaTrayBlockEntity.TYPE 同一约定）。 */
    public static net.minecraft.world.level.block.entity.BlockEntityType<TeaBushBlockEntity> TYPE;

    /** 这一轮成熟总共需要多少游戏刻。 */
    private int totalTicks;

    /** 已经累积了多少游戏刻（不含当前这一段）。 */
    private int elapsedTicks;

    /** 上一段的起点（gameTime）；-1 表示不在计时。 */
    private long anchor = -1L;

    /** 是否因太冷而暂停。 */
    private boolean paused;

    public TeaBushBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    /* ---------------- 读写状态 ---------------- */

    public int totalTicks() {
        return totalTicks;
    }

    public boolean isPaused() {
        return paused;
    }

    /** 有没有在计时（有总时长，且还没成熟）。 */
    public boolean isGrowing() {
        return totalTicks > 0 && remainingTicks(0L) > 0;
    }

    /**
     * 还差多少游戏刻成熟。
     *
     * @param now 当前 gameTime；传 0 表示「不算正在走的这一段」
     */
    public int remainingTicks(long now) {
        int done = elapsedTicks;
        if (anchor >= 0L && now > 0L) {
            done += (int) Math.max(0L, now - anchor);
        }
        return Math.max(0, totalTicks - done);
    }

    /** HUD 用：不带当前段的剩余量。 */
    public int remainingTicks() {
        return remainingTicks(0L);
    }

    /** 进度 0~1；没开始计时返回 -1。 */
    public float progress(long now) {
        if (totalTicks <= 0) return -1f;
        int remain = remainingTicks(now);
        if (remain <= 0) return 1f;
        float p = 1f - (remain / (float) totalTicks);
        return p < 0f ? 0f : (p > 1f ? 1f : p);
    }

    public float progress() {
        return progress(0L);
    }

    /** 设置一轮新的计时（刚种下、或采摘后重新起算）。 */
    public void startGrowing(int totalTicks, long now) {
        this.totalTicks = Math.max(1, totalTicks);
        this.elapsedTicks = 0;
        this.anchor = now;
        this.paused = false;
        setChanged();
        syncToClient();
    }

    /**
     * 按当前 gameTime 结算一次：推进进度、处理暂停/恢复。
     *
     * @param coldNow 当前位置是否「太冷」（需要篝火但没有）
     * @param now     当前 gameTime
     * @return true 表示到这一刻已经成熟
     */
    public boolean advanceTo(long now, boolean coldNow) {
        if (totalTicks <= 0) return false;

        // 已经成熟：只需要维护暂停标记
        if (remainingTicks(now) <= 0) {
            if (paused != coldNow) {
                paused = coldNow;
                setChanged();
                syncToClient();
            }
            return true;
        }

        if (coldNow) {
            // 暂停：把本段已走的量收进 elapsedTicks，然后停表
            if (anchor >= 0L) {
                elapsedTicks += (int) Math.max(0L, now - anchor);
                anchor = -1L;
                paused = true;
                setChanged();
                syncToClient();
            } else if (!paused) {
                paused = true;
                setChanged();
                syncToClient();
            }
            return false;
        }

        // 不冷：如果之前在暂停，从此刻重新起表
        if (anchor < 0L) {
            anchor = now;
            paused = false;
            setChanged();
            syncToClient();
        }

        // 进度有变化就同步（每秒一次足够 HUD 用）
        if (now % 20L == 0L) {
            setChanged();
            syncToClient();
        }
        return remainingTicks(now) <= 0;
    }

    public void syncToClient() {
        Level level = this.level;
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, 3);
        }
    }

    /**
     * 仅供自检：把计时锚点往前拨，模拟「已经过了 ticks 刻」。
     *
     * <p>自检里世界时间不会自己走，而 {@code tick()} 内部读的是
     * {@code level.getGameTime()}。为了验证「真实 tick 路径能成熟」，
     * 只能把 anchor 往前拨，等价于时间已经流逝。</p>
     *
     * <p>生产代码不要调这个。</p>
     */
    public void debugWindBack(long ticks) {
        if (anchor >= 0L) {
            anchor -= Math.max(0L, ticks);
        }
        setChanged();
    }

    /* ---------------- 存盘 / 同步 ---------------- */

    @Override
    protected void saveAdditional(CompoundTag out, HolderLookup.Provider registries) {
        super.saveAdditional(out, registries);
        out.putInt("total", totalTicks);
        out.putInt("elapsed", elapsedTicks);
        out.putLong("anchor", anchor);
        out.putBoolean("paused", paused);
    }

    @Override
    protected void loadAdditional(CompoundTag in, HolderLookup.Provider registries) {
        super.loadAdditional(in, registries);
        this.totalTicks = in.getInt("total");
        this.elapsedTicks = in.getInt("elapsed");
        this.anchor = in.getLong("anchor");
        this.paused = in.getBoolean("paused");
    }

    /** 区块首次发给客户端时带上。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("total", totalTicks);
        tag.putInt("elapsed", elapsedTicks);
        tag.putLong("anchor", anchor);
        tag.putBoolean("paused", paused);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
