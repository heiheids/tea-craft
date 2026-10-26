package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 烧水壶装水的<b>兜底入口</b>。
 *
 * <h2>为什么需要它</h2>
 * <p>方块自身的 {@code useItemOn} 在某些情况下收不到右键请求
 * （用户实测：壶能放下，但手持水桶右键毫无反应，日志里连方法都没进来）。
 * 与其继续猜是哪一层拦了，不如用 NeoForge 的
 * {@link PlayerInteractEvent.RightClickBlock} 在更靠前的时机接住 ——
 * 只要目标烧水壶 + 手持水桶/水瓶就自己处理。</p>
 *
 * <p>它是幂等的：如果方块自己的 {@code useItemOn} 已经处理过，
 * 这里会看到 {@code WATER=true} 而直接放行，不会重复加水。</p>
 *
 * <h2>Fabric 支 → 本支</h2>
 * <p>Fabric 那边订阅 {@code UseBlockCallback.EVENT}，签名是
 * {@code (player, level, hand, hitResult) -> InteractionResult}。
 * NeoForge 这边对应 {@link PlayerInteractEvent.RightClickBlock}，
 * 挂在游戏总线上，用 {@code setCancellationResult} 表达「我处理了」。</p>
 */
public final class KettleFillHandler {

    private KettleFillHandler() {
    }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                KettleFillHandler::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 只处理主手，避免双触发
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(TeaItems.TEA_KETTLE.get())) return;

        var player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        boolean waterBucket = stack.is(Items.WATER_BUCKET);
        boolean waterBottle = false;
        if (stack.is(Items.POTION)) {
            var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            waterBottle = contents != null
                    && contents.is(net.minecraft.world.item.alchemy.Potions.WATER);
        }
        if (!waterBucket && !waterBottle) return;

        if (level.isClientSide()) {
            // 客户端先回 SUCCESS，避免这一下又被水桶拿去倒水
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (!(level.getBlockEntity(pos) instanceof TeaKettleBlockEntity kettle)) return;
        // 已经有水就不再重复加（说明方块自己处理过了）
        if (kettle.hasWater()) return;
        // 已经标了 WATER 的也不再处理
        if (level.getBlockState(pos).getValue(TeaKettleBlock.WATER)) return;

        kettle.fillWater();
        var filled = level.getBlockState(pos).setValue(TeaKettleBlock.WATER, Boolean.TRUE);
        level.setBlock(pos, filled, net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.sendBlockUpdated(pos, filled, filled,
                net.minecraft.world.level.block.Block.UPDATE_ALL);

        stack.shrink(1);
        player.addItem(new ItemStack(waterBucket ? Items.BUCKET : Items.GLASS_BOTTLE));
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
