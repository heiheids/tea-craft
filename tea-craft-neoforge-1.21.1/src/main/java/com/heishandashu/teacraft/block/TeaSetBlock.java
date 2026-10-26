package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.TeaBrew;
import com.heishandashu.teacraft.item.GaiwanItem;
import com.heishandashu.teacraft.item.TeaDrinkItem;
import com.heishandashu.teacraft.item.TeaKettleItem;
import com.heishandashu.teacraft.item.TeaCupItem;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 茶盘 —— 1 个方块大小、3 像素高，盘上摆一只盖碗和三个方杯。
 *
 * <h2>状态怎么放</h2>
 * <p><b>方块状态</b>只放"决定渲染"的东西：盖碗在不在、每个杯子是 没有/空/有茶。
 * 因为模型是 multipart 按条件拼的，状态一变模型就变。</p>
 * <p><b>方块实体</b>只放看不见的数据：盖碗和每个杯子里的茶汤（茶类 + 品质）。</p>
 *
 * <h2>交互</h2>
 * <ul>
 *   <li>空手右键：先取走盖碗，没有盖碗就取最近的杯子</li>
 *   <li>手持盖碗右键杯子：倒一杯茶（盖碗份数 -1）</li>
 *   <li>手持盖碗右键空位：把盖碗放回盘上</li>
 *   <li>手持茶杯右键空位：把杯子放回盘上</li>
 *   <li>手持茶杯右键（不在茶盘上）：直接喝，已由 {@link TeaCupItem} 处理</li>
 * </ul>
 */
public class TeaSetBlock extends Block implements EntityBlock {

    /** 整个茶盘只有 3 像素高（3/16 格）。 */
    public static final double HEIGHT = 3.0;

    /** 盘上有没有盖碗。 */
    /**
     * 茶盘朝向 —— 盖碗对着玩家那一侧。
     *
     * <p>1.21.1 的 blockstate 用 variants + y 旋转即可（不像 26.3 的 multipart，
     * 那边 apply.y 不生效，只能发 4 份预旋转模型）。</p>
     */
    public static final EnumProperty<net.minecraft.core.Direction> FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    public static final BooleanProperty GAIWAN = BooleanProperty.create("gaiwan");

    /** 三个杯子的状态。 */
    public static final EnumProperty<CupState> CUP_A = EnumProperty.create("cup_a", CupState.class);
    public static final EnumProperty<CupState> CUP_B = EnumProperty.create("cup_b", CupState.class);
    public static final EnumProperty<CupState> CUP_C = EnumProperty.create("cup_c", CupState.class);

    /** 三个杯子的属性数组，按 a/b/c 索引，省掉重复的 if。 */
    public static final EnumProperty<CupState>[] CUPS =
            new EnumProperty[]{CUP_A, CUP_B, CUP_C};

    /** 三个杯子的中心（方块内坐标 0~16），用于"点到哪只杯子"。 */
    private static final double[][] CUP_CENTERS = {
            {2.8, 11.5}, {8.0, 11.9}, {13.2, 11.5}
    };

    /** 点击判定半径（方块内坐标，16 = 一格）。杯子外径 3.2、盖碗 5.6，取 2.4 折中。 */
    private static final double CUP_PICK_RADIUS = 2.4;

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, HEIGHT, 16.0);

    public TeaSetBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(GAIWAN, Boolean.FALSE)
                // 默认是<b>空盘</b>：没有盖碗、也没有杯子（NONE 而不是 EMPTY）。
                //
                // CupState 三个值：NONE=没有这只杯子、EMPTY=有杯子但空、FULL=有茶。
                // 之前默认写成 EMPTY，所以创造栏里拿出来的茶盘自带三个空杯，
                // 玩家看不到「空茶盘」。
                .setValue(CUP_A, CupState.NONE)
                .setValue(CUP_B, CupState.NONE)
                .setValue(CUP_C, CupState.NONE));
    }

    /** 杯子状态。名字必须和 blockstate 里 multipart 条件一致。 */
    public enum CupState implements StringRepresentable {
        NONE("none"), EMPTY("empty"), FULL("full");

        private final String name;

        CupState(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GAIWAN, CUP_A, CUP_B, CUP_C);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    /** 放置时保持默认（盖碗 + 三个空杯）。 */
    /**
     * 放置时朝向玩家。
     *
     * <p>茶盘的「正面」是玩家自己这一侧 —— 放下后盖碗对着自己，
     * 三只杯子在前方一字排开，泡茶时的操作视角最顺。</p>
     *
     * <p>{@code getHorizontalDirection()} 是玩家<b>看向</b>的方向，
     * 取反得到「朝向玩家」，即 {@code getOpposite()}。</p>
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState base = defaultBlockState();
        ItemStack stack = context.getItemInHand();

        // 从「拿起时存下的组件」还原盘上的内容。
        // 有记录就用记录的；没有（创造栏新拿的）就保持默认的空盘。
        net.minecraft.core.Direction facing =
                context.getHorizontalDirection().getOpposite();
        if (!facing.getAxis().isHorizontal()) {
            facing = net.minecraft.core.Direction.NORTH;
        }
        base = base.setValue(FACING, facing);

        Boolean gaiwan = stack.get(
                com.heishandashu.teacraft.core.TeaComponents.SET_GAIWAN.get());
        if (gaiwan != null) {
            base = base.setValue(GAIWAN, gaiwan)
                    .setValue(CUP_A, cupFromComponent(stack,
                            com.heishandashu.teacraft.core.TeaComponents.SET_CUP_A.get()))
                    .setValue(CUP_B, cupFromComponent(stack,
                            com.heishandashu.teacraft.core.TeaComponents.SET_CUP_B.get()))
                    .setValue(CUP_C, cupFromComponent(stack,
                            com.heishandashu.teacraft.core.TeaComponents.SET_CUP_C.get()));
        }
        return base;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeaSetBlockEntity(pos, state);
    }

    /**
     * 从组件里读一只杯子的状态；缺失或非法值当作 NONE。
     *
     * <p>组件里存的是 {@link CupState#getSerializedName()} 的字符串，
     * 玩家拿起茶盘时写进去，放下时读回来 —— 这样盘上的茶具不会丢。</p>
     */
    private static CupState cupFromComponent(
            ItemStack stack,
            net.minecraft.core.component.DataComponentType<String> type) {
        String s = stack.get(type);
        if (s == null) return CupState.NONE;
        for (CupState cs : CupState.values()) {
            if (cs.getSerializedName().equals(s)) return cs;
        }
        return CupState.NONE;
    }

    /**
     * 放置完成后修正朝向。
     *
     * <p>实测 26.3 的放置路径里 {@code getStateForPlacement} <b>不一定被调用</b>
     * （烧水壶那次实测就没进）；而 {@code setPlacedBy} 在放置完成后必定回调。
     * 两个都写才稳。1.21.1 的路径更可靠，但保留双保险没有坏处。</p>
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;

        BlockState fixed = state;

        // ① 盘上内容：以物品组件为准。
        //
        // 【这是「放回去茶具被吞掉」的根因】pickUp() 会把盖碗/杯子状态写进物品组件
        // （set_gaiwan / set_cup_*），但放置时如果没人读回来，方块就用默认的空盘状态
        // —— 茶具看着没了。注意新的空盘没有这些组件，此时保持默认（空盘）。
        Boolean gaiwan = stack.get(
                com.heishandashu.teacraft.core.TeaComponents.SET_GAIWAN.get());
        if (gaiwan != null) {
            fixed = fixed.setValue(GAIWAN, gaiwan)
                    .setValue(CUP_A, cupFromComponent(stack,
                            com.heishandashu.teacraft.core.TeaComponents.SET_CUP_A.get()))
                    .setValue(CUP_B, cupFromComponent(stack,
                            com.heishandashu.teacraft.core.TeaComponents.SET_CUP_B.get()))
                    .setValue(CUP_C, cupFromComponent(stack,
                            com.heishandashu.teacraft.core.TeaComponents.SET_CUP_C.get()));
        } else if (level.getBlockEntity(pos) instanceof TeaSetBlockEntity be) {
            // 兜底：物品上没有组件（旧版本拿起来的盘，或别的途径放下的）。
            // 数据还原（updateCustomBlockEntityTag）在这个钩子之前就跑完了，
            // 所以方块实体里如果有茶汤，就说明杯子/盖碗不该是空盘状态。
            boolean hasBrew = !be.gaiwanBrew().isEmpty() || !be.dryTea().isEmpty();
            for (int i = 0; i < 3; i++) {
                if (!be.cupBrew(i).isEmpty()) hasBrew = true;
            }
            if (hasBrew) {
                fixed = fixed.setValue(GAIWAN, Boolean.TRUE);
                for (int i = 0; i < 3; i++) {
                    if (!be.cupBrew(i).isEmpty()) {
                        fixed = fixed.setValue(CUPS[i], CupState.FULL);
                    }
                }
            }
        }

        // ② 朝向：一律朝玩家。不读物品里保存的 set_facing ——
        // 那会导致「拿起来一次之后永远同一个方向」。
        net.minecraft.core.Direction want = placer.getDirection().getOpposite();
        if (!want.getAxis().isHorizontal()) {
            want = net.minecraft.core.Direction.NORTH;
        }
        fixed = fixed.setValue(FACING, want);

        if (fixed != state) {
            level.setBlock(pos, fixed, Block.UPDATE_ALL);
            level.sendBlockUpdated(pos, fixed, fixed, Block.UPDATE_ALL);
        }
    }

    /* ---------------- 交互 ---------------- */

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        boolean holdingGaiwan = stack.getItem() instanceof GaiwanItem;
        boolean holdingCup = stack.getItem() instanceof TeaCupItem;
        if (!holdingGaiwan && !holdingCup) {
            // 先看是不是"放茶叶 / 注水"，这两个不需要手持茶具
            InteractionResult brewing = tryBrewing(stack, state, level, pos, player);
            if (brewing != InteractionResult.PASS) return item(brewing);
            // 其余情况交给 useWithoutItem（原版只有返回 TRY_WITH_EMPTY_HAND 才会接着调）
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return net.minecraft.world.ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TeaSetBlockEntity be)) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        int cup = cupIndexAt(hit, state, player);

        if (holdingGaiwan) {
            // ① 对着空杯子倒茶
            if (cup >= 0 && state.getValue(CUPS[cup]) == CupState.EMPTY) {
                TeaBrew brew = GaiwanItem.brewOf(stack);
                if (brew != null && !brew.isEmpty()) {
                    TeaBrew one = brew.pourOne();
                    be.setCupBrew(cup, new TeaBrew(brew.kind(), brew.quality(), 1).encode());
                    GaiwanItem.setBrew(stack, one.isEmpty() ? null : one);
                    level.setBlock(pos, state.setValue(CUPS[cup], CupState.FULL), Block.UPDATE_ALL);
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                    return net.minecraft.world.ItemInteractionResult.SUCCESS;
                }
            }
            // ② 把盖碗放回盘上
            if (!state.getValue(GAIWAN)) {
                TeaBrew brew = GaiwanItem.brewOf(stack);
                be.setGaiwanBrew(brew == null ? "" : brew.encode());
                // 干茶也要一起放回去，否则"放回盘子"会把茶叶弄丢
                TeaBrew dry = GaiwanItem.dryTeaOf(stack);
                be.setDryTea(dry == null ? "" : dry.encode());
                level.setBlock(pos, state.setValue(GAIWAN, Boolean.TRUE), Block.UPDATE_ALL);
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                stack.shrink(1);
                return net.minecraft.world.ItemInteractionResult.SUCCESS;
            }
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 手持茶杯：放到空位
        if (cup >= 0 && state.getValue(CUPS[cup]) == CupState.NONE) {
            TeaBrew brew = TeaCupItem.brewOf(stack);
            be.setCupBrew(cup, brew == null ? "" : brew.encode());
            level.setBlock(pos, state.setValue(CUPS[cup],
                    brew == null || brew.isEmpty() ? CupState.EMPTY : CupState.FULL), Block.UPDATE_ALL);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            stack.shrink(1);
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TeaSetBlockEntity be)) return InteractionResult.PASS;

        // 【拿整盘必须潜行】
        //
        // 以前是「点不到茶具 → 拿起整盘」，结果坐在茶椅上时视线与盘面齐平、
        // 射线打在侧面，坐标算不准，右键总是落到「拿起整盘」这个兜底分支，
        // 茶具根本取不下来。
        //
        // 现在把因果关系反过来：
        //   潜行右键  = 拿起整盘（明确的意图，不会误触）
        //   普通右键  = 只操作茶具（点不中就什么都不做）
        // 这样「整盘被拿走」永远需要玩家主动确认，坐标系精度不再是瓶颈。
        if (player.isShiftKeyDown()) {
            return pickUp(state, level, pos, player, be);
        }

        int cup = cupIndexAt(hit, state, player);

        // ① 点到杯子 → 取走这只杯子（不管盖碗还在不在）
        if (cup >= 0 && state.getValue(CUPS[cup]) != CupState.NONE) {
            return takeCup(state, level, pos, player, be, cup);
        }

        // ② 点到盖碗 → 取走盖碗
        if (state.getValue(GAIWAN) && isGaiwanAt(hit, state, player)) {
            return takeGaiwan(state, level, pos, player, be);
        }

        // ③ 点空了：什么都不做（要拿盘请潜行右键）
        return InteractionResult.PASS;
    }

    /**
     * 把整只茶盘连同盘上的东西一起拿起来。
     *
     * <p>盘里有什么要<b>记在物品上</b>：盖碗在不在、三只杯子各自的状态、
     * 朝向。只靠方块状态的话，拿起来再放下就全空了。</p>
     *
     * <p>存法：方块实体用 {@code saveWithFullMetadata} 写进物品的
     * {@code BLOCK_ENTITY_DATA}（它带 {@code id} 字段，原生放置时能自动还原）。</p>
     */
    private InteractionResult pickUp(BlockState state, Level level, BlockPos pos,
                                     Player player, TeaSetBlockEntity be) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        ItemStack stack = new ItemStack(TeaItems.TEA_SET_ITEM.get());

        // ① 方块状态：盖碗 + 三只杯子 + 朝向
        stack.set(com.heishandashu.teacraft.core.TeaComponents.SET_GAIWAN.get(),
                state.getValue(GAIWAN));
        stack.set(com.heishandashu.teacraft.core.TeaComponents.SET_CUP_A.get(),
                state.getValue(CUP_A).getSerializedName());
        stack.set(com.heishandashu.teacraft.core.TeaComponents.SET_CUP_B.get(),
                state.getValue(CUP_B).getSerializedName());
        stack.set(com.heishandashu.teacraft.core.TeaComponents.SET_CUP_C.get(),
                state.getValue(CUP_C).getSerializedName());
        stack.set(com.heishandashu.teacraft.core.TeaComponents.SET_FACING.get(),
                state.getValue(FACING).getSerializedName());


        // ② 图标要跟着内容变：盘上有任何茶具 → 换成「盘+茶具」的图标。
        //
        // 靠 CUSTOM_MODEL_DATA + item 模型谓词实现（见 TeaCraftClient）。
        // 不设这一项的话，拿着有茶具的盘还显示空盘图标，玩家看不出里面有没有东西。
        boolean hasAnyVessel = state.getValue(GAIWAN)
                || state.getValue(CUP_A) != CupState.NONE
                || state.getValue(CUP_B) != CupState.NONE
                || state.getValue(CUP_C) != CupState.NONE;
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                        hasAnyVessel ? 1 : 0));
        // ② 方块实体数据：盖碗里的干茶 / 茶汤、每只杯子里的茶汤
        CompoundTag beTag = be.saveWithFullMetadata(level.registryAccess());
        net.minecraft.world.item.BlockItem.setBlockEntityData(
                stack, be.getType(), beTag);

        if (!player.addItem(stack)) {
            popResource(level, pos, stack);
        }
        level.removeBlock(pos, false);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BAMBOO_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.4F);
        return InteractionResult.SUCCESS;
    }
    /**
     * 把 {@code InteractionResult} 转成 {@code ItemInteractionResult}。
     *
     * <p>1.21.1 的 {@code useItemOn} 要求返回 ItemInteractionResult，
     * 而辅助方法同时被 {@code useWithoutItem}（要 InteractionResult）复用，
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

    private static InteractionResult takeGaiwan(BlockState state, Level level, BlockPos pos,
                                                Player player, TeaSetBlockEntity be) {
        ItemStack gaiwan = new ItemStack(TeaItems.GAIWAN.get());
        TeaBrew brew = TeaBrew.decode(be.gaiwanBrew());
        if (brew != null) GaiwanItem.setBrew(gaiwan, brew);
        TeaBrew dry = TeaBrew.decode(be.dryTea());
        if (dry != null) GaiwanItem.setDryTea(gaiwan, dry);

        player.addItem(gaiwan);
        be.setGaiwanBrew("");
        be.setDryTea("");
        level.setBlock(pos, state.setValue(GAIWAN, Boolean.FALSE), Block.UPDATE_ALL);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }

    /**
     * 把某只杯子从盘上取下，连同杯里的茶汤一起交给玩家。
     */
    private static InteractionResult takeCup(BlockState state, Level level, BlockPos pos,
                                             Player player, TeaSetBlockEntity be, int cup) {
        ItemStack cupItem = new ItemStack(TeaItems.TEA_CUP.get());
        TeaBrew brew = TeaBrew.decode(be.cupBrew(cup));
        if (brew != null) TeaCupItem.setBrew(cupItem, brew);
        player.addItem(cupItem);
        be.setCupBrew(cup, "");
        level.setBlock(pos, state.setValue(CUPS[cup], CupState.NONE), Block.UPDATE_ALL);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }

    /**
     * 放茶叶 / 注水 —— 泡茶的两步。
     *
     * <p>顺序：先把成品茶放进盖碗（记下茶类与品质），再倒水，才得到茶汤。
     * 茶汤固定 3 杯的量，倒一杯少一杯。</p>
     *
     * <p>只在<b>盖碗在盘上</b>时才允许，避免"对着空盘倒水"这种怪操作。</p>
     */
    private InteractionResult tryBrewing(ItemStack stack, BlockState state, Level level,
                                         BlockPos pos, Player player) {
        if (!state.getValue(GAIWAN)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TeaSetBlockEntity be)) return InteractionResult.PASS;

        // 手持烧水壶：这是「倒热水」的意图，无论成不成功都**不能再往下走到取物**。
        // 之前失败时返回 PASS → 外层落到 TRY_WITH_EMPTY_HAND → useWithoutItem
        // 把盖碗拿走，就是「拿着热水壶右键盖碗，盖碗进背包」的原因。
        if (stack.getItem() instanceof TeaKettleItem) {
            if (!TeaKettleItem.hasHotWater(stack)) {
                actionBar(player, "teacraft.msg.kettle_not_hot");
                return InteractionResult.SUCCESS;
            }
            if (!pourHotWater(stack, state, level, pos, player, be)) {
                actionBar(player, "teacraft.msg.kettle_need_leaves");
            }
            return InteractionResult.SUCCESS;
        }

        // ① 放茶叶：手持成品茶。可以连续投多份，最多 3 份（对应 3 只杯子）。
        if (stack.getItem() instanceof TeaDrinkItem) {
            if (!be.gaiwanBrew().isEmpty()) return InteractionResult.PASS;
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            com.heishandashu.teacraft.core.TeaKind kind =
                    com.heishandashu.teacraft.effect.TeaEffects.kindOf(id.getPath());
            if (kind == null) return InteractionResult.PASS;
            int quality = TeaDrinkItem.qualityOf(stack);

            String[] cur = be.dryTea().split("\\|");
            int count = 0;
            if (cur.length >= 3 && kind.id().equals(cur[0])) {
                try {
                    count = Integer.parseInt(cur[2]);
                } catch (NumberFormatException ignored) {
                    count = 0;
                }
            } else if (!be.dryTea().isEmpty()) {
                // 换了别的茶：不允许混投，先把旧的取出来太复杂，直接拒绝
                return InteractionResult.PASS;
            }
            if (count >= MAX_LEAVES) return InteractionResult.PASS;

            be.setDryTea(kind.id() + "|" + quality + "|" + (count + 1));
            stack.shrink(1);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.4F);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            return InteractionResult.SUCCESS;
        }

        // ② 注水：水桶 / 水瓶（烧水壶已在上面单独处理）
        boolean waterBucket = stack.is(net.minecraft.world.item.Items.WATER_BUCKET);
        boolean waterBottle = false;
        if (stack.is(net.minecraft.world.item.Items.POTION)) {
            var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            waterBottle = contents != null
                    && contents.is(net.minecraft.world.item.alchemy.Potions.WATER);
        }
        if (!waterBucket && !waterBottle) return InteractionResult.PASS;
        if (be.dryTea().isEmpty() || !be.gaiwanBrew().isEmpty()) return InteractionResult.PASS;

        String[] parts = be.dryTea().split("\\|");
        if (parts.length < 3) return InteractionResult.PASS;
        com.heishandashu.teacraft.core.TeaKind kind =
                com.heishandashu.teacraft.core.TeaKind.byId(parts[0]);
        if (kind == null) return InteractionResult.PASS;
        int quality;
        int leaves;
        try {
            quality = Integer.parseInt(parts[1]);
            leaves = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return InteractionResult.PASS;
        }
        if (leaves <= 0) return InteractionResult.PASS;

        // 投入几份干茶就出几杯 —— 投入与产出对等
        be.setGaiwanBrew(new TeaBrew(kind, quality, leaves).encode());
        be.setDryTea("");
        stack.shrink(1);
        player.addItem(new ItemStack(waterBucket
                ? net.minecraft.world.item.Items.BUCKET
                : net.minecraft.world.item.Items.GLASS_BOTTLE));
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }

    /**
     * 用烧水壶里的开水泡茶。
     *
     * @return 是否成功泡上（失败时外层会提示"先放茶叶"，但<b>不会</b>让盖碗被拿走）
     */
    private static boolean pourHotWater(ItemStack stack, BlockState state, Level level,
                                        BlockPos pos, Player player, TeaSetBlockEntity be) {
        if (be.dryTea().isEmpty() || !be.gaiwanBrew().isEmpty()) return false;
        String[] parts = be.dryTea().split("\\|");
        if (parts.length < 3) return false;
        com.heishandashu.teacraft.core.TeaKind kind =
                com.heishandashu.teacraft.core.TeaKind.byId(parts[0]);
        if (kind == null) return false;
        int quality;
        int leaves;
        try {
            quality = Integer.parseInt(parts[1]);
            leaves = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (leaves <= 0) return false;

        be.setGaiwanBrew(new TeaBrew(kind, quality, leaves).encode());
        be.setDryTea("");
        // 壶不消耗，只是把"开水"用掉变成空壶
        TeaKettleItem.setHotWater(stack, false);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return true;
    }

    /** 给玩家发一条动作栏提示（Player 本身没有 sendSystemMessage）。 */
    private static void actionBar(Player player, String key) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key)
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
        }
    }
    /** 一只盖碗最多投几份干茶（正好对应盘上的 3 只杯子）。 */
    public static final int MAX_LEAVES = 3;

    /** 点击位置落在哪只杯子上；没落到杯子范围返回 -1。 */
    /**
     * 点击位置落在哪只杯子上；没落到杯子范围返回 -1。
     *
     * <p>模型里盖碗和杯子的位置是<b>按朝向 NORTH 画的</b>（盖碗在 z=6，
     * 杯子在 z≈11.5 一字排开）。方块放下后可能转向任意四个方向，
     * 所以必须把世界坐标换算回模型坐标系，否则「点左边那只杯子会取到右边那只」。</p>
     */
    private static int cupIndexAt(BlockHitResult hit, BlockState state, Player player) {
        double[] local = localXZ(hit, state, player);
        double lx = local[0];
        double lz = local[1];
        int best = -1;
        double bestDist = CUP_PICK_RADIUS * CUP_PICK_RADIUS;
        for (int i = 0; i < 3; i++) {
            double dx = lx - CUP_CENTERS[i][0];
            double dz = lz - CUP_CENTERS[i][1];
            double d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** 相对方块中心的偏移，按朝向转回模型坐标系（范围 0~16）。 */
    private static double[] localXZ(BlockHitResult hit, BlockState state, Player player) {
        var loc = hit.getLocation();
        double px = loc.x;
        double pz = loc.z;

        // 【为什么不能直接用 hit.getLocation()】
        //
        // 茶盘的碰撞箱是「满宽 x 3 像素高」的实心块。站着俯视时视线打到顶面，
        // 命中点的 x/z 就是正确位置；但坐在茶椅上时（眼高约 0.6 格，与盘面几乎
        // 齐平）视线是水平擦过去的，会先打到侧面 —— 侧面点的 x 或 z 被钉在
        // 方块边缘（0 或 16），于是算出来永远落在盘沿上，点不到任何杯子、也点
        // 不到盖碗，表现就是「右键都是拿起茶盘」。
        //
        // 解法：把「眼睛 -> 命中点」这条射线延长到盘面高度（y = HEIGHT），
        // 用那个交点当点击位置。这样无论俯视还是平视，落点都对应
        // 「玩家实际瞄准的盘面位置」。
        if (player != null) {
            double eyeY = player.getEyeY();
            double faceY = hit.getBlockPos().getY() + HEIGHT / 16.0;   // 盘面的世界高度
            double dy = loc.y - eyeY;
            if (Math.abs(dy) > 1.0E-4) {
                double t = (faceY - eyeY) / dy;      // 延长倍数
                if (t > 0 && t < 64.0) {
                    px = player.getX() + (loc.x - player.getX()) * t;
                    pz = player.getZ() + (loc.z - player.getZ()) * t;
                }
            }
        }

        // 相对方块中心的偏移，范围 -8~8
        double dx = (px - Math.floor(px)) * 16.0 - 8.0;
        double dz = (pz - Math.floor(pz)) * 16.0 - 8.0;
        return rotateToLocal(dx, dz, state.getValue(FACING));
    }

    /**
     * 把「相对方块中心的偏移」按朝向转回模型坐标系，再平移到 0~16。
     *
     * @param dx 相对中心的 x 偏移（-8~8）
     * @param dz 相对中心的 z 偏移（-8~8）
     * @return 模型坐标系下的 {x, z}，范围 0~16
     */
    public static double[] rotateToLocal(double dx, double dz,
                                        net.minecraft.core.Direction facing) {
        double lx;
        double lz;
        switch (facing) {
            case EAST -> { lx = dz;  lz = -dx; }   // 逆时针 90°
            case SOUTH -> { lx = -dx; lz = -dz; }  // 180°
            case WEST -> { lx = -dz; lz = dx; }    // 顺时针 90°
            default -> { lx = dx;  lz = dz; }      // NORTH
        }
        return new double[]{lx + 8.0, lz + 8.0};
    }

    /** 盖碗在方块里的占位中心 (x,z)，用于"点没点到盖碗"。 */
    private static final double[] GAIWAN_CENTER = {8.0, 6.0};

    /** 点击位置是否落在盖碗上（同样要按朝向换算）。 */
    private static boolean isGaiwanAt(BlockHitResult hit, BlockState state, Player player) {
        double[] local = localXZ(hit, state, player);
        double dx = local[0] - GAIWAN_CENTER[0];
        double dz = local[1] - GAIWAN_CENTER[1];
        return dx * dx + dz * dz <= CUP_PICK_RADIUS * CUP_PICK_RADIUS;
    }
}
