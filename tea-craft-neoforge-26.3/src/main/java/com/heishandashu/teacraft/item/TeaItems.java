package com.heishandashu.teacraft.item;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.block.TeaBushBlock;
import com.heishandashu.teacraft.block.TeaCharcoalStoveBlock;
import com.heishandashu.teacraft.block.TeaCharcoalStoveBlockEntity;
import com.heishandashu.teacraft.block.TeaRollingTrayBlock;
import com.heishandashu.teacraft.block.TeaWokBlock;
import com.heishandashu.teacraft.block.TeaWokBlockEntity;
import com.heishandashu.teacraft.block.TeaTrayBlock;
import com.heishandashu.teacraft.block.TeaTrayBlockEntity;
import com.heishandashu.teacraft.core.TeaKind;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.core.TeaStages;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 鏈ā缁勭殑<b>鍏ㄩ儴鍐呭娉ㄥ唽</b>銆?
 *
 * <p>缁撴瀯锛氳尪鏍?鈫?鏅掔洏 鈫?椴滃彾 5 绉嶏紙鎸変骇鍦帮級鈫?涓夊宸ヨ壓鐨勫叏閮ㄩ樁娈电墿鍝併€?/p>
 *
 * <p><b>2026-10-21锛?/b>鍘熸潵閭?8 鍙般€屽埗鑼舵満鍣ㄦ柟鍧椼€嶅凡鎸夐渶姹傚垹闄わ紝
 * 宸ヨ壓鏀圭敱鍦烘櫙鍖栧伐鍏烽€愭瀹炵幇锛堢涓€浠舵槸鏅掔洏锛夈€?/p>
 */
public final class TeaItems {

    /* ---------------- 注册器 ---------------- */

    /** 物品注册器。{@code DeferredRegister.Items} 自带 registerItem 等便利方法。 */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeaCraft.MOD_ID);

    /** 方块注册器。 */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TeaCraft.MOD_ID);

    /** 方块实体类型注册器。 */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TeaCraft.MOD_ID);

    /** 实体类型注册器。 */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TeaCraft.MOD_ID);

    /** 椴滃彾锛氫骇鍦?-> 鐗╁搧銆?*/
    private static final Map<TeaOrigin, DeferredItem<? extends Item>> FRESH_LEAVES = new EnumMap<>(TeaOrigin.class);
    /** 闃舵鐗╁搧锛歩d -> 鐗╁搧銆?*/
    private static final Map<String, DeferredItem<? extends Item>> STAGE_ITEMS = new LinkedHashMap<>();

    /** 鑼舵爲鏂瑰潡銆?*/
    public static final DeferredBlock<Block> TEA_BUSH = registerBlock("tea_bush",
            key -> new TeaBushBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.PLANT)
                    .noCollision()
                    .instabreak()
                    .noOcclusion()
                    .randomTicks()
                    .sound(SoundType.GRASS)
                    .pushReaction(PushReaction.POPPED)));

    /** 鑼舵爲鐗╁搧锛堝彲鏀剧疆锛夈€?*/
    public static final DeferredItem<Item> TEA_BUSH_ITEM = registerItem("tea_bush",
            key -> new BlockItem(TEA_BUSH.get(), new Item.Properties().setId(key)));

    /** 鏅掔洏鏂瑰潡锛?6脳3脳16 鐨勭缂栧渾鐩橈紝鏀惧湪鏂瑰潡椤堕潰銆?*/
    public static final DeferredBlock<Block> TEA_TRAY = registerBlock("tea_tray",
            key -> new TeaTrayBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.WOOD)
                    .strength(0.6F)
                    .noOcclusion()
                    .sound(SoundType.BAMBOO_WOOD)
                    .pushReaction(PushReaction.POPPED)));

    /** 鏅掔洏鐗╁搧锛堟姳鍦ㄦ墜涓婄殑褰㈡€侊紝鐢ㄧ粍浠惰褰曠洏閲岀殑鑼跺彾锛夈€?*/
    public static final DeferredItem<Item> TEA_TRAY_ITEM = registerItem("tea_tray",
            key -> new TeaTrayItem(TEA_TRAY.get(), new Item.Properties().setId(key).stacksTo(1)));

    /** 鎻夎尪鍖炬柟鍧楋細鎻夋嵒 鈫?鍙戦叺銆傚褰笌鏅掔洏涓€鑷达紙鏈夎竟妗嗐€? 鍍忕礌楂橈級銆?*/
    public static final DeferredBlock<Block> TEA_ROLLING_TRAY = registerBlock("tea_rolling_tray",
            key -> new TeaRollingTrayBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.WOOD)
                    .strength(0.6F)
                    .noOcclusion()
                    .sound(SoundType.BAMBOO_WOOD)
                    .pushReaction(PushReaction.POPPED)));

    /** 鎻夎尪鍖剧墿鍝併€?*/
    public static final DeferredItem<Item> TEA_ROLLING_TRAY_ITEM = registerItem("tea_rolling_tray",
            key -> new BlockItem(TEA_ROLLING_TRAY.get(), new Item.Properties().setId(key)));

    /** 纰崇剻妗嗭細鐢ㄦ湪鐐剻鑼剁殑绠卞瓙锛堢被浼肩啍鐐夛級銆?*/
    public static final DeferredBlock<Block> TEA_CHARCOAL_STOVE = registerBlock("tea_charcoal_stove",
            key -> new TeaCharcoalStoveBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.BAMBOO_WOOD)));

    public static final DeferredItem<Item> TEA_CHARCOAL_STOVE_ITEM = registerItem("tea_charcoal_stove",
            key -> new BlockItem(TEA_CHARCOAL_STOVE.get(), new Item.Properties().setId(key)));

    /** 閾侀攨锛氱倰鍒舵弶鎹昏繃鐨勮尪锛屾ā鍨嬫槸鐐艰嵂閿呭帇鍒?2/3 楂樸€?*/
    public static final DeferredBlock<Block> TEA_WOK = registerBlock("tea_wok",
            key -> new TeaWokBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.METAL)
                    .strength(2.0F)
                    .noOcclusion()
                    .sound(SoundType.METAL)));

    public static final DeferredItem<Item> TEA_WOK_ITEM = registerItem("tea_wok",
            key -> new BlockItem(TEA_WOK.get(), new Item.Properties().setId(key)));

    /**
     * 鑼剁洏锛? 涓柟鍧楀ぇ灏忋€?px 楂樼殑鑼跺叿鎽嗕欢锛堢洊纰?+ 涓変釜鏂规澂锛夈€?
     *
     * <p>杩欎竴鐗堝彧鏈夐潤鎬佹ā鍨嬶紱鍚庣画浼氭崲鎴愬甫鏂瑰潡瀹炰綋娓叉煋鍣ㄧ殑鐗堟湰锛?
     * 濂借鐩栫鍜屾澂瀛愯兘鍚勮嚜鐙珛鍔ㄨ捣鏉ャ€?/p>
     */
    public static final DeferredBlock<Block> TEA_SET = registerBlock("tea_set",
            key -> new com.heishandashu.teacraft.block.TeaSetBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F)
                    .noOcclusion()
                    .sound(SoundType.WOOD)));

    public static final DeferredItem<Item> TEA_SET_ITEM = registerItem("tea_set",
            // stacksTo(1)：茶盘不可堆叠。
            //
            // 茶盘每只都带自己的状态（朝向、盖碗、三只杯子、盘上的茶汤），
            // 堆叠会让「拿起来的那一只」和「堆里其它只」共用组件而互相污染。
            // 盖碗和茶杯早就是 stacksTo(1)，茶盘漏了。
            key -> new TeaSetItem(TEA_SET.get(), new Item.Properties().setId(key).stacksTo(1)));

    /**
     * 绐戠倝锛氱儳鍒堕櫠鐡风殑鐮栫獞銆?
     *
     * <p><b>蹇呴』 {@code noOcclusion()}</b>锛氭ā鍨嬩笉鏄弧鏂瑰潡 鈥斺€?鍥涗釜鐡疯剼涔嬮棿鏄┖闅欙紝
     * 鐑熷洷杩樻帰鍑烘柟鍧楅《闈€?b>涓嶅姞鐨勮瘽锛岀浉閭绘柟鍧椾細鎶婄獞鐐夎嚜宸辩殑璐撮潰鍓旈櫎鎺?/b>锛?
     * 琛ㄧ幇灏辨槸銆屽簳閮ㄩ€忔槑銆佽兘鐪嬪埌搴曚笅鐨勫湴闈€嶃€備箣鍓嶆垜璇垽鎴?鏁存柟鍧椾綋绉?婕忎簡杩欎竴鍙ャ€?/p>
     */

    /**
     * 鑼舵锛氭弧鏍兼柟鍧楋紝妗岄潰鍦?y=16锛岃尪鐩樻斁涓婂幓鏃犵紳璐村悎銆?
     *
     * <p>涓嶅垎鏈濆悜锛堝洓闈㈠绉帮級锛岀鎾炵婊℃牸銆?/p>
     */
    public static final DeferredBlock<Block> TEA_TABLE = registerBlock("tea_table",
            key -> new com.heishandashu.teacraft.block.TeaTableBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.WOOD)
                            .strength(1.2F)
                            .noOcclusion()
                            .sound(SoundType.WOOD)));

    public static final DeferredItem<Item> TEA_TABLE_ITEM = registerItem("tea_table",
            key -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                    TEA_TABLE.get(), new Item.Properties().setId(key),
                    "teacraft.tooltip.tea_table",
                    "teacraft.tooltip.tea_table_hint"));

    /**
     * 烧水茶桌：茶桌里嵌了一块岩浆。
     *
     * <p>中心 6×6 像素是岩浆贴面，与桌面齐平。把烧水壶放上去就能烧水 ——
     * 靠 {@code TeaKettleBlock.isHeatSource()} 认这个方块实现。</p>
     *
     * <p><b>不做踩踏伤害</b>：它继承 {@code Block} 而不是 {@code MagmaBlock}，
     * 没有 {@code stepOn} / {@code entityInside} 钩子。岩浆只是「嵌在桌子里的热源」，
     * 不是会烫人的岩浆。</p>
     */
    public static final DeferredBlock<Block> TEA_TABLE_HOT = registerBlock("tea_table_hot",
            key -> new com.heishandashu.teacraft.block.TeaTableHotBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.WOOD)
                            .strength(1.2F)
                            .noOcclusion()
                            .sound(SoundType.WOOD)));

    public static final DeferredItem<Item> TEA_TABLE_HOT_ITEM = registerItem("tea_table_hot",
            key -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                    TEA_TABLE_HOT.get(), new Item.Properties().setId(key),
                    "teacraft.tooltip.tea_table_hot"));

    /** 茶盒：27 个槽位，每槽最多 999 个同种成品茶。 */
    public static final DeferredBlock<Block> TEA_BOX = registerBlock("tea_box",
            key -> new com.heishandashu.teacraft.block.TeaBoxBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.WOOD)
                            .strength(1.0F)
                            .noOcclusion()
                            .sound(SoundType.WOOD)));

    public static final DeferredItem<Item> TEA_BOX_ITEM = registerItem("tea_box",
            key -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                    TEA_BOX.get(), new Item.Properties().setId(key),
                    "teacraft.tooltip.tea_box",
                    "teacraft.tooltip.tea_box_hint"));

    /**
     * 鑼舵锛氬垎鏈濆悜銆佺鎾炵鍙埌搴ч潰锛?~9.5锛夛紝妞呰儗涓嶆尅璺紝绌烘墜鍙抽敭鍧愪笅銆?
     */
    public static final DeferredBlock<Block> TEA_CHAIR = registerBlock("tea_chair",
            key -> new com.heishandashu.teacraft.block.TeaChairBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.WOOD)
                            .strength(1.0F)
                            .noOcclusion()
                            .sound(SoundType.WOOD)));

    public static final DeferredItem<Item> TEA_CHAIR_ITEM = registerItem("tea_chair",
            key -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                    TEA_CHAIR.get(), new Item.Properties().setId(key),
                    "teacraft.tooltip.tea_chair",
                    "teacraft.tooltip.tea_chair_sit"));
    public static final DeferredBlock<Block> CLAY_KILN = registerBlock("clay_kiln",
            key -> new com.heishandashu.teacraft.block.ClayKilnBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.CLAY)
                            .strength(3.5F, 6.0F)
                            .noOcclusion()
                            .sound(SoundType.STONE)));

    public static final DeferredItem<Item> CLAY_KILN_ITEM = registerItem("clay_kiln",
            key -> new PairedBlockItem(CLAY_KILN.get(), new Item.Properties().setId(key),
                    "teacraft.tooltip.clay_kiln_pair"));

    /**
     * 鎷夊澂鏈猴細鎶婇粡鍦熷仛鎴愰櫠鐡峰澂浣撱€?
     *
     * <p>妯″瀷涓嶆槸婊℃柟鍧楋紙鍥涙潯鑵夸箣闂存湁缂濓級锛屽繀椤?{@code noOcclusion()}锛?
     * 鍚﹀垯鐩搁偦鏂瑰潡鐨勮创闈細琚墧闄ゃ€佺湅璧锋潵缂洪潰銆?/p>
     */
    public static final DeferredBlock<Block> POTTER_WHEEL = registerBlock("potter_wheel",
            key -> new com.heishandashu.teacraft.block.PotterWheelBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.WOOD)
                            .strength(2.0F)
                            .noOcclusion()
                            .sound(SoundType.WOOD)));

    public static final DeferredItem<Item> POTTER_WHEEL_ITEM = registerItem("potter_wheel",
            key -> new PairedBlockItem(POTTER_WHEEL.get(), new Item.Properties().setId(key),
                    "teacraft.tooltip.potter_wheel_pair"));

    /** 鐩栫锛氭场鑼跺鍣紝鍙嬁鍦ㄦ墜涓婏紙鑼舵堡璁板湪缁勪欢閲岋級銆?*/
    public static final DeferredItem<Item> GAIWAN = registerItem("gaiwan",
            key -> new com.heishandashu.teacraft.item.GaiwanItem(
                    new Item.Properties().setId(key).stacksTo(1)));

    /** 鑼舵澂锛氫粠鑼剁洏涓婃嬁璧锋潵鍠濈殑閭ｄ竴鍙€?*/
    public static final DeferredItem<Item> TEA_CUP = registerItem("tea_cup",
            key -> new com.heishandashu.teacraft.item.TeaCupItem(
                    new Item.Properties().setId(key).stacksTo(1)));

    /**
     * 銆婅尪宸ヨ壓璇存槑銆?鈥斺€?妯＄粍璇存槑涔︺€?
     *
     * <p>鍙抽敭鎵撳紑鍒嗛〉鐣岄潰锛屽唴瀹瑰叏閮ㄥ湪璇█鏂囦欢閲岋紙{@code teacraft.guide.*}锛夈€?
     * 鍚堟垚锛氫竴棰楄尪鏍戣嫍 + 涓€鏈功銆?/p>
     */
    public static final DeferredItem<Item> TEA_GUIDE = registerItem("tea_guide",
            key -> new com.heishandashu.teacraft.item.TeaGuideItem(
                    new Item.Properties().setId(key).stacksTo(1)));

    /**
     * 鑼跺６锛堣楗帮級鈥斺€?鍙槸鎽嗕欢锛屼笉鑳藉姞鐑€?
     *
     * <p>涓庣儳姘村６鍏辩敤鍚屼竴濂楁ā鍨嬶紝浣?b>瀹冧篃鏄柟鍧?/b>锛?
     * 鏈€鍒濊繖閲屾敞鍐屾垚绾墿鍝侊紝瀵艰嚧瀹冩牴鏈斁涓嶅埌鍦颁笂锛?
     * 寰堝鏄撳拰"鐑ф按澹舵斁涓嶄笂鍘?娣锋穯銆?/p>
     */
    public static final DeferredBlock<Block> TEAPOT_BLOCK = registerBlock("teapot",
            key -> new com.heishandashu.teacraft.block.TeapotBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.TERRACOTTA_WHITE)
                            .strength(1.0F)
                            .noOcclusion()
                            .sound(SoundType.STONE)));

    public static final DeferredItem<Item> TEAPOT = registerItem("teapot",
            key -> new net.minecraft.world.item.BlockItem(TEAPOT_BLOCK.get(),
                    new Item.Properties().setId(key).stacksTo(1)));

    /**
     * 鐑ф按澹讹紙鏂瑰潡褰㈡€侊級锛氭斁鍦ㄥ博娴嗗潡 / 绡濈伀 / 鐕冪儳鐨勭啍鐐変笂鍔犵儹銆?
     *
     * <p>涓庤楗扮敤鐨?{@code teapot} 澶栧舰鍏辩敤鍚屼竴濂楀嚑浣曘€?
     * 杩欓噷鐢ㄣ€屾柟鍧?+ BlockItem銆嶈€屼笉鏄函鐗╁搧锛屽洜涓鸿鏀惧湪鐑簮涓婃寔缁彈鐑€?/p>
     */
    public static final DeferredBlock<Block> TEA_KETTLE = registerBlock("kettle",
            key -> new com.heishandashu.teacraft.block.TeaKettleBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.TERRACOTTA_WHITE)
                            .strength(1.0F)
                            .noOcclusion()
                            .sound(SoundType.STONE)));

    public static final DeferredItem<Item> TEA_KETTLE_ITEM = registerItem("kettle",
            key -> new com.heishandashu.teacraft.item.TeaKettleItem(
                    (com.heishandashu.teacraft.block.TeaKettleBlock) TEA_KETTLE.get(),
                    new Item.Properties().setId(key).stacksTo(1)));

    /**
     * 鏅掔洏鐨勬柟鍧楀疄浣撶被鍨嬨€?
     *
     * <p>26.3 鐨?{@code BlockEntityType} 娌℃湁 Builder锛岀洿鎺?{@code new} 涓€涓€?
     * 鐒跺悗娉ㄥ唽杩?{@code BuiltInRegistries.BLOCK_ENTITY_TYPE}銆?/p>
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeaTrayBlockEntity>> TRAY_BE_TYPE =
            BLOCK_ENTITIES.register("tea_tray", () -> new BlockEntityType<>(
                    TeaTrayBlockEntity::new,
                    java.util.Set.of(TEA_TRAY.get(), TEA_ROLLING_TRAY.get())));


    /** 炭焙筐的方块实体类型。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeaCharcoalStoveBlockEntity>>
            STOVE_BE_TYPE = BLOCK_ENTITIES.register("tea_charcoal_stove", () -> new BlockEntityType<>(
                    TeaCharcoalStoveBlockEntity::new,
                    java.util.Set.of(TEA_CHARCOAL_STOVE.get())));

    /** 炒茶锅的方块实体类型。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeaWokBlockEntity>> WOK_BE_TYPE =
            BLOCK_ENTITIES.register("tea_wok", () -> new BlockEntityType<>(
                    TeaWokBlockEntity::new,
                    java.util.Set.of(TEA_WOK.get())));

    /** 烧水壶的方块实体类型（记水温、烧开状态）。 */
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.heishandashu.teacraft.block.TeaKettleBlockEntity>> KETTLE_BE_TYPE =
            BLOCK_ENTITIES.register("kettle", () -> new BlockEntityType<>(
                    com.heishandashu.teacraft.block.TeaKettleBlockEntity::new,
                    java.util.Set.of(TEA_KETTLE.get())));

    /** 茶盒的方块实体（27 格 x 999）。 */
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.heishandashu.teacraft.block.TeaBoxBlockEntity>> BOX_BE_TYPE =
            BLOCK_ENTITIES.register("tea_box", () -> new BlockEntityType<>(
                    com.heishandashu.teacraft.block.TeaBoxBlockEntity::new,
                    java.util.Set.of(TEA_BOX.get())));


    /** 茶盘的方块实体类型。 */
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.heishandashu.teacraft.block.TeaSetBlockEntity>> SET_BE_TYPE =
            BLOCK_ENTITIES.register("tea_set", () -> new BlockEntityType<>(
                    com.heishandashu.teacraft.block.TeaSetBlockEntity::new,
                    java.util.Set.of(TEA_SET.get())));

    /** 窑炉的方块实体类型。 */
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.heishandashu.teacraft.block.ClayKilnBlockEntity>> CLAY_KILN_BE_TYPE =
            BLOCK_ENTITIES.register("clay_kiln", () -> new BlockEntityType<>(
                    com.heishandashu.teacraft.block.ClayKilnBlockEntity::new,
                    java.util.Set.of(CLAY_KILN.get())));

    /** 拉坯机的方块实体类型。 */
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.heishandashu.teacraft.block.PotterWheelBlockEntity>> POTTER_WHEEL_BE_TYPE =
            BLOCK_ENTITIES.register("potter_wheel", () -> new BlockEntityType<>(
                    com.heishandashu.teacraft.block.PotterWheelBlockEntity::new,
                    java.util.Set.of(POTTER_WHEEL.get())));

    /**
     * 茶椅的隐形座位实体。
     *
     * <p>方块本身不能当载具，所以右键茶椅时生成一个零尺寸的不可见实体，
     * 把玩家挂上去。它完全不可见，但<b>仍然必须注册渲染器</b>
     * （见 EmptyEntityRenderer 的注释：缺渲染器会在渲染线程 NPE 崩溃）。</p>
     *
     * <h2>必须用 of(TeaSeatEntity::new, ...)，不能用 createNothing</h2>
     * <p>{@code createNothing} 的工厂 lambda 返回 null。服务端因为绕过工厂
     * 直接 new，看不出问题；但客户端收到生成包后是用工厂重建实体的：</p>
     * <pre>
     *   EntityType.create(Level, EntitySpawnRequest)
     *     -&gt; this.factory.create(this, level)   // createNothing 这里给 null
     * </pre>
     * <p>结果：客户端没有载具，玩家一坐上去立刻被甩下来。</p>
     *
     * <p>另外 {@code noSave()} 会把 serialize 置 false，
     * startRiding 直接返回 false（连坐都坐不上）。</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heishandashu.teacraft.block.TeaSeatEntity>>
            SEAT_TYPE = ENTITY_TYPES.register("tea_seat", id ->
                    net.minecraft.world.entity.EntityType.Builder
                            .<com.heishandashu.teacraft.block.TeaSeatEntity>of(
                                    com.heishandashu.teacraft.block.TeaSeatEntity::new,
                                    net.minecraft.world.entity.MobCategory.MISC)
                            .sized(0.0F, 0.0F)
                            .noSummon()
                            .build(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ENTITY_TYPE, id)));


    /**
     * 全部物品（创造栏顺序 = 注册顺序）。
     *
     * <h2>为什么是方法而不是静态字段</h2>
     * <p>Fabric 支里它是 {@code public static final List<Item> ALL_ITEMS}，
     * 在类加载时的静态块里就把物品塞进去。那在本支<b>不可能</b>：
     * 字段现在是 {@link DeferredItem}（延迟绑定），静态初始化阶段调 {@code .get()}
     * 会抛 {@code NullPointerException: Trying to access unbound value}。</p>
     *
     * <p>所以改成方法，调用方（创造栏、自检、启动日志）都在注册完成之后才调它。
     * 结果缓存在 {@link #ALL_ITEMS_CACHE} 里，重复调用不重复构建。</p>
     */
    public static List<Item> allItems() {
        List<Item> cached = ALL_ITEMS_CACHE;
        if (cached != null) return cached;

        List<Item> all = new ArrayList<>();
        all.add(TEA_BUSH_ITEM.get());
        all.add(TEA_TRAY_ITEM.get());
        all.add(TEA_ROLLING_TRAY_ITEM.get());
        all.add(TEA_CHARCOAL_STOVE_ITEM.get());
        all.add(TEA_WOK_ITEM.get());
        all.add(TEA_SET_ITEM.get());
        all.add(TEA_TABLE_ITEM.get());
        all.add(TEA_TABLE_HOT_ITEM.get());
        all.add(TEA_BOX_ITEM.get());
        all.add(TEA_CHAIR_ITEM.get());
        all.add(CLAY_KILN_ITEM.get());
        all.add(POTTER_WHEEL_ITEM.get());
        all.add(TEA_GUIDE.get());
        all.add(GAIWAN.get());
        all.add(TEA_CUP.get());
        // 装饰茶壶不进创造栏：它和烧水壶同模型，放出来分不清，容易误当烧水壶
        all.add(TEA_KETTLE_ITEM.get());
        for (TeaOrigin origin : TeaOrigin.values()) all.add(FRESH_LEAVES.get(origin).get());

        // 必须去重：第 2 阶段「摊晾后凋叶」是三条路线共享的，
        // 它会出现在每条链的首位。直接 add 会把它塞进创造栏三次，
        // Minecraft 立刻抛 IllegalStateException:
        //   "Accidentally adding the same item stack twice"
        // 而且这是启动期崩溃（创造栏构建时），不是进游戏才炸。
        java.util.Set<Item> seen = new java.util.LinkedHashSet<>();
        // 先放共享的第 2 阶段，保证它在创造栏里排在各自路线之前
        DeferredItem<? extends Item> shared = STAGE_ITEMS.get(TeaStages.SHARED_WITHER_ID);
        if (shared != null && seen.add(shared.get())) all.add(shared.get());

        for (TeaKind kind : TeaKind.values()) {
            for (TeaStages.Stage stage : TeaStages.chain(kind)) {
                DeferredItem<? extends Item> item = STAGE_ITEMS.get(stage.id());
                if (item != null && seen.add(item.get())) all.add(item.get());
            }
        }

        List<Item> result = Collections.unmodifiableList(all);
        ALL_ITEMS_CACHE = result;
        return result;
    }

    /** {@link #allItems()} 的结果缓存。注册期不初始化。 */
    private static volatile List<Item> ALL_ITEMS_CACHE;

    static {
        for (TeaOrigin origin : TeaOrigin.values()) {
            DeferredItem<? extends Item> item = registerItem(origin.itemId(),
                    key -> new TeaFreshLeafItem(new Item.Properties().setId(key).stacksTo(64), origin));
            FRESH_LEAVES.put(origin, item);
        }

        for (TeaStages.Stage stage : TeaStages.all()) {
            DeferredItem<? extends Item> item = registerItem(stage.id(),
                    // 成品茶可以「喝」：用 TeaDrinkItem（按品质给效果）。
                    // 其余阶段仍是普通 TeaStageItem。
                    key -> stage.isFinished()
                            ? new TeaDrinkItem(new Item.Properties().setId(key).stacksTo(64), stage)
                            : new TeaStageItem(new Item.Properties().setId(key).stacksTo(64), stage));
            STAGE_ITEMS.put(stage.id(), item);
        }
    }

    private TeaItems() {
    }
    /* ---------------- 鏌ヨ鎺ュ彛 ---------------- */

    public static Item freshLeaf(TeaOrigin origin) {
        return FRESH_LEAVES.get(origin).get();
    }

    public static Item stage(TeaStages.Stage stage) {
        return STAGE_ITEMS.get(stage.id()).get();
    }

    public static Item stage(String id) {
        return STAGE_ITEMS.get(id).get();
    }

    public static Map<TeaOrigin, Item> freshLeaves() {
        Map<TeaOrigin, Item> out = new EnumMap<>(TeaOrigin.class);
        FRESH_LEAVES.forEach((k, v) -> out.put(k, v.get()));
        return Collections.unmodifiableMap(out);
    }

    public static Map<String, Item> stageItems() {
        Map<String, Item> out = new LinkedHashMap<>();
        STAGE_ITEMS.forEach((k, v) -> out.put(k, v.get()));
        return Collections.unmodifiableMap(out);
    }

    public static boolean isStageItem(Item item) {
        return item instanceof TeaStageItem;
    }

    public static boolean isFreshLeaf(Item item) {
        return item instanceof TeaFreshLeafItem;
    }

    /* ---------------- 娉ㄥ唽宸ュ叿 ---------------- */

    /**
     * 注册一个方块，返回 {@link DeferredBlock}。
     *
     * <h2>为什么签名跟 Fabric 支几乎一样</h2>
     * <p>26.3 的 {@code BlockBehaviour.Properties.setId(...)} 要的是
     * <b>{@code ResourceKey<Block>}</b>，不是 Identifier。
     * 所以这里保留「先造 ResourceKey 再交给工厂」的形状，
     * 41 个字段的初始化代码（{@code key -> new XxxBlock(... .setId(key) ...)}）
     * 一行都不用改。</p>
     *
     * <p>区别只在返回值和注册方式：交给 {@code DeferredRegister} 延迟绑定，
     * 而不是立刻写进 {@code BuiltInRegistries}。</p>
     */
    private static DeferredBlock<Block> registerBlock(
            String path, java.util.function.Function<ResourceKey<Block>, Block> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TeaCraft.id(path));
        return BLOCKS.register(path, () -> factory.apply(key));
    }

    /** 注册一个物品，返回 {@link DeferredItem}。理由同 {@link #registerBlock}。 */
    private static DeferredItem<Item> registerItem(
            String path, java.util.function.Function<ResourceKey<Item>, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TeaCraft.id(path));
        return ITEMS.register(path, () -> factory.apply(key));
    }

    /**
     * 把四个注册器挂到 mod 事件总线上。
     *
     * <p>⚠ 这和 Fabric 支的 {@code register()} 语义完全不同：Fabric 那边是
     * 「调一下就立刻注册」，这里是「把注册器交给事件总线，等 FML 在合适的
     * 时机触发」。所以<b>本方法必须在 Mod 构造函数里调用</b>，
     * 而且调用之后不能马上 {@code .get()} 任何东西。</p>
     *
     * <p>注册完成后的内容清点放在 {@link #logRegisteredContent()}，
     * 由 FMLCommonSetupEvent 触发（那时已绑定）。</p>
     */
    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITY_TYPES.register(modBus);
        // 注册完成后的绑定 + 清点，都放 FMLCommonSetupEvent（那时 holder 才绑定）
        modBus.addListener(TeaItems::onCommonSetup);
    }

    /**
     * 注册期结束后（{@code FMLCommonSetupEvent}）执行一次的初始化。
     */
    private static void onCommonSetup(
            net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            bindBlockEntityTypes();
            logRegisteredContent();
        });
    }

    /**
     * 把各 BlockEntityType 回填进各自方块实体类的静态字段。
     *
     * <h2>为什么必须等到这里</h2>
     * <p>那些 {@code TYPE} 字段被方块实体的构造函数用（{@code super(TYPE, pos, state)}），
     * 所以它们必须在「有人开始造方块实体」之前就位。</p>
     *
     * <p>但我一开始把赋值写在 <b>static 块</b>里 —— 那是错的：
     * 静态块在类加载时跑，那时 {@code DeferredHolder} 还没绑到注册表，
     * {@code .get()} 直接抛：</p>
     * <pre>
     *   NullPointerException: Trying to access unbound value:
     *   ResourceKey[minecraft:block_entity_type / teacraft:tea_tray]
     * </pre>
     * <p>这个错误编译期完全看不出来，只在服务端启动时炸。
     * 正确时机是 {@code FMLCommonSetupEvent}。</p>
     */
    private static void bindBlockEntityTypes() {
        TeaTrayBlockEntity.TYPE = TRAY_BE_TYPE.get();
        TeaCharcoalStoveBlockEntity.TYPE = STOVE_BE_TYPE.get();
        TeaWokBlockEntity.TYPE = WOK_BE_TYPE.get();
        com.heishandashu.teacraft.block.TeaSetBlockEntity.TYPE = SET_BE_TYPE.get();
        com.heishandashu.teacraft.block.TeaKettleBlockEntity.TYPE = KETTLE_BE_TYPE.get();
        com.heishandashu.teacraft.block.ClayKilnBlockEntity.TYPE = CLAY_KILN_BE_TYPE.get();
        com.heishandashu.teacraft.block.PotterWheelBlockEntity.TYPE = POTTER_WHEEL_BE_TYPE.get();
        com.heishandashu.teacraft.block.TeaBoxBlockEntity.TYPE = BOX_BE_TYPE.get();
        com.heishandashu.teacraft.block.TeaSeatEntity.TYPE = SEAT_TYPE.get();
    }

    /**
     * 注册完成后的清点日志。
     *
     * <p>只在 FMLCommonSetupEvent 里调 —— 那时 {@code .get()} 才是安全的。
     * 如果内容没注册上（例如缺 {@code setId}），这里会立刻暴露。</p>
     */
    public static void logRegisteredContent() {
        var items = allItems();
        if (items.isEmpty()) {
            throw new IllegalStateException("teacraft: content failed to register");
        }
        TeaCraft.LOGGER.info("[teacraft] 注册完成：{} 个物品（含 {} 个阶段物品、{} 个产地鲜叶）",
                items.size(), STAGE_ITEMS.size(), FRESH_LEAVES.size());
    }
}
