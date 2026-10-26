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

    /** 椴滃彾锛氫骇鍦?-> 鐗╁搧銆?*/
    private static final Map<TeaOrigin, Item> FRESH_LEAVES = new EnumMap<>(TeaOrigin.class);
    /** 闃舵鐗╁搧锛歩d -> 鐗╁搧銆?*/
    private static final Map<String, Item> STAGE_ITEMS = new LinkedHashMap<>();

    /** 鑼舵爲鏂瑰潡銆?*/
    public static final Block TEA_BUSH = registerBlock("tea_bush",
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
    public static final Item TEA_BUSH_ITEM = registerItem("tea_bush",
            key -> new BlockItem(TEA_BUSH, new Item.Properties().setId(key)));

    /** 鏅掔洏鏂瑰潡锛?6脳3脳16 鐨勭缂栧渾鐩橈紝鏀惧湪鏂瑰潡椤堕潰銆?*/
    public static final Block TEA_TRAY = registerBlock("tea_tray",
            key -> new TeaTrayBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.WOOD)
                    .strength(0.6F)
                    .noOcclusion()
                    .sound(SoundType.BAMBOO_WOOD)
                    .pushReaction(PushReaction.POPPED)));

    /** 鏅掔洏鐗╁搧锛堟姳鍦ㄦ墜涓婄殑褰㈡€侊紝鐢ㄧ粍浠惰褰曠洏閲岀殑鑼跺彾锛夈€?*/
    public static final Item TEA_TRAY_ITEM = registerItem("tea_tray",
            key -> new TeaTrayItem(TEA_TRAY, new Item.Properties().setId(key).stacksTo(1)));

    /** 鎻夎尪鍖炬柟鍧楋細鎻夋嵒 鈫?鍙戦叺銆傚褰笌鏅掔洏涓€鑷达紙鏈夎竟妗嗐€? 鍍忕礌楂橈級銆?*/
    public static final Block TEA_ROLLING_TRAY = registerBlock("tea_rolling_tray",
            key -> new TeaRollingTrayBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.WOOD)
                    .strength(0.6F)
                    .noOcclusion()
                    .sound(SoundType.BAMBOO_WOOD)
                    .pushReaction(PushReaction.POPPED)));

    /** 鎻夎尪鍖剧墿鍝併€?*/
    public static final Item TEA_ROLLING_TRAY_ITEM = registerItem("tea_rolling_tray",
            key -> new BlockItem(TEA_ROLLING_TRAY, new Item.Properties().setId(key)));

    /** 纰崇剻妗嗭細鐢ㄦ湪鐐剻鑼剁殑绠卞瓙锛堢被浼肩啍鐐夛級銆?*/
    public static final Block TEA_CHARCOAL_STOVE = registerBlock("tea_charcoal_stove",
            key -> new TeaCharcoalStoveBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.BAMBOO_WOOD)));

    public static final Item TEA_CHARCOAL_STOVE_ITEM = registerItem("tea_charcoal_stove",
            key -> new BlockItem(TEA_CHARCOAL_STOVE, new Item.Properties().setId(key)));

    /** 閾侀攨锛氱倰鍒舵弶鎹昏繃鐨勮尪锛屾ā鍨嬫槸鐐艰嵂閿呭帇鍒?2/3 楂樸€?*/
    public static final Block TEA_WOK = registerBlock("tea_wok",
            key -> new TeaWokBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.METAL)
                    .strength(2.0F)
                    .noOcclusion()
                    .sound(SoundType.METAL)));

    public static final Item TEA_WOK_ITEM = registerItem("tea_wok",
            key -> new BlockItem(TEA_WOK, new Item.Properties().setId(key)));

    /**
     * 鑼剁洏锛? 涓柟鍧楀ぇ灏忋€?px 楂樼殑鑼跺叿鎽嗕欢锛堢洊纰?+ 涓変釜鏂规澂锛夈€?
     *
     * <p>杩欎竴鐗堝彧鏈夐潤鎬佹ā鍨嬶紱鍚庣画浼氭崲鎴愬甫鏂瑰潡瀹炰綋娓叉煋鍣ㄧ殑鐗堟湰锛?
     * 濂借鐩栫鍜屾澂瀛愯兘鍚勮嚜鐙珛鍔ㄨ捣鏉ャ€?/p>
     */
    public static final Block TEA_SET = registerBlock("tea_set",
            key -> new com.heishandashu.teacraft.block.TeaSetBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F)
                    .noOcclusion()
                    .sound(SoundType.WOOD)));

    public static final Item TEA_SET_ITEM = registerItem("tea_set",
            // stacksTo(1)：茶盘不可堆叠。
            //
            // 茶盘每只都带自己的状态（朝向、盖碗、三只杯子、盘上的茶汤），
            // 堆叠会让「拿起来的那一只」和「堆里其它只」共用组件而互相污染。
            // 盖碗和茶杯早就是 stacksTo(1)，茶盘漏了。
            key -> new TeaSetItem(TEA_SET, new Item.Properties().setId(key).stacksTo(1)));

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
    public static final Block TEA_TABLE = registerBlock("tea_table",
            key -> new com.heishandashu.teacraft.block.TeaTableBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.WOOD)
                            .strength(1.2F)
                            .noOcclusion()
                            .sound(SoundType.WOOD)));

    public static final Item TEA_TABLE_ITEM = registerItem("tea_table",
            key -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                    TEA_TABLE, new Item.Properties().setId(key),
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
    public static final Block TEA_TABLE_HOT = registerBlock("tea_table_hot",
            key -> new com.heishandashu.teacraft.block.TeaTableHotBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.WOOD)
                            .strength(1.2F)
                            .noOcclusion()
                            .sound(SoundType.WOOD)));

    public static final Item TEA_TABLE_HOT_ITEM = registerItem("tea_table_hot",
            key -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                    TEA_TABLE_HOT, new Item.Properties().setId(key),
                    "teacraft.tooltip.tea_table_hot"));

    /** 茶盒：27 个槽位，每槽最多 999 个同种成品茶。 */
    public static final Block TEA_BOX = registerBlock("tea_box",
            key -> new com.heishandashu.teacraft.block.TeaBoxBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.WOOD)
                            .strength(1.0F)
                            .noOcclusion()
                            .sound(SoundType.WOOD)));

    public static final Item TEA_BOX_ITEM = registerItem("tea_box",
            key -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                    TEA_BOX, new Item.Properties().setId(key),
                    "teacraft.tooltip.tea_box",
                    "teacraft.tooltip.tea_box_hint"));

    /**
     * 鑼舵锛氬垎鏈濆悜銆佺鎾炵鍙埌搴ч潰锛?~9.5锛夛紝妞呰儗涓嶆尅璺紝绌烘墜鍙抽敭鍧愪笅銆?
     */
    public static final Block TEA_CHAIR = registerBlock("tea_chair",
            key -> new com.heishandashu.teacraft.block.TeaChairBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.WOOD)
                            .strength(1.0F)
                            .noOcclusion()
                            .sound(SoundType.WOOD)));

    public static final Item TEA_CHAIR_ITEM = registerItem("tea_chair",
            key -> new com.heishandashu.teacraft.item.DescribedBlockItem(
                    TEA_CHAIR, new Item.Properties().setId(key),
                    "teacraft.tooltip.tea_chair",
                    "teacraft.tooltip.tea_chair_sit"));
    public static final Block CLAY_KILN = registerBlock("clay_kiln",
            key -> new com.heishandashu.teacraft.block.ClayKilnBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.CLAY)
                            .strength(3.5F, 6.0F)
                            .noOcclusion()
                            .sound(SoundType.STONE)));

    public static final Item CLAY_KILN_ITEM = registerItem("clay_kiln",
            key -> new PairedBlockItem(CLAY_KILN, new Item.Properties().setId(key),
                    "teacraft.tooltip.clay_kiln_pair"));

    /**
     * 鎷夊澂鏈猴細鎶婇粡鍦熷仛鎴愰櫠鐡峰澂浣撱€?
     *
     * <p>妯″瀷涓嶆槸婊℃柟鍧楋紙鍥涙潯鑵夸箣闂存湁缂濓級锛屽繀椤?{@code noOcclusion()}锛?
     * 鍚﹀垯鐩搁偦鏂瑰潡鐨勮创闈細琚墧闄ゃ€佺湅璧锋潵缂洪潰銆?/p>
     */
    public static final Block POTTER_WHEEL = registerBlock("potter_wheel",
            key -> new com.heishandashu.teacraft.block.PotterWheelBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.WOOD)
                            .strength(2.0F)
                            .noOcclusion()
                            .sound(SoundType.WOOD)));

    public static final Item POTTER_WHEEL_ITEM = registerItem("potter_wheel",
            key -> new PairedBlockItem(POTTER_WHEEL, new Item.Properties().setId(key),
                    "teacraft.tooltip.potter_wheel_pair"));

    /** 鐩栫锛氭场鑼跺鍣紝鍙嬁鍦ㄦ墜涓婏紙鑼舵堡璁板湪缁勪欢閲岋級銆?*/
    public static final Item GAIWAN = registerItem("gaiwan",
            key -> new com.heishandashu.teacraft.item.GaiwanItem(
                    new Item.Properties().setId(key).stacksTo(1)));

    /** 鑼舵澂锛氫粠鑼剁洏涓婃嬁璧锋潵鍠濈殑閭ｄ竴鍙€?*/
    public static final Item TEA_CUP = registerItem("tea_cup",
            key -> new com.heishandashu.teacraft.item.TeaCupItem(
                    new Item.Properties().setId(key).stacksTo(1)));

    /**
     * 銆婅尪宸ヨ壓璇存槑銆?鈥斺€?妯＄粍璇存槑涔︺€?
     *
     * <p>鍙抽敭鎵撳紑鍒嗛〉鐣岄潰锛屽唴瀹瑰叏閮ㄥ湪璇█鏂囦欢閲岋紙{@code teacraft.guide.*}锛夈€?
     * 鍚堟垚锛氫竴棰楄尪鏍戣嫍 + 涓€鏈功銆?/p>
     */
    public static final Item TEA_GUIDE = registerItem("tea_guide",
            key -> new com.heishandashu.teacraft.item.TeaGuideItem(
                    new Item.Properties().setId(key).stacksTo(1)));

    /**
     * 鑼跺６锛堣楗帮級鈥斺€?鍙槸鎽嗕欢锛屼笉鑳藉姞鐑€?
     *
     * <p>涓庣儳姘村６鍏辩敤鍚屼竴濂楁ā鍨嬶紝浣?b>瀹冧篃鏄柟鍧?/b>锛?
     * 鏈€鍒濊繖閲屾敞鍐屾垚绾墿鍝侊紝瀵艰嚧瀹冩牴鏈斁涓嶅埌鍦颁笂锛?
     * 寰堝鏄撳拰"鐑ф按澹舵斁涓嶄笂鍘?娣锋穯銆?/p>
     */
    public static final Block TEAPOT_BLOCK = registerBlock("teapot",
            key -> new com.heishandashu.teacraft.block.TeapotBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.TERRACOTTA_WHITE)
                            .strength(1.0F)
                            .noOcclusion()
                            .sound(SoundType.STONE)));

    public static final Item TEAPOT = registerItem("teapot",
            key -> new net.minecraft.world.item.BlockItem(TEAPOT_BLOCK,
                    new Item.Properties().setId(key).stacksTo(1)));

    /**
     * 鐑ф按澹讹紙鏂瑰潡褰㈡€侊級锛氭斁鍦ㄥ博娴嗗潡 / 绡濈伀 / 鐕冪儳鐨勭啍鐐変笂鍔犵儹銆?
     *
     * <p>涓庤楗扮敤鐨?{@code teapot} 澶栧舰鍏辩敤鍚屼竴濂楀嚑浣曘€?
     * 杩欓噷鐢ㄣ€屾柟鍧?+ BlockItem銆嶈€屼笉鏄函鐗╁搧锛屽洜涓鸿鏀惧湪鐑簮涓婃寔缁彈鐑€?/p>
     */
    public static final Block TEA_KETTLE = registerBlock("kettle",
            key -> new com.heishandashu.teacraft.block.TeaKettleBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.TERRACOTTA_WHITE)
                            .strength(1.0F)
                            .noOcclusion()
                            .sound(SoundType.STONE)));

    public static final Item TEA_KETTLE_ITEM = registerItem("kettle",
            key -> new com.heishandashu.teacraft.item.TeaKettleItem(
                    (com.heishandashu.teacraft.block.TeaKettleBlock) TEA_KETTLE,
                    new Item.Properties().setId(key).stacksTo(1)));

    /**
     * 鏅掔洏鐨勬柟鍧楀疄浣撶被鍨嬨€?
     *
     * <p>26.3 鐨?{@code BlockEntityType} 娌℃湁 Builder锛岀洿鎺?{@code new} 涓€涓€?
     * 鐒跺悗娉ㄥ唽杩?{@code BuiltInRegistries.BLOCK_ENTITY_TYPE}銆?/p>
     */
    public static final BlockEntityType<TeaTrayBlockEntity> TRAY_BE_TYPE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TeaCraft.id("tea_tray"),
                    new BlockEntityType<>(TeaTrayBlockEntity::new,
                            java.util.Set.of(TEA_TRAY, TEA_ROLLING_TRAY)));

    static {
        // 鏂瑰潡瀹炰綋鐨勬瀯閫犲嚱鏁拌鎷垮埌绫诲瀷锛屼絾绫诲瀷鍙堥渶瑕佹柟鍧楀厛瀛樺湪 鈥斺€?鍦ㄨ繖閲岃ˉ涓婅繖涓洖鎸囥€?
        // 灏戜簡杩欎竴琛岋紝鏀句笅鏅掔洏鏃朵細 NPE锛欱lockEntityType.isValid(...) because "this.type" is null
        TeaTrayBlockEntity.TYPE = TRAY_BE_TYPE;
    }

    /** 纰崇剻妗嗙殑鏂瑰潡瀹炰綋绫诲瀷銆?*/
    public static final BlockEntityType<TeaCharcoalStoveBlockEntity> STOVE_BE_TYPE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TeaCraft.id("tea_charcoal_stove"),
                    new BlockEntityType<>(TeaCharcoalStoveBlockEntity::new,
                            java.util.Set.of(TEA_CHARCOAL_STOVE)));

    /** 閾侀攨鐨勬柟鍧楀疄浣撶被鍨嬨€?*/
    public static final BlockEntityType<TeaWokBlockEntity> WOK_BE_TYPE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TeaCraft.id("tea_wok"),
                    new BlockEntityType<>(TeaWokBlockEntity::new, java.util.Set.of(TEA_WOK)));

    /** 鐑ф按澹剁殑鏂瑰潡瀹炰綋绫诲瀷锛堣姘撮噺涓庡姞鐑繘搴︼級銆?*/
    public static final BlockEntityType<com.heishandashu.teacraft.block.TeaKettleBlockEntity> KETTLE_BE_TYPE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TeaCraft.id("kettle"),
                    new BlockEntityType<>(com.heishandashu.teacraft.block.TeaKettleBlockEntity::new,
                            java.util.Set.of(TEA_KETTLE)));

    /** 茶盒的方块实体（27 槽 x 999）。 */
    public static final BlockEntityType<com.heishandashu.teacraft.block.TeaBoxBlockEntity> BOX_BE_TYPE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TeaCraft.id("tea_box"),
                    new BlockEntityType<>(
                            com.heishandashu.teacraft.block.TeaBoxBlockEntity::new,
                            java.util.Set.of(TEA_BOX)));

    static {
        com.heishandashu.teacraft.block.TeaBoxBlockEntity.TYPE = BOX_BE_TYPE;
    }
    /** 鑼剁洏鐨勬柟鍧楀疄浣撶被鍨嬶紙瀛樼洊纰楀拰涓変釜鏉瓙閲岀殑鑼舵堡锛夈€?*/
    public static final BlockEntityType<com.heishandashu.teacraft.block.TeaSetBlockEntity> SET_BE_TYPE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TeaCraft.id("tea_set"),
                    new BlockEntityType<>(com.heishandashu.teacraft.block.TeaSetBlockEntity::new,
                            java.util.Set.of(TEA_SET)));

    /** 绐戠倝鐨勬柟鍧楀疄浣撶被鍨嬨€?*/
    public static final BlockEntityType<com.heishandashu.teacraft.block.ClayKilnBlockEntity>
            CLAY_KILN_BE_TYPE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TeaCraft.id("clay_kiln"),
                    new BlockEntityType<>(
                            com.heishandashu.teacraft.block.ClayKilnBlockEntity::new,
                            java.util.Set.of(CLAY_KILN)));

    /** 鎷夊澂鏈虹殑鏂瑰潡瀹炰綋绫诲瀷銆?*/
    public static final BlockEntityType<com.heishandashu.teacraft.block.PotterWheelBlockEntity>
            POTTER_WHEEL_BE_TYPE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TeaCraft.id("potter_wheel"),
                    new BlockEntityType<>(
                            com.heishandashu.teacraft.block.PotterWheelBlockEntity::new,
                            java.util.Set.of(POTTER_WHEEL)));

    /**
     * 妞呭瓙搴т綅瀹炰綋銆?
     *
     * <p>鍧愪笅蹇呴』闈犮€岄獞涔樹竴涓疄浣撱€嶅疄鐜帮紝鏂瑰潡鏈韩褰撲笉浜嗚浇鍏枫€傛墍浠ユ瀛愭湁绌烘墜
     * 鍙抽敭鏃剁敓鎴愯繖涓笉鍙瀹炰綋锛岀帺瀹堕獞涓婂幓灏辫鍥哄畾鍦ㄥ骇闈㈤珮搴︺€?/p>
     *
     * <p>{@code createNothing} + {@code sized} + {@code noSummon}锛?
     * 涓嶈兘鐢?/summon 鍒峰嚭鏉ワ紝涔熶笉鍙備笌鑷劧鐢熸垚銆?/p>
     */
    public static final EntityType<com.heishandashu.teacraft.block.TeaSeatEntity> SEAT_TYPE =
            Registry.register(
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE,
                    TeaCraft.id("tea_seat"),
                    net.minecraft.world.entity.EntityType.Builder
                            .<com.heishandashu.teacraft.block.TeaSeatEntity>createNothing(
                                    net.minecraft.world.entity.MobCategory.MISC)
                            .sized(0.0F, 0.0F)
                            .noSummon()
                            .noSave()
                            .build(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ENTITY_TYPE,
                                    TeaCraft.id("tea_seat"))));

    static {
        TeaCharcoalStoveBlockEntity.TYPE = STOVE_BE_TYPE;
        TeaWokBlockEntity.TYPE = WOK_BE_TYPE;
        com.heishandashu.teacraft.block.TeaSetBlockEntity.TYPE = SET_BE_TYPE;
        com.heishandashu.teacraft.block.TeaKettleBlockEntity.TYPE = KETTLE_BE_TYPE;
        com.heishandashu.teacraft.block.ClayKilnBlockEntity.TYPE = CLAY_KILN_BE_TYPE;
        com.heishandashu.teacraft.block.PotterWheelBlockEntity.TYPE = POTTER_WHEEL_BE_TYPE;
        com.heishandashu.teacraft.block.TeaSeatEntity.TYPE = SEAT_TYPE;
    }

    /** 鍏ㄩ儴鐗╁搧锛堝垱閫犳爮椤哄簭 = 娉ㄥ唽椤哄簭锛夈€?*/
    public static final List<Item> ALL_ITEMS;

    static {
        for (TeaOrigin origin : TeaOrigin.values()) {
            Item item = registerItem(origin.itemId(),
                    key -> new TeaFreshLeafItem(new Item.Properties().setId(key).stacksTo(64), origin));
            FRESH_LEAVES.put(origin, item);
        }

        for (TeaStages.Stage stage : TeaStages.all()) {
            Item item = registerItem(stage.id(),
                    // 鎴愬搧鑼跺彲浠ャ€屽枬銆嶏細鐢?TeaDrinkItem锛堟寜鍝佽川缁欐晥鏋滐級銆?
            // 鍏朵綑闃舵浠嶆槸鏅€?TeaStageItem銆?
            key -> stage.isFinished()
                    ? new TeaDrinkItem(new Item.Properties().setId(key).stacksTo(64), stage)
                    : new TeaStageItem(new Item.Properties().setId(key).stacksTo(64), stage));
            STAGE_ITEMS.put(stage.id(), item);
        }

        List<Item> all = new ArrayList<>();
        all.add(TEA_BUSH_ITEM);
        all.add(TEA_TRAY_ITEM);
        all.add(TEA_ROLLING_TRAY_ITEM);
        all.add(TEA_CHARCOAL_STOVE_ITEM);
        all.add(TEA_WOK_ITEM);
        all.add(TEA_SET_ITEM);
        all.add(TEA_TABLE_ITEM);
        all.add(TEA_TABLE_HOT_ITEM);
        all.add(TEA_BOX_ITEM);
        all.add(TEA_CHAIR_ITEM);
        all.add(CLAY_KILN_ITEM);
        all.add(POTTER_WHEEL_ITEM);
        all.add(TEA_GUIDE);
        all.add(GAIWAN);
        all.add(TEA_CUP);
        // 瑁呴グ鑼跺６涓嶈繘鍒涢€犳爮锛氬畠鍜岀儳姘村６鍚屾ā鍨嬶紝鏀惧嚭鏉ュ垎涓嶆竻锛屽鏄撹褰撴垚鐑ф按澹?
        all.add(TEA_KETTLE_ITEM);
        for (TeaOrigin origin : TeaOrigin.values()) all.add(FRESH_LEAVES.get(origin));

        // 鈿?蹇呴』鍘婚噸锛氱 2 闃舵銆屾憡鏅捐悗鍑嬪彾銆嶆槸涓夋潯璺嚎鍏变韩鐨勶紝
        // 瀹冧細鍑虹幇鍦ㄦ瘡鏉￠摼鐨勯浣嶃€傜洿鎺?add 浼氭妸瀹冨杩涘垱閫犳爮涓夋锛?
        // Minecraft 绔嬪埢鎶?IllegalStateException:
        //   "Accidentally adding the same item stack twice"
        // 骞朵笖杩欐槸<b>鍚姩鏈熷穿婧?/b>锛堝垱閫犳爮鏋勫缓鏃讹級锛屼笉鏄繘娓告垙鎵嶇偢銆?
        java.util.Set<Item> seen = new java.util.LinkedHashSet<>();
        // 鍏堟斁鍏变韩鐨勭 2 闃舵锛屼繚璇佸畠鍦ㄥ垱閫犳爮閲屾帓鍦ㄥ悇鑷矾绾夸箣鍓?
        Item shared = STAGE_ITEMS.get(TeaStages.SHARED_WITHER_ID);
        if (shared != null && seen.add(shared)) all.add(shared);

        for (TeaKind kind : TeaKind.values()) {
            for (TeaStages.Stage stage : TeaStages.chain(kind)) {
                Item item = STAGE_ITEMS.get(stage.id());
                if (item != null && seen.add(item)) all.add(item);
            }
        }
        ALL_ITEMS = Collections.unmodifiableList(all);
    }

    private TeaItems() {
    }

    /* ---------------- 鏌ヨ鎺ュ彛 ---------------- */

    public static Item freshLeaf(TeaOrigin origin) {
        return FRESH_LEAVES.get(origin);
    }

    public static Item stage(TeaStages.Stage stage) {
        return STAGE_ITEMS.get(stage.id());
    }

    public static Item stage(String id) {
        return STAGE_ITEMS.get(id);
    }

    public static Map<TeaOrigin, Item> freshLeaves() {
        return Collections.unmodifiableMap(FRESH_LEAVES);
    }

    public static Map<String, Item> stageItems() {
        return Collections.unmodifiableMap(STAGE_ITEMS);
    }

    public static boolean isStageItem(Item item) {
        return item instanceof TeaStageItem;
    }

    public static boolean isFreshLeaf(Item item) {
        return item instanceof TeaFreshLeafItem;
    }

    /* ---------------- 娉ㄥ唽宸ュ叿 ---------------- */

    private static Block registerBlock(String path, java.util.function.Function<ResourceKey<Block>, Block> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TeaCraft.id(path));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(key));
    }

    private static Item registerItem(String path, java.util.function.Function<ResourceKey<Item>, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TeaCraft.id(path));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(key));
    }

    /** 瑙﹀彂绫诲姞杞斤細闈欐€佸瓧娈靛湪绫诲垵濮嬪寲鏃跺畬鎴愭敞鍐屻€?*/
    public static void register() {
        if (ALL_ITEMS.isEmpty()) {
            throw new IllegalStateException("teacraft: content failed to register");
        }
        TeaCraft.LOGGER.info("[teacraft] 娉ㄥ唽瀹屾垚锛歿} 涓墿鍝侊紙鍚?{} 涓樁娈电墿鍝併€亄} 涓骇鍦伴矞鍙讹級",
                ALL_ITEMS.size(), STAGE_ITEMS.size(), FRESH_LEAVES.size());
    }
}
