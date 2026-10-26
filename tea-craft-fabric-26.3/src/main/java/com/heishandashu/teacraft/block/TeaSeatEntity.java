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

    /**
     * 生成后的宽限 tick 数。
     *
     * <p>见 {@link #tick()} 的注释：实体加入世界与玩家上车之间有一个窗口，
     * 这期间 {@code getPassengers()} 可能还是空的。10 tick = 0.5 秒。</p>
     */
    private static final int SPAWN_GRACE_TICKS = 10;

    /** 剩余的宽限 tick。每 tick 自减。 */
    private int spawnGrace = SPAWN_GRACE_TICKS;

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
     *
     * <h2>⚠ 为什么要有「宽限期」</h2>
     * <p>用户实测「坐上去立刻被抛出来」。原因是执行顺序：</p>
     * <pre>
     *   level.addFreshEntity(seat);        // ① 实体立刻进入世界
     *   player.startRiding(seat, true, true);  // ② 玩家才上车
     * </pre>
     * <p>如果实体在 ① 之后、② 之前被 tick 到（同一 tick 内实体列表正在遍历时
     * 新加入的实体可能立刻被处理），{@code getPassengers()} 还是空的，
     * 老代码就会执行 {@code discard()} —— 座位没了，玩家自然被抛下来。</p>
     *
     * <p>所以前 {@link #SPAWN_GRACE_TICKS} 个 tick 不做「无乘客就销毁」的判断。
     * 这个宽限很短（0.5 秒），但足以跨过「生成 → 上车」这个窗口，
     * 又不会让真正废弃的座位实体长期滞留。</p>
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        // ① 刚生成的前几个 tick：玩家可能还没挂上来，不判「无人」。
        if (spawnGrace-- > 0) {
            // 但仍然要检查椅子是否还在，避免椅子被秒拆后座位残留
            if (chairGone()) {
                for (Entity rider : this.getPassengers()) {
                    rider.stopRiding();
                }
                this.discard();
            }
            return;
        }

        // ② 宽限期过后：真的没人坐 → 自己消失
        if (this.getPassengers().isEmpty()) {
            this.discard();
            return;
        }

        // ③ 椅子没了 → 放下乘客并消失
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
