package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 烧水壶 —— 放在热源上把水烧开，倒进盖碗泡茶。
 *
 * <h2>热源判定</h2>
 * <p>壶正下方那一格必须是下列之一：</p>
 * <ul>
 *   <li>{@link MagmaBlock} 岩浆块</li>
 *   <li>点燃的篝火（{@link CampfireBlock#isLitCampfire}）</li>
 *   <li>正在燃烧的熔炉（{@code AbstractFurnaceBlock.LIT} 为 true）</li>
 * </ul>
 *
 * <h2>流程</h2>
 * <ol>
 *   <li>手持水桶 / 水瓶右键 → 装水（桶换空桶、瓶换空瓶）</li>
 *   <li>放在热源上，5 秒（100 tick）烧开</li>
 *   <li>烧开后壶嘴冒白色蒸汽粒子</li>
 *   <li>空手右键把壶拿起来（壶里保留"已烧开"状态）</li>
 *   <li>手持壶右键茶盘上的盖碗 → 注入热水</li>
 * </ol>
 */
public class TeaKettleBlock extends Block implements EntityBlock {

    /** 壶里有没有水（决定渲染：空壶 / 有水）。 */
    public static final BooleanProperty WATER = BooleanProperty.create("water");

    /**
     * 水是否<b>已烧开</b>。
     *
     * <p>做成方块状态而不是只存在方块实体里，因为：方块状态的同步是原版内置的、
     * 可靠的；而方块实体要自己调 {@code sendBlockUpdated} 才发，
     * 时序上容易漏（HUD 一直显示"正在烧水 0%"就是这么来的）。</p>
     */
    public static final BooleanProperty BOILED = BooleanProperty.create("boiled");

    /**
     * 壶嘴朝向（水平四向）。
     *
     * <p>模型里壶嘴是朝<b>北</b>画的（元素 z 3.6~5.0，是最靠 -z 的那一段），
     * 放置时按玩家朝向旋转，让壶嘴对着玩家 —— 倒水时手感最自然。</p>
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * 碰撞/选择箱：<b>只包住壶身</b>，不包提梁。
     *
     * <p>模型实际范围是 x 4.6~11.4、y 1~7.9、z 3.6~11.4（16 = 一格）。
     * 之前写成 0~16 全宽、13 高，比模型大了一大圈 ——
     * 用户反馈「碰撞箱巨大」就是这个。</p>
     */
    private static final VoxelShape SHAPE =
            Block.box(4.6, 0.0, 3.6, 11.4, 7.9, 11.4);

    public TeaKettleBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATER, Boolean.FALSE)
                .setValue(BOILED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATER, BOILED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * 碰撞箱也要收窄。
     *
     * <p>{@code getShape} 只影响<b>选择框</b>（准星描边），
     * 真正决定"撞不撞得到"的是 {@code getCollisionShape}。
     * 只重写前者的话，玩家仍会被当成整块方块挡住 —— 用户反馈的
     * 「碰撞箱也是完整方块的」就是这个原因。</p>
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    /**
     * 方块放下之后，按方块实体里恢复出来的数据修正方块状态。
     *
     * <h2>为什么不用 getStateForPlacement</h2>
     * <p>实测 26.3 的放置路径<b>不会调用</b> {@code getStateForPlacement}
     * （我加了日志，一次都没打印）。而 {@code setPlacedBy} 在放置完成后必定回调，
     * 此时方块实体已经从物品的 {@code BLOCK_ENTITY_DATA} 恢复了
     * {@code hasWater/boiled}，直接读它来修正状态最可靠。</p>
     *
     * <p>修的就是"装了开水的壶放回去，HUD 一直显示正在烧水 99%"——
     * 那种情况下方块实体有开水，但方块状态还是默认的 boiled=false，
     * 而 HUD 判断"是否烧开"读的是状态。</p>
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;

        // 优先从"刚放下的那个 ItemStack"读数据 —— 它一定还在手上。
        // 此时方块实体还没被 setBlockEntityData 填充，所以不能依赖它。
        boolean hasWater = false;
        boolean boiled = false;
        boolean fromItem = false;
        if (stack != null) {
            var data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
            if (data != null) {
                var tag = data.copyTagWithoutId();
                hasWater = tag.getBooleanOr("has_water", false);
                boiled = tag.getBooleanOr("boiled", false);
                fromItem = true;
            }
        }
        // 兜底：如果物品上没有数据，再看方块实体（例如 /setblock 之类）
        if (!fromItem && level.getBlockEntity(pos) instanceof TeaKettleBlockEntity kettle) {
            hasWater = kettle.hasWater();
            boiled = kettle.boiled();
        }

        com.heishandashu.teacraft.TeaCraft.LOGGER.info(
                "[teacraft] setPlacedBy: fromItem={} water={} boiled={} hasBEdata={}",
                fromItem, hasWater, boiled,
                stack != null && stack.has(
                        net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA));
        BlockState fixed = state.setValue(WATER, hasWater).setValue(BOILED, boiled);

        // 朝向：一律按放置者转向。
        //
        // 【曾经优先读壶上记的朝向】，以为「拿起来再放下要保原样」更好。实际
        // 后果是：壶被拿起过一次之后，朝向就永久烘焙进物品了，之后不管站在哪边
        // 放下都还原成同一个方向 —— 玩家再也转不动它。
        // 朝向是「摆放时决定」的事，不该被物品记住。
        Direction want = placer.getDirection().getOpposite();
        if (!want.getAxis().isHorizontal()) {
            // 玩家俯视/仰视放置时拿不到水平朝向，退回默认
            want = Direction.NORTH;
        }
        fixed = fixed.setValue(FACING, want);

        if (fixed != state) {
            level.setBlock(pos, fixed, Block.UPDATE_ALL);
            level.sendBlockUpdated(pos, fixed, fixed, Block.UPDATE_ALL);
        }
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // 直接读物品里的 BLOCK_ENTITY_DATA。
        //
        // 时序（实测得出，两个方法都要有）：
        //   getStateForPlacement —— 此时物品数据可用，决定了初始状态
        //   setPlacedBy          —— 此时 BE 还没恢复数据，但可用于兜底
        //   setBlockEntityData   —— 最后才把数据写进 BE
        // 只在 setPlacedBy 里读 BE 是不够的：那时 BE 还是空的。
        ItemStack stack = context.getItemInHand();
        BlockState base = defaultBlockState();

        // 朝向：一律按玩家转向（与 setPlacedBy 保持一致）。
        // 不读物品上保存的朝向 —— 那会让壶被拿起过一次后再也转不动。
        Direction want = context.getHorizontalDirection().getOpposite();
        if (!want.getAxis().isHorizontal()) want = Direction.NORTH;
        base = base.setValue(FACING, want);

        var data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return base;

        var tag = data.copyTagWithoutId();
        return base.setValue(WATER, tag.getBooleanOr("has_water", false))
                .setValue(BOILED, tag.getBooleanOr("boiled", false));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeaKettleBlockEntity(pos, state);
    }

    /** 正下方那一格是不是有效热源。 */
    public static boolean isHeatSource(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState state = level.getBlockState(below);
        if (state.getBlock() instanceof MagmaBlock) return true;
        // 烧水茶桌：岩浆块已经嵌进桌面，所以整张桌子就当热源用。
        // 它本身不是 MagmaBlock（那样会继承踩踏伤害），只是内部嵌了岩浆。
        if (state.getBlock() instanceof TeaTableHotBlock) return true;
        // 烧水茶桌：岩浆块已经嵌进桌面，所以整张桌子就当热源用。
        // 它本身不是 MagmaBlock（那样会继承踩踏伤害），只是内部嵌了岩浆。
        if (state.getBlock() instanceof CampfireBlock && CampfireBlock.isLitCampfire(state)) return true;
        return state.getBlock() instanceof AbstractFurnaceBlock
                && state.hasProperty(AbstractFurnaceBlock.LIT)
                && state.getValue(AbstractFurnaceBlock.LIT);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (!(be instanceof TeaKettleBlockEntity kettle)) return;
            // 每 tick 校准方块状态与方块实体是否一致。
            //
            // 为什么需要：壶被放下时，方块实体从物品的 BLOCK_ENTITY_DATA
            // 恢复了 has_water/boiled，但方块状态是放置流程给的默认值。
            // getStateForPlacement / setPlacedBy / getPlacementState 三个钩子
            // 在这条路径上都拿不到物品数据（逐个加日志确认过），
            // 所以在 tick 里直接以方块实体为准校正 —— 一定生效，且成本极低
            //（状态本来一致时不做任何事）。
            boolean wantWater = kettle.hasWater();
            boolean wantBoiled = kettle.boiled();
            if (st.getValue(WATER) != wantWater || st.getValue(BOILED) != wantBoiled) {
                BlockState corrected = st.setValue(WATER, wantWater).setValue(BOILED, wantBoiled);
                lvl.setBlock(pos, corrected, Block.UPDATE_ALL);
                if (lvl instanceof net.minecraft.server.level.ServerLevel sv) {
                    sv.sendBlockUpdated(pos, corrected, corrected, Block.UPDATE_ALL);
                }
                st = corrected;
            }
            boolean heated = isHeatSource(lvl, pos);
            // 返回值 = "需要同步"。加热中每 10 tick 一次 + 烧开那一下。
            boolean needSync = kettle.tickHeat(heated);
            boolean justBoiled = kettle.boiled() && kettle.heatTicks() == TeaKettleBlockEntity.BOIL_TICKS
                    && needSync;
            if (justBoiled) {
                // 烧开的一瞬间：响一次 + 大量蒸汽
                lvl.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.6F);
            }
            if (needSync && lvl instanceof net.minecraft.server.level.ServerLevel server) {
                if (justBoiled) {
                    server.sendParticles(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE,
                            pos.getX() + 0.5, pos.getY() + 0.34, pos.getZ() + 0.23,
                            12, 0.12, 0.10, 0.12, 0.01);
                }
                // ① "已烧开"写进方块状态：状态同步是原版内置的，客户端一定收得到。
                BlockState newState = st.setValue(BOILED, kettle.boiled());
                if (newState != st) {
                    lvl.setBlock(pos, newState, Block.UPDATE_ALL);
                }
                // ② 加热百分比只能走方块实体数据，必须显式发包。
                //    上一轮我在这里漏掉了这两行，结果进度永远停在 0%
                //    （用户反馈"百分比没有走，直接变成开水"）。
                server.getChunkSource().blockChanged(pos);
                server.sendBlockUpdated(pos, st, st, Block.UPDATE_ALL);
            }
            // 水开之后持续冒一点蒸汽（每 5 tick 一次，不要每 tick）
            if (kettle.boiled() && lvl.getGameTime() % 5 == 0) {
                kettle.emitSteam(lvl);
            }
        };
    }

    /* ---------------- 交互 ---------------- */

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            com.heishandashu.teacraft.TeaCraft.LOGGER.info(
                    "[teacraft] 烧水壶 useItemOn 被调用：手持={}",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        boolean waterBucket = stack.is(Items.WATER_BUCKET);
        boolean waterBottle = false;
        if (stack.is(Items.POTION)) {
            var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            waterBottle = contents != null
                    && contents.is(net.minecraft.world.item.alchemy.Potions.WATER);
        }
        if (!waterBucket && !waterBottle) return InteractionResult.TRY_WITH_EMPTY_HAND;

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (!(level.getBlockEntity(pos) instanceof TeaKettleBlockEntity kettle)) {
            com.heishandashu.teacraft.TeaCraft.LOGGER.warn(
                    "[teacraft] 烧水壶没有方块实体 @ {}", pos);
            return InteractionResult.PASS;
        }

        kettle.fillWater();
        BlockState filled = state.setValue(WATER, Boolean.TRUE);
        level.setBlock(pos, filled, Block.UPDATE_ALL);
        level.sendBlockUpdated(pos, filled, filled, Block.UPDATE_ALL);
        stack.shrink(1);
        player.addItem(new ItemStack(waterBucket ? Items.BUCKET : Items.GLASS_BOTTLE));
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /** 空手右键：拿起壶（壶里已烧开的状态跟着走）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TeaKettleBlockEntity kettle)) return InteractionResult.PASS;

        ItemStack kettleItem = new ItemStack(TeaItems.TEA_KETTLE_ITEM.get());
        com.heishandashu.teacraft.item.TeaKettleItem.setHotWater(kettleItem, kettle.boiled());
        // 壶嘴朝向也要带走，否则拿起来再放下会转回"对着玩家"
        kettleItem.set(com.heishandashu.teacraft.core.TeaComponents.KETTLE_FACING,
                state.getValue(FACING).getSerializedName());
        // 同时把方块实体数据写进 BLOCK_ENTITY_DATA：
        // 这样把壶原样放下时，加热进度与"已烧开"都不会丢。
        // 只记 hot_water 组件的话，放下会重新变成空壶。
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        // 必须用 saveWithId：saveCustomOnly 不写 "id" 字段，
        // 而还原时正是靠 id 找方块实体类型的。缺了它，整份数据会被静默丢弃，
        // 表现为"把壶放回去后里面没有水、也没烧开"。
        kettle.saveWithId(out);
        net.minecraft.world.item.BlockItem.setBlockEntityData(
                kettleItem, kettle.getType(), out);
        player.addItem(kettleItem);
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.2F);
        return InteractionResult.SUCCESS;
    }
}
