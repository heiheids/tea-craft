package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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

/**
 * 揉茶匾 —— 单丛工艺里「揉捻 → 发酵」这一段用的竹匾。
 *
 * <h2>交互约定（和晒盘刻意不同）</h2>
 * <table border="1">
 *   <tr><th>手上</th><th>右键</th><th>结果</th></tr>
 *   <tr><td>杀青叶</td><td>普通右键</td><td>放入匾里</td></tr>
 *   <tr><td>任意 / 空手</td><td><b>潜行 + 右键</b></td><td><b>揉捻</b>（不是拿起来）</td></tr>
 *   <tr><td>空手</td><td>普通右键</td><td>把茶取出来</td></tr>
 * </table>
 *
 * <p>需求原文：「揉茶匾放在地上，右键放入杀青过的茶，蹲下右键揉捻，不是拿起来，3 次过后，
 * 完成揉捻。然后进入发酵阶段……贴图自动换成这个。」</p>
 *
 * <p>所以这里<b>没有</b>「端起整只匾」的操作 —— 潜行键被揉捻占用了。要搬走请直接挖掉。</p>
 */
public class TeaRollingTrayBlock extends Block implements EntityBlock {

    /** 匾里茶叶的状态：0 空 / 1 杀青叶 / 2 揉捻完成 / 3 发酵中。 */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
    /** 份数 0~4。 */
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 0, 32);

    public static final int STAGE_EMPTY = 0;
    public static final int STAGE_KILLGREEN = 1;
    public static final int STAGE_ROLLED = 2;
    public static final int STAGE_FERMENTED = 3;

    /** 揉茶匾一次最多放 32 份（需求：一次最多可放入 32 个茶）。 */
    public static final int MAX_LEAVES = 32;
    /** 揉够几次算完成。 */
    public static final int ROLLS_NEEDED = 3;

    /** 揉一次要按住多久：3 秒 = 60 tick（和摇青一致）。 */
    public static final int ROLL_TICKS = 3 * 20;

    /** 揉捻完成后，静置多久开始发酵（30 秒）。 */
    public static final int FERMENT_TICKS = 30 * 20;

    /** 取出映射：揉捻完成 → 第 5 阶段；发酵完成 → 第 6 阶段。 */
    /**
     * 按<b>输入物品</b>决定揉完的产出 —— 三种茶走同一台揉茶匾。
     *
     * <pre>
     *   共享萎凋叶    → black_3_rolling   （揉捻 → 红茶路线）
     *   绿茶杀青叶    → green_4_rolling   （炒茶之后揉捻 → 绿茶路线）
     *   单丛杀青叶    → dancong_5_rolling （摇青杀青之后揉捻 → 单丛路线）
     * </pre>
     */
    /** 需要「揉 3 次」的输入 → 揉完的产出。 */
    private static final java.util.Map<String, String> ROLLED_BY_INPUT = java.util.Map.of(
            "spread_2_withered", "black_3_rolling",     // 红茶：萎凋叶 → 揉捻
            "green_3_killgreen", "green_4_rolling");     // 绿茶：杀青叶 → 揉捻

    /** 揉完再发酵的（红茶）。 */
    private static final java.util.Map<String, String> FERMENTED_BY_INPUT = java.util.Map.of(
            "black_3_rolling", "black_4_fermenting");

    /**
     * <b>不揉捻、直接发酵</b>的输入 → 发酵产出。
     *
     * <p>需求：单丛是「杀青 → 发酵（揉茶匾）→ 碳焙」，<b>没有揉捻工序</b>。
     * 所以单丛杀青叶放进揉茶匾就直接开始发酵。</p>
     */
    private static final java.util.Map<String, String> DIRECT_FERMENT = java.util.Map.of(
            "dancong_4_killgreen", "dancong_6_roasting");

    public static final String ROLLED_OUTPUT_ID = "dancong_5_rolling";
    public static final String FERMENTED_OUTPUT_ID = "dancong_6_roasting";

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);

    public TeaRollingTrayBlock(Properties properties) {
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

    public static TeaTrayBlockEntity trayAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TeaTrayBlockEntity t ? t : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
                                     net.minecraft.world.level.ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    /* ---------------- 放茶 ---------------- */

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                         Player player, InteractionHand hand, BlockHitResult hit) {
        // 只有「拿着杀青叶 + 没潜行」才是放入；潜行一律交给揉捻（见 useWithoutItem）。
        // 单丛的杀青叶来自晒盘，绿茶的杀青叶来自炒茶锅 —— 两种都收。
        if (!player.isShiftKeyDown() && isKillGreenLeaf(stack)) {
            return insert(state, level, pos, player, stack);
        }
        // 其余情况（含空手）必须回 TRY_WITH_EMPTY_HAND，原版才会继续调 useWithoutItem。
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    private InteractionResult insert(BlockState state, Level level, BlockPos pos, Player player, ItemStack stack) {
        int count = state.getValue(COUNT);
        int stage = state.getValue(STAGE);

        // 禁止混装，但<b>同一种输入</b>可以继续加满到 32。
        // 之前这里用 stage != STAGE_KILLGREEN 判断，而单丛放进来就变成
        // STAGE_ROLLED（直接发酵），于是第二把茶全被拒 —— 表现为"放不满 32 个"。
        TeaTrayBlockEntity trayNow = trayAt(level, pos);
        String incomingKind = stageIdOf(stack);
        if (count > 0 && trayNow != null && !incomingKind.equals(trayNow.kind())) {
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

        String inputId = stageIdOf(stack);
        boolean directFerment = DIRECT_FERMENT.containsKey(inputId);

        // 需求：「右键一下放入」—— 一次把手上整叠都放进去，最多 32 份。
        int moved = movedInto(count, stack.getCount());
        stack.shrink(moved);

        // 单丛（DIRECT_FERMENT）没有揉捻工序：放进来就直接进入发酵阶段，
        // 其余茶先停在「杀青叶」等玩家揉 3 次。
        int newStage = directFerment ? STAGE_ROLLED : STAGE_KILLGREEN;
        BlockState next = state.setValue(COUNT, count + moved).setValue(STAGE, newStage);
        level.setBlock(pos, next, 3);
        if (tray != null) {
            tray.setOrigin(incoming);
            tray.setRolls(0);
            // 记下输入是哪一种茶：产出按输入映射，不能写死成单丛
            tray.setKind(inputId);
            if (directFerment && count == 0) {
                // 第一批开始发酵计时
                tray.beginWithering(level.getGameTime());
                level.scheduleTick(pos, this, FERMENT_TICKS);
            }
            sync(level, pos, next);
        }
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.6F, 1.1F);
        return InteractionResult.SUCCESS;
    }

    /* ---------------- 揉捻 / 取出 ---------------- */

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return roll(state, level, pos, player);
        }
        if (state.getValue(COUNT) > 0) {
            return takeOut(state, level, pos, player);
        }
        return InteractionResult.PASS;
    }

    /** 潜行 + 右键 = 揉一次。揉满 3 次完成揉捻，之后自动进入发酵。 */
    private InteractionResult roll(BlockState state, Level level, BlockPos pos, Player player) {
        int stage = state.getValue(STAGE);
        if (state.getValue(COUNT) <= 0) {
            hint(level, player, "teacraft.msg.rolling_empty");
            return InteractionResult.SUCCESS;
        }
        if (stage == STAGE_KILLGREEN) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            TeaTrayBlockEntity tray = trayAt(level, pos);
            if (tray == null) {
                return InteractionResult.SUCCESS;
            }
            // 已经揉过一次就拒绝重复开始：一次揉捻要按满 3 秒，中途不能叠加
            if (tray.rollStart() >= 0) {
                hint(level, player, "teacraft.msg.rolling_in_progress");
                return InteractionResult.SUCCESS;
            }
            tray.startRoll(level.getGameTime());
            level.scheduleTick(pos, this, ROLL_TICKS);
            sync(level, pos, state);
            level.playSound(null, pos, SoundEvents.BAMBOO_STEP, SoundSource.BLOCKS, 0.5F, 1.0F);
            hint(level, player, "teacraft.msg.rolling_started");
            return InteractionResult.SUCCESS;
        }
        if (stage == STAGE_ROLLED) {
            hint(level, player, "teacraft.msg.rolling_wait_ferment");
        } else {
            hint(level, player, "teacraft.msg.rolling_already_fermented");
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 取出映射 —— 和晒盘一样，游戏与自检共用这一份。
     *
     * <p>揉捻完成 → 第 5 阶段（揉捻叶）；发酵完成 → 第 6 阶段（焙火前）。</p>
     */
    /** 兼容旧调用：默认按单丛杀青叶算（自检与历史逻辑用）。 */
    public static ItemStack outputForStage(int stage, TeaOrigin origin) {
        return outputForStage(stage, origin, "dancong_4_killgreen");
    }

    /**
     * 取出映射（按茶类区分）。
     *
     * <pre>
     *   单丛：揉完 → dancong_5_rolling；发酵完 → dancong_6_roasting
     *   绿茶：揉完 → green_4_rolling（绿茶不发酵，揉完直接回炒锅）
     * </pre>
     */
    public static ItemStack outputForStage(int stage, TeaOrigin origin, String inputId) {
        String id = switch (stage) {
            // 揉捻完成：直接发酵的（单丛）此时还没发酵完 → 退回原叶
            case STAGE_ROLLED -> ROLLED_BY_INPUT.containsKey(inputId)
                    ? ROLLED_BY_INPUT.get(inputId)
                    : (DIRECT_FERMENT.containsKey(inputId) ? inputId : null);
            case STAGE_FERMENTED -> {
                if (DIRECT_FERMENT.containsKey(inputId)) yield DIRECT_FERMENT.get(inputId);
                String rolled = ROLLED_BY_INPUT.get(inputId);
                if (rolled == null) yield null;
                // 没进发酵表的（绿茶）→ 保持揉捻叶，不发酵
                yield FERMENTED_BY_INPUT.getOrDefault(rolled, rolled);
            }
            default -> null;
        };
        if (id == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(TeaItems.stage(id));
        TeaComponents.setOrigin(s, origin);
        return s;
    }

    /** 一次右键能放进去几份（整叠，最多 32）。 */
    public static int movedInto(int already, int held) {
        return Math.min(Math.max(0, held), Math.max(0, MAX_LEAVES - already));
    }

    /** 揉捻产出（按输入物品查表）。 */
    public static String rolledOutputFor(String inputId) {
        return ROLLED_BY_INPUT.get(inputId);
    }

    /** 发酵产出（没有就是这条路线不发酵）。 */
    public static String fermentedOutputFor(String inputId) {
        String rolled = ROLLED_BY_INPUT.get(inputId);
        return rolled == null ? null : FERMENTED_BY_INPUT.get(rolled);
    }

    /** 这个物品能不能放进揉茶匾。 */
    public static boolean isKillGreenLeaf(ItemStack stack) {
        String id = stageIdOf(stack);
        return ROLLED_BY_INPUT.containsKey(id) || DIRECT_FERMENT.containsKey(id);
    }

    /** 取阶段 id（不是阶段物品就返回空串）。 */
    public static String stageIdOf(ItemStack stack) {
        if (stack.getItem() instanceof com.heishandashu.teacraft.item.TeaStageItem s) {
            return s.stage().id();
        }
        return "";
    }


    private InteractionResult takeOut(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        int stage = state.getValue(STAGE);
        TeaTrayBlockEntity tray = trayAt(level, pos);
        TeaOrigin origin = tray == null ? TeaOrigin.TEMPERATE : tray.origin();

        String inputId = tray == null ? "" : tray.kind();
        ItemStack out = outputForStage(stage, origin, inputId);
        if (out.isEmpty()) {
            // 需求：「无论是否完成当前步骤都可取出，未完成时取出退回放入状态」。
            // 所以取不出成品时，原样退回当初放进去的那种物品（不吞玩家的茶）。
            String back = (inputId == null || inputId.isEmpty()) ? "dancong_4_killgreen" : inputId;
            out = new ItemStack(TeaItems.stage(back));
            TeaComponents.setOrigin(out, origin);
        }
        out.setCount(Math.max(1, state.getValue(COUNT)));
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

    /* ---------------- 揉完自动发酵 ---------------- */

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);

        // 一：揉捻计时结束 → 算揉了一次
        if (stage == STAGE_KILLGREEN) {
            TeaTrayBlockEntity tray = trayAt(level, pos);
            if (tray == null) return;
            if (tray.rollStart() < 0) return;          // 起点已经被清掉，说明这次作废
            tray.finishRoll();
            int rolls = tray.addRoll();
            sync(level, pos, state);
            level.playSound(null, pos, SoundEvents.BAMBOO_BREAK, SoundSource.BLOCKS,
                    0.6F, 0.9F + rolls * 0.06F);

            if (rolls >= ROLLS_NEEDED) {
                BlockState next = state.setValue(STAGE, STAGE_ROLLED);
                level.setBlock(pos, next, 3);
                // 记下起点：悬停面板要算进度
                tray.beginWithering(level.getGameTime());
                sync(level, pos, next);
                // 只有真的会发酵的才排计时：红茶发酵；绿茶揉完直接回炒茶锅。
                // 用 ferments() 统一判断，避免和 tick()/onPlace 三处逻辑走岔。
                if (ferments(tray.kind())) {
                    level.scheduleTick(pos, this, FERMENT_TICKS);
                }
                announce(level, pos, "teacraft.msg.rolling_done");
            } else {
                announce(level, pos, "teacraft.msg.rolling_progress." + rolls);
            }
            return;
        }

        // 二：发酵计时结束
        if (stage == STAGE_ROLLED) {
            if (!(trayAt(level, pos) instanceof TeaTrayBlockEntity tray)) return;
            if (!ferments(tray.kind())) return;

            BlockState next = state.setValue(STAGE, STAGE_FERMENTED);
            level.setBlock(pos, next, 3);
            sync(level, pos, next);
            // 贴图在这一刻换成「发酵完成」那张（见 blockstates/tea_rolling_tray.json）
            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.3F, 0.8F);
        }
    }

    /**
     * 这一批茶会不会发酵？<b>两条路径都要算</b>。
     *
     * <h2>为什么必须抽成一个方法</h2>
     * <p>揉茶匾上有两种茶会发酵，但入口完全不同：</p>
     * <ul>
     *   <li><b>先揉捻再发酵</b>（红茶、绿茶）：输入是萎凋叶，揉 3 次后
     *       变成 {@code *_rolling}，再查 {@link #FERMENTED_BY_INPUT}
     *       决定发不发酵。绿茶不在表里 → 不发酵。</li>
     *   <li><b>直接发酵</b>（单丛）：输入是杀青叶，一放进来就进
     *       {@code STAGE_ROLLED}，输入 id 登记在 {@link #DIRECT_FERMENT}。
     *       它<b>根本不在</b> {@code ROLLED_BY_INPUT} 里 —— 所以只看后者
     *       就会把单丛误判成「不发酵」，卡在发酵中永远出不来。</li>
     * </ul>
     *
     * <p>这个 bug 是修绿茶时引入的：当时只堵了「不该发酵的别发酵」，
     * 漏了「该发酵的两种入口」里少认了一种。</p>
     *
     * @param kind 盘里记下的输入物品 id（见 {@code tray.kind()}）
     */
    public static boolean ferments(String kind) {
        if (kind == null || kind.isEmpty()) return false;
        // 路径一：直接发酵（单丛杀青叶）
        if (DIRECT_FERMENT.containsKey(kind)) return true;
        // 路径二：揉捻后再发酵（红茶）；绿茶查不到 → false
        String rolled = ROLLED_BY_INPUT.get(kind);
        return rolled != null && FERMENTED_BY_INPUT.containsKey(rolled);
    }

    /** 没有具体玩家可指向时，用附近 8 格内的玩家播报。 */
    private static void announce(ServerLevel level, BlockPos pos, String key) {
        Component msg = Component.translatable(key);
        for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(pos).inflate(8))) {
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(msg, true);
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (level.isClientSide() || oldState.is(this)) return;
        // 揉捻完成 / 直接发酵的托盘重新加载时，恢复发酵计时。
        //
        // 【两处历史教训】
        // 1) 原来只检查 STAGE_ROLLED + COUNT>0，没看茶类 → 绿茶揉完放下也被排上
        //    计时，30 秒后变成「发酵完成」，而绿茶根本没有发酵工序。
        // 2) 后来加了茶类判断，但只查 FERMENTED_BY_INPUT → 单丛（走
        //    DIRECT_FERMENT、不在 ROLLED_BY_INPUT 里）被误判成不发酵，
        //    卡在发酵中永远出不来。
        // 所以统一走 ferments()：两条发酵路径都认，绿茶仍然被排除。
        if (state.getValue(STAGE) == STAGE_ROLLED && state.getValue(COUNT) > 0) {
            if (level.getBlockEntity(pos) instanceof TeaTrayBlockEntity tray
                    && ferments(tray.kind())) {
                level.scheduleTick(pos, this, FERMENT_TICKS);
            }
        }
    }

    /**
     * 自检用：跳过 30 秒等待，直接跑一次「发酵计时结束」的真实逻辑。
     *
     * <p><b>故意不在这里判断茶类</b> —— 它直接调用 {@link #tick}，走的正是
     * 游戏里计时到点的同一条代码路径（包括里面的 {@code ferments()} 检查）。
     * 早先这里是「无条件 SET 成 STAGE_FERMENTED」，等于绕开了判断，
     * 于是「单丛卡在发酵中出不来」这个 bug 在自检里完全看不出来。</p>
     */
    public void fermentNow(ServerLevel level, BlockPos pos, BlockState state) {
        tick(state, level, pos, level.getRandom());
    }

    /** 自检用：模拟「揉捻计时到点」的那一次 tick。 */
    public void rollTickNow(ServerLevel level, BlockPos pos, BlockState state) {
        tick(state, level, pos, level.getRandom());
    }

    private static void sync(Level level, BlockPos pos, BlockState state) {
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static void hint(Level level, Player player, String key) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable(key), true);
        }
    }
}
