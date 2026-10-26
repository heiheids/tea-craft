package com.heishandashu.teacraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * 椅子上的「座位」—— 一个不可见、不可攻击、不参与活塞/推动的实体。
 *
 * <h2>为什么必须要有实体</h2>
 * <p>原版的「坐」是靠 {@code Entity.startRiding} 实现的，而骑乘必须有载具实体。
 * 方块本身不能当载具，所以椅子坐下必须生成这样一个座位实体，
 * 玩家骑在它上面，位置就被固定住了。</p>
 *
 * <p>起身时（玩家潜行、被伤害、椅子被破坏）实体自己消失。</p>
 *
 * <h2>26.3 的存档 API</h2>
 * <p>这里用 {@link ValueInput}/{@link ValueOutput}（1.21.1 是 {@code CompoundTag}）。
 * 座位只需要记一个「所属方块坐标」，本来可以完全不存 —— 但存活 tick 里要
 * 检查椅子还在不在，坐标必须持久化。</p>
 */
public class TeaSeatEntity extends Entity {

    /** 由 TeaItems 在注册后回填。 */
    public static EntityType<TeaSeatEntity> TYPE;

    /** 绑定的椅子方块坐标。 */
    private BlockPos seatPos = BlockPos.ZERO;

    public TeaSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        // 座位不该被推动、不该占碰撞
        this.blocksBuilding = false;
    }

    /**
     * 造一个座位。
     *
     * @param level  世界
     * @param pos    椅子方块坐标
     * @param seatY  玩家屁股应该待的绝对 y
     */
    public static TeaSeatEntity create(Level level, BlockPos pos, double seatY) {
        if (TYPE == null) return null;
        TeaSeatEntity seat = new TeaSeatEntity(TYPE, level);
        seat.seatPos = pos.immutable();
        seat.setPos(pos.getX() + 0.5, seatY, pos.getZ() + 0.5);
        return seat;
    }

    /** 把某个椅子上的座位全部清掉（椅子被破坏时调用）。 */
    public static void ejectAt(Level level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(1.5);
        for (TeaSeatEntity seat : level.getEntitiesOfClass(TeaSeatEntity.class, box)) {
            if (!seat.seatPos.equals(pos)) continue;
            for (Entity rider : seat.getPassengers()) {
                rider.stopRiding();
            }
            seat.discard();
        }
    }

    /**
     * 每个 tick 检查：
     * <ol>
     *   <li>没人坐了 → 自己消失；</li>
     *   <li>椅子没了（被挖掉/被替换）→ 放下乘客并消失。</li>
     * </ol>
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        if (this.getPassengers().isEmpty()) {
            this.discard();
            return;
        }

        if (!(this.level().getBlockState(this.seatPos).getBlock() instanceof TeaChairBlock)) {
            for (Entity rider : this.getPassengers()) {
                rider.stopRiding();
            }
            this.discard();
        }
    }

    /* ---------------- 无关紧要的实现 ---------------- */

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 座位没有任何需要同步的字段
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.seatPos = new BlockPos(
                input.getIntOr("seat_x", 0),
                input.getIntOr("seat_y", 0),
                input.getIntOr("seat_z", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("seat_x", this.seatPos.getX());
        output.putInt("seat_y", this.seatPos.getY());
        output.putInt("seat_z", this.seatPos.getZ());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;                       // 打不动
    }

    @Override
    public boolean isPickable() {
        return false;                       // 鼠标点不到
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@org.jetbrains.annotations.Nullable Entity other) {
        return false;                       // 不挡路
    }
}
