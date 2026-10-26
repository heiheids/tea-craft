package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.TeaBrew;
import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.item.GaiwanItem;
import com.heishandashu.teacraft.item.TeaDrinkItem;
import com.heishandashu.teacraft.item.TeaKettleItem;
import com.heishandashu.teacraft.item.TeaCupItem;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 鑼剁洏 鈥斺€?1 涓柟鍧楀ぇ灏忋€? 鍍忕礌楂樸€?
 *
 * <p>鍚堟垚鍑烘潵鐨勬槸涓€涓?b>绌鸿尪鐩?/b>锛氱洊纰楀拰涓夊彧鏂规澂瑕佺帺瀹惰嚜宸辨斁涓婂幓锛?
 * 鐩樹笂涓嶄細鑷甫鑼跺叿銆?/p>
 *
 * <h2>鐘舵€佹€庝箞鏀?/h2>
 * <p><b>鏂瑰潡鐘舵€?/b>鍙斁"鍐冲畾娓叉煋"鐨勪笢瑗匡細鐩栫鍦ㄤ笉鍦ㄣ€佹瘡涓澂瀛愭槸 娌℃湁/绌?鏈夎尪銆?
 * 鍥犱负妯″瀷鏄?multipart 鎸夋潯浠舵嫾鐨勶紝鐘舵€佷竴鍙樻ā鍨嬪氨鍙樸€?/p>
 * <p><b>鏂瑰潡瀹炰綋</b>鍙斁鐪嬩笉瑙佺殑鏁版嵁锛氱洊纰楀拰姣忎釜鏉瓙閲岀殑鑼舵堡锛堣尪绫?+ 鍝佽川锛夈€?/p>
 *
 * <h2>浜や簰</h2>
 * <ul>
 *   <li>鎵嬫寔鐩栫鍙抽敭绌轰綅锛氭妸鐩栫鏀句笂鍘?/li>
 *   <li>鎵嬫寔鑼舵澂鍙抽敭绌轰綅锛氭妸鏉瓙鏀句笂鍘?/li>
 *   <li>鎵嬫寔鐩栫鍙抽敭鏉瓙锛氬€掍竴鏉尪锛堢洊纰椾唤鏁?-1锛?/li>
 *   <li>绌烘墜鍙抽敭鐩樻部锛?b>鎶婃暣鍙尪鐩樻嬁璧锋潵</b>锛堢洏涓婄殑鑼跺叿鍜岃尪姹や竴璧峰甫璧帮級</li>
 *   <li>绌烘墜鍙抽敭鐩栫锛氬彇璧扮洊纰楋紙骞茶尪銆佽尪姹や竴璧峰甫锛?/li>
 *   <li>绌烘墜鍙抽敭鏉瓙锛氬彇璧伴偅鍙澂瀛?/li>
 *   <li>鎵嬫寔鑼舵澂鍙抽敭锛堜笉鍦ㄨ尪鐩樹笂锛夛細鐩存帴鍠濓紝宸茬敱 {@link TeaCupItem} 澶勭悊</li>
 * </ul>
 */
public class TeaSetBlock extends Block implements EntityBlock {

    /** 鏁翠釜鑼剁洏鍙湁 3 鍍忕礌楂橈紙3/16 鏍硷級銆?*/
    public static final double HEIGHT = 3.0;

    /** 鐩樹笂鏈夋病鏈夌洊纰椼€?*/
    public static final BooleanProperty GAIWAN = BooleanProperty.create("gaiwan");

    /**
     * 鏈濆悜锛堟按骞冲洓鍚戯級銆?
     *
     * <p>鍐冲畾妯″瀷閲岀洊纰?涓夊彧鏉瓙鎽嗗湪鍝竴渚с€傛斁缃椂鎸夌帺瀹舵湞鍚戣嚜鍔ㄨ瀹氥€?/p>
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 涓変釜鏉瓙鐨勭姸鎬併€?*/
    public static final EnumProperty<CupState> CUP_A = EnumProperty.create("cup_a", CupState.class);
    public static final EnumProperty<CupState> CUP_B = EnumProperty.create("cup_b", CupState.class);
    public static final EnumProperty<CupState> CUP_C = EnumProperty.create("cup_c", CupState.class);

    /** 涓変釜鏉瓙鐨勫睘鎬ф暟缁勶紝鎸?a/b/c 绱㈠紩锛岀渷鎺夐噸澶嶇殑 if銆?*/
    public static final EnumProperty<CupState>[] CUPS =
            new EnumProperty[]{CUP_A, CUP_B, CUP_C};

    /** 涓変釜鏉瓙鐨勪腑蹇冿紙鏂瑰潡鍐呭潗鏍?0~16锛夛紝鐢ㄤ簬"鐐瑰埌鍝彧鏉瓙"銆?*/
    private static final double[][] CUP_CENTERS = {
            {2.8, 11.5}, {8.0, 11.9}, {13.2, 11.5}
    };

    /** 鐐瑰嚮鍒ゅ畾鍗婂緞锛堟柟鍧楀唴鍧愭爣锛?6 = 涓€鏍硷級銆傛澂瀛愬寰?3.2銆佺洊纰?5.6锛屽彇 2.4 鎶樹腑銆?*/
    private static final double CUP_PICK_RADIUS = 2.4;

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, HEIGHT, 16.0);

    public TeaSetBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                // 榛樿鏄€岀┖鐩樸€嶏細鍚堟垚鍑烘潵鐨勫彧鏄竴涓尪鐩橈紝
                // 鐩栫鍜屾澂瀛愯鐜╁鑷繁鏀句笂鍘伙紙鑼跺叿浠ュ悗鍙︽湁鍒朵綔鏂瑰紡锛夈€?
                .setValue(GAIWAN, Boolean.FALSE)
                .setValue(CUP_A, CupState.NONE)
                .setValue(CUP_B, CupState.NONE)
                .setValue(CUP_C, CupState.NONE));
    }

    /** 鏉瓙鐘舵€併€傚悕瀛楀繀椤诲拰 blockstate 閲?multipart 鏉′欢涓€鑷淬€?*/
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

    /**
     * 鏀剧疆鏃舵湞鍚戠帺瀹躲€?
     *
     * <p>鑼剁洏鐨勩€屾闈€嶆槸鐜╁鑷繁杩欎竴渚?鈥斺€?鏀句笅鍚庣洊纰楀鐫€鑷繁銆?
     * 涓夊彧鏉瓙鍦ㄥ墠鏂逛竴瀛楁帓寮€锛岃繖鏍锋场鑼舵椂鐨勬搷浣滆瑙掓渶椤恒€?/p>
     *
     * <p>{@code getHorizontalDirection()} 鏄帺瀹?b>鐪嬪悜</b>鐨勬柟鍚戯紝
     * 鍙栧弽寰楀埌銆屾湞鍚戠帺瀹躲€嶏紝鍗?{@code getOpposite()}銆?/p>
     *
     * <p><b>娉ㄦ剰</b>锛氳繖涓柟娉曞湪 26.3 鐨勬斁缃矾寰勯噷<b>涓嶄竴瀹氳璋冪敤</b>
     * 锛堢儳姘村６閭ｆ瀹炴祴灏辨病杩涳級銆傜湡姝ｅ厹搴曠殑鏄笅闈㈢殑
     * {@link #setPlacedBy}锛屼袱涓兘鍐欐墠绋炽€?/p>
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState base = defaultBlockState();
        ItemStack stack = context.getItemInHand();

        // 浠庛€屾嬁璧锋椂瀛樹笅鐨勭粍浠躲€嶈繕鍘熺洏涓婄殑鍐呭銆?
        // 鏈夎褰曞氨鐢ㄨ褰曠殑锛屾病鏈夛紙鍒涢€犳爮鏂版嬁鐨勶級灏辨寜榛樿锛氭湁鐩栫 + 涓変釜绌烘澂銆?
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!facing.getAxis().isHorizontal()) {
            facing = Direction.NORTH;
        }

        base = base.setValue(FACING, facing);

        Boolean gaiwan = stack.get(com.heishandashu.teacraft.core.TeaComponents.SET_GAIWAN);
        if (gaiwan != null) {
            base = base.setValue(GAIWAN, gaiwan);
            base = base.setValue(CUP_A, cupFromComponent(stack,
                    com.heishandashu.teacraft.core.TeaComponents.SET_CUP_A));
            base = base.setValue(CUP_B, cupFromComponent(stack,
                    com.heishandashu.teacraft.core.TeaComponents.SET_CUP_B));
            base = base.setValue(CUP_C, cupFromComponent(stack,
                    com.heishandashu.teacraft.core.TeaComponents.SET_CUP_C));
        }
        return base;
    }

    /** 浠庣粍浠惰涓€鍙澂瀛愮殑鐘舵€侊紱缂哄け鎴栭潪娉曞€煎綋 NONE銆?*/
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
     * 鏀剧疆瀹屾垚鍚庡厹搴曚慨姝ｃ€?
     *
     * <p>杩欐槸<b>鐪熸蹇呭畾鐢熸晥</b>鐨勯偅涓挬瀛愶細26.3 鐨勬斁缃祦绋嬩細鍥炶皟
     * setPlacedBy锛岃€?getStateForPlacement 瀹炴祴鍙兘鏍规湰涓嶈繘銆?/p>
     *
     * <p>杩欓噷鍋氫袱浠朵簨锛?/p>
     * <ol>
     *   <li>鐩樹笂鍐呭锛堢洊纰?/ 涓夊彧鏉瓙锛夋寜鐗╁搧涓婄殑缁勪欢杩樺師 鈥斺€?
     *       getStateForPlacement 娌¤璋冪敤鏃讹紝杩欐槸鍞竴鐨勬満浼?/li>
     *   <li>鏈濆悜鎸夋斁缃€呬慨姝ｏ紙娌℃湁淇濆瓨鏈濆悜鏃讹級</li>
     * </ol>
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;

        BlockState fixed = state;

        // 鈶?鐩樹笂鍐呭锛氫互鐗╁搧缁勪欢涓哄噯锛堟柊鏀剧殑鐩樻病鏈夌粍浠讹紝淇濇寔榛樿锛?
        Boolean gaiwan = stack.get(com.heishandashu.teacraft.core.TeaComponents.SET_GAIWAN);
        if (gaiwan != null) {
            fixed = fixed.setValue(GAIWAN, gaiwan)
                    .setValue(CUP_A, cupFromComponent(stack,
                            com.heishandashu.teacraft.core.TeaComponents.SET_CUP_A))
                    .setValue(CUP_B, cupFromComponent(stack,
                            com.heishandashu.teacraft.core.TeaComponents.SET_CUP_B))
                    .setValue(CUP_C, cupFromComponent(stack,
                            com.heishandashu.teacraft.core.TeaComponents.SET_CUP_C));
        } else if (level.getBlockEntity(pos) instanceof TeaSetBlockEntity be) {
            // 鍏滃簳锛氱墿鍝佷笂娌℃湁缁勪欢锛堟棫鐗堟湰鎷胯捣鐨勭洏銆佹垨鍒殑鏂瑰紡鏀句笅鐨勶級锛?
            // 灏辩湅鏂瑰潡瀹炰綋閲屾湁娌℃湁鑼舵堡 鈥斺€?鏈夎尪灏辫鏄庢澂瀛?鐩栫涓嶈鏄粯璁ゆ牱瀛愩€?
            // 鏁版嵁杩樺師锛坲pdateCustomBlockEntityTag锛夊湪杩欎釜閽╁瓙涔嬪墠灏辫窇瀹屼簡銆?
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

        // (2) FACING: always face the player.
        //
        // Do NOT restore a saved-facing here. pickUp() writes set_facing into
        // the item, so honoring it on placement would make the tray land in the
        // SAME direction forever once it has been picked up once -- the player
        // could no longer turn it. Facing is decided when placing, not stored.
        Direction want = placer.getDirection().getOpposite();
        if (!want.getAxis().isHorizontal()) {
            want = Direction.NORTH;
        }
        fixed = fixed.setValue(FACING, want);

        if (fixed != state) {
            level.setBlock(pos, fixed, Block.UPDATE_ALL);
            // 鏂瑰潡鐘舵€佹湰璇ヨ嚜鍔ㄥ悓姝ワ紝浣嗗拰鐑ф按澹朵竴鏍疯ˉ涓€娆★紝閬垮厤瀹㈡埛绔繕鎷跨潃鏃х姸鎬?
            level.sendBlockUpdated(pos, fixed, fixed, Block.UPDATE_ALL);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeaSetBlockEntity(pos, state);
    }

    /* ---------------- 浜や簰 ---------------- */

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        boolean holdingGaiwan = stack.getItem() instanceof GaiwanItem;
        boolean holdingCup = stack.getItem() instanceof TeaCupItem;
        if (!holdingGaiwan && !holdingCup) {
            // 鍏堢湅鏄笉鏄?鏀捐尪鍙?/ 娉ㄦ按"锛岃繖涓や釜涓嶉渶瑕佹墜鎸佽尪鍏?
            InteractionResult brewing = tryBrewing(stack, state, level, pos, player);
            if (brewing != InteractionResult.PASS) return brewing;
            // 鍏朵綑鎯呭喌浜ょ粰 useWithoutItem锛堝師鐗堝彧鏈夎繑鍥?TRY_WITH_EMPTY_HAND 鎵嶄細鎺ョ潃璋冿級
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TeaSetBlockEntity be)) return InteractionResult.PASS;

        int cup = cupIndexAt(hit, state, player);

        if (holdingGaiwan) {
            // 鈶?瀵圭潃绌烘澂瀛愬€掕尪
            if (cup >= 0 && state.getValue(CUPS[cup]) == CupState.EMPTY) {
                TeaBrew brew = GaiwanItem.brewOf(stack);
                if (brew != null && !brew.isEmpty()) {
                    TeaBrew one = brew.pourOne();
                    be.setCupBrew(cup, new TeaBrew(brew.kind(), brew.quality(), 1).encode());
                    GaiwanItem.setBrew(stack, one.isEmpty() ? null : one);
                    var poured = state.setValue(CUPS[cup], CupState.FULL);
                    level.setBlock(pos, poured, Block.UPDATE_ALL);
                    level.sendBlockUpdated(pos, state, poured, Block.UPDATE_ALL);
                    return InteractionResult.SUCCESS;
                }
            }
            // 鈶?鎶婄洊纰楁斁鍥炵洏涓?
            if (!state.getValue(GAIWAN)) {
                TeaBrew brew = GaiwanItem.brewOf(stack);
                be.setGaiwanBrew(brew == null ? "" : brew.encode());
                // 骞茶尪涔熻涓€璧锋斁鍥炲幓锛屽惁鍒?鏀惧洖鐩樺瓙"浼氭妸鑼跺彾寮勪涪
                TeaBrew dry = GaiwanItem.dryTeaOf(stack);
                be.setDryTea(dry == null ? "" : dry.encode());
                var withGaiwan = state.setValue(GAIWAN, Boolean.TRUE);
                level.setBlock(pos, withGaiwan, Block.UPDATE_ALL);
                level.sendBlockUpdated(pos, state, withGaiwan, Block.UPDATE_ALL);
                stack.shrink(1);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        // 鎵嬫寔鑼舵澂锛氭斁鍒扮┖浣?
        if (cup >= 0 && state.getValue(CUPS[cup]) == CupState.NONE) {
            TeaBrew brew = TeaCupItem.brewOf(stack);
            be.setCupBrew(cup, brew == null ? "" : brew.encode());
            var withCup = state.setValue(CUPS[cup],
                    brew == null || brew.isEmpty() ? CupState.EMPTY : CupState.FULL);
            level.setBlock(pos, withCup, Block.UPDATE_ALL);
            level.sendBlockUpdated(pos, state, withCup, Block.UPDATE_ALL);
            stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
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

        // ③ 点空了（没命中任何茶具）：什么都不做。
        //
        //    这里以前是 return pickUp(...)，等于「没点中 = 整盘没了」。
        //    现在改成 PASS，让手上物品的其它交互（放茶杯、注水等）继续；
        //    要拿盘请潜行右键。
        return InteractionResult.PASS;
    }

    /**
     * 鎶婃暣鍙尪鐩樿繛鍚岀洏涓婄殑涓滆タ涓€璧锋嬁璧般€?
     *
     * <p>鐩橀噷鏈変粈涔堣<b>璁板湪鐗╁搧涓?/b>锛氱洊纰楀湪涓嶅湪銆佷笁鍙澂瀛愬悇鑷殑鐘舵€併€?
     * 鐩栫閲岀殑骞茶尪/鑼舵堡銆佹瘡鍙澂瀛愰噷鐨勮尪姹ゃ€傚彧鐢ㄦ柟鍧楃姸鎬佺殑璇濓紝
     * 鎷胯捣鏉ュ啀鏀句笅灏卞叏绌轰簡銆?/p>
     *
     * <p>瀛樻硶锛氭柟鍧楃姸鎬佸啓杩涚墿鍝佺殑 {@code BLOCK_ENTITY_DATA}
     * 锛坽@code saveWithId} 浼氬甫涓?BE 鍏ㄩ儴鏁版嵁锛夛紝
     * 杩欐牱鏀句笅鏃跺師鐗堜細鑷姩杩樺師 鈥斺€?鍜岀儳姘村６鎼潵鎼幓鏄悓涓€濂楁満鍒躲€?/p>
     */
    private InteractionResult pickUp(BlockState state, Level level, BlockPos pos,
                                     Player player, TeaSetBlockEntity be) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        ItemStack stack = new ItemStack(TeaItems.TEA_SET_ITEM.get());

        // 鈶?鏂瑰潡鐘舵€侊細鐩栫 + 涓夊彧鏉瓙 + 鏈濆悜
        //    杩欎簺闈?BLOCK_ENTITY_DATA 涔嬪鐨勬柟寮忓甫涓嶈蛋锛屽繀椤昏嚜宸卞啓缁勪欢
        stack.set(TeaComponents.SET_GAIWAN, state.getValue(GAIWAN));
        stack.set(TeaComponents.SET_CUP_A, state.getValue(CUP_A).getSerializedName());
        stack.set(TeaComponents.SET_CUP_B, state.getValue(CUP_B).getSerializedName());
        stack.set(TeaComponents.SET_CUP_C, state.getValue(CUP_C).getSerializedName());
        stack.set(TeaComponents.SET_FACING, state.getValue(FACING).getSerializedName());

        // 鍥炬爣瑕佽窡鐫€鍐呭鍙橈細鐩樹笂鏈変换浣曡尪鍏峰氨鎹㈡垚銆岀洏+鑼跺叿銆嶇殑鍥炬爣锛?
        // 鍏嶅緱鎷垮湪鎵嬮噷杩樻樉绀虹┖鐩樸€佺湅涓嶅嚭閲岄潰鏈変笢瑗裤€?
        boolean hasAnyVessel = state.getValue(GAIWAN)
                || state.getValue(CUP_A) != CupState.NONE
                || state.getValue(CUP_B) != CupState.NONE
                || state.getValue(CUP_C) != CupState.NONE;
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                        java.util.List.of(hasAnyVessel ? 1.0F : 0.0F),
                        java.util.List.of(), java.util.List.of(), java.util.List.of()));

        // 鈶?鏂瑰潡瀹炰綋鏁版嵁锛氱洊纰楅噷鐨勫共鑼?鑼舵堡銆佹瘡鍙澂瀛愰噷鐨勮尪姹?
        //    鐢?saveWithId 鑰屼笉鏄?saveCustomOnly 鈥斺€?杩樺師鏃堕潬 "id" 瀛楁鎵?BE 绫诲瀷锛?
        //    灏戜簡瀹冩暣浠芥暟鎹細琚潤榛樹涪寮冿紙鐑ф按澹惰俯杩囪繖涓潙锛夈€?
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        be.saveWithId(out);
        net.minecraft.world.item.BlockItem.setBlockEntityData(stack, be.getType(), out);

        if (!player.addItem(stack)) {
            popResource(level, pos, stack);
        }
        level.removeBlock(pos, false);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BAMBOO_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    /**
     * 鎶婄洊纰椾粠鐩樹笂鍙栦笅浜ょ粰鐜╁銆?
     *
     * <p><b>骞茶尪鍜岃尪姹ら兘瑕佸甫璧?/b>锛氫箣鍓嶅彧鍐欎簡鑼舵堡銆佹妸 {@code dryTea} 鐩存帴娓呯┖锛?
     * 浜庢槸銆屾湁鑼跺彾鐨勭洊纰椾竴鎷胯捣鏉ヨ尪鍙跺氨娌′簡銆嶃€?
     * 鐜板湪骞茶尪鍐欒繘 {@code teacraft:dry_tea} 缁勪欢锛岃尪姹ゅ啓杩?{@code teacraft:tea_brew}銆?/p>
     */
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
        var noGaiwan = state.setValue(GAIWAN, Boolean.FALSE);
        level.setBlock(pos, noGaiwan, Block.UPDATE_ALL);
        level.sendBlockUpdated(pos, state, noGaiwan, Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }

    /**
     * 鎶婃煇鍙澂瀛愪粠鐩樹笂鍙栦笅锛岃繛鍚屾澂閲岀殑鑼舵堡涓€璧蜂氦缁欑帺瀹躲€?
     */
    private static InteractionResult takeCup(BlockState state, Level level, BlockPos pos,
                                             Player player, TeaSetBlockEntity be, int cup) {
        ItemStack cupItem = new ItemStack(TeaItems.TEA_CUP.get());
        TeaBrew brew = TeaBrew.decode(be.cupBrew(cup));
        if (brew != null) TeaCupItem.setBrew(cupItem, brew);
        player.addItem(cupItem);
        be.setCupBrew(cup, "");
        var noCup = state.setValue(CUPS[cup], CupState.NONE);
        level.setBlock(pos, noCup, Block.UPDATE_ALL);
        level.sendBlockUpdated(pos, state, noCup, Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }

    /**
     * 鏀捐尪鍙?/ 娉ㄦ按 鈥斺€?娉¤尪鐨勪袱姝ャ€?
     *
     * <p>椤哄簭锛氬厛鎶婃垚鍝佽尪鏀捐繘鐩栫锛堣涓嬭尪绫讳笌鍝佽川锛夛紝鍐嶅€掓按锛屾墠寰楀埌鑼舵堡銆?
     * 鑼舵堡鍥哄畾 3 鏉殑閲忥紝鍊掍竴鏉皯涓€鏉€?/p>
     *
     * <p>鍙湪<b>鐩栫鍦ㄧ洏涓?/b>鏃舵墠鍏佽锛岄伩鍏?瀵圭潃绌虹洏鍊掓按"杩欑鎬搷浣溿€?/p>
     */
    private InteractionResult tryBrewing(ItemStack stack, BlockState state, Level level,
                                         BlockPos pos, Player player) {
        if (!state.getValue(GAIWAN)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TeaSetBlockEntity be)) return InteractionResult.PASS;

        // 鎵嬫寔鐑ф按澹讹細杩欐槸銆屽€掔儹姘淬€嶇殑鎰忓浘锛屾棤璁烘垚涓嶆垚鍔熼兘**涓嶈兘鍐嶅線涓嬭蛋鍒板彇鐗?*銆?
        // 涔嬪墠澶辫触鏃惰繑鍥?PASS 鈫?澶栧眰钀藉埌 TRY_WITH_EMPTY_HAND 鈫?useWithoutItem
        // 鎶婄洊纰楁嬁璧帮紝灏辨槸銆屾嬁鐫€鐑按澹跺彸閿洊纰楋紝鐩栫杩涜儗鍖呫€嶇殑鍘熷洜銆?
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

        // 鈶?鏀捐尪鍙讹細鎵嬫寔鎴愬搧鑼躲€傚彲浠ヨ繛缁姇澶氫唤锛屾渶澶?3 浠斤紙瀵瑰簲 3 鍙澂瀛愶級銆?
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
                // 鎹簡鍒殑鑼讹細涓嶅厑璁告贩鎶曪紝鍏堟妸鏃х殑鍙栧嚭鏉ュお澶嶆潅锛岀洿鎺ユ嫆缁?
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

        // 鈶?娉ㄦ按锛氭按妗?/ 姘寸摱锛堢儳姘村６宸插湪涓婇潰鍗曠嫭澶勭悊锛?
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

        // 鎶曞叆鍑犱唤骞茶尪灏卞嚭鍑犳澂 鈥斺€?鎶曞叆涓庝骇鍑哄绛?
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
     * 鐢ㄧ儳姘村６閲岀殑寮€姘存场鑼躲€?
     *
     * @return 鏄惁鎴愬姛娉′笂锛堝け璐ユ椂澶栧眰浼氭彁绀?鍏堟斁鑼跺彾"锛屼絾<b>涓嶄細</b>璁╃洊纰楄鎷胯蛋锛?
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
        // 澹朵笉娑堣€楋紝鍙槸鎶?寮€姘?鐢ㄦ帀鍙樻垚绌哄６
        TeaKettleItem.setHotWater(stack, false);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return true;
    }

    /** 缁欑帺瀹跺彂涓€鏉″姩浣滄爮鎻愮ず锛圥layer 鏈韩娌℃湁 sendSystemMessage锛夈€?*/
    private static void actionBar(Player player, String key) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key)
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
        }
    }
    /** 涓€鍙洊纰楁渶澶氭姇鍑犱唤骞茶尪锛堟濂藉搴旂洏涓婄殑 3 鍙澂瀛愶級銆?*/
    public static final int MAX_LEAVES = 3;

    /** 点击位置落在哪只杯子上；没落到杯子范围返回 -1。 */
    private static int cupIndexAt(BlockHitResult hit, BlockState state, Player player) {
        double[] local = localXZ(hit, state, player);
        int best = -1;
        double bestDist = CUP_PICK_RADIUS * CUP_PICK_RADIUS;
        for (int i = 0; i < 3; i++) {
            double dx = local[0] - CUP_CENTERS[i][0];
            double dz = local[1] - CUP_CENTERS[i][1];
            double d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** 鐩栫鍦ㄦ柟鍧楅噷鐨勫崰浣嶄腑蹇?(x,z)锛岀敤浜?鐐规病鐐瑰埌鐩栫"銆?*/
    private static final double[] GAIWAN_CENTER = {8.0, 6.0};

    /** 鐐瑰嚮浣嶇疆鏄惁钀藉湪鐩栫涓娿€?*/
    private static boolean isGaiwanAt(BlockHitResult hit, BlockState state, Player player) {
        double[] local = localXZ(hit, state, player);
        double dx = local[0] - GAIWAN_CENTER[0];
        double dz = local[1] - GAIWAN_CENTER[1];
        return dx * dx + dz * dz <= CUP_PICK_RADIUS * CUP_PICK_RADIUS;
    }

    /**
     * 鎶婄偣鍑讳綅缃崲绠楁垚銆岀洏闈㈣嚜韬殑灞€閮ㄥ潗鏍囥€嶏紙0~16锛夈€?
     *
     * <h2>涓轰粈涔堝繀椤绘崲绠?/h2>
     * <p>妯″瀷閲岀洊纰楀拰鏉瓙鐨勪綅缃槸<b>鎸夋湞鍚?NORTH 鐢荤殑</b>锛堢洊纰楀湪 z=6锛?
     * 鏉瓙鍦?z鈮?1.5 涓€瀛楁帓寮€锛夈€傛柟鍧楁斁涓嬪悗鍙兘杞悜浠绘剰鍥涗釜鏂瑰悜锛?
     * 鐜╁鐪嬪埌鐨勬澂瀛?A 鍦ㄤ笘鐣屽潗鏍囬噷鍙兘鏄埆鐨勪綅缃€?/p>
     *
     * <p>涓嶆崲绠楃殑璇濓紝鏈濅笢鏀剧疆鏃剁偣鏈€宸﹁竟閭ｅ彧鏉瓙锛屼細鍙栧埌鏈€鍙宠竟閭ｅ彧 鈥斺€?
     * 琛ㄧ幇灏辨槸銆岀偣閿欎簡鏉瓙銆嶃€?/p>
     *
     * <p>鍋氭硶锛氭妸涓栫晫鍧愭爣鐩稿鏂瑰潡涓績鐨勫亸绉伙紝鎸夋湞鍚戝弽鐫€杞洖鍘伙紝
     * 寰楀埌妯″瀷鍧愭爣绯讳笅鐨?(x, z)銆?/p>
     */
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
     * 鎶娿€岀浉瀵规柟鍧椾腑蹇冪殑鍋忕Щ銆嶆寜鏈濆悜杞洖妯″瀷鍧愭爣绯伙紝鍐嶅钩绉诲埌 0~16銆?
     *
     * <p>鎶芥垚鐙珛鏂规硶鏄负浜嗚鑷鑳界洿鎺ヨ皟鐢ㄩ獙璇?鈥斺€?杩欐鏃嬭浆鍐欓敊浜?
     * 琛ㄧ幇鏄?鐐归敊鏉瓙"锛屽緢闅鹃潬鑲夌溂鍙戠幇銆?/p>
     *
     * @param dx 鐩稿涓績鐨勪笘鐣?x 鍋忕Щ锛?8~8锛?
     * @param dz 鐩稿涓績鐨勪笘鐣?z 鍋忕Щ锛?8~8锛?
     * @return 妯″瀷鍧愭爣绯讳笅鐨?{x, z}锛岃寖鍥?0~16
     */
    public static double[] rotateToLocal(double dx, double dz, Direction facing) {
        double lx;
        double lz;
        switch (facing) {
            case EAST -> { lx = dz;  lz = -dx; }   // 閫嗘椂閽?90掳
            case SOUTH -> { lx = -dx; lz = -dz; }  // 180掳
            case WEST -> { lx = -dz; lz = dx; }    // 椤烘椂閽?90掳
            default -> { lx = dx;  lz = dz; }      // NORTH
        }
        return new double[]{lx + 8.0, lz + 8.0};
    }
}
