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
 * 茶树方块实体：只负责记录「成熟还剩多少 tick」。
 *
 * <h2>为什么茶树需要 BlockEntity</h2>
 * <p>原先成熟完全靠计划刻（{@code level.scheduleTick}），到期直接 {@code ripen()}。
 * 但计划刻<b>没法反查</b>「这个坐标还有多少 tick 到期」—— 原版队列存在
 * {@code LevelChunkTicks} 里，没有对外查询接口，也没写进 BlockState。</p>
 *
 * <p>需求要「显示成熟倒计时」并且「太冷暂停、取暖恢复」，就必须有一个
 * 可读可写的<b>剩余时间</b>，所以改成由方块实体持有状态：</p>
 * <ul>
 *   <li>{@link #remainingTicks} —— 还差多少 tick 成熟；</li>
 *   <li>{@link #totalTicks} —— 这一轮总共需要多少 tick（算百分比用）；</li>
 *   <li>{@link #paused} —— 当前是否因为太冷而暂停。</li>
 * </ul>
 *
 * <h2>暂停 / 恢复的语义</h2>
 * <p>太冷时 {@code remainingTicks} <b>不再减少</b>，而不是清零。
 * 重新放上篝火后从原值继续倒数 —— 这就是「取暖后恢复生长」。</p>
 *
 * <h2>同步</h2>
 * <p>照搬 {@link TeaTrayBlockEntity} 的做法：三个值都进 {@link #getUpdateTag}
 * 和 {@link #saveAdditional}，客户端 {@code level.getBlockEntity(pos)} 就能读到，
 * HUD 直接用即可（服务端不需要每 tick 发包）。</p>
 */
public class TeaBushBlockEntity extends BlockEntity {

    /** 由 TeaItems 在注册后回填（与 TeaTrayBlockEntity.TYPE 同一约定）。 */
    public static net.minecraft.world.level.block.entity.BlockEntityType<TeaBushBlockEntity> TYPE;

    /** 还差多少 tick 成熟。0 表示已经成熟（或还没开始计时）。 */
    private int remainingTicks;

    /** 这一轮成熟总共需要多少 tick。用于算进度百分比。 */
    private int totalTicks;

    /** 是否因太冷而暂停。 */
    private boolean paused;

    public TeaBushBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    /* ---------------- 读写状态 ---------------- */

    public int remainingTicks() {
        return remainingTicks;
    }

    public int totalTicks() {
        return totalTicks;
    }

    public boolean isPaused() {
        return paused;
    }

    /** 有没有在计时（totalTicks > 0 且还没成熟）。 */
    public boolean isGrowing() {
        return totalTicks > 0 && remainingTicks > 0;
    }

    /** 设置一轮新的成熟计时（刚种下、或采摘后重新起算）。 */
    public void startGrowing(int totalTicks) {
        this.totalTicks = Math.max(1, totalTicks);
        this.remainingTicks = this.totalTicks;
        this.paused = false;
        setChanged();
        syncToClient();
    }

    /**
     * 每 tick 推进一次倒计时。
     *
     * @param coldNow 当前位置是否「太冷」（需要篝火但没有）
     * @return true 表示这一 tick 刚好到点成熟
     */
    public boolean tickCountdown(boolean coldNow) {
        // 已成熟：不再倒数
        if (remainingTicks <= 0) {
            if (paused != coldNow) {
                paused = coldNow;
                setChanged();
                syncToClient();
            }
            return false;
        }

        // 太冷 → 暂停，remainingTicks 保持不变（这是「取暖后恢复」的关键）
        if (coldNow) {
            if (!paused) {
                paused = true;
                setChanged();
                syncToClient();
            }
            return false;
        }

        // 恢复取暖 → 继续倒数
        if (paused) {
            paused = false;
            setChanged();
            syncToClient();
        }

        remainingTicks--;
        // 每秒同步一次就够 HUD 用，不必每 tick 发包
        if (remainingTicks % 20 == 0 || remainingTicks <= 0) {
            setChanged();
            syncToClient();
        }
        return remainingTicks <= 0;
    }

    /** 进度 0~1；没开始计时返回 -1。 */
    public float progress() {
        if (totalTicks <= 0) return -1f;
        if (remainingTicks <= 0) return 1f;
        float p = 1f - (remainingTicks / (float) totalTicks);
        return p < 0f ? 0f : (p > 1f ? 1f : p);
    }

    public void syncToClient() {
        Level level = this.level;
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, 3);
        }
    }

    /* ---------------- 存盘 / 同步 ---------------- */

    @Override
    protected void saveAdditional(CompoundTag out, HolderLookup.Provider registries) {
        super.saveAdditional(out, registries);
        out.putInt("remaining", remainingTicks);
        out.putInt("total", totalTicks);
        out.putBoolean("paused", paused);
    }

    @Override
    protected void loadAdditional(CompoundTag in, HolderLookup.Provider registries) {
        super.loadAdditional(in, registries);
        this.remainingTicks = in.getInt("remaining");
        this.totalTicks = in.getInt("total");
        this.paused = in.getBoolean("paused");
    }

    /** 区块首次发给客户端时带上。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("remaining", remainingTicks);
        tag.putInt("total", totalTicks);
        tag.putBoolean("paused", paused);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
