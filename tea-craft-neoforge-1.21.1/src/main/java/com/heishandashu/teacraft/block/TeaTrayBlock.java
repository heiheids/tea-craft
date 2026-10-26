package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.item.TeaFreshLeafItem;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * 晒盘：放在方块顶面上的一只竹编方盘，用来晒茶青。
 *
 * <h2>状态分两处存</h2>
 * <ul>
 *   <li><b>方块状态</b>：{@link #STAGE}（0 空 / 1 新茶 / 2 萎凋 / 3 摇青）与 {@link #COUNT}（0~4），
 *       只有它们影响模型；</li>
 *   <li><b>方块实体</b>（{@link TeaTrayBlockEntity}）：<b>产地</b>与<b>萎凋开始时间</b>。
 *       产地有 5 种、时间是连续量，塞进方块状态会让变体爆炸。</li>
 * </ul>
 *
 * <h2>交互</h2>
 * <ul>
 *   <li>右键放鲜叶（最多 4 份）：同盘只接受<b>同一产地</b>的茶，混了会退回并提示；</li>
 *   <li>新茶放进去 <b>5 分钟</b>自动萎凋（计划刻）；</li>
 *   <li><b>Shift + 右键</b>端起（潜行时原版会把方块交互转成 {@code TRY_WITH_EMPTY_HAND}，
 *       所以走 {@link #useWithoutItem}）。</li>
 * </ul>
 */
public class TeaTrayBlock extends Block implements EntityBlock {

    /** 盘里茶叶的状态：0 空 / 1 新茶 / 2 萎凋 / 3 摇青 / 4 杀青完成。 */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);
    /** 盘里茶叶的份数 0~4。 */
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 0, 32);

    public static final int STAGE_EMPTY = 0;
    public static final int STAGE_FRESH = 1;
    public static final int STAGE_WITHERED = 2;
    public static final int STAGE_SHAKEN = 3;
    /** 摇青后又放了 5 分钟 → 杀青完成，可以拿去揉捻。 */
    public static final int STAGE_KILLGREEN = 4;

    /** 一盘最多放 4 份茶叶。 */
    /** 一次最多放 32 份（需求：所有工作方块统一 32）。 */
    public static final int MAX_LEAVES = 32;

    /** 萎凋后取出的物品：单丛工艺第 2 阶段「晒青/萎凋」。 */
    /**
     * 萎凋产出：<b>三条路线共享</b>的摊晾萎凋叶。
     *
     * <p>第 2 步还不分路线 —— 鲜叶萎凋完，玩家拿它去摇青/炒茶/揉捻，才决定做哪种茶。</p>
     */
    public static final String WITHERED_OUTPUT_ID = com.heishandashu.teacraft.core.TeaStages.SHARED_WITHER_ID;

    /** 摇青完成后取出的物品 id：单丛工艺第 3 阶段「做青」。 */
    public static final String SHAKEN_OUTPUT_ID = "dancong_3_shaking";

    /** 杀青完成后取出的物品 id：单丛工艺第 4 阶段「杀青」，下一步进揉茶匾。 */
    public static final String KILLGREEN_OUTPUT_ID = "dancong_4_killgreen";

    /** 新茶变萎凋、以及摇青变杀青，各需要 5 分钟 = 6000 tick。 */
    /** 进度检查间隔（1 秒）。雨天要能及时暂停，所以不能一次排 5 分钟。 */
    public static final int CHECK_INTERVAL = 20;

    public static final int WITHER_TICKS = 5 * 60 * 20;
    public static final int KILLGREEN_TICKS = 5 * 60 * 20;

    /** 盘沿高 3px、中间下沉 1.5px（模型里也是这个比例）。 */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);

    public TeaTrayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, STAGE_EMPTY).setValue(COUNT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, COUNT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeaTrayBlockEntity(pos, state);
    }

    /** 取盘子的方块实体（可能为 null，例如刚破坏）。 */
    public static TeaTrayBlockEntity trayAt(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof TeaTrayBlockEntity tray ? tray : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    /* ---------------- 放茶 ---------------- */

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                         Player player, InteractionHand hand, BlockHitResult hit) {
        // 潜行优先 → 端起盘子
        if (player.isShiftKeyDown()) {
            return item(pickUp(state, level, pos, player));
        }
        // 把「萎凋叶」放回盘里 → 盘恢复成已萎凋，可以接着摇青（两条分支之间的衔接）
        if (stack.getItem() == TeaItems.stage(WITHERED_OUTPUT_ID)) {
            return item(putBack(state, level, pos, player, stack, STAGE_WITHERED));
        }
        if (stack.getItem() == TeaItems.stage(SHAKEN_OUTPUT_ID)) {
            return item(putBack(state, level, pos, player, stack, STAGE_SHAKEN));
        }
        if (!(stack.getItem() instanceof TeaFreshLeafItem leafItem)) {
            // 手里不是茶叶（含空手）→ 交给「空手交互」去取出。
            // 这里**必须**返回 TRY_WITH_EMPTY_HAND：原版就是靠这个返回值
            // 才会接着调 useWithoutItem；返回 PASS 会把这条路堵死（曾经就是这么坏的）。
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int count = state.getValue(COUNT);
        int stage = state.getValue(STAGE);

        // 【禁止混装】盘里已经有别的阶段的茶时，不许再放新叶。
        // 早期版本这里直接 setValue(STAGE, STAGE_FRESH)，会把「已萎凋/已摇青」的茶
        // 悄悄降级成新茶，看起来就是「不同阶段混在一个盘里」。
        if (count > 0 && stage != STAGE_FRESH) {
            hint(level, player, "teacraft.msg.tray_mixed_stage");
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        if (count >= MAX_LEAVES) {
            hint(level, player, "teacraft.msg.tray_full");
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }

        TeaOrigin incoming = leafItem.origin();
        TeaTrayBlockEntity tray = trayAt(level, pos);
        if (count > 0 && tray != null && tray.origin() != incoming) {
            // 不同产地的茶不能混在同一盘：萎凋出来的风味是要区分的
            hint(level, player, "teacraft.msg.tray_mixed_origin");
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }

        // 需求：「右键一下放入」—— 一次把手上整叠都放进去，最多放到 32 份。
        // 早期是每次只放 1 个，玩家得点 32 下，太累。
        int moved = movedInto(count, stack.getCount());
        stack.shrink(moved);

        BlockState next = state.setValue(COUNT, count + moved).setValue(STAGE, STAGE_FRESH);
        level.setBlock(pos, next, 3);
        if (tray != null) {
            tray.setOrigin(incoming);
            if (count == 0) {
                tray.beginWithering(level.getGameTime());
                level.scheduleTick(pos, this, CHECK_INTERVAL);
            }
            sync(level, pos, next);
        }
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }

    /**
     * 某个阶段取出来是什么物品 —— <b>唯一映射</b>，游戏与自检共用。
     *
     * <table border="1">
     *   <tr><th>阶段</th><th>取出</th><th>说明</th></tr>
     *   <tr><td>1 新茶</td><td>{@code fresh_leaf_<产地>}</td><td>还没萎凋，原样退还（产地由物品 id 决定）</td></tr>
     *   <tr><td>2 已萎凋</td><td>{@code dancong_2_sunwither}</td><td>晒青/萎凋分支，带产地组件</td></tr>
     *   <tr><td>3 已摇青</td><td>{@code dancong_3_shaking}</td><td>做青分支，带产地组件</td></tr>
     *   <tr><td>4 杀青完成</td><td>{@code dancong_4_killgreen}</td><td>下一步进揉茶匾揉捻</td></tr>
     * </table>
     *
     * <p>空盘返回 {@link ItemStack#EMPTY}。</p>
     */
    /** 盘里已有 {@code count} 份时，还能再放几份。 */
    public static int roomFor(int count) {
        return Math.max(0, MAX_LEAVES - count);
    }

    /**
     * 一次右键能放进去几份 —— <b>整叠</b>放入，最多放到 {@link #MAX_LEAVES}。
     *
     * <p>需求：「右键一下放入」。早期是每次 1 个，玩家得点 32 下。</p>
     */
    public static int movedInto(int already, int held) {
        return Math.min(Math.max(0, held), roomFor(already));
    }

    public static ItemStack outputForStage(int stage, TeaOrigin origin) {
        switch (stage) {
            case STAGE_FRESH:
                return new ItemStack(TeaItems.freshLeaf(origin));
            case STAGE_WITHERED: {
                ItemStack s = new ItemStack(TeaItems.stage(WITHERED_OUTPUT_ID));
                TeaComponents.setOrigin(s, origin);
                return s;
            }
            case STAGE_SHAKEN: {
                ItemStack s = new ItemStack(TeaItems.stage(SHAKEN_OUTPUT_ID));
                TeaComponents.setOrigin(s, origin);
                return s;
            }
            case STAGE_KILLGREEN: {
                ItemStack s = new ItemStack(TeaItems.stage(KILLGREEN_OUTPUT_ID));
                TeaComponents.setOrigin(s, origin);
                return s;
            }
            default:
                return ItemStack.EMPTY;
        }
    }

    /**
     * 把萎凋叶放回空盘，继续走摇青。
     *
     * <p>需求：「也可以把萎凋的茶叶放回茶盘继续摇青」。盘里必须先是空的
     * （或同产地），避免把两条分支的茶混在一起。</p>
     */
    /**
     * 把 {@code InteractionResult} 转成 {@code ItemInteractionResult}。
     *
     * <p>1.21.1 里 {@code useItemOn} 要求返回 ItemInteractionResult，
     * 而这些辅助方法同时被 {@code useWithoutItem}（要 InteractionResult）复用，
     * 所以统一保留 InteractionResult，只在 useItemOn 里转一次。</p>
     */
    private static net.minecraft.world.ItemInteractionResult item(InteractionResult r) {
        if (r == InteractionResult.SUCCESS) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        if (r == InteractionResult.CONSUME) {
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }
        if (r == InteractionResult.FAIL) {
            return net.minecraft.world.ItemInteractionResult.FAIL;
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private InteractionResult putBack(BlockState state, Level level, BlockPos pos,
                                      Player player, ItemStack stack, int targetStage) {
        int count = state.getValue(COUNT);
        int stage = state.getValue(STAGE);
        // 同一种茶可以继续加满到 32；只有"阶段/种类不同"才拒绝。
        // 单丛的摇青叶放回时盘里已经是这个阶段，不该被拦。
        if (stage != STAGE_EMPTY && stage != targetStage) {
            hint(level, player, "teacraft.msg.tray_mixed_stage");
            return InteractionResult.SUCCESS;
        }
        if (count >= MAX_LEAVES) {
            hint(level, player, "teacraft.msg.tray_full");
            return InteractionResult.SUCCESS;
        }
        TeaOrigin incoming = TeaComponents.originOf(stack, TeaOrigin.TEMPERATE);
        TeaTrayBlockEntity tray = trayAt(level, pos);
        if (count > 0 && tray != null && tray.origin() != incoming) {
            hint(level, player, "teacraft.msg.tray_mixed_origin");
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // 需求：「右键一下放入」—— 整叠放入（这里也一样，之前漏了这条路径）
        int moved = movedInto(count, stack.getCount());
        stack.shrink(moved);
        BlockState next = state.setValue(COUNT, count + moved).setValue(STAGE, targetStage);
        level.setBlock(pos, next, 3);
        if (tray != null) {
            tray.setOrigin(incoming);
            tray.beginWithering(level.getGameTime());
            sync(level, pos, next);
        }
        armTimer(level, pos, next);
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.6F, 1.1F);
        return InteractionResult.SUCCESS;
    }

    /**
     * 端起盘子。
     *
     * <p><b>只有潜行才端</b>：早期版本是「空手右键就端起」，玩家拿茶叶对满盘右键时会误触，
     * 盘子当场消失。</p>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return pickUp(state, level, pos, player);      // 潜行 = 端起整只盘
        }
        // 任何阶段、只要盘里有茶，空手右键就能取出来（新茶阶段也一样）
        if (state.getValue(COUNT) > 0) {
            return collectProcessed(state, level, pos, player);
        }
        return InteractionResult.PASS;
    }

    /**
     * 摇青完成后把茶青取出来 —— <b>产地在这一步从方块实体流进物品</b>。
     *
     * <p>单丛工艺里「做青」是第 3 阶段，所以产出 {@code dancong_3_shaking}；
     * 盘里放了几份就产出几份，并带上这盘茶的产地。取完盘子变空。</p>
     */
    private InteractionResult collectProcessed(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        int count = Math.max(1, state.getValue(COUNT));
        int stage = state.getValue(STAGE);
        TeaTrayBlockEntity tray = trayAt(level, pos);
        TeaOrigin origin = tray == null ? TeaOrigin.TEMPERATE : tray.origin();

        ItemStack out = outputForStage(stage, origin);
        if (out.isEmpty()) {
            return InteractionResult.PASS;
        }
        out.setCount(count);
        if (!player.addItem(out)) {
            popResource(level, pos, out);
        }

        BlockState next = state.setValue(COUNT, 0).setValue(STAGE, STAGE_EMPTY);
        level.setBlock(pos, next, 3);
        if (tray != null) {
            tray.clearAll();
            sync(level, pos, next);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult pickUp(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        int count = state.getValue(COUNT);
        int stage = state.getValue(STAGE);
        TeaTrayBlockEntity tray = trayAt(level, pos);

        ItemStack stack = new ItemStack(TeaItems.TEA_TRAY_ITEM.get());
        stack.set(TeaComponents.TRAY_COUNT, count);
        stack.set(TeaComponents.TRAY_STAGE, stage);
        stack.set(TeaComponents.TRAY_SHAKES, 0);
        stack.set(TeaComponents.TRAY_ORIGIN, tray == null ? TeaOrigin.TEMPERATE.id() : tray.origin().id());
        // 让手上的图标跟着内容变（见 assets/teacraft/items/tea_tray.json）
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(stage));

        if (!player.addItem(stack)) {
            popResource(level, pos, stack);
        }
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.BAMBOO_BREAK, SoundSource.BLOCKS, 0.5F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    /* ---------------- 计时：新茶→萎凋，摇青→杀青 ---------------- */

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (level.isClientSide()) return;
        if (oldState.is(this)) return;
        armTimer(level, pos, state);
    }

    /**
     * 按当前阶段排队下一次自动转变。
     *
     * <p>两段都是 5 分钟：</p>
     * <ul>
     *   <li>新茶 → 萎凋（晒青）；</li>
     *   <li><b>摇青 → 杀青</b>（需求：已经摇青的继续放在茶盘里 5 分钟后完成杀青）。</li>
     * </ul>
     * <p>用计划刻而不是随机刻：计划刻会随区块存档一起保存，退出重进不丢进度。</p>
     */
    public void armTimer(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(COUNT) <= 0) return;
        int stage = state.getValue(STAGE);
        // 记下起点：悬停面板的进度条要读它（杀青同样需要，不然只能干等 5 分钟没反馈）
        TeaTrayBlockEntity tray = trayAt(level, pos);
        if (tray != null) {
            tray.beginWithering(level.getGameTime());
            sync(level, pos, state);
        }
        if (stage == STAGE_FRESH || stage == STAGE_SHAKEN) {
            // 一律用短间隔轮询：雨天要能立刻停下来
            level.scheduleTick(pos, this, CHECK_INTERVAL);
        }
    }

    /**
     * 这一刻能不能晾晒。
     *
     * <p>需求：「<b>露天情况下，雨天晾晒进度停止</b>」。</p>
     *
     * <ul>
     *   <li>{@code canSeeSky} —— 判断是否<b>露天</b>（头顶没有方块遮挡）；</li>
     *   <li>{@code isRaining} —— 该位置是否真在下雨（{@code isRainingAt} 已包含露天判定，
     *       但这里显式再判一次，方便将来加"屋檐下也能晾"之类的规则）。</li>
     * </ul>
     *
     * @return true 表示当前<b>可以</b>推进晾晒
     */
    public static boolean canDryAt(Level level, BlockPos pos) {
        if (!level.isRaining()) return true;
        boolean exposed = level.canSeeSky(pos.above());
        if (!exposed) return true;               // 有遮挡（屋檐下）→ 淋不到，照常晾
        return !level.isRainingAt(pos.above());  // 露天 + 下雨 → 停
    }

    /**
     * 周期性检查。
     *
     * <p>雨天时<b>把开始时间往后推</b>，进度条就会停住而不是继续走 ——
     * 这比"另存一个暂停标记"简单，而且读进度的地方（HUD）不用改。</p>
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);
        if (stage != STAGE_FRESH && stage != STAGE_SHAKEN) return;

        int needed = stage == STAGE_FRESH ? WITHER_TICKS : KILLGREEN_TICKS;
        TeaTrayBlockEntity tray = trayAt(level, pos);
        if (tray == null) return;

        if (!canDryAt(level, pos)) {
            // 下雨：把起点顺延，等价于"进度暂停"
            tray.beginWithering(tray.witherStart() + CHECK_INTERVAL);
            sync(level, pos, state);
            level.scheduleTick(pos, this, CHECK_INTERVAL);
            return;
        }

        long elapsed = level.getGameTime() - tray.witherStart();
        if (elapsed >= needed) {
            advanceOneStage(level, pos, state,
                    stage == STAGE_FRESH ? STAGE_WITHERED : STAGE_KILLGREEN);
        } else {
            level.scheduleTick(pos, this, CHECK_INTERVAL);
        }
    }

    /** 把新茶变成萎凋（保留旧名，自检在用）。 */
    public void witherNow(ServerLevel level, BlockPos pos, BlockState state) {
        advanceOneStage(level, pos, state, STAGE_WITHERED);
    }

    /** 摇青叶放够时间 → 杀青完成。 */
    public void killGreenNow(ServerLevel level, BlockPos pos, BlockState state) {
        advanceOneStage(level, pos, state, STAGE_KILLGREEN);
    }

    /** 只有当前阶段等于 {@code from} 时才推进到 {@code to}，避免计划刻串场。 */
    private void advanceOneStage(ServerLevel level, BlockPos pos, BlockState state, int to) {
        int stage = state.getValue(STAGE);
        int from = to - 1;
        if (stage != from) return;
        BlockState next = state.setValue(STAGE, to);
        level.setBlock(pos, next, 3);
        sync(level, pos, next);
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.3F, 0.9F);
    }

    /** 方块实体数据变化后主动推给客户端，否则悬停面板读到的是旧值。 */
    private static void sync(Level level, BlockPos pos, BlockState state) {
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static void hint(Level level, Player player, String key) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key), true);
        }
    }

    /* ---------------- 掉落 ---------------- */

    /**
     * 被挖掉时把「盘 + 里面所有茶叶」一起掉出来。
     *
     * <p>战利品表故意留空（{@code "pools": []}），掉落全部在这里手写，
     * 否则会「代码掉一个 + 战利品表再掉一个」。</p>
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()) {
            int count = state.getValue(COUNT);
            int stage = state.getValue(STAGE);
            TeaTrayBlockEntity tray = trayAt(level, pos);

            ItemStack stack = new ItemStack(TeaItems.TEA_TRAY_ITEM.get());
            stack.set(TeaComponents.TRAY_COUNT, count);
            stack.set(TeaComponents.TRAY_STAGE, stage);
            stack.set(TeaComponents.TRAY_SHAKES, 0);
            stack.set(TeaComponents.TRAY_ORIGIN, tray == null ? TeaOrigin.TEMPERATE.id() : tray.origin().id());
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(stage));
            popResource(level, pos, stack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
