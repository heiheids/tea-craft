package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 烧水壶的方块实体：记「有没有水」「烧了多久」「水开没开」。
 *
 * <h2>为什么用方块实体而不是方块状态</h2>
 * <p>加热进度是连续数值（每 tick 变），塞进方块状态会让状态数爆炸、还会频繁
 * 触发区块重发。所以只把「有没有壶」放方块状态，其余都放这里。</p>
 */
public class TeaKettleBlockEntity extends BlockEntity {

    public static net.minecraft.world.level.block.entity.BlockEntityType<TeaKettleBlockEntity> TYPE;

    /** 装满一壶需要加热多少 tick（20 tick = 1 秒 → 100 tick = 5 秒）。 */
    public static final int BOIL_TICKS = 100;

    /** 壶里有没有水。 */
    private boolean hasWater = false;

    /** 已加热的 tick 数（0~BOIL_TICKS）。 */
    private int heatTicks = 0;

    /** 水是否已经烧开。 */
    private boolean boiled = false;

    public TeaKettleBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public boolean hasWater() { return hasWater; }

    public boolean boiled() { return boiled; }

    public int heatTicks() { return heatTicks; }

    /** 装入水：需要壶在火上才会开始加热。 */
    public void fillWater() {
        this.hasWater = true;
        this.heatTicks = 0;
        this.boiled = false;
        setChanged();
    }

    /** 倒出热水：壶变空。 */
    public void empty() {
        this.hasWater = false;
        this.heatTicks = 0;
        this.boiled = false;
        setChanged();
    }

    /**
     * 每 tick 由方块调用。
     *
     * @param heated 下方是否是有效热源
     * @return 这一刻是否<b>刚刚</b>烧开（用来播一次声音/粒子）
     */
    /**
     * 加热一 tick。
     *
     * @param heated 下方是否是有效热源
     * @return 是否需要<b>同步给客户端</b>（进度变了或刚烧开）
     *
     * <h2>为什么返回值语义从"刚烧开"改成"需要同步"</h2>
     * <p>以前只有烧开那一刻返回 true，导致客户端在整个加热过程中
     * 拿不到任何 `heat_ticks` 更新 —— HUD 永远显示"正在烧水 0%"
     * （用户截图里就是这个）。</p>
     *
     * <p>现在每隔 {@link #SYNC_EVERY} tick 以及烧开时都要求同步。</p>
     */
    public boolean tickHeat(boolean heated) {
        if (!hasWater || boiled) return false;
        if (!heated) return false;
        heatTicks++;
        if (heatTicks >= BOIL_TICKS) {
            boiled = true;
            heatTicks = BOIL_TICKS;
            setChanged();
            return true;
        }
        setChanged();
        // 每 10 tick（0.5 秒）同步一次：HUD 百分比够顺滑，又不至于每 tick 发包
        return heatTicks % SYNC_EVERY == 0;
    }

    /** 加热过程中每多少 tick 同步一次给客户端。 */
    public static final int SYNC_EVERY = 10;

    /**
     * 水开时壶嘴冒白色蒸汽。
     *
     * <p>坐标要对准<b>壶嘴</b>：模型里壶嘴在 z 3.6~5.0、y 4.2~5.4（16 = 一格），
     * 取壶嘴尖端即 z≈3.6/16≈0.23、y≈5.4/16≈0.34。
     * 之前写成 y+0.62，粒子会飘在壶身上方而不是从壶嘴出来。</p>
     */
    public void emitSteam(net.minecraft.world.level.Level level) {
        if (!(level instanceof ServerLevel server)) return;
        BlockPos pos = getBlockPos();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.34;
        double z = pos.getZ() + 0.23;
        server.sendParticles(ParticleTypes.WHITE_SMOKE,
                x, y, z, 1, 0.02, 0.02, 0.02, 0.004);
    }

    /* ---------------- 存盘 / 同步 ---------------- */

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean("has_water", hasWater);
        out.putInt("heat_ticks", heatTicks);
        out.putBoolean("boiled", boiled);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.hasWater = in.getBooleanOr("has_water", false);
        this.heatTicks = in.getIntOr("heat_ticks", 0);
        this.boiled = in.getBooleanOr("boiled", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("has_water", hasWater);
        tag.putInt("heat_ticks", heatTicks);
        tag.putBoolean("boiled", boiled);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
