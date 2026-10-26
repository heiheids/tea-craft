package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.TeaOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 晒盘的附加数据：<b>盘里装的是哪个产地的茶</b>，以及<b>什么时候开始萎凋</b>。
 *
 * <h2>为什么用方块实体而不是方块状态</h2>
 * <p>「产地」有 5 个取值、「萎凋进度」是连续时间，如果都塞进方块状态，
 * 变体会膨胀到上千个（5 产地 × 4 状态 × 5 数量 × N 进度）。方块状态只保留
 * 「阶段 / 数量」这种决定模型的东西，其余放这里。</p>
 *
 * <h2>为什么这些字段要同步到客户端</h2>
 * <p>鼠标指着晒盘时要显示「内容物名 / 进度条」，那是客户端画的，
 * 所以 {@link #getUpdateTag} 与 {@link #getUpdatePacket} 必须把这三个值带上。</p>
 */
public class TeaTrayBlockEntity extends BlockEntity {

    /** 盘里茶叶的产地；空盘时为 {@link TeaOrigin#TEMPERATE}（不显示）。 */
    private TeaOrigin origin = TeaOrigin.TEMPERATE;

    /** 开始萎凋的游戏刻；-1 表示没在萎凋。 */
    private long witherStart = -1L;

    /** 揉茶匾：已经揉了几次（0~3）。晒盘不用这个字段。 */
    private int rolls = 0;

    /** 正在进行的这一次揉捻的起点（游戏刻）；-1 表示没在揉。 */
    private long rollStart = -1L;

    /**
     * 匾里这份茶属于哪一类（{@code dancong} / {@code green} / …）。
     *
     * <p>揉茶匾现在同时服务单丛和绿茶，产出必须按类别区分，
     * 不能再写死成单丛。放入时由物品 id 前缀决定。</p>
     */
    private String kind = "dancong";

    /** 方块实体类型；由 {@code TeaItems} 在方块注册后填进来（方块与类型互相依赖）。 */
    public static net.minecraft.world.level.block.entity.BlockEntityType<TeaTrayBlockEntity> TYPE;

    public TeaTrayBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public TeaOrigin origin() { return origin; }

    public void setOrigin(TeaOrigin origin) {
        this.origin = origin;
        setChanged();
    }

    public long witherStart() { return witherStart; }

    /** 放入新茶时调用：从这一刻开始算 5 分钟。 */
    public void beginWithering(long gameTime) {
        this.witherStart = gameTime;
        setChanged();
    }

    public int rolls() { return rolls; }

    public String kind() { return kind; }

    public void setKind(String kind) { this.kind = kind == null ? "" : kind; setChanged(); }

    /** 开始揉一次：记下起点，方块自己按 tick 推进（和摇青一样，一次 3 秒）。 */
    public void startRoll(long now) {
        this.rollStart = now;
        setChanged();
    }

    public long rollStart() { return rollStart; }

    /** 这一次揉捻的进度 0~1；没在揉返回 -1。 */
    public float rollProgress(long now, int totalTicks) {
        if (rollStart < 0 || totalTicks <= 0) return -1f;
        long e = now - rollStart;
        if (e < 0) return 0f;
        return Math.min(1f, e / (float) totalTicks);
    }

    /**
     * 结束一次揉捻计时。
     *
     * <p><b>注意：这里绝对不能碰 {@code kind}。</b>
     * 之前这里写了 {@code this.kind = "dancong"}，导致绿茶揉完就被改判成单丛，
     * 取出时变成单丛揉捻叶 —— 这正是"绿茶揉完变单丛"的根因。
     * 茶类只在放入时由物品决定，揉捻/发酵都不该改它。</p>
     */
    public void finishRoll() {
        this.rollStart = -1L;
        setChanged();
    }

    public void setRolls(int rolls) {
        this.rolls = rolls;
        setChanged();
    }

    /** 揉一次，返回揉了之后累计的次数。 */
    public int addRoll() {
        this.rolls++;
        setChanged();
        return this.rolls;
    }

    public void clearAll() {
        this.origin = TeaOrigin.TEMPERATE;
        this.witherStart = -1L;
        this.rolls = 0;
        this.rollStart = -1L;
        this.kind = "dancong";
        setChanged();
    }

    /**
     * 当前阶段的进度（0~1）；没有记录起点时返回 -1。
     *
     * <p>萎凋和杀青都是「放够 N tick」，所以共用同一个起始时间字段，
     * 只是总时长不同（{@code WITHER_TICKS} / {@code KILLGREEN_TICKS}）。</p>
     */
    public float stageProgress(long now, int totalTicks) {
        if (witherStart < 0 || totalTicks <= 0) return -1f;
        long elapsed = now - witherStart;
        if (elapsed < 0) return 0f;
        return Math.min(1f, elapsed / (float) totalTicks);
    }

    /** 0~1 的萎凋进度；不在萎凋中返回 -1。 */
    public float witherProgress(long now) {
        if (witherStart < 0) return -1f;
        long elapsed = now - witherStart;
        float p = elapsed / (float) TeaTrayBlock.WITHER_TICKS;
        return p < 0f ? 0f : (p > 1f ? 1f : p);
    }

    /* ---------------- 存盘 / 同步 ---------------- */

    @Override
    protected void saveAdditional(CompoundTag out, HolderLookup.Provider registries) {
        super.saveAdditional(out, registries);
        out.putString("origin", origin.id());
        out.putLong("wither_start", witherStart);
        out.putInt("rolls", rolls);
        out.putLong("roll_start", rollStart);
        out.putString("kind", kind);
    }

    @Override
    protected void loadAdditional(CompoundTag in, HolderLookup.Provider registries) {
        super.loadAdditional(in, registries);
        this.origin = TeaOrigin.byId(in.getString("origin"));
        this.witherStart = in.getLong("wither_start");
        this.rolls = in.getInt("rolls");
        this.rollStart = in.getLong("roll_start");
        this.kind = in.getString("kind");
    }

    /** 区块首次发给客户端时带上的数据。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("origin", origin.id());
        tag.putLong("wither_start", witherStart);
        tag.putInt("rolls", rolls);
        tag.putLong("roll_start", rollStart);
        tag.putString("kind", kind);
        return tag;
    }

    /** 数据变了主动推给客户端（放入茶叶、开始萎凋时调）。 */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
