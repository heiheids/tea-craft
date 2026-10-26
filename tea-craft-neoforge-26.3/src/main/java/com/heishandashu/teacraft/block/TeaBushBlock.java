package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.climate.TeaClimate;
import com.heishandashu.teacraft.config.TeaConfig;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 茶树：<b>三个生长阶段 + 一个成熟标记</b>，成树靠随机刻长大（概率仿小麦），
 * 长够时间才能采茶。
 *
 * <h2>四个状态，对应三张成长贴图</h2>
 * <table border="1">
 *   <tr><th>age</th><th>含义</th><th>到达方式</th></tr>
 *   <tr><td>0</td><td>幼体</td><td>刚种下</td></tr>
 *   <tr><td>1</td><td>生长期</td><td>随机刻（或骨粉）</td></tr>
 *   <tr><td>2</td><td><b>成树</b>（满一格高）</td><td>随机刻（或骨粉，骨粉最多催到这一阶）</td></tr>
 *   <tr><td>3</td><td>成树 <b>且长够时间</b>（顶端冒出嫩绿小叶，可采摘）</td><td><b>只能靠时间</b>：计划刻到点才置位</td></tr>
 * </table>
 *
 * <p>把「成树」和「可采」分开，是为了满足两条需求同时成立：
 * 「骨粉可以催熟但不能催出茶叶，必须生长够时间」，以及
 * 「成熟茶叶顶端有嫩绿色小叶片」——那几片嫩叶就是「可采」的视觉信号。</p>
 *
 * <h2>生长概率：仿小麦</h2>
 * <p>原版小麦是 {@code random.nextInt((int)(25.0F / growthSpeed) + 1) == 0} 才长一阶
 * （growthSpeed=1 时即 1/26）。这里同样用「每随机刻按 1/N 概率长一阶」，但 N 不是固定的，
 * 而是由<b>该群系需要的成熟天数</b>反推出来的：</p>
 *
 * <pre>
 * 每游戏日的随机刻数 = 24000 × 3 / 4096 ≈ 17.58   （randomTickSpeed 默认 3）
 * N = 目标天数 × 17.58 / 2                        （两阶：幼体→生长期→成树）
 *   热带草原 3 天 → N = 26   ← 正好等于小麦的 25/1+1
 *   积雪山坡 8 天 → N = 70
 * </pre>
 *
 * <p>于是「越热长得越快」和「概率仿小麦」是同一套机制。
 * 改 {@code randomTickSpeed} 会按比例改变所有植物的速度，和原版农作物一致。</p>
 */
public class TeaBushBlock extends Block implements BonemealableBlock,
        net.minecraft.world.level.block.EntityBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    /** 幼体。 */
    public static final int AGE_YOUNG = 0;
    /** 生长期。 */
    public static final int AGE_GROWING = 1;
    /** 成树（满一格高），但还没长够时间。 */
    public static final int AGE_TREE = 2;
    /** 成树且长够时间，可以采摘。 */
    public static final int AGE_RIPE = 3;

    /** 剪刀分枝能得到几个新茶树种。 */
    public static final int BRANCH_SEEDLINGS = 2;

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    public TeaBushBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, AGE_YOUNG));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 只能种在泥土类方块上，且群系必须允许（沙漠 / 恶地不行）。 */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        boolean soil = below.is(Blocks.DIRT)
                || below.is(Blocks.GRASS_BLOCK)
                || below.is(Blocks.COARSE_DIRT)
                || below.is(Blocks.ROOTED_DIRT)
                || below.is(Blocks.PODZOL)
                || below.is(Blocks.FARMLAND);
        if (!soil) return false;
        return TeaClimate.canPlant(level, pos);
    }

    /**
     * 支撑没了立刻变空气。
     *
     * <p>少了这个方法，茶树在支撑消失后会悬空挂着，直到下一次随机刻才发现自己不该在那。</p>
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
                                     net.minecraft.world.level.ScheduledTickAccess tickAccess,
                                     BlockPos pos, net.minecraft.core.Direction direction,
                                     BlockPos neighborPos, BlockState neighborState,
                                     RandomSource random) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    /* ------------------------------------------------------------------
     * 计时：只有「新种下」才起算，阶段变化不重置计时
     * ------------------------------------------------------------------ */

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide()) return;
        // 只有「空 → 茶树」才算新种下；随机刻/骨粉改 age 也会走这里，不能重置计时
        if (oldState.is(this)) return;
        if (level instanceof ServerLevel serverLevel) {
            startCountdown(serverLevel, pos, state);
        }
    }

    /** 篝火保温的搜索半径（格）。需求：「半径 2 格之内有篝火」。 */
    public static final int CAMPFIRE_RADIUS = 2;

    /**
     * 半径 2 格内有没有篝火（含灵魂篝火）。
     *
     * <p>用原版标签 {@code #minecraft:campfires}，这样原版以后加变体也自动生效。</p>
     */
    public static boolean hasCampfireNearby(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(
                pos.offset(-CAMPFIRE_RADIUS, -CAMPFIRE_RADIUS, -CAMPFIRE_RADIUS),
                pos.offset(CAMPFIRE_RADIUS, CAMPFIRE_RADIUS, CAMPFIRE_RADIUS))) {
            if (level.getBlockState(p).is(net.minecraft.tags.BlockTags.CAMPFIRES)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这株茶树是不是「太冷了」。
     *
     * <p>需求：「寒带和极寒带的茶树必须半径 2 格之内有篝火维持温度，否则不生长」。</p>
     *
     * @return true 表示需要篝火但附近没有 → 停止生长
     */
    public static boolean isTooCold(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        TeaOrigin origin = TeaClimate.originAt(level, pos);
        if (!origin.needsCampfire()) return false;
        return !hasCampfireNearby(level, pos);
    }

    /** 排一次「到点成熟」的计划刻；计划刻会随区块存档保存，退出重进不丢。 */
    private void scheduleRipen(Level level, BlockPos pos) {
        long ticks = TeaClimate.growthTicks(level, pos);
        level.scheduleTick(pos, this, (int) Math.min(Integer.MAX_VALUE, Math.max(1L, ticks)));
    }

    /* ------------------------------------------------------------------
     * 随机刻生长（仿小麦）
     * ------------------------------------------------------------------ */

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        int age = state.getValue(AGE);
        if (age >= AGE_TREE) return;                       // 成树后随机刻不再推进（第 4 阶只能靠时间）

        if (isTooCold(level, pos)) return;   // 寒带/极寒缺篝火 → 不生长
        int bound = TeaClimate.growthRollBound(level, pos);
        if (random.nextInt(bound) == 0) {
            growOneStage(level, pos, state, AGE_TREE);
        }
    }

    /**
     * 推进一阶，最多到 {@code maxAge}。
     *
     * <p>随机刻与骨粉走同一条路径，只是上限不同：骨粉传 {@link #AGE_TREE}，
     * 所以永远催不出第 4 阶（可采）。自检也直接调它。</p>
     *
     * @return 是否推进了
     */
    public boolean growOneStage(ServerLevel level, BlockPos pos, BlockState state, int maxAge) {
        int age = state.getValue(AGE);
        if (age >= maxAge) return false;
        level.setBlock(pos, state.setValue(AGE, age + 1), 3);
        return true;
    }

    /**
     * 时间到点：直接变成「成树且可采」。
     *
     * <p>哪怕随机刻运气差还停在幼体，长够时间也一次性补到满格 —— 这样
     * 「必须生长够时间」这条需求就由时间保证，而不是靠随机刻碰运气。</p>
     */
    public void ripen(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(AGE) != AGE_RIPE) {
            level.setBlock(pos, state.setValue(AGE, AGE_RIPE), 3);
        }
    }

    /* ------------------------------------------------------------------
     * 到点成熟：age 直接拉到 AGE_RIPE
     * ------------------------------------------------------------------ */

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }

        TeaBushBlockEntity be = bushEntityAt(level, pos);
        if (be == null) {
            // 没有方块实体（旧存档 / 刚放置）：补一个计时再继续
            startCountdown(level, pos, state);
            return;
        }

        boolean cold = isTooCold(level, pos);
        // 按 gameTime 结算，而不是「每 20 tick 减 1」——
        // 后者速率慢 20 倍，/tick sprint 也推不动（详见 TeaBushBlockEntity 的注释）
        boolean justRipened = be.advanceTo(level.getGameTime(), cold);

        if (justRipened) {
            ripen(level, pos, state);
            return;   // 成熟后不再排计划刻
        }

        // 每 20 tick（1 秒）结算一次；暂停时也继续排，才能感知「篝火回来了」
        level.scheduleTick(pos, this, 20);
    }

    /** 取该坐标的茶树方块实体。 */
    public static TeaBushBlockEntity bushEntityAt(net.minecraft.world.level.BlockGetter level,
                                                  BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TeaBushBlockEntity be ? be : null;
    }

    /**
     * 供自检调用的一次「成熟推进」。
     *
     * <p>{@link #tick} 是 protected，自检包不在同一包里，所以开这个口子。
     * 它<b>只是转发</b>，不改变任何语义 —— 这样自检跑的就是真实逻辑，
     * 而不是像以前那样绕过 tick 直接调 {@code ripen()} 导致假绿。</p>
     */
    public void tickForSelfCheck(BlockState state, ServerLevel level, BlockPos pos) {
        tick(state, level, pos, level.getRandom());
    }

    /**
     * 开始（或重新开始）一轮成熟计时。
     *
     * <p>把「还差多少 tick」写进方块实体，同时排一个 20 tick 后的计划刻来驱动它。
     * 这样倒计时既能在 HUD 上显示，也能在太冷时原地暂停。</p>
     */
    public static void startCountdown(ServerLevel level, BlockPos pos, BlockState state) {
        long total = TeaClimate.growthTicks(level, pos);
        int totalInt = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, total));
        TeaBushBlockEntity be = bushEntityAt(level, pos);
        if (be != null) {
            // 从当前 gameTime 起算
            be.startGrowing(totalInt, level.getGameTime());
        }
        // 20 tick 后开始驱动倒计时
        level.scheduleTick(pos, state.getBlock(), 20);
    }

    /** 方块实体：承载成熟倒计时。 */
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            BlockPos pos, BlockState state) {
        return new TeaBushBlockEntity(pos, state);
    }

    /* ------------------------------------------------------------------
     * 骨粉：只催到「成树」，催不出茶叶
     * ------------------------------------------------------------------ */

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return state.getValue(AGE) < AGE_TREE
                && !isTooCold(level, pos);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state,
                                     BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
                                BonemealSource source) {
        // 一次骨粉推进一阶；上限是 AGE_TREE —— 第 4 阶（可采）骨粉永远够不到
        if (isTooCold(level, pos)) return;   // 太冷，骨粉也催不动
        growOneStage(level, pos, state, AGE_TREE);
    }

    /* ------------------------------------------------------------------
     * 剪刀分枝
     * ------------------------------------------------------------------ */

    /**
     * 手持剪刀右键成熟茶树 → 分枝，得到 2 个新茶树种，老苗退回幼苗。
     *
     * <h2>需求</h2>
     * <p>「成熟可采茶的成熟茶树，如果不采茶直接用剪刀对茶树进行分枝，
     * 剪刀右键 1 下，得到 2 个新茶树苗，老苗退回到幼苗状态。」</p>
     *
     * <h2>为什么单独一个方法而不是塞进 useWithoutItem</h2>
     * <p>原版的分发顺序是：先 {@code useItemOn}（手上有东西），
     * 拿不到结果才落到 {@code useWithoutItem}（空手）。
     * 剪刀在手上时走的是这条，所以必须覆写它，
     * 否则剪刀会被当成普通右键（采摘）。</p>
     *
     * <h2>只在「可采」时生效</h2>
     * <p>age != AGE_RIPE 时返回 PASS，让剪刀保持原版行为。
     * 这条很关键：否则玩家拿剪刀点幼体茶树会莫名其妙消耗耐久。</p>
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(net.minecraft.world.item.Items.SHEARS)) {
            return InteractionResult.PASS;
        }
        if (state.getValue(AGE) != AGE_RIPE) {
            if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("teacraft.msg.branch_not_ripe"), true);
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        for (int i = 0; i < BRANCH_SEEDLINGS; i++) {
            ItemStack seedling = new ItemStack(TeaItems.TEA_BUSH_ITEM.get());
            if (!player.addItem(seedling)) {
                popResource(level, pos, seedling);
            }
        }

        stack.hurtAndBreak(1, player, hand == net.minecraft.world.InteractionHand.MAIN_HAND
                ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                : net.minecraft.world.entity.EquipmentSlot.OFFHAND);

        level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS,
                1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);

        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("teacraft.msg.branched", BRANCH_SEEDLINGS), true);
        }

        // 老苗退回幼苗，重新计时
        level.setBlock(pos, state.setValue(AGE, AGE_YOUNG), 3);
        if (level instanceof ServerLevel serverLevel) {
            startCountdown(serverLevel, pos, level.getBlockState(pos));
        }
        return InteractionResult.SUCCESS;
    }
    /* ------------------------------------------------------------------
     * 右键采摘
     * ------------------------------------------------------------------ */

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        int age = state.getValue(AGE);
        if (age != AGE_RIPE) {
            // 成树但没长够时间：给一句提示，别让玩家以为坏了
            if (!level.isClientSide() && age == AGE_TREE && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("teacraft.msg.not_ripe"), true);
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        TeaConfig cfg = TeaConfig.get();
        TeaOrigin origin = TeaClimate.originAt(level, pos);
        Item leaf = TeaItems.freshLeaf(origin);
        RandomSource random = level.getRandom();
        int count = cfg.minLeaves + random.nextInt(Math.max(1, cfg.maxLeaves - cfg.minLeaves + 1));
        for (int i = 0; i < count; i++) {
            ItemStack stack = new ItemStack(leaf);
            // 【关键】必须把产地写进组件。
            //
            // 物品类型（fresh_leaf_polar 之类）只决定名字和贴图；
            // 后续每一道工序都用 TeaComponents.originOf(stack, TEMPERATE) 读产地，
            // 读不到就退回温带 —— 于是所有茶都变成温带、品质 3、60 分钟。
            com.heishandashu.teacraft.core.TeaComponents.setOrigin(stack, origin);
            if (!player.addItem(stack)) {
                popResource(level, pos, stack);
            }
        }
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS,
                1.0F, 0.8F + random.nextFloat() * 0.4F);

        // 采完退回到「成树」（age 2），不是生长期。
        level.setBlock(pos, state.setValue(AGE, AGE_TREE), 3);
        if (level instanceof ServerLevel serverLevel) {
            startCountdown(serverLevel, pos, level.getBlockState(pos));
        }
        return InteractionResult.SUCCESS;
    }
}
