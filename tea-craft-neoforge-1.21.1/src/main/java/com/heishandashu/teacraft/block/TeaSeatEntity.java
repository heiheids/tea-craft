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
    /** 绑定的椅子方块坐标。 */
    private BlockPos seatPos = BlockPos.ZERO;

    /**
     * 生成后的宽限 tick 数。
     *
     * <p>见 tick() 的注释：实体加入世界与玩家上车之间有一个窗口，
     * 这期间 getPassengers() 可能还是空的。10 tick = 0.5 秒。</p>
     */
    private static final int SPAWN_GRACE_TICKS = 10;

    /** 剩余的宽限 tick。每 tick 自减。 */
    private int spawnGrace = SPAWN_GRACE_TICKS;

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
     * 每个 tick 检查：
     * <ol>
     *   <li>没人坐了 → 自己消失；</li>
     *   <li>椅子没了（被挖掉/被替换）→ 放下乘客并消失。</li>
     * </ol>
     *
     * <h2>为什么要有「宽限期」</h2>
     * <p>执行顺序是：</p>
     * <pre>
     *   level.addFreshEntity(seat);            // 实体立刻进入世界
     *   player.startRiding(seat, force, true); // 玩家才上车
     * </pre>
     * <p>如果实体在两者之间被 tick 到，getPassengers() 还是空的，
     * 旧的「无乘客即 discard」逻辑会把座位删掉，玩家一上车就被抛出来。</p>
     *
     * <p>所以前 SPAWN_GRACE_TICKS 个 tick 不做「无乘客就销毁」判断。
     * 0.5 秒足以跨过这个窗口，又不会让废弃座位长期滞留。</p>
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        // 刚生成的前几个 tick：玩家可能还没挂上来，不判「无人」
        if (spawnGrace-- > 0) {
            if (chairGone()) {
                for (Entity rider : this.getPassengers()) {
                    rider.stopRiding();
                }
                this.discard();
            }
            return;
        }

        // 宽限期过后：真的没人坐 → 自己消失
        if (this.getPassengers().isEmpty()) {
            this.discard();
            return;
        }

        // 椅子没了 → 放下乘客并消失
        if (chairGone()) {
            for (Entity rider : this.getPassengers()) {
                rider.stopRiding();
            }
            this.discard();
        }
    }

    /** 座位绑定的椅子方块还在不在。 */
    private boolean chairGone() {
        return !(this.level().getBlockState(this.seatPos).getBlock() instanceof TeaChairBlock);
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
