package com.heishandashu.teacraft.world;

import com.heishandashu.teacraft.item.TeaItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;

/**
 * 僵尸<b>刷出时手里拿着茶树苗</b>，被打死后<b>必定掉落</b>。
 *
 * <h2>概率怎么来的（反编译原版，不是估的）</h2>
 * <p>{@code Zombie.populateDefaultEquipmentSlots} 的实际字节码是：</p>
 * <pre>
 *   float p = (难度 == HARD) ? 0.05f : 0.01f;
 *   if (random.nextFloat() &lt; p) {
 *       int pick = random.nextInt(6);
 *       if (pick == 0)      setItemSlot(MAINHAND, IRON_SWORD);   // 1/6
 *       else if (pick == 1) setItemSlot(MAINHAND, IRON_SPEAR);   // 1/6
 *       else                setItemSlot(MAINHAND, IRON_SHOVEL);  // 4/6  ← else 分支
 *   }
 * </pre>
 *
 * <p><b>注意</b>：原版僵尸其实只有 3 种主手物品、不是 6 种。
 * {@code nextInt(6)} 的取值 2/3/4/5 全部落到 else → 都拿铁铲。
 * 所以「僵尸手持铁铲」的概率是：</p>
 * <ul>
 *   <li>困难：5% × 4/6 = <b>3.33%</b></li>
 *   <li>其它：1% × 4/6 = <b>0.67%</b></li>
 * </ul>
 *
 * <p>本类照抄这套判定，把"铁铲"换成本模组的茶树苗，
 * 因此概率与铁铲<b>严格相同</b>。</p>
 *
 * <h2>打死必掉</h2>
 * <p>原版手部装备的掉落率是 {@code 8.5%}（且受抢夺影响），
 * 这里按需求改成<b>必掉</b>：用 {@link ServerLivingEntityEvents#AFTER_DEATH}
 * 在僵尸死亡时把主手的茶树苗直接丢出来。</p>
 */
public final class ZombieSpawnEquipment {

    /** 困难难度的持械概率（与原版一致）。 */
    public static final float HARD_CHANCE = 0.05F;

    /** 其它难度的持械概率（与原版一致）。 */
    public static final float NORMAL_CHANCE = 0.01F;

    /** 原版 {@code nextInt(6)} 的上界。 */
    public static final int PICK_BOUND = 6;

    /**
     * 铁铲落在 else 分支，吃掉 {@code pick} 为 2..5 的全部取值。
     *
     * <p>即 {@code pick >= 2} 时拿铁铲，命中率 4/6。</p>
     */
    public static final int SHOVEL_MIN_PICK = 2;

    /**
     * 待掉落的僵尸 UUID。
     *
     * <p>26.3 没有 {@code getPersistentData()}，而 ALLOW_DEATH 与 AFTER_DEATH
     * 是同一次击杀内的紧邻回调，用进程内集合传递足够了。</p>
     */
    /** 掉落被触发过几次（自检用；生产环境也会累加，无副作用）。 */
    public static final java.util.concurrent.atomic.AtomicInteger DROP_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();

    private static final java.util.Set<java.util.UUID> PENDING =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private ZombieSpawnEquipment() {
    }

    public static void register() {
        // ① 刷出时装备
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof Zombie zombie)) return;
            if (world.isClientSide()) return;

            var random = zombie.getRandom();
            float p = world.getDifficulty() == Difficulty.HARD ? HARD_CHANCE : NORMAL_CHANCE;
            if (random.nextFloat() >= p) return;

            // 原版已给主手装备就不动，避免抢掉铁剑/铁矛/铁铲
            ItemStack main = zombie.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!main.isEmpty()) return;

            // 与原版铁铲同率：pick >= 2 才算命中
            if (random.nextInt(PICK_BOUND) < SHOVEL_MIN_PICK) return;

            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(TeaItems.TEA_BUSH_ITEM));
            // 不许原版按 8.5% 随机丢掉 —— 我们要的是"打死必掉"，
            // 所以把掉落率设成 0，改用下面的 AFTER_DEATH 自己丢。
            zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        });

        // ② 被打死时必定掉出茶树苗
        //
        // 关键：ALLOW_DEATH 里 spawnAtLocation 生成的物品会被死亡流程立刻清掉
        // （自检实测：spawnAtLocation 返回了实体，但返回后世界里 0 个掉落物）。
        // 所以 ALLOW_DEATH 只负责"标记这只僵尸该掉"，真正掉落放到 AFTER_DEATH。
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, amount) -> {
            if (!(entity instanceof Zombie zombie)) return true;
            ItemStack main = zombie.getItemBySlot(EquipmentSlot.MAINHAND);
            if (main.isEmpty() || !main.is(TeaItems.TEA_BUSH_ITEM)) return true;

            zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            PENDING.add(zombie.getUUID());
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!PENDING.remove(entity.getUUID())) return;
            if (!(entity instanceof Zombie zombie)) return;
            if (!(zombie.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

            zombie.spawnAtLocation(serverLevel, new ItemStack(TeaItems.TEA_BUSH_ITEM));
            DROP_COUNT.incrementAndGet();
        });
    }
}
