package com.heishandashu.teacraft.world;

import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * 僵尸刷新时手里拿着茶树苗，被打死后必定掉落。
 *
 * <h2>概率怎么来的（反编译原版，不是估的）</h2>
 * <p>{@code Zombie.populateDefaultEquipmentSlots} 的实际字节码是：</p>
 * <pre>
 *   float p = (难度 == HARD) ? 0.05f : 0.01f;
 *   if (random.nextFloat() &lt; p) {
 *       int pick = random.nextInt(6);
 *       if (pick == 0)      setItemSlot(MAINHAND, IRON_SWORD);   // 1/6
 *       else if (pick == 1) setItemSlot(MAINHAND, IRON_SPEAR);   // 1/6
 *       else                setItemSlot(MAINHAND, IRON_SHOVEL);  // 4/6  → else 分支
 *   }
 * </pre>
 *
 * <p>注意：原版僵尸其实只有 3 种主手物品、不是 6 种。
 * {@code nextInt(6)} 的取值 2/3/4/5 全部落到 else → 都拿铁锹。
 * 所以「僵尸手持铁锹」的概率是：困难 5% x 4/6 = 3.33%，其它 1% x 4/6 = 0.67%。</p>
 *
 * <p>本类照抄这套判定，把「铁锹」换成模组的茶树苗，
 * 因此概率与铁锹严格相同。</p>
 *
 * <h2>打死必掉</h2>
 * <p>原版手部装备的掉落率是 8.5%（且受抢夺影响），
 * 这里按需求改成必掉：把手部掉落率设为 0，再用
 * {@link LivingDropsEvent} 在死亡掉落里补一个茶树苗。</p>
 *
 * <h2>Fabric 支 → 本支</h2>
 * <table>
 *   <tr><th>Fabric</th><th>本支</th></tr>
 *   <tr><td>{@code ServerEntityEvents.ENTITY_LOAD}</td>
 *       <td>{@link EntityJoinLevelEvent}</td></tr>
 *   <tr><td>{@code ServerLivingEntityEvents.ALLOW_DEATH} + {@code AFTER_DEATH}</td>
 *       <td>{@link LivingDropsEvent}（一个事件搞定）</td></tr>
 * </table>
 * <p>Fabric 那边需要「先标记、再在 AFTER_DEATH 里补掉落」，是因为
 * ALLOW_DEATH 阶段生成的掉落物会被死亡流程清掉。
 * NeoForge 的 LivingDropsEvent 直接操作掉落集合，不需要中间状态，
 * 所以本支去掉了 PENDING 集合。</p>
 */
public final class ZombieSpawnEquipment {

    /** 困难难度的持械概率（与原版一致）。 */
    public static final float HARD_CHANCE = 0.05F;

    /** 其它难度的持械概率（与原版一致）。 */
    public static final float NORMAL_CHANCE = 0.01F;

    /** 原版 {@code nextInt(6)} 的上界。 */
    public static final int PICK_BOUND = 6;

    /**
     * 铁锹落在 else 分支，吃掉 {@code pick} 为 2..5 的全部取值。
     *
     * <p>即 {@code pick >= 2} 时拿铁锹，命中率 4/6。</p>
     */
    public static final int SHOVEL_MIN_PICK = 2;

    /** 掉落被触发过几次（自检用；生产环境也会累加，无副作用）。 */
    public static final java.util.concurrent.atomic.AtomicInteger DROP_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();

    private ZombieSpawnEquipment() {
    }

    public static void register() {
        var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;

        // 1) 刷出时装备
        bus.addListener(ZombieSpawnEquipment::onJoinLevel);
        // 2) 被打死时必定掉落
        bus.addListener(ZombieSpawnEquipment::onDrops);
    }

    private static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        var world = event.getLevel();
        if (world.isClientSide()) return;
        // 从磁盘读出来的不再重新掷骰子，否则每次读档都可能改它手里的东西
        if (event.loadedFromDisk()) return;

        var random = zombie.getRandom();
        float p = world.getDifficulty() == Difficulty.HARD ? HARD_CHANCE : NORMAL_CHANCE;
        if (random.nextFloat() >= p) return;

        // 原版已给主手装备就不动，避免抢掉铁剑/铁矛/铁锹
        ItemStack main = zombie.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!main.isEmpty()) return;

        // 与原版铁锹同概率：pick >= 2 才算命中
        if (random.nextInt(PICK_BOUND) < SHOVEL_MIN_PICK) return;

        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(TeaItems.TEA_BUSH_ITEM.get()));
        // 不要原版那 8.5% 随机掉落 —— 我们要的是「打死必掉」，
        // 所以把手部掉落率设为 0，改由下面的 onDrops 自己补。
        zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (!(zombie.level() instanceof ServerLevel serverLevel)) return;

        ItemStack main = zombie.getItemBySlot(EquipmentSlot.MAINHAND);
        if (main.isEmpty() || !main.is(TeaItems.TEA_BUSH_ITEM.get())) return;

        // 直接往掉落集合里加一个，由原版负责生成实体（比 spawnAtLocation 更稳）
        var dropped = new ItemEntity(serverLevel,
                zombie.getX(), zombie.getY(0.5), zombie.getZ(),
                new ItemStack(TeaItems.TEA_BUSH_ITEM.get()));
        event.getDrops().add(dropped);
        DROP_COUNT.incrementAndGet();
    }
}
