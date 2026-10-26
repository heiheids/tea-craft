package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.item.TeaItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 烧水壶装水的<b>兜底入口</b>。
 *
 * <h2>为什么需要它</h2>
 * <p>方块自身的 {@code useItemOn} 在某些情况下收不到右键请求
 * （用户实测：壶能放下，但手持水桶右键毫无反应，日志里连方法都没进来）。
 * 与其继续猜是哪一层拦了，不如用 Fabric 的 {@link UseBlockCallback}
 * 在更靠前的时机接住，只要<b>目标是烧水壶 + 手持水桶/水瓶</b>就自己处理。</p>
 *
 * <p>它是<b>幂等</b>的：如果方块自己的 {@code useItemOn} 已经处理过，
 * 这里会看到 {@code WATER=true} 而直接放行，不会重复加水。</p>
 */
public final class KettleFillHandler {

    private KettleFillHandler() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(hitResult instanceof BlockHitResult hit)) return InteractionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            if (!level.getBlockState(pos).is(TeaItems.TEA_KETTLE)) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            boolean waterBucket = stack.is(Items.WATER_BUCKET);
            boolean waterBottle = false;
            if (stack.is(Items.POTION)) {
                var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                waterBottle = contents != null
                        && contents.is(net.minecraft.world.item.alchemy.Potions.WATER);
            }
            if (!waterBucket && !waterBottle) return InteractionResult.PASS;

            if (level.isClientSide()) {
                // 客户端先回 SUCCESS，避免这一下又被水桶拿去倒水
                return InteractionResult.SUCCESS;
            }

            if (!(level.getBlockEntity(pos) instanceof TeaKettleBlockEntity kettle)) {
                return InteractionResult.PASS;
            }
            // 已经有水就不再重复加（说明方块自己处理过了）
            if (kettle.hasWater()) return InteractionResult.PASS;

            kettle.fillWater();
            var filled = level.getBlockState(pos)
                    .setValue(TeaKettleBlock.WATER, Boolean.TRUE);
            level.setBlock(pos, filled, net.minecraft.world.level.block.Block.UPDATE_ALL);
            level.sendBlockUpdated(pos, filled, filled,
                    net.minecraft.world.level.block.Block.UPDATE_ALL);

            stack.shrink(1);
            player.addItem(new ItemStack(waterBucket ? Items.BUCKET : Items.GLASS_BOTTLE));
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
            return InteractionResult.SUCCESS;
        });
    }
}
