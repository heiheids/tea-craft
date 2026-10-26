package com.heishandashu.teacraft.item;
import com.heishandashu.teacraft.TeaCraft;
import net.minecraft.world.entity.EntityType;
import com.heishandashu.teacraft.block.TeaBushBlock;
import com.heishandashu.teacraft.block.TeaBushBlockEntity;
import com.heishandashu.teacraft.block.TeaCharcoalStoveBlock;
import com.heishandashu.teacraft.block.TeaCharcoalStoveBlockEntity;
import com.heishandashu.teacraft.block.TeaKettleBlock;
import com.heishandashu.teacraft.block.TeaKettleBlockEntity;
import com.heishandashu.teacraft.block.TeaRollingTrayBlock;
import com.heishandashu.teacraft.block.TeaSetBlock;
import com.heishandashu.teacraft.block.TeaSetBlockEntity;
import com.heishandashu.teacraft.block.TeaTrayBlock;
import com.heishandashu.teacraft.block.TeaTrayBlockEntity;
import com.heishandashu.teacraft.block.TeaWokBlock;
import com.heishandashu.teacraft.block.TeaWokBlockEntity;
import com.heishandashu.teacraft.block.TeapotBlock;
import com.heishandashu.teacraft.core.TeaKind;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.core.TeaStages;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 鏈ā缁勭殑<b>鍏ㄩ儴鍐呭娉ㄥ唽</b>銆?
 *
 * <p>缁撴瀯锛氳尪鏍?鈫?鏅掔洏 鈫?椴滃彾 5 绉嶏紙鎸変骇鍦帮級鈫?涓夊宸ヨ壓鐨勫叏閮ㄩ樁娈电墿鍝併€?/p>
 *
 * <p><b>2026-10-21锛?/b>鍘熸潵閭?8 鍙般€屽埗鑼舵満鍣ㄦ柟鍧椼€嶅凡鎸夐渶姹傚垹闄わ紝
 * 宸ヨ壓鏀圭敱鍦烘櫙鍖栧伐鍏烽€愭瀹炵幇锛堢涓€浠舵槸鏅掔洏锛夈€?/p>
 *
 * <h2>NeoForge 1.21.1 杩佺Щ璇存槑锛圖eferredRegister锛?/h2>
 * <p>26.3 鐨勫啓娉曟槸 {@code Registry.register(BuiltInRegistries.ITEM, id, new XxxItem(...))}锛?
 * 鍦ㄧ被鍒濆鍖栨椂绔嬪埢鎶婂璞″杩涙敞鍐岃〃銆侼eoForge 1.21.1 蹇呴』鏀规垚
 * {@link DeferredRegister}锛氳繖閲?B>鍙櫥璁般€屾€庝箞閫犮€?/B>锛岀湡姝ｇ殑瀵硅薄鐢?
 * {@code RegisterEvent} 鍦ㄦā缁勬瀯閫犱箣鍚庣粺涓€鍒涘缓銆?/p>
 *
 * <p>鐢辨甯︽潵涓ゆ潯蹇呴』閬靛畧鐨勮鍒欙細</p>
 * <ol>
 *   <li>鎵€鏈夐潤鎬佸瓧娈电殑绫诲瀷閮芥槸 <b>holder</b>锛坽@link DeferredItem} / {@link DeferredBlock} /
 *       {@link DeferredHolder}锛夛紝<b>涓嶆槸</b> {@code Item} / {@code Block}銆?
 *       鍒殑绫昏鎷跨湡姝ｇ殑瀵硅薄锛屽繀椤诲啓 {@code TeaItems.XXX.get()}锛?/li>
 *   <li>{@link #ALL_ITEMS} 涓?{@link #stage(String)} 杩欑被銆屾煡璇㈡帴鍙ｃ€嶄粛鐒惰繑鍥?
 *       {@code Item}锛屽洜涓哄畠浠殑璋冪敤鐐瑰湪娓告垙杩愯鏈燂紙閭ｆ椂鏃╁凡娉ㄥ唽瀹屾瘯锛夈€?/li>
 * </ol>
 */
public final class TeaItems {

    /* ---------------- 娉ㄥ唽鍣?---------------- */

    /** 鐗╁搧娉ㄥ唽鍣紙{@code DeferredRegister.Items} 鑷甫 {@code registerItem} 绛変究鍒╂柟娉曪級銆?*/
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeaCraft.MOD_ID);

    /** 鏂瑰潡娉ㄥ唽鍣ㄣ€?*/
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TeaCraft.MOD_ID);

    /** 鏂瑰潡瀹炰綋绫诲瀷娉ㄥ唽鍣ㄣ€?*/
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TeaCraft.MOD_ID);

    /**
     * 椴滃彾锛氫骇鍦?鈫?鐗╁搧 holder銆?
     *
     * <p>瀛?holder 鑰屼笉鏄?{@code Item}锛氭敞鍐屽畬鎴愪箣鍓?{@code Item} 杩樹笉瀛樺湪銆?/p>
     */
    private static final Map<TeaOrigin, DeferredItem<? extends Item>> FRESH_LEAVES =
            new EnumMap<>(TeaOrigin.class);

    /** 闃舵鐗╁搧锛歩d 鈫?鐗╁搧 holder銆?*/
    private static final Map<String, DeferredItem<? extends Item>> STAGE_ITEMS = new LinkedHashMap<>();

    /* ---------------- 鏂瑰潡 ---------------- */

    /** 鑼舵爲鏂瑰潡銆?*/
    public static final DeferredBlock<TeaBushBlock> TEA_BUSH = BLOCKS.register("tea_bush",
            () -> new TeaBushBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .noCollission()          // 1.21.1 鐨勬柟娉曞悕灏辨槸杩欎釜鎷煎啓锛坣oCollission锛?
                    .instabreak()
                    .noOcclusion()
                    .randomTicks()
                    .sound(SoundType.GRASS)
                    .pushReaction(PushReaction.DESTROY)));   // 1.21.1 娌℃湁 POPPED

    /** 鑼舵爲鐗╁搧锛堝彲鏀剧疆锛夈€?*/
    public static final DeferredItem<BlockItem> TEA_BUSH_ITEM = ITEMS.register("tea_bush",
            () -> new BlockItem(TEA_BUSH.get(), new Item.Properties()));

    /** 鏅掔洏鏂瑰潡锛?6脳3脳16 鐨勭缂栧渾鐩橈紝鏀惧湪鏂瑰潡椤堕潰銆?*/
    public static final DeferredBlock<TeaTrayBlock> TEA_TRAY = BLOCKS.register("tea_tray",
            () -> new TeaTrayBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.6F)
                    .noOcclusion()
                    .sound(SoundType.BAMBOO_WOOD)
                    .pushReaction(PushReaction.DESTROY)));

    /** 鏅掔洏鐗╁搧锛堟姳鍦ㄦ墜涓婄殑褰㈡€侊紝鐢ㄧ粍浠惰褰曠洏閲岀殑鑼跺彾锛夈€?*/
    public static final DeferredItem<TeaTrayItem> TEA_TRAY_ITEM = ITEMS.register("tea_tray",
            () -> new TeaTrayItem(TEA_TRAY.get(), new Item.Properties().stacksTo(1)));

    /** 鎻夎尪鍖炬柟鍧楋細鎻夋嵒 鈫?鍙戦叺銆傚褰笌鏅掔洏涓€鑷达紙鏈夎竟妗嗐€? 鍍忕礌楂橈級銆?*/
    public static final DeferredBlock<TeaRollingTrayBlock> TEA_ROLLING_TRAY = BLOCKS.register("tea_rolling_tray",
            () -> new TeaRollingTrayBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.6F)
                    .noOcclusion()
                    .sound(SoundType.BAMBOO_WOOD)
                    .pushReaction(PushReaction.DESTROY)));

    /** 鎻夎尪鍖剧墿鍝併€?*/
    public static final DeferredItem<BlockItem> TEA_ROLLING_TRAY_ITEM = ITEMS.register("tea_rolling_tray",
            () -> new BlockItem(TEA_ROLLING_TRAY.get(), new Item.Properties()));

    /** 纰崇剻妗嗭細鐢ㄦ湪鐐剻鑼剁殑绠卞瓙锛堢被浼肩啍鐐夛級銆?*/
    public static final DeferredBlock<TeaCharcoalStoveBlock> TEA_CHARCOAL_STOVE =
            BLOCKS.register("tea_charcoal_stove",
                    () -> new TeaCharcoalStoveBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.0F)
                            .sound(SoundType.BAMBOO_WOOD)));

    public static final DeferredItem<BlockItem> TEA_CHARCOAL_STOVE_ITEM = ITEMS.register("tea_charcoal_stove",
            () -> new BlockItem(TEA_CHARCOAL_STOVE.get(), new Item.Properties()));

    /** 閾侀攨锛氱倰鍒舵弶鎹昏繃鐨勮尪锛屾ā鍨嬫槸鐐艰嵂閿呭帇鍒?2/3 楂樸€?*/
    public static final DeferredBlock<TeaWokBlock> TEA_WOK = BLOCKS.register("tea_wok",
            () -> new TeaWokBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F)
                    .noOcclusion()
                    .sound(SoundType.METAL)));

    public static final DeferredItem<BlockItem> TEA_WOK_ITEM = ITEMS.register("tea_wok",
            () -> new BlockItem(TEA_WOK.get(), new Item.Properties()));

    /**
     * 鑼剁洏锛? 涓柟鍧楀ぇ灏忋€?px 楂樼殑鑼跺叿鎽嗕欢锛堢洊纰?+ 涓変釜鏂规澂锛夈€?
     *
     * <p>杩欎竴鐗堝彧鏈夐潤鎬佹ā鍨嬶紱鍚庣画浼氭崲鎴愬甫鏂瑰潡瀹炰綋娓叉煋鍣ㄧ殑鐗堟湰锛?
     * 濂借鐩栫鍜屾澂瀛愯兘鍚勮嚜鐙珛鍔ㄨ捣鏉ャ€?/p>
     */
    public static final DeferredBlock<TeaSetBlock> TEA_SET = BLOCKS.register("tea_set",
            () -> new TeaSetBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F)
                    .noOcclusion()
                    .sound(SoundType.WOOD)));

    /**
     * 銆婅尪宸ヨ壓璇存槑銆?鈥斺€?妯＄粍璇存槑涔︺€?
     *
     * <p>鍙抽敭鎵撳紑鍒嗛〉鐣岄潰锛屽唴瀹瑰叏閮ㄥ湪璇█鏂囦欢閲岋紙{@code teacraft.guide.*}锛夈€?
     * 鍚堟垚锛氫竴棰楄尪鏍戣嫍 + 涓€鏈功銆?/p>
     */
    public static final DeferredItem<TeaGuideItem> TEA_GUIDE = ITEMS.register("tea_guide",
            () -> new TeaGuideItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TeaSetItem> TEA_SET_ITEM = ITEMS.register("tea_set",
            () -> new TeaSetItem(TEA_SET.get(), new Item.Properties().stacksTo(1)));

    /* ---------------- 绐戠倝 / 鎷夊澂鏈?---------------- */

    /**
     * 绐戠倝锛氱儳鍒堕櫠鐡风殑鐮栫獞銆?
     *
     * <p><b>蹇呴』 {@code noOcclusion()}</b>锛氭ā鍨嬩笉鏄弧鏂瑰潡 鈥斺€?鍥涗釜鐡疯剼涔嬮棿鏄┖闅欙紝
     * 鐑熷洷杩樻帰鍑烘柟鍧楅《闈€備笉鍔犵殑璇濈浉閭绘柟鍧椾細鎶婄獞鐐夎嚜宸辩殑璐撮潰鍓旈櫎鎺夛紝
     * 琛ㄧ幇灏辨槸銆屽簳閮ㄩ€忔槑銆佽兘鐪嬪埌搴曚笅鐨勫湴闈€嶃€?/p>
     */
    public static final DeferredBlock<com.heishandashu.teacraft.block.ClayKilnBlock> CLAY_KILN =
            BLOCKS.register("clay_kiln",
                    () -> new com.heishandashu.teacraft.block.ClayKilnBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.CLAY)
                                    .strength(3.5F, 6.0F)
                                    .noOcclusion()
                                    .sound(SoundType.STONE)));

    /** 绐戠倝鐗╁搧锛氬甫銆屽繀椤荤揣璐存媺鍧満鏀剧疆銆嶇殑鐗╁搧璇存槑銆?*/
    public static final DeferredItem<com.heishandashu.teacraft.item.PairedBlockItem> CLAY_KILN_ITEM =
            ITEMS.register("clay_kiln",
                    () -> new com.heishandashu.teacraft.item.PairedBlockItem(
                            CLAY_KILN.get(), new Item.Properties(),
                            "teacraft.tooltip.clay_kiln_pair"));

    /**
     * 鎷夊澂鏈猴細鎶婇粡鍦熷仛鎴愰櫠鐡峰澂浣撱€?
     *
     * <p>妯″瀷鍥涜吙涔嬮棿鏄┖鐨勶紝蹇呴』 {@code noOcclusion()}銆?/p>
     */
    public static final DeferredBlock<com.heishandashu.teacraft.block.PotterWheelBlock> POTTER_WHEEL =
            BLOCKS.register("potter_wheel",
                    () -> new com.heishandashu.teacraft.block.PotterWheelBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.WOOD)
                                    .strength(2.0F)
                                    .noOcclusion()
                                    .sound(SoundType.WOOD)));

    /** 鎷夊澂鏈虹墿鍝侊細甯︺€屽繀椤荤揣璐寸獞鐐夋斁缃€嶇殑鐗╁搧璇存槑銆?*/
    public static final DeferredItem<com.heishandashu.teacraft.item.PairedBlockItem> POTTER_WHEEL_ITEM =
            ITEMS.register("potter_wheel",
                    () -> new com.heishandashu.teacraft.item.PairedBlockItem(
                            POTTER_WHEEL.get(), new Item.Properties(),
                            "teacraft.tooltip.potter_wheel_pair"));

    /**
     * 鑼舵锛氭弧鏍兼柟鍧楋紝妗岄潰鍦?y=16锛岃尪鐩樻斁涓婂幓鏃犵紳璐村悎銆?
     *
     * <p>涓嶅垎鏈濆悜锛堝洓闈㈠绉帮級锛岀鎾炵婊℃牸銆?/p>
     */
    public static final DeferredBlock<com.heishandashu.teacraft.block.TeaTableBlock> TEA_TABLE =
            BLOCKS.register("tea_table",
                    () -> new com.heishandashu.teacraft.block.TeaTableBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.WOOD)
                                    .strength(1.2F)
                                    .noOcclusion()
                                    .sound(SoundType.WOOD)));

    public static final DeferredItem<com.heishandashu.teacraft.item.DescribedBlockItem> TEA_TABLE_ITEM =
            ITEMS.register("tea_table",
                    () -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                            TEA_TABLE.get(), new Item.Properties(),
                            "teacraft.tooltip.tea_table",
                            "teacraft.tooltip.tea_table_hint"));

    /**
     * 烧水茶桌：茶桌里嵌了一块岩浆。
     *
     * <p>中心 6x6 像素是岩浆贴面，与桌面齐平。把烧水壶放上去就能烧水 ——
     * 靠 {@code TeaKettleBlock.isHeatSource()} 认这个方块实现。</p>
     *
     * <p><b>不做踩踏伤害</b>：它继承 {@code Block} 而不是 {@code MagmaBlock}。</p>
     */
    public static final DeferredBlock<com.heishandashu.teacraft.block.TeaTableHotBlock> TEA_TABLE_HOT =
            BLOCKS.register("tea_table_hot",
                    () -> new com.heishandashu.teacraft.block.TeaTableHotBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.WOOD)
                                    .strength(1.2F)
                                    .noOcclusion()
                                    .sound(SoundType.WOOD)));

    public static final DeferredItem<com.heishandashu.teacraft.item.DescribedBlockItem> TEA_TABLE_HOT_ITEM =
            ITEMS.register("tea_table_hot",
                    () -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                            TEA_TABLE_HOT.get(), new Item.Properties(),
                            "teacraft.tooltip.tea_table_hot"));

    /** 茶盒：27 个槽位，每槽最多 999 个同种成品茶。 */
    public static final DeferredBlock<com.heishandashu.teacraft.block.TeaBoxBlock> TEA_BOX =
            BLOCKS.register("tea_box",
                    () -> new com.heishandashu.teacraft.block.TeaBoxBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.WOOD)
                                    .strength(1.0F)
                                    .noOcclusion()
                                    .sound(SoundType.WOOD)));

    public static final DeferredItem<com.heishandashu.teacraft.item.DescribedBlockItem> TEA_BOX_ITEM =
            ITEMS.register("tea_box",
                    () -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                            TEA_BOX.get(), new Item.Properties(),
                            "teacraft.tooltip.tea_box",
                            "teacraft.tooltip.tea_box_hint"));

    /**
     * 鑼舵锛氬垎鏈濆悜銆佺鎾炵鍙埌搴ч潰锛?~9.5锛夛紝妞呰儗涓嶆尅璺紝绌烘墜鍙抽敭鍧愪笅銆?
     */
    public static final DeferredBlock<com.heishandashu.teacraft.block.TeaChairBlock> TEA_CHAIR =
            BLOCKS.register("tea_chair",
                    () -> new com.heishandashu.teacraft.block.TeaChairBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.WOOD)
                                    .strength(1.0F)
                                    .noOcclusion()
                                    .sound(SoundType.WOOD)));

    public static final DeferredItem<com.heishandashu.teacraft.item.DescribedBlockItem> TEA_CHAIR_ITEM =
            ITEMS.register("tea_chair",
                    () -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                            TEA_CHAIR.get(), new Item.Properties(),
                            "teacraft.tooltip.tea_chair",
                            "teacraft.tooltip.tea_chair_sit"));

    /* ---------------- 绾墿鍝?---------------- */

    /** 鐩栫锛氭场鑼跺鍣紝鍙嬁鍦ㄦ墜涓婏紙鑼舵堡璁板湪缁勪欢閲岋級銆?*/
    public static final DeferredItem<GaiwanItem> GAIWAN = ITEMS.register("gaiwan",
            () -> new GaiwanItem(new Item.Properties().stacksTo(1)));

    /** 鑼舵澂锛氫粠鑼剁洏涓婃嬁璧锋潵鍠濈殑閭ｄ竴鍙€?*/
    public static final DeferredItem<TeaCupItem> TEA_CUP = ITEMS.register("tea_cup",
            () -> new TeaCupItem(new Item.Properties().stacksTo(1)));

    /**
     * 鑼跺６锛堣楗帮級鈥斺€?鍙槸鎽嗕欢锛屼笉鑳藉姞鐑€?
     *
     * <p>涓庣儳姘村６鍏辩敤鍚屼竴濂楁ā鍨嬶紝浣?b>瀹冧篃鏄柟鍧?/b>锛?
     * 鏈€鍒濊繖閲屾敞鍐屾垚绾墿鍝侊紝瀵艰嚧瀹冩牴鏈斁涓嶅埌鍦颁笂锛?
     * 寰堝鏄撳拰"鐑ф按澹舵斁涓嶄笂鍘?娣锋穯銆?/p>
     */
    public static final DeferredBlock<TeapotBlock> TEAPOT_BLOCK = BLOCKS.register("teapot",
            () -> new TeapotBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(1.0F)
                    .noOcclusion()
                    .sound(SoundType.STONE)));

    public static final DeferredItem<BlockItem> TEAPOT = ITEMS.register("teapot",
            () -> new BlockItem(TEAPOT_BLOCK.get(), new Item.Properties().stacksTo(1)));

    /**
     * 鐑ф按澹讹紙鏂瑰潡褰㈡€侊級锛氭斁鍦ㄥ博娴嗗潡 / 绡濈伀 / 鐕冪儳鐨勭啍鐐変笂鍔犵儹銆?
     *
     * <p>涓庤楗扮敤鐨?{@code teapot} 澶栧舰鍏辩敤鍚屼竴濂楀嚑浣曘€?
     * 杩欓噷鐢ㄣ€屾柟鍧?+ BlockItem銆嶈€屼笉鏄函鐗╁搧锛屽洜涓鸿鏀惧湪鐑簮涓婃寔缁彈鐑€?/p>
     */
    public static final DeferredBlock<TeaKettleBlock> TEA_KETTLE = BLOCKS.register("kettle",
            () -> new TeaKettleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(1.0F)
                    .noOcclusion()
                    .sound(SoundType.STONE)));

    public static final DeferredItem<TeaKettleItem> TEA_KETTLE_ITEM = ITEMS.register("kettle",
            () -> new TeaKettleItem(TEA_KETTLE.get(), new Item.Properties().stacksTo(1)));

    /* ---------------- 鏂瑰潡瀹炰綋绫诲瀷 ---------------- */

    /**
     * 鏅掔洏鐨勬柟鍧楀疄浣撶被鍨嬨€?
     *
     * <p>1.21.1 鐢?{@code BlockEntityType.Builder.of(BlockEntitySupplier, Block...)}锛?
     * 绗簩涓弬鏁拌捣鏄?b>鍙彉鍙傛暟</b>鐨勬柟鍧楋紝鎵€浠ユ檼鐩樺拰鎻夎尪鍖惧彲浠ュ叡鐢ㄤ竴涓被鍨嬨€?/p>
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeaTrayBlockEntity>> TRAY_BE_TYPE =
            BLOCK_ENTITIES.register("tea_tray", () -> BlockEntityType.Builder.of(
                    TeaTrayBlockEntity::new, TEA_TRAY.get(), TEA_ROLLING_TRAY.get()).build(null));

    /** 纰崇剻妗嗙殑鏂瑰潡瀹炰綋绫诲瀷銆?*/
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeaCharcoalStoveBlockEntity>>
            STOVE_BE_TYPE = BLOCK_ENTITIES.register("tea_charcoal_stove", () -> BlockEntityType.Builder.of(
                    TeaCharcoalStoveBlockEntity::new, TEA_CHARCOAL_STOVE.get()).build(null));

    /** 茶树方块实体类型：承载成熟倒计时，支持太冷暂停 / 取暖恢复。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeaBushBlockEntity>>
            BUSH_BE_TYPE = BLOCK_ENTITIES.register("tea_bush", () -> BlockEntityType.Builder.of(
                    TeaBushBlockEntity::new, TEA_BUSH.get()).build(null));

    /** 閾侀攨鐨勬柟鍧楀疄浣撶被鍨嬨€?*/
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeaWokBlockEntity>> WOK_BE_TYPE =
            BLOCK_ENTITIES.register("tea_wok", () -> BlockEntityType.Builder.of(
                    TeaWokBlockEntity::new, TEA_WOK.get()).build(null));

    /** 鐑ф按澹剁殑鏂瑰潡瀹炰綋绫诲瀷锛堣姘撮噺涓庡姞鐑繘搴︼級銆?*/
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeaKettleBlockEntity>> KETTLE_BE_TYPE =
            BLOCK_ENTITIES.register("kettle", () -> BlockEntityType.Builder.of(
                    TeaKettleBlockEntity::new, TEA_KETTLE.get()).build(null));

    /** 鑼剁洏鐨勬柟鍧楀疄浣撶被鍨嬶紙瀛樼洊纰楀拰涓変釜鏉瓙閲岀殑鑼舵堡锛夈€?*/
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeaSetBlockEntity>> SET_BE_TYPE =
            BLOCK_ENTITIES.register("tea_set", () -> BlockEntityType.Builder.of(
                    TeaSetBlockEntity::new, TEA_SET.get()).build(null));

    /** 绐戠倝鐨勬柟鍧楀疄浣撶被鍨嬶紙6 鍧枡 + 6 鎴愬搧 + 鐕冩枡锛夈€?*/
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.heishandashu.teacraft.block.ClayKilnBlockEntity>> CLAY_KILN_BE_TYPE =
            BLOCK_ENTITIES.register("clay_kiln", () -> BlockEntityType.Builder.of(
                    com.heishandashu.teacraft.block.ClayKilnBlockEntity::new,
                    CLAY_KILN.get()).build(null));

    /** 鎷夊澂鏈虹殑鏂瑰潡瀹炰綋绫诲瀷锛堥粡鍦熸牸 + 鎵剧浉閭荤獞鐐夛級銆?*/
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.heishandashu.teacraft.block.PotterWheelBlockEntity>> POTTER_WHEEL_BE_TYPE =
            BLOCK_ENTITIES.register("potter_wheel", () -> BlockEntityType.Builder.of(
                    com.heishandashu.teacraft.block.PotterWheelBlockEntity::new,
                    POTTER_WHEEL.get()).build(null));

    /** 茶盒的方块实体类型（27 槽 x 999）。 */
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.heishandashu.teacraft.block.TeaBoxBlockEntity>> BOX_BE_TYPE =
            BLOCK_ENTITIES.register("tea_box", () -> BlockEntityType.Builder.of(
                    com.heishandashu.teacraft.block.TeaBoxBlockEntity::new,
                    TEA_BOX.get()).build(null));

    /**
     * 妞呭瓙搴т綅瀹炰綋銆?
     *
     * <p>鍧愪笅蹇呴』闈犮€岄獞涔樹竴涓疄浣撱€嶅疄鐜帮紝鏂瑰潡鏈韩褰撲笉浜嗚浇鍏枫€?/p>
     */
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    TeaCraft.MOD_ID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>,
            EntityType<com.heishandashu.teacraft.block.TeaSeatEntity>> SEAT_TYPE =
            ENTITIES.register("tea_seat", () -> EntityType.Builder
                    .<com.heishandashu.teacraft.block.TeaSeatEntity>of(
                            com.heishandashu.teacraft.block.TeaSeatEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.0F, 0.0F)
                    .noSummon()
                    .build("tea_seat"));

    /* ---------------- 椴滃彾 / 闃舵鐗╁搧 ---------------- */

    static {
        for (TeaOrigin origin : TeaOrigin.values()) {
            FRESH_LEAVES.put(origin, ITEMS.register(origin.itemId(),
                    () -> new TeaFreshLeafItem(new Item.Properties().stacksTo(64), origin)));
        }

        for (TeaStages.Stage stage : TeaStages.all()) {
            // 鎴愬搧鑼跺彲浠ャ€屽枬銆嶏細鐢?TeaDrinkItem锛堟寜鍝佽川缁欐晥鏋滐級銆?
            // 鍏朵綑闃舵浠嶆槸鏅€?TeaStageItem銆?
            STAGE_ITEMS.put(stage.id(), ITEMS.register(stage.id(),
                    () -> stage.isFinished()
                            ? new TeaDrinkItem(new Item.Properties().stacksTo(64), stage)
                            : new TeaStageItem(new Item.Properties().stacksTo(64), stage)));
        }
    }

    /* ---------------- 鍒涢€犳爮娓呭崟 ---------------- */

    private static volatile List<Item> allItemsCache;

    /**
     * 鍏ㄩ儴鐗╁搧锛堝垱閫犳爮椤哄簭 = 娉ㄥ唽椤哄簭锛夈€?
     *
     * <p>鍥犱负鐗╁搧瑕佺瓑 {@code RegisterEvent} 涔嬪悗鎵嶅瓨鍦紝杩欓噷鍋氭垚涓€涓?
     * <b>鎯版€ц鍥?/b>锛氱涓€娆¤鍙栨椂鎸?holder 缁勮锛屼箣鍚庤蛋缂撳瓨銆?
     * 璋冪敤鏂逛粛鐒舵妸瀹冨綋鏅€氱殑 {@code List<Item>} 鐢紝婧愮爜涓嶇敤鏀广€?/p>
     */
    public static final List<Item> ALL_ITEMS = new AbstractList<>() {

        @Override
        public Item get(int index) {
            return allItems().get(index);
        }

        @Override
        public int size() {
            return allItems().size();
        }
    };

    private static List<Item> allItems() {
        List<Item> cached = allItemsCache;
        if (cached == null) {
            cached = buildAllItems();
            allItemsCache = cached;
        }
        return cached;
    }

    private static List<Item> buildAllItems() {
        List<Item> all = new ArrayList<>();
        all.add(TEA_BUSH_ITEM.get());
        all.add(TEA_TRAY_ITEM.get());
        all.add(TEA_ROLLING_TRAY_ITEM.get());
        all.add(TEA_CHARCOAL_STOVE_ITEM.get());
        all.add(TEA_WOK_ITEM.get());
        all.add(TEA_SET_ITEM.get());
        all.add(CLAY_KILN_ITEM.get());
        all.add(POTTER_WHEEL_ITEM.get());
        all.add(TEA_TABLE_ITEM.get());
        all.add(TEA_TABLE_HOT_ITEM.get());
        all.add(TEA_BOX_ITEM.get());
        all.add(TEA_CHAIR_ITEM.get());
        all.add(TEA_GUIDE.get());
        all.add(GAIWAN.get());
        all.add(TEA_CUP.get());
        // 瑁呴グ鑼跺６涓嶈繘鍒涢€犳爮锛氬畠鍜岀儳姘村６鍚屾ā鍨嬶紝鏀惧嚭鏉ュ垎涓嶆竻锛屽鏄撹褰撴垚鐑ф按澹?
        all.add(TEA_KETTLE_ITEM.get());
        for (TeaOrigin origin : TeaOrigin.values()) all.add(FRESH_LEAVES.get(origin).get());

        // 鈿?蹇呴』鍘婚噸锛氱 2 闃舵銆屾憡鏅捐悗鍑嬪彾銆嶆槸涓夋潯璺嚎鍏变韩鐨勶紝
        // 瀹冧細鍑虹幇鍦ㄦ瘡鏉￠摼鐨勯浣嶃€傜洿鎺?add 浼氭妸瀹冨杩涘垱閫犳爮涓夋锛?
        // Minecraft 绔嬪埢鎶?IllegalStateException:
        //   "Accidentally adding the same item stack twice"
        // 骞朵笖杩欐槸<b>鍚姩鏈熷穿婧?/b>锛堝垱閫犳爮鏋勫缓鏃讹級锛屼笉鏄繘娓告垙鎵嶇偢銆?
        Set<Item> seen = new LinkedHashSet<>();
        // 鍏堟斁鍏变韩鐨勭 2 闃舵锛屼繚璇佸畠鍦ㄥ垱閫犳爮閲屾帓鍦ㄥ悇鑷矾绾夸箣鍓?
        DeferredItem<? extends Item> shared = STAGE_ITEMS.get(TeaStages.SHARED_WITHER_ID);
        if (shared != null && seen.add(shared.get())) all.add(shared.get());

        for (TeaKind kind : TeaKind.values()) {
            for (TeaStages.Stage stage : TeaStages.chain(kind)) {
                DeferredItem<? extends Item> holder = STAGE_ITEMS.get(stage.id());
                if (holder == null) continue;
                Item item = holder.get();
                if (seen.add(item)) all.add(item);
            }
        }
        return Collections.unmodifiableList(all);
    }

    private TeaItems() {
    }

    /* ---------------- 鏌ヨ鎺ュ彛 ---------------- */

    public static Item freshLeaf(TeaOrigin origin) {
        DeferredItem<? extends Item> holder = FRESH_LEAVES.get(origin);
        return holder == null ? null : holder.get();
    }

    public static Item stage(TeaStages.Stage stage) {
        return stage(stage.id());
    }

    public static Item stage(String id) {
        DeferredItem<? extends Item> holder = STAGE_ITEMS.get(id);
        return holder == null ? null : holder.get();
    }

    public static Map<TeaOrigin, Item> freshLeaves() {
        Map<TeaOrigin, Item> out = new EnumMap<>(TeaOrigin.class);
        FRESH_LEAVES.forEach((origin, holder) -> out.put(origin, holder.get()));
        return Collections.unmodifiableMap(out);
    }

    public static Map<String, Item> stageItems() {
        Map<String, Item> out = new LinkedHashMap<>();
        STAGE_ITEMS.forEach((id, holder) -> out.put(id, holder.get()));
        return Collections.unmodifiableMap(out);
    }

    public static boolean isStageItem(Item item) {
        return item instanceof TeaStageItem;
    }

    public static boolean isFreshLeaf(Item item) {
        return item instanceof TeaFreshLeafItem;
    }

    /* ---------------- 娉ㄥ唽鍏ュ彛 ---------------- */

    /**
     * 鎶婁笁涓敞鍐屽櫒鎸傚埌妯＄粍浜嬩欢鎬荤嚎涓婏紝骞跺湪娉ㄥ唽鍏ㄩ儴瀹屾垚鍚庤ˉ涓娿€屾柟鍧楃被鍨?鈫?鏂瑰潡瀹炰綋銆嶇殑鍥炴寚銆?
     *
     * <p>鏂瑰潡瀹炰綋鐨勬瀯閫犲嚱鏁拌鎷垮埌绫诲瀷锛屼絾绫诲瀷鍙堥渶瑕佹柟鍧楀厛瀛樺湪 鈥斺€?
     * 鍘熸潵闈?{@code static { TeaTrayBlockEntity.TYPE = ...; }}锛岀幇鍦ㄦ敞鍐岃鎺ㄨ繜鍒?
     * {@code RegisterEvent}锛岄潤鎬佸垵濮嬪寲鏃剁被鍨嬭繕涓嶅瓨鍦紝鎵€浠ユ敼鍒?
     * {@link FMLCommonSetupEvent}锛堟鏃舵墍鏈夋敞鍐岃〃閮藉凡鍐荤粨銆佹墍鏈?holder 閮藉凡缁戝畾锛夈€?
     * 灏戜簡杩欎竴姝ワ紝鏀句笅鏅掔洏鏃朵細 NPE锛歿@code BlockEntityType.isValid(...)}
     * 鍥犱负 "this.type" 涓?null銆?/p>
     */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(TeaItems::onCommonSetup);
        TeaCraft.LOGGER.info("[teacraft] 鍐呭娉ㄥ唽鍣ㄥ凡鎸傝浇锛氱墿鍝?/ 鏂瑰潡 / 鏂瑰潡瀹炰綋");
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // 娉ㄥ唽琛ㄥ凡鍐荤粨锛宧older 鍙互瀹夊叏鍙栧€硷紱鏀惧埌涓荤嚎绋嬮槦鍒楅噷鍋氾紝閬垮厤骞惰 mod 鍔犺浇鏃朵簰鐩歌俯銆?
        event.enqueueWork(TeaItems::bindBlockEntityTypes);
    }

    /** 鎶婃敞鍐屽嚭鏉ョ殑绫诲瀷濉繘鍚勬柟鍧楀疄浣撶被鐨勯潤鎬佸瓧娈碉紙瀹冧滑鐢卞埆鐨勬枃浠跺０鏄庯紝杩欓噷鍙祴鍊硷級銆?*/
    private static void bindBlockEntityTypes() {
        TeaTrayBlockEntity.TYPE = TRAY_BE_TYPE.get();
        TeaCharcoalStoveBlockEntity.TYPE = STOVE_BE_TYPE.get();
        TeaWokBlockEntity.TYPE = WOK_BE_TYPE.get();
        TeaKettleBlockEntity.TYPE = KETTLE_BE_TYPE.get();
        TeaSetBlockEntity.TYPE = SET_BE_TYPE.get();
        com.heishandashu.teacraft.block.ClayKilnBlockEntity.TYPE = CLAY_KILN_BE_TYPE.get();
        com.heishandashu.teacraft.block.PotterWheelBlockEntity.TYPE =
                POTTER_WHEEL_BE_TYPE.get();
        com.heishandashu.teacraft.block.TeaSeatEntity.TYPE = SEAT_TYPE.get();
        com.heishandashu.teacraft.block.TeaBoxBlockEntity.TYPE = BOX_BE_TYPE.get();
        TeaBushBlockEntity.TYPE = BUSH_BE_TYPE.get();

        // 闄剁摲鍥炬牱琛細姝ゆ椂鑼舵澂 / 鐩栫 / 鐑ф按澹堕兘宸叉敞鍐岋紝鍙互瀹夊叏鍙栧€笺€?
        com.heishandashu.teacraft.core.CeramicPatterns.bootstrap();

        TeaCraft.LOGGER.info("[teacraft] 娉ㄥ唽瀹屾垚锛歿} 涓墿鍝侊紙鍚?{} 涓樁娈电墿鍝併€亄} 涓骇鍦伴矞鍙讹級",
                ALL_ITEMS.size(), STAGE_ITEMS.size(), FRESH_LEAVES.size());
    }
}
