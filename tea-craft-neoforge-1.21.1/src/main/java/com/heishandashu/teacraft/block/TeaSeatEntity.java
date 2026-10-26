package com.heishandashu.teacraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 椅子上的「座位」—— 不可见、不可攻击、不挡路的实体（NeoForge 1.21.1）。
 *
 * <h2>为什么必须要有实体</h2>
 * <p>原版「坐」靠 {@code Entity.startRiding} 实现，而骑乘必须有载具实体。
 * 方块本身当不了载具，所以椅子坐下必须生成这个座位实体。</p>
 *
 * <h2>1.21.1 存档 API</h2>
 * <p>用 {@code CompoundTag}（26.3 是 {@code ValueInput}/{@code ValueOutput}）。</p>
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
        this.blocksBuilding = false;
    }

    /** 造一个座位。 */
    public static @Nullable TeaSeatEntity create(Level level, BlockPos pos, double seatY) {
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
     * 每个 tick 检查：没人坐了自己消失；椅子没了放下乘客并消失。
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
        // 座位没有需要同步的字段
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.seatPos = new BlockPos(
                tag.getInt("seat_x"), tag.getInt("seat_y"), tag.getInt("seat_z"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("seat_x", this.seatPos.getX());
        tag.putInt("seat_y", this.seatPos.getY());
        tag.putInt("seat_z", this.seatPos.getZ());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
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
    public boolean canBeCollidedWith() {
        return false;                       // 不挡路
    }

    @Override
    protected void doWaterSplashEffect() {
        // 座位落水不该溅水
    }
}
