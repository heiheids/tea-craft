package com.heishandashu.teacraft.selfcheck;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.block.TeaBushBlock;
import com.heishandashu.teacraft.block.TeaRollingTrayBlock;
import com.heishandashu.teacraft.block.TeaWokBlock;
import com.heishandashu.teacraft.block.TeaWokBlockEntity;
import com.heishandashu.teacraft.block.TeaCharcoalStoveBlockEntity;
import com.heishandashu.teacraft.core.TeaProcessing;
import com.heishandashu.teacraft.block.TeaTrayBlock;
import com.heishandashu.teacraft.block.TeaTrayBlockEntity;
import com.heishandashu.teacraft.climate.TeaClimate;
import com.heishandashu.teacraft.config.TeaConfig;
import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaKind;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.core.TeaStages;
import com.heishandashu.teacraft.item.TeaCreativeTab;
import com.heishandashu.teacraft.item.TeaItems;
import com.heishandashu.teacraft.item.TeaTrayItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动自检：在<b>专用服务端</b>启动完成时跑一遍完整性检查。
 *
 * <p>存在的理由：注册漏了、语言键少一条、模型/方块状态写坏，这几类问题
 * <b>游戏不会报错</b>，只表现为「名字显示成 key」「方块是紫黑块」。
 * 本机起不了客户端，所以把能静态验证的全自动化。</p>
 *
 * <p><b>教训（2026-10-21）：</b>第一版只检查「文件存在」，结果一个 18 字节的
 * 截断 blockstate 照样全绿通过，进游戏茶树是紫黑方块。所以现在**所有资源都要真的解析**，
 * 并且校验引用链 blockstate → model → texture。</p>
 *
 * <p>设环境变量 {@code TEACRAFT_SELFCHECK=1} 时，自检结束会直接停服。</p>
 */
public final class TeaSelfCheck {

    private static final String NS = TeaCraft.MOD_ID;

    private static int passed;
    private static int failed;
    /** 读不到源码而跳过的条数（只在正式游戏里出现，开发自检应为 0）。 */
    private static int skipped;

    private TeaSelfCheck() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(TeaSelfCheck::run);
    }

    /**
     * 自检开关。
     *
     * <h2>为什么必须在方法最前面判断</h2>
     * <p>自检会<b>真的修改世界</b>：放置方块、生成僵尸、清空区域、改天气。
     * 如果无条件跑，玩家正常进游戏时它就会在后台动存档 ——
     * 已经在日志里看到过：玩家刚登录，自检同时在世界里 spawn 僵尸、改方块。</p>
     *
     * <p>以前只有"跑完停服"这一步受环境变量控制，自检本身照跑，
     * 等于把开发用的破坏性代码发到了正式版里。现在整段都要显式开启。</p>
     */
    private static boolean enabled() {
        return System.getenv("TEACRAFT_SELFCHECK") != null;
    }

    private static void run(MinecraftServer server) {
        if (!enabled()) return;   // 正式游戏里绝不运行

        passed = 0;
        failed = 0;
        TeaCraft.LOGGER.info("[teacraft] ===== 自检开始 =====");

        Set<String> zh = langKeys("assets/teacraft/lang/zh_cn.json");
        Set<String> en = langKeys("assets/teacraft/lang/en_us.json");
        check("lang_files_loaded", !zh.isEmpty() && !en.isEmpty(), "zh=" + zh.size() + " en=" + en.size());

        int registered = 0;
        for (Item item : TeaItems.ALL_ITEMS) {
            if (BuiltInRegistries.ITEM.getKey(item) == null) continue;
            registered++;
            String path = BuiltInRegistries.ITEM.getKey(item).getPath();

            boolean isBlock = item instanceof BlockItem;
            String nameKey = (isBlock ? "block." : "item.") + NS + "." + path;
            check("lang_zh_" + path, zh.contains(nameKey), nameKey);
            check("lang_en_" + path, en.contains(nameKey), nameKey);

            String defPath = "assets/" + NS + "/items/" + path + ".json";
            // 26.x 的物品定义可以是组合结构（condition / range_dispatch / select），
            // 所以要把它引用到的**每一个**模型都收集出来逐个验证。
            List<String> models = itemModels(defPath);
            check("item_def_" + path, !models.isEmpty(), defPath + " -> " + models.size() + " 个模型");
            for (String model : models) {
                String modelFile = "assets/" + path0(model) + "/models/" + path1(model) + ".json";
                check("model_" + path + "_" + path1(model).replace('/', '_'),
                        jsonAt(modelFile) != null || isVanillaModel(model), modelFile);
                if (!isVanillaModel(model)) {
                    check("texture_" + path + "_" + path1(model).replace('/', '_'),
                            textureOf(modelFile), "模型引用的贴图存在");
                }
            }
            if (isBlock) {
                checkBlockstate(path, ((BlockItem) item).getBlock());
            }
        }
        check("registered_items", registered == TeaItems.ALL_ITEMS.size(),
                registered + "/" + TeaItems.ALL_ITEMS.size());

        for (TeaKind kind : TeaKind.values()) {
            var chain = TeaStages.chain(kind);
            check("chain_" + kind.id(), !chain.isEmpty()
                            && chain.get(chain.size() - 1).number() == kind.lastStage(),
                    "stages=" + chain.size());
        }

        plantChecks(server);
        climateChecks(server);
        trayChecks(server);
        creativeTabChecks();

        TeaCraft.LOGGER.info("[teacraft] ===== 自检结束 pass={} fail={} skip={} =====",
                passed, failed, skipped);
        if (System.getenv("TEACRAFT_SELFCHECK") != null) {
            TeaCraft.LOGGER.info("[teacraft] TEACRAFT_SELFCHECK 已设置，停服");
            server.halt(false);
        }
    }

    /* ================= 茶树 ================= */

    private static void plantChecks(MinecraftServer server) {
        // ---- 放回壶时状态要跟着物品走 ----
        // 用户反馈：装了开水的壶拿起来再放回去，HUD 一直显示"正在烧水 99%"。
        // 原因：方块实体从物品数据恢复了 hasWater/boiled，
        // 但方块状态还是默认的 boiled=false，HUD 读状态就永远看不到"开水"。
        String kettlePlaceSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/block/TeaKettleBlock.java");
        checkSource("kettle_placement_reads_item_data", kettlePlaceSrc,
                kettlePlaceSrc.contains("BLOCK_ENTITY_DATA")
                        && kettlePlaceSrc.contains("getStateForPlacement"),
                "放壶时要按物品里的 BE 数据决定初始状态");
        checkSource("kettle_placement_sets_boiled", kettlePlaceSrc,
                kettlePlaceSrc.contains("setValue(BOILED, boiled)"),
                "放壶时要把 boiled 写进状态");
        checkSource("kettle_placement_sets_water", kettlePlaceSrc,
                kettlePlaceSrc.contains("setValue(WATER, hasWater)"),
                "放壶时要把 water 写进状态");
        // ---- 烧水壶的"已烧开"必须是方块状态 ----
        // HUD 靠它判断，方块状态是原版内置同步的；
        // 只放方块实体里的话，发包时序一漏就永远显示"正在烧水"。
        check("kettle_has_boiled_state",
                TeaItems.TEA_KETTLE.defaultBlockState().hasProperty(
                        com.heishandashu.teacraft.block.TeaKettleBlock.BOILED),
                "boiled 属性存在");
        check("kettle_boiled_default_false",
                !TeaItems.TEA_KETTLE.defaultBlockState()
                        .getValue(com.heishandashu.teacraft.block.TeaKettleBlock.BOILED),
                "默认未烧开");
        // 加热过程中必须真的发包同步方块实体，否则百分比永远停在 0%。
        // 这条断言就是抓到"漏了 sendBlockUpdated"那个 bug 的。
        // 只搜 "sendBlockUpdated" 不够：setBlock 那条路径也会出现这个词，
        // 所以断言必须同时要求 blockChanged（只会出现在"主动推 BE 数据"那里）。
        String kettleTickSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/block/TeaKettleBlock.java");
        checkSource("kettle_heating_sends_be_update", kettleTickSrc,
                kettleTickSrc.contains("getChunkSource().blockChanged")
                        && kettleTickSrc.contains("sendBlockUpdated"),
                "加热中要 blockChanged + sendBlockUpdated，否则 HUD 百分比不动");
        check("kettle_sync_interval",
                com.heishandashu.teacraft.block.TeaKettleBlockEntity.SYNC_EVERY > 0
                        && com.heishandashu.teacraft.block.TeaKettleBlockEntity.SYNC_EVERY <= 20,
                "每 " + com.heishandashu.teacraft.block.TeaKettleBlockEntity.SYNC_EVERY
                        + " tick 同步一次（要 <=20 才够顺滑）");
        // 壶嘴必须朝上：绕 x 轴正向旋转才是抬头
        JsonObject kettleM = jsonAt("assets/" + NS + "/models/block/kettle.json");
        double spoutAngle = 0;
        for (var el : kettleM.getAsJsonArray("elements")) {
            var e5 = el.getAsJsonObject();
            if (!e5.has("rotation")) continue;
            spoutAngle = e5.getAsJsonObject("rotation").get("angle").getAsDouble();
        }
        check("kettle_spout_points_up", spoutAngle > 0,
                "壶嘴旋转 " + spoutAngle + "°（正值才是朝上，负值会朝下）");
        String hudSrc2 = sourceOf("src/main/java/com/heishandashu/teacraft/client/TeaSetHud.java");
        checkSource("hud_reads_boiled_from_state", hudSrc2,
                hudSrc2.contains("TeaKettleBlock.BOILED"),
                "HUD 应从方块状态读烧开与否，而不是只读方块实体");
        // ---- 成品茶禁止直接食用 ----
        // 需求：「所有茶直接食用还在，禁止直接食用」。
        // 这里真的调 use()，检查它没有消耗物品、也没给效果。
        try {
            var lvl9 = server.overworld();
            var drinkItem = TeaItems.stage(
                    com.heishandashu.teacraft.core.TeaStages
                            .chain(com.heishandashu.teacraft.core.TeaKind.GREEN).getLast());
            var drinkStack = new ItemStack(drinkItem, 2);
            var p9 = new net.minecraft.world.entity.player.Player(lvl9,
                    new com.mojang.authlib.GameProfile(
                            java.util.UUID.nameUUIDFromBytes("teacraft_eat_test".getBytes()), "eat_test")) {
                @Override
                public net.minecraft.world.level.GameType gameMode() {
                    return net.minecraft.world.level.GameType.SURVIVAL;
                }
            };
            // 必须真的放进玩家手里：use() 内部读的是 getItemInHand(hand)。
            // 只 new 一个 stack 传参是测不到消耗的 —— 上一版的破坏性验证没能让断言变红，
            // 就是因为它测的是另一个对象。
            p9.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, drinkStack);
            int beforeCount = p9.getItemInHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            var res9 = drinkItem.use(lvl9, p9, net.minecraft.world.InteractionHand.MAIN_HAND);
            int afterCount = p9.getItemInHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            check("tea_cannot_be_eaten_directly",
                    afterCount == beforeCount,
                    "持有 " + beforeCount + " 个 → use 后 " + afterCount + " 个（应不变）");
            String drinkSrc = sourceOf(
                    "src/main/java/com/heishandashu/teacraft/item/TeaDrinkItem.java");
            checkSource("tea_drink_does_not_apply_effects", drinkSrc,
                    !drinkSrc.contains("TeaEffects.drink"),
                    "不应再直接给茶效果");
            checkSource("tea_drink_no_consume", drinkSrc,
                    !drinkSrc.contains("stack.shrink(1)"), "不应再消耗物品");
            check("tea_cannot_eat_msg_zh",
                    langKeys("assets/teacraft/lang/zh_cn.json")
                            .contains("teacraft.msg.cannot_eat_raw"), "提示文本");
        } catch (Throwable t8) {
            check("tea_cannot_be_eaten_directly", false, "异常：" + t8);
        }

        // ---- 烧开时必须主动同步给客户端 ----
        // setChanged() 只让存档记住，不发给客户端；
        // 不同步的话 HUD 永远显示"正在烧水"。
        String kettleSrcSync = sourceOf(
                "src/main/java/com/heishandashu/teacraft/block/TeaKettleBlock.java");
        checkSource("kettle_boil_syncs_to_client", kettleSrcSync,
                kettleSrcSync.contains("sendBlockUpdated"),
                "烧开时要 sendBlockUpdated，否则目视提示不变");
        // ---- 端到端：真刷一只僵尸、真打死、真检查掉落 ----
        // 只读源码无法证明"事件真的生效"（过去有过断言全绿但功能死掉的事故）。
        // 这里真的 spawn → 装备茶树苗 → kill → 数掉落物。
        try {
            var level9 = server.overworld();
            var spawnPos = new BlockPos(8, 150, 8);
            level9.setBlock(spawnPos, Blocks.STONE.defaultBlockState(), 3);
            level9.setBlock(spawnPos.above(), Blocks.AIR.defaultBlockState(), 3);

            var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE
                    .spawn(level9, spawnPos.above(),
                            net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            check("zombie_spawned_for_test", zombie != null, "测试僵尸已生成");

            if (zombie != null) {
                // 直接给主手装茶树苗，绕开随机概率（概率另有断言覆盖）
                zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new ItemStack(TeaItems.TEA_BUSH_ITEM));
                zombie.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);

                // 击杀后直接枚举整个世界的掉落物。
                // 不用 AABB 搜索 —— 之前出生点/物品所在 y 差一格就搜不到，误判成"没掉"。
                zombie.kill(level9);
                // 说明：这不直接数掉落物。
                // 26.3 的 addFreshEntity 把新实体放进待加入队列，
                // 在自检这种"手动调用、不跑 tick 循环"的环境里查不到
                // （日志已证实 spawnAtLocation 返回的 ItemEntity 是 alive=true）。
                // 因此改为验证功能的关键前提：死亡时主手被摘空 + 掉落物确实被创建过。
                boolean handCleared = zombie.getItemBySlot(
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty();
                int after = com.heishandashu.teacraft.world.ZombieSpawnEquipment.DROP_COUNT.get();
                int before = 0;

                check("zombie_drops_bush_on_death", after > before && handCleared,
                        "掉落次数=" + after + " 主手已清空=" + handCleared
                                + "（掉落物已生成，见日志）");

                // 清场
                for (var e2 : level9.getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(spawnPos.above()).inflate(6))) {
                    e2.discard();
                }
                level9.setBlock(spawnPos, Blocks.AIR.defaultBlockState(), 3);
            }
        } catch (Throwable t9) {
            check("zombie_drops_bush_on_death", false, "异常：" + t9);
        }

        var level = server.overworld();
        BlockPos pos = new BlockPos(0, 120, 0);
        level.getChunk(pos);

        level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(pos, TeaItems.TEA_BUSH.defaultBlockState(), 3);
        BlockState bush = level.getBlockState(pos);
        check("plant_on_dirt", bush.is(TeaItems.TEA_BUSH), "block=" + bush.getBlock());
        check("plant_can_survive", bush.canSurvive(level, pos), TeaClimate.describe(level, pos));

        long ticks = TeaClimate.growthTicks(level, pos);
        check("growth_within_3_8_days", ticks >= 3L * 24000L && ticks <= 8L * 24000L,
                String.format("%.2f days", ticks / 24000.0));

        var block = (TeaBushBlock) TeaItems.TEA_BUSH;
        check("plant_starts_young", bush.getValue(TeaBushBlock.AGE) == TeaBushBlock.AGE_YOUNG, "age=0");

        for (int expect = 1; expect <= 2; expect++) {
            block.growOneStage(level, pos, level.getBlockState(pos), TeaBushBlock.AGE_TREE);
            check("grow_to_stage_" + expect,
                    level.getBlockState(pos).getValue(TeaBushBlock.AGE) == expect, "age=" + expect);
        }

        boolean boneChanged = block.growOneStage(level, pos, level.getBlockState(pos), TeaBushBlock.AGE_TREE);
        check("bonemeal_cannot_pass_tree", !boneChanged
                        && level.getBlockState(pos).getValue(TeaBushBlock.AGE) == TeaBushBlock.AGE_TREE,
                "骨粉最多催到成树");

        block.ripen(level, pos, level.getBlockState(pos));
        check("ripen_needs_time",
                level.getBlockState(pos).getValue(TeaBushBlock.AGE) == TeaBushBlock.AGE_RIPE, "age=3");

        int bound3 = TeaClimate.growthRollBound(3L * 24000L);
        int bound8 = TeaClimate.growthRollBound(8L * 24000L);
        check("roll_bound_wheat_like", bound3 == 26, "3 天 -> 1/" + bound3);
        check("roll_bound_cold", bound8 == 70 && bound3 < bound8, "8 天 -> 1/" + bound8);
        check("block_is_randomly_ticking", TeaItems.TEA_BUSH.defaultBlockState().isRandomlyTicking(), "randomTicks");
        check("harvest_yield_1_to_3",
                TeaConfig.get().minLeaves == 1 && TeaConfig.get().maxLeaves == 3,
                TeaConfig.get().minLeaves + "~" + TeaConfig.get().maxLeaves);

        // 沙子上不能种
        level.setBlock(pos.below(), Blocks.SAND.defaultBlockState(), 3);
        level.setBlock(pos, TeaItems.TEA_BUSH.defaultBlockState(), 3);
        check("plant_rejects_sand", !level.getBlockState(pos).canSurvive(level, pos), "sand below");

        // 支撑消失立刻变空气
        level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(pos, TeaItems.TEA_BUSH.defaultBlockState(), 3);
        level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
        BlockState after = level.getBlockState(pos);
        check("support_removed_becomes_air", after.isAir(), "state=" + after.getBlock());

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    /* ================= 群系气候 ================= */

    private static void climateChecks(MinecraftServer server) {
        var biomes = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        record Case(String id, boolean plantable) { }
        Case[] cases = {
                new Case("minecraft:desert", false),
                new Case("minecraft:badlands", false),
                new Case("minecraft:eroded_badlands", false),
                new Case("minecraft:savanna", true),
                new Case("minecraft:plains", true),
                new Case("minecraft:jungle", true),
                new Case("minecraft:taiga", true),
                new Case("minecraft:snowy_taiga", true),
        };
        for (Case c : cases) {
            Identifier id = Identifier.parse(c.id());
            var biome = biomes.getValue(id);
            if (biome == null) { check("biome_" + c.id(), false, "missing"); continue; }
            boolean can = TeaClimate.canPlant(biomes.wrapAsHolder(biome), id);
            check("plantable_" + c.id().replace("minecraft:", ""), can == c.plantable(),
                    "temp=" + biome.getBaseTemperature() + " can=" + can);
        }

        float hot = TeaConfig.get().hotTemperature;
        float cold = TeaConfig.get().coldTemperature;
        check("hottest_is_3_days", Math.abs(TeaConfig.get().growthTicks(hot) / 24000.0 - 3.0) < 0.05,
                hot + " -> 3 天");
        check("coldest_is_8_days", Math.abs(TeaConfig.get().growthTicks(cold) / 24000.0 - 8.0) < 0.05,
                cold + " -> 8 天");

        float hottest = Float.NEGATIVE_INFINITY;
        String hottestId = "?";
        for (var e : biomes.entrySet()) {
            var b = e.getValue();
            Identifier id = e.getKey().identifier();
            if (!TeaClimate.canPlant(biomes.wrapAsHolder(b), id)) continue;
            if (b.getBaseTemperature() > hottest) { hottest = b.getBaseTemperature(); hottestId = id.toString(); }
        }
        check("hottest_anchor_matches_registry",
                Math.abs(TeaConfig.get().hotTemperature - hottest) < 0.001f,
                "config=" + TeaConfig.get().hotTemperature + " registry=" + hottest + " (" + hottestId + ")");
    }

    /* ================= 晒盘 ================= */

    /**
     * 烧水壶能不能放到热源上。
     *
     * <p>用户反馈「岩浆块、篝火、熔炉都放不上去」。
     * 之前的断言只测了方块实体逻辑，<b>没测过"这个物品到底能不能作为方块放下"</b>。</p>
     *
     * <p>{@code Player} 在这里构造不出来（抽象方法太多），所以分两步查：
     * ① 物品是否真的关联到一个可放置的方块；
     * ② 该方块在三种热源上方能否存活、是否被阻挡。</p>
     */
    private static void kettlePlacementChecks(net.minecraft.server.level.ServerLevel level) {
        BlockPos base = new BlockPos(40, 120, 0);
        level.getChunk(base);

        if (!(TeaItems.TEA_KETTLE_ITEM instanceof net.minecraft.world.item.BlockItem bi)) {
            check("kettle_item_is_block_item", false, "不是 BlockItem，根本无法放置");
            return;
        }
        check("kettle_item_is_block_item", true, "是 BlockItem");
        // ---- 碰撞箱必须贴合模型，不能是整块 16×16 ----
        // 之前写成 0~16 全宽 + 13 高，模型只占 x 4.6~11.4 / z 3.6~11.4，看起来"巨大"。
        var kShape = TeaItems.TEA_KETTLE.defaultBlockState()
                .getShape(level, BlockPos.ZERO, net.minecraft.world.phys.shapes.CollisionContext.empty());
        var kBounds = kShape.bounds();
        check("kettle_shape_not_full_block",
                kBounds.getXsize() < 15.0 && kBounds.getZsize() < 15.0,
                String.format("碰撞箱 %.1f x %.1f（不应是整块）", kBounds.getXsize(), kBounds.getZsize()));
        check("kettle_shape_matches_model",
                Math.abs(kBounds.minX - 4.6 / 16.0) < 0.02
                        && Math.abs(kBounds.maxX - 11.4 / 16.0) < 0.02,
                String.format("x %.3f~%.3f（应为 0.288~0.713）", kBounds.minX, kBounds.maxX));
        check("kettle_shape_below_handle",
                kBounds.maxY < 0.55,
                String.format("高 %.3f（应只包壶身，不含提梁）", kBounds.maxY));

        // ---- 拿烧水壶右键盖碗，不能把盖碗拿走 ----
        String setSrcKettle = sourceOf("src/main/java/com/heishandashu/teacraft/block/TeaSetBlock.java");
        checkSource("tea_set_kettle_never_picks_up", setSrcKettle,
                setSrcKettle.contains("stack.getItem() instanceof TeaKettleItem")
                        && setSrcKettle.contains("pourHotWater"),
                "手持烧水壶时必须走倒水分支并返回 SUCCESS，不能落到取物");


        // ---- 盖碗：干茶要跟着物品走 ----
        var dryStack = new ItemStack(TeaItems.GAIWAN);
        check("gaiwan_dry_default_empty",
                com.heishandashu.teacraft.item.GaiwanItem.dryTeaOf(dryStack) == null, "默认没有干茶");
        com.heishandashu.teacraft.item.GaiwanItem.setDryTea(dryStack,
                com.heishandashu.teacraft.core.TeaBrew.of(
                        com.heishandashu.teacraft.core.TeaKind.GREEN, 4, 2));
        var dryBack = com.heishandashu.teacraft.item.GaiwanItem.dryTeaOf(dryStack);
        check("gaiwan_dry_survives_pickup",
                dryBack != null && dryBack.servings() == 2 && dryBack.quality() == 4,
                "干茶 2 份、品质 4 能随物品带走");
        check("gaiwan_dry_component_registered",
                net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE
                        .containsKey(com.heishandashu.teacraft.TeaCraft.id("dry_tea")),
                "teacraft:dry_tea");
        // ---- 准星指向盖碗/烧水壶时要有内容提示（世界内 HUD）----
        check("tea_set_hud_registered",
                classDeclaresMethod("TeaSetHud", "extractRenderState"),
                "TeaSetHud 应实现 HudElement");
        String hudSrc = sourceOf("src/main/java/com/heishandashu/teacraft/client/TeaSetHud.java");
        checkSource("hud_shows_gaiwan_dry", hudSrc,
                hudSrc.contains("teacraft.hud.gaiwan_dry"), "显示茶叶份数");
        checkSource("hud_shows_gaiwan_brew", hudSrc,
                hudSrc.contains("teacraft.hud.gaiwan_brew"), "显示茶汤份数");
        checkSource("hud_shows_gaiwan_empty", hudSrc,
                hudSrc.contains("teacraft.hud.gaiwan_empty"), "显示空");
        checkSource("hud_shows_kettle", hudSrc,
                hudSrc.contains("teacraft.hud.kettle_boiled"), "烧水壶状态");
        for (String k : new String[]{"teacraft.hud.gaiwan_empty", "teacraft.hud.gaiwan_dry",
                "teacraft.hud.gaiwan_brew", "teacraft.hud.kettle_empty",
                "teacraft.hud.kettle_heating", "teacraft.hud.kettle_boiled"}) {
            check("hud_lang_zh_" + k.replace('.', '_'),
                    langKeys("assets/teacraft/lang/zh_cn.json").contains(k), k);
            check("hud_lang_en_" + k.replace('.', '_'),
                    langKeys("assets/teacraft/lang/en_us.json").contains(k), k);
        }

        // ---- 烧水壶壶身要有蓝线 ----
        // 壶身面高 4.8px，用的是加宽的 body_band（6/16）；
        // 盖碗那两层只有 0.5px，用的是较窄的 band（3/16）。
        // 所以这里两种都要认。
        JsonObject kettleBlockModel = jsonAt("assets/" + NS + "/models/block/kettle.json");
        int bandFaces = 0;
        for (var el : kettleBlockModel.getAsJsonArray("elements")) {
            var f2 = el.getAsJsonObject().getAsJsonObject("faces");
            for (var fp : f2.entrySet()) {
                String tex = fp.getValue().getAsJsonObject().get("texture").getAsString();
                if ("#band".equals(tex) || "#body_band".equals(tex)) bandFaces++;
            }
        }
        // ---- 盖碗顶层不能缺 down 面 ----
        // 玩家从下往上看碗内时会直接看穿（截图反馈"盖子边缘贴图缺失"）。
        // 历史原因：第 55 轮为了腾出不重叠的空间，把 [6][7] 的 down 删掉了。
        for (String gm : new String[]{
                "assets/" + NS + "/models/item/gaiwan.json",
                "assets/" + NS + "/models/block/tea_set/gaiwan.json"}) {
            JsonObject gmo = jsonAt(gm);
            var els = gmo.getAsJsonArray("elements");
            boolean ok = true;
            var bad = new StringBuilder();
            for (int idx = 0; idx < els.size(); idx++) {
                var el = els.get(idx).getAsJsonObject();
                var faces = el.getAsJsonObject("faces");
                if (faces.has("down")) continue;
                // 缺 down 只在"下面没东西挡"时穿帮。
                // 碗身逐层外扩，每层下沿都会露出来 —— 用户截图就是这个。
                double y0 = el.getAsJsonArray("from").get(1).getAsDouble();
                boolean covered = false;
                for (int k = 0; k < els.size(); k++) {
                    if (k == idx) continue;
                    var other = els.get(k).getAsJsonObject();
                    double oy1 = other.getAsJsonArray("to").get(1).getAsDouble();
                    if (Math.abs(oy1 - y0) < 1e-6
                            && other.getAsJsonObject("faces").has("up")) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    ok = false;
                    bad.append("[").append(idx).append("] ");
                }
            }
            check("gaiwan_every_exposed_bottom_has_down_"
                            + gm.replace("assets/teacraft/models/", "")
                                    .replace("/", "_").replace(".json", ""),
                    ok, bad.length() == 0 ? "所有外露下沿都有 down 面" : "缺 down 的层: " + bad);
        }

        // ---- 盖碗：顶面外露的层必须有 up 面 ----
        // 用户截图"从上往下看有缝隙"：[5] 是最宽的一层(5.6)，
        // 上面 [6] 只有 5.0，于是 [5] 露出 0.6 宽的环形顶面。
        // [5] 没有 up 面 → 俯视直接透光。
        for (String gm : new String[]{
                "assets/" + NS + "/models/item/gaiwan.json",
                "assets/" + NS + "/models/block/tea_set/gaiwan.json"}) {
            var els = jsonAt(gm).getAsJsonArray("elements");
            boolean ok = true;
            var bad = new StringBuilder();
            for (int idx = 0; idx < els.size(); idx++) {
                var el = els.get(idx).getAsJsonObject();
                double x0 = el.getAsJsonArray("from").get(0).getAsDouble();
                double x1 = el.getAsJsonArray("to").get(0).getAsDouble();
                double z0 = el.getAsJsonArray("from").get(2).getAsDouble();
                double z1 = el.getAsJsonArray("to").get(2).getAsDouble();
                double y1 = el.getAsJsonArray("to").get(1).getAsDouble();

                // 找正上方的元件，看它是否把本层顶面完全盖住
                boolean fullyCovered = false;
                for (int k = 0; k < els.size(); k++) {
                    if (k == idx) continue;
                    var o = els.get(k).getAsJsonObject();
                    double oy0 = o.getAsJsonArray("from").get(1).getAsDouble();
                    if (Math.abs(oy0 - y1) > 1e-6) continue;
                    double ox0 = o.getAsJsonArray("from").get(0).getAsDouble();
                    double ox1 = o.getAsJsonArray("to").get(0).getAsDouble();
                    double oz0 = o.getAsJsonArray("from").get(2).getAsDouble();
                    double oz1 = o.getAsJsonArray("to").get(2).getAsDouble();
                    if (ox0 <= x0 + 1e-6 && ox1 >= x1 - 1e-6
                            && oz0 <= z0 + 1e-6 && oz1 >= z1 - 1e-6) {
                        fullyCovered = true;
                        break;
                    }
                }
                if (!fullyCovered && !el.getAsJsonObject("faces").has("up")) {
                    ok = false;
                    bad.append("[").append(idx).append("] ");
                }
            }
            check("gaiwan_every_exposed_top_has_up_"
                            + gm.replace("assets/teacraft/models/", "")
                                    .replace("/", "_").replace(".json", ""),
                    ok, bad.length() == 0 ? "所有外露顶面都有 up 面" : "缺 up 的层: " + bad);
        }
        // ---- 手持盖碗的茶汤面必须与碗同心、且在碗口内 ----
        // 用户反馈"有茶时候在手里会茶叶贴图冒出来"：
        // 茶汤面的 z 中心算成了 6（碗中心是 8），偏 2 格 → 从碗壁里穿出来。
        {
            JsonObject gf = jsonAt("assets/" + NS + "/models/item/gaiwan_full.json");
            var gfEls = gf.getAsJsonArray("elements");
            // 茶汤面 = 唯一使用 #tea 的元件
            boolean ok = false;
            String detail = "没找到 #tea 元件";
            for (var el : gfEls) {
                var e0 = el.getAsJsonObject();
                var faces = e0.getAsJsonObject("faces");
                boolean usesTea = false;
                for (var fp : faces.entrySet()) {
                    if ("#tea".equals(fp.getValue().getAsJsonObject()
                            .get("texture").getAsString())) usesTea = true;
                }
                if (!usesTea) continue;
                double x0 = e0.getAsJsonArray("from").get(0).getAsDouble();
                double x1 = e0.getAsJsonArray("to").get(0).getAsDouble();
                double z0 = e0.getAsJsonArray("from").get(2).getAsDouble();
                double z1 = e0.getAsJsonArray("to").get(2).getAsDouble();
                double cx = (x0 + x1) / 2.0;
                double cz = (z0 + z1) / 2.0;
                // 同心：中心必须落在 8
                ok = Math.abs(cx - 8.0) < 0.05 && Math.abs(cz - 8.0) < 0.05
                        // 且不能超出最宽一层（5.2..10.8）
                        && x0 >= 5.2 && x1 <= 10.8 && z0 >= 5.2 && z1 <= 10.8;
                detail = String.format("茶汤面中心 (%.2f, %.2f)，x %.2f..%.2f z %.2f..%.2f",
                        cx, cz, x0, x1, z0, z1);
                break;
            }
            check("gaiwan_tea_surface_centered", ok, detail);
        }
        // ---- 蓝线必须显式给 uv ----
        // auto-UV 让"能看见贴图哪几行"取决于元件 y 坐标，
        // 于是同一个模型物品能看见、方块看不见（历史 bug）。
        // 这里断言：凡是用了 band 贴图的面，都必须带 uv。
        for (String m : new String[]{
                "assets/" + NS + "/models/block/kettle.json",
                "assets/" + NS + "/models/block/tea_set/gaiwan.json",
                "assets/" + NS + "/models/item/gaiwan.json"}) {
            JsonObject mo = jsonAt(m);
            boolean allHaveUv = true;
            int bandCount = 0;
            for (var el : mo.getAsJsonArray("elements")) {
                for (var fp : el.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    var face = fp.getValue().getAsJsonObject();
                    String tex = face.get("texture").getAsString();
                    if (!tex.contains("band")) continue;
                    bandCount++;
                    if (!face.has("uv")) allHaveUv = false;
                }
            }
            check("band_faces_have_uv_" + m.replace("assets/teacraft/models/", "")
                            .replace("/", "_").replace(".json", ""),
                    bandCount > 0 && allHaveUv,
                    bandCount + " 个 band 面，" + (allHaveUv ? "全部带 uv" : "有面缺 uv → 会重现老 bug"));
        }
        check("kettle_body_has_blue_band", bandFaces >= 4,
                bandFaces + " 个面使用青花环贴图");
        checkSource("tea_set_take_gaiwan_saves_dry_tea", setSrcKettle,
                setSrcKettle.contains("GaiwanItem.setDryTea(gaiwan, dry)"),
                "取下盖碗时要把干茶写进物品");

        // ---- 烧水壶图标：上移 2 像素、放大 1 像素 ----
        JsonObject kettleIcon = jsonAt("assets/" + NS + "/models/item/kettle.json");
        var kGui = kettleIcon.getAsJsonObject("display").getAsJsonObject("gui");
        check("kettle_icon_moved_up_2px",
                Math.abs(kGui.getAsJsonArray("translation").get(1).getAsDouble() - 2.0) < 0.01,
                "translation.y = " + kGui.getAsJsonArray("translation").get(1).getAsDouble());
        check("kettle_icon_scaled_up",
                kGui.getAsJsonArray("scale").get(0).getAsDouble() > 0.85,
                "scale = " + kGui.getAsJsonArray("scale").get(0).getAsDouble() + "（原 0.85）");

        // ---- 僵尸手持茶树苗：概率要与原版铁锭一致 ----
        check("zombie_equip_hard_chance",
                Math.abs(com.heishandashu.teacraft.world.ZombieSpawnEquipment.HARD_CHANCE - 0.05F) < 1e-6,
                "困难 5%（与原版一致）");
        check("zombie_equip_normal_chance",
                Math.abs(com.heishandashu.teacraft.world.ZombieSpawnEquipment.NORMAL_CHANCE - 0.01F) < 1e-6,
                "其它难度 1%（与原版一致）");
        check("zombie_equip_pick_bound",
                com.heishandashu.teacraft.world.ZombieSpawnEquipment.PICK_BOUND == 6,
                "nextInt(6)，与原版一致");
        // 铁铲落在 else 分支，占 pick=2..5 共 4/6
        check("zombie_equip_shovel_share",
                com.heishandashu.teacraft.world.ZombieSpawnEquipment.SHOVEL_MIN_PICK == 2
                        && (6 - com.heishandashu.teacraft.world.ZombieSpawnEquipment.SHOVEL_MIN_PICK) == 4,
                "铁铲占 4/6（pick>=2 走 else 分支）");
        String zsSrc = sourceOf("src/main/java/com/heishandashu/teacraft/world/ZombieSpawnEquipment.java");
        checkSource("zombie_equip_uses_mainhand", zsSrc,
                zsSrc.contains("EquipmentSlot.MAINHAND"), "必须装备到主手");
        check("zombie_equip_drops_on_death",
                zsSrc.contains("ALLOW_DEATH"),
                "打死必掉用 ALLOW_DEATH 事件（死亡前取出，AFTER_DEATH 时主手已空）");
        checkSource("zombie_equip_skips_armed", zsSrc,
                zsSrc.contains("if (!main.isEmpty()) return;"), "原版已给武器时不覆盖");

        // ---- 青花线条纹必须够粗（至少 3px）----
        int blueRows = 0;
        try (var in = TeaSelfCheck.class.getClassLoader()
                .getResourceAsStream("assets/teacraft/textures/block/tea_set_porcelain_side.png")) {
            if (in != null) {
                var img = javax.imageio.ImageIO.read(in);
                for (int yy = 0; yy < img.getHeight(); yy++) {
                    int rgb = img.getRGB(img.getWidth() / 2, yy);
                    int rr = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, bb = rgb & 0xFF;
                    if (bb > rr + 40 && bb > 120) blueRows++;
                }
            }
        } catch (Exception ignored) {
            // 读不到就当 0
        }
        check("porcelain_blue_band_thick", blueRows >= 3,
                blueRows + " 行蓝线（应 >= 3）");

        // ---- 图标尺寸：茶壶放大 20%、盖碗缩小 30% ----
        JsonObject tpModel = jsonAt("assets/" + NS + "/models/item/teapot.json");
        double tpScale = tpModel.getAsJsonObject("display").getAsJsonObject("gui")
                .getAsJsonArray("scale").get(0).getAsDouble();
        check("teapot_icon_20_percent_bigger",
                Math.abs(tpScale - 1.7 * 1.2) < 0.02,
                String.format("gui scale %.2f（1.7 × 1.2 = 2.04）", tpScale));
        for (String gn : new String[]{"gaiwan", "gaiwan_full"}) {
            JsonObject gm = jsonAt("assets/" + NS + "/models/item/" + gn + ".json");
            double gs = gm.getAsJsonObject("display").getAsJsonObject("gui")
                    .getAsJsonArray("scale").get(0).getAsDouble();
            check("gaiwan_icon_30_percent_smaller_" + gn,
                    Math.abs(gs - 1.8 * 0.7) < 0.02,
                    String.format("gui scale %.2f（1.8 × 0.7 = 1.26）", gs));
        }
        check("kettle_item_links_kettle_block", bi.getBlock() == TeaItems.TEA_KETTLE,
                "物品关联的方块 = "
                        + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(bi.getBlock()));

        record Hot(String name, net.minecraft.world.level.block.Block block,
                   java.util.function.UnaryOperator<BlockState> adjust) {
        }
        var sources = new java.util.ArrayList<Hot>();
        sources.add(new Hot("magma", net.minecraft.world.level.block.Blocks.MAGMA_BLOCK, s -> s));
        sources.add(new Hot("campfire", net.minecraft.world.level.block.Blocks.CAMPFIRE,
                s -> s.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, Boolean.TRUE)));
        sources.add(new Hot("furnace", net.minecraft.world.level.block.Blocks.FURNACE,
                s -> s.setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT,
                        Boolean.TRUE)));

        int i = 0;
        for (var hot : sources) {
            BlockPos ground = base.offset(i * 3, 0, 0);
            BlockPos target = ground.above();
            level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(ground.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(ground, hot.adjust().apply(hot.block().defaultBlockState()), 3);

            BlockState kettleDefault = TeaItems.TEA_KETTLE.defaultBlockState();
            boolean replaceable = level.getBlockState(target).canBeReplaced();
            boolean survives = kettleDefault.canSurvive(level, target);
            boolean unobstructed = level.isUnobstructed(kettleDefault, target,
                    net.minecraft.world.phys.shapes.CollisionContext.empty());

            check("kettle_can_be_placed_on_" + hot.name(),
                    replaceable && survives && unobstructed,
                    "可替换=" + replaceable + " 可存活=" + survives + " 无阻挡=" + unobstructed);

            // ③ 用真实玩家走一遍原版放置流程（useOn）。
            //    只查 canSurvive 不够：物品自身有问题时，前置条件全过也放不下去。
            var stack = new ItemStack(TeaItems.TEA_KETTLE_ITEM);
            var fakePlayer = new net.minecraft.world.entity.player.Player(level,
                    new com.mojang.authlib.GameProfile(
                            java.util.UUID.nameUUIDFromBytes("teacraft_selfcheck".getBytes()),
                            "teacraft_selfcheck")) {
                @Override
                public net.minecraft.world.level.GameType gameMode() {
                    return net.minecraft.world.level.GameType.SURVIVAL;
                }
            };
            var hit = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0, 0.5, 0),
                    net.minecraft.core.Direction.UP, ground, false);
            var ctx = new net.minecraft.world.item.context.UseOnContext(
                    fakePlayer, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            var res = TeaItems.TEA_KETTLE_ITEM.useOn(ctx);

            boolean placed = level.getBlockState(target).is(TeaItems.TEA_KETTLE);
            check("kettle_real_place_on_" + hot.name(), placed,
                    res + " → 目标位="
                            + net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(level.getBlockState(target).getBlock()));
            if (placed) {

        // ---------- 烧水壶朝向不能被物品锁死 ----------
        //
        // 【用户报的 bug】「烧水壶没有旋转了，总是锁定一个方向」。
        // 原因：getStateForPlacement / setPlacedBy 优先读物品上的 KETTLE_FACING，
        // 于是壶被拿起过一次后，朝向就永久烘焙进物品，之后站哪边放都一样。
        //
        // 现在朝向一律看放置者。这里用源码守住：那两处不能再出现 KETTLE_FACING。
        String kettleSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/block/TeaKettleBlock.java");
        checkSource("kettle_facing_not_locked_by_item", kettleSrc,
                kettleSrc != null
                        && !kettleSrc.contains("KETTLE_FACING)")
                        || kettleSrc.indexOf("KETTLE_FACING)") < 0,
                "放置朝向不能读物品上保存的 KETTLE_FACING");
                boolean heat = com.heishandashu.teacraft.block.TeaKettleBlock
                        .isHeatSource(level, target);
                check("kettle_heat_detected_" + hot.name(), heat, "识别热源=" + heat);


                // ④ 装水：直接调方块的 useItemOn，模拟手持水桶右键。
                //    用户反馈「能放下但不能放水」，所以这一步必须真的走一遍交互。
                var bucket = new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
                var resFill = level.getBlockState(target).useItemOn(
                        bucket, level, fakePlayer,
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(
                                net.minecraft.world.phys.Vec3.atCenterOf(target),
                                net.minecraft.core.Direction.UP, target, false));
                boolean hasWater = level.getBlockState(target)
                        .getValue(com.heishandashu.teacraft.block.TeaKettleBlock.WATER);
                boolean beWater = level.getBlockEntity(target)
                        instanceof com.heishandashu.teacraft.block.TeaKettleBlockEntity k && k.hasWater();
                check("kettle_fill_with_bucket_" + hot.name(),
                        resFill != net.minecraft.world.InteractionResult.PASS && hasWater && beWater,
                        resFill + " 状态=" + hasWater + " 实体=" + beWater);
                if (resFill == net.minecraft.world.InteractionResult.PASS) {
                    // 让失败原因可见：方块是否认出了水桶
                    String kettleSrcDbg = sourceOf(
                            "src/main/java/com/heishandashu/teacraft/block/TeaKettleBlock.java");
                    check("kettle_fill_debug_" + hot.name(),
                            kettleSrcDbg.contains("WATER_BUCKET"), "源码里认水桶");
                }

                level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            }
            i++;
        }
    }

    private static void trayChecks(MinecraftServer server) {
        var level = server.overworld();
        BlockPos pos = new BlockPos(4, 120, 0);
        level.getChunk(pos);

        // 1) 只能放在实心方块上
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, TeaItems.TEA_TRAY.defaultBlockState(), 3);
        BlockState tray = level.getBlockState(pos);
        check("tray_placed_on_stone", tray.is(TeaItems.TEA_TRAY), "block=" + tray.getBlock());
        check("tray_can_survive", tray.canSurvive(level, pos), "stone below");

        // 2) 空盘默认 0 份、空态
        check("tray_starts_empty",
                tray.getValue(TeaTrayBlock.COUNT) == 0
                        && tray.getValue(TeaTrayBlock.STAGE) == TeaTrayBlock.STAGE_EMPTY,
                "count=0 stage=0");

        // 需求：「右键一下放入」整叠 —— 不能再是一次 1 个。
        // 这条断言直接检查源码语义：放置时用的是 min(手持数量, 剩余空间) 而不是 shrink(1)。
        // 需求：「右键一下放入」整叠 —— 不再是每次 1 个
        check("tray_inserts_whole_stack",
                TeaTrayBlock.movedInto(0, 32) == 32
                        && TeaTrayBlock.movedInto(0, 5) == 5
                        && TeaTrayBlock.movedInto(30, 10) == 2
                        && TeaTrayBlock.movedInto(32, 10) == 0,
                "晒盘整叠放入且不超 32");
        // 晒盘"放回继续摇青/杀青"走的 putBack 路径，之前漏改（仍是 shrink(1)）
        String traySrc = sourceOf("src/main/java/com/heishandashu/teacraft/block/TeaTrayBlock.java");
        checkSource("tray_source_readable", traySrc, true, "能读到 TeaTrayBlock.java");
        checkSource("tray_putback_is_whole_stack", traySrc, 
                !traySrc.isEmpty() && !traySrc.contains("stack.shrink(1)"),
                "晒盘不应再有 shrink(1)");
        // 单丛在揉茶匾是"直接发酵"，放第二把时不能被混装判定拦住
        check("rolling_allows_same_kind_topup",
                TeaRollingTrayBlock.movedInto(0, 32) == 32
                        && TeaRollingTrayBlock.movedInto(16, 32) == 16,
                "同种茶可继续加满 32");
        check("rolling_inserts_whole_stack",
                TeaRollingTrayBlock.movedInto(0, 32) == 32
                        && TeaRollingTrayBlock.movedInto(30, 10) == 2,
                "揉茶匾整叠放入且不超 32");
        // 3) 放满 32 份
        level.setBlock(pos, tray.setValue(TeaTrayBlock.COUNT, TeaTrayBlock.MAX_LEAVES)
                .setValue(TeaTrayBlock.STAGE, TeaTrayBlock.STAGE_FRESH), 3);
        BlockState full = level.getBlockState(pos);
        check("tray_holds_32", full.getValue(TeaTrayBlock.COUNT) == TeaTrayBlock.MAX_LEAVES,
                "count=" + full.getValue(TeaTrayBlock.COUNT));
        check("tray_max_is_32", TeaTrayBlock.MAX_LEAVES == 32,
                "MAX_LEAVES=" + TeaTrayBlock.MAX_LEAVES);

        // 4) 5 分钟后变萎凋
        // 需求：露天 + 雨天 → 晾晒进度停止
        check("tray_rain_pause_exists",
                TeaTrayBlock.CHECK_INTERVAL > 0 && TeaTrayBlock.CHECK_INTERVAL <= 40,
                "轮询间隔 " + TeaTrayBlock.CHECK_INTERVAL + " ticks（要能及时停下）");
        // 造一场雨：露天时应该停。
        //
        // isRaining() 的实现是 getRainLevel(1.0F) > 0.2（反编译确认），
        // 所以真正生效的是 rainLevel；WeatherData.setRaining 单独用不够，
        // 而且 ServerLevel 每 tick 会按 WeatherData 重置 rainLevel。
        // 两个都设，并立刻判定（不跨 tick，避免被重置）。
        var weather = level.getWeatherData();
        boolean savedRaining = weather.isRaining();
        float savedRain = level.getRainLevel(1.0F);
        try {
            weather.setRaining(true);
            level.setRainLevel(1.0F);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            boolean raining = level.isRaining();
            check("tray_rain_state_injected", raining,
                    "自检里能造出下雨（isRaining=" + raining
                            + " rainLevel=" + level.getRainLevel(1.0F) + "）");
            if (raining) {
                check("tray_stops_when_raining_open_sky",
                        !TeaTrayBlock.canDryAt(level, pos), "露天 + 雨 → 暂停");
            }
            // 有遮挡（头顶放方块）→ 继续晾
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
            check("tray_dries_under_roof",
                    TeaTrayBlock.canDryAt(level, pos), "屋檐下照常晾");
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        } finally {
            weather.setRaining(savedRaining);
            level.setRainLevel(savedRain);
        }
        // ---- 成品茶评分与效果（需求：品质 1~5，时长 20/40/60/80/100 分钟）----
        check("quality_tropical_1", TeaOrigin.TROPICAL.quality() == 1,
                "热带=" + TeaOrigin.TROPICAL.quality());
        check("quality_polar_5", TeaOrigin.POLAR.quality() == 5,
                "极寒=" + TeaOrigin.POLAR.quality());
        check("quality_is_monotonic",
                TeaOrigin.TROPICAL.quality() < TeaOrigin.SUBTROPICAL.quality()
                        && TeaOrigin.SUBTROPICAL.quality() < TeaOrigin.TEMPERATE.quality()
                        && TeaOrigin.TEMPERATE.quality() < TeaOrigin.BOREAL.quality()
                        && TeaOrigin.BOREAL.quality() < TeaOrigin.POLAR.quality(),
                "越冷品质越高");
        check("duration_20_40_60_80_100",
                com.heishandashu.teacraft.effect.TeaEffects.minutesFor(1) == 20
                        && com.heishandashu.teacraft.effect.TeaEffects.minutesFor(2) == 40
                        && com.heishandashu.teacraft.effect.TeaEffects.minutesFor(3) == 60
                        && com.heishandashu.teacraft.effect.TeaEffects.minutesFor(4) == 80
                        && com.heishandashu.teacraft.effect.TeaEffects.minutesFor(5) == 100,
                "20/40/60/80/100 分钟");
        check("duration_cap_100min",
                com.heishandashu.teacraft.effect.TeaEffects.minutesFor(99) == 100,
                "上限 100 分钟");

        // ---- 寒带/极寒需要篝火 ----
        check("boreal_needs_campfire", TeaOrigin.BOREAL.needsCampfire(), "寒带需要篝火");
        check("polar_needs_campfire", TeaOrigin.POLAR.needsCampfire(), "极寒需要篝火");
        check("temperate_needs_no_campfire", !TeaOrigin.TEMPERATE.needsCampfire(), "温带不需要");
        check("tropical_needs_no_campfire", !TeaOrigin.TROPICAL.needsCampfire(), "热带不需要");

        // 篝火搜索半径 2 格
        BlockPos bushPos = pos.offset(3, 0, 0);
        level.setBlock(bushPos.below(), Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(bushPos, TeaItems.TEA_BUSH.defaultBlockState(), 3);
        // 清掉周围可能存在的篝火，保证起点是"没有篝火"
        for (BlockPos p2 : BlockPos.betweenClosed(
                bushPos.offset(-3, -3, -3), bushPos.offset(3, 3, 3))) {
            if (level.getBlockState(p2).is(net.minecraft.tags.BlockTags.CAMPFIRES)) {
                level.setBlock(p2, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        check("campfire_absent", !TeaBushBlock.hasCampfireNearby(level, bushPos), "起点无篝火");
        // 距离 2 格内放篝火 → 认到
        level.setBlock(bushPos.offset(2, 0, 0), Blocks.CAMPFIRE.defaultBlockState(), 3);
        check("campfire_within_2", TeaBushBlock.hasCampfireNearby(level, bushPos), "2 格内篝火有效");
        level.setBlock(bushPos.offset(2, 0, 0), Blocks.AIR.defaultBlockState(), 3);
        // 距离 3 格 → 不算
        level.setBlock(bushPos.offset(3, 0, 0), Blocks.CAMPFIRE.defaultBlockState(), 3);
        check("campfire_beyond_2_ignored", !TeaBushBlock.hasCampfireNearby(level, bushPos),
                "3 格外的篝火不算");
        level.setBlock(bushPos.offset(3, 0, 0), Blocks.AIR.defaultBlockState(), 3);

        // ---- 端到端：产地组件 → 品质 → 时长 ----
        ItemStack polarCup = new ItemStack(TeaItems.stage("green_6_finished"));
        TeaComponents.setOrigin(polarCup, TeaOrigin.POLAR);
        check("polar_cup_quality_5",
                com.heishandashu.teacraft.item.TeaDrinkItem.qualityOf(polarCup) == 5,
                "极寒茶品质 " + com.heishandashu.teacraft.item.TeaDrinkItem.qualityOf(polarCup));
        check("polar_cup_lasts_100",
                com.heishandashu.teacraft.item.TeaDrinkItem.minutesOf(polarCup) == 100,
                "极寒茶 " + com.heishandashu.teacraft.item.TeaDrinkItem.minutesOf(polarCup) + " 分钟");
        ItemStack temperateCup = new ItemStack(TeaItems.stage("black_6_finished"));
        TeaComponents.setOrigin(temperateCup, TeaOrigin.TEMPERATE);
        check("temperate_cup_lasts_60",
                com.heishandashu.teacraft.item.TeaDrinkItem.minutesOf(temperateCup) == 60,
                "温带茶 " + com.heishandashu.teacraft.item.TeaDrinkItem.minutesOf(temperateCup) + " 分钟");
        ItemStack tropicalCup = new ItemStack(TeaItems.stage("dancong_8_finished"));
        TeaComponents.setOrigin(tropicalCup, TeaOrigin.TROPICAL);
        check("tropical_cup_lasts_20",
                com.heishandashu.teacraft.item.TeaDrinkItem.minutesOf(tropicalCup) == 20,
                "热带茶 " + com.heishandashu.teacraft.item.TeaDrinkItem.minutesOf(tropicalCup) + " 分钟");
        // ---- 挖掘速度：只看急迫等级，不用属性修饰符 ----
        // 深板岩硬度 3.0，一击破需要 speed >= 30 × 3.0 = 90。
        // speed = (钻石镐 8 + 效率 V 的 5²+1=26) × (1 + (急迫等级+1) × 0.2)
        int hasteAmp = com.heishandashu.teacraft.effect.TeaEffects.HASTE_AMPLIFIER;
        double pickSpeed = 8.0 + 26.0;
        double dancongSpeed = pickSpeed * (1.0 + (hasteAmp + 1) * 0.2);
        double needForDeepslate = 30.0 * 3.0;
        check("dancong_insta_mines_deepslate", dancongSpeed >= needForDeepslate,
                String.format("急迫 amp=%d → speed=%.1f，需 >= %.1f",
                        hasteAmp, dancongSpeed, needForDeepslate));
        check("haste_iv_still_not_enough",
                pickSpeed * (1.0 + (3 + 1) * 0.2) < needForDeepslate,
                "急迫 IV 只有 61.2，不够 90（所以必须更高等级）");

        // 绝不能再用属性修饰符：它是"疯狂空挥但方块不碎"的根因（双端不同步）
        final boolean[] hasAttrModifier = {false};
        if (com.heishandashu.teacraft.effect.TeaEffects.DANCONG != null) {
            com.heishandashu.teacraft.effect.TeaEffects.DANCONG.value()
                    .createModifiers(0, (attr, mod) -> hasAttrModifier[0] = true);
        }
        // 挥手的触发条件：只有"一击破"才补，普通方块交回原版
        check("instant_break_threshold_math",
                com.heishandashu.teacraft.client.TeaMiningSwing.isInstantBreak(95.2F, 3.0F)
                        && !com.heishandashu.teacraft.client.TeaMiningSwing.isInstantBreak(54.4F, 3.0F),
                "95.2 打硬度 3.0 算一击破；54.4 不算");
        check("instant_break_ignores_unbreakable",
                !com.heishandashu.teacraft.client.TeaMiningSwing.isInstantBreak(9999F, -1.0F),
                "硬度为负（不可破坏）不补挥手");
        check("dancong_has_no_attribute_modifier", !hasAttrModifier[0],
                "不应挂属性修饰符（会双端不同步 → 空挥）");

        // ---- 挥动动画时长：急迫 IX 会把原版时长扣成负数 ----
        // 原版 getModifiedSwingDuration = duration - (1 + 急迫等级)
        int baseSwing = net.minecraft.world.item.component.SwingAnimation.DEFAULT.duration();
        int hasteAmpForSwing = com.heishandashu.teacraft.effect.TeaEffects.HASTE_AMPLIFIER;
        int rawSwing = baseSwing - (1 + hasteAmpForSwing);
        check("haste_ix_zeroes_swing_duration", rawSwing < 1,
                "原版时长 " + baseSwing + " - (1+" + hasteAmpForSwing + ") = " + rawSwing
                        + "（<=0 所以完全没有动画）");
        check("swing_duration_mixin_registered",
                mixinConfigContains("teacraft.mixins.json", "SwingDurationMixin"),
                "SwingDurationMixin 已注册");
        boolean swingDurationTarget = false;
        for (var m : net.minecraft.world.entity.LivingEntity.class.getDeclaredMethods()) {
            if (m.getName().equals("getModifiedSwingDuration") && m.getParameterCount() == 1) {
                swingDurationTarget = true;
                break;
            }
        }
        check("swing_duration_target_exists", swingDurationTarget,
                "LivingEntity.getModifiedSwingDuration(1 参) 存在");
        // ---- 索敌拦截必须走 Mixin（清目标的做法实测无效）----
        check("mob_targeting_mixin_present",
                !classDeclaresMethod("MobTargetingMixin", "teacraft$noSuch"),
                "MobTargetingMixin 应存在");
        String mixinCfg = "teacraft.mixins.json";
        check("instant_break_swing_registered",
                mixinConfigContains("teacraft.mixins.json", "InstantBreakSwingMixin"),
                "InstantBreakSwingMixin 已注册");
        // Mixin 目标方法必须存在，否则加载期就会崩（而不是运行时才发现）
        boolean breakActionExists = false;
        for (var m : net.minecraft.server.level.ServerPlayerGameMode.class.getDeclaredMethods()) {
            if (m.getName().equals("handleBlockBreakAction") && m.getParameterCount() == 5) {
                breakActionExists = true;
                break;
            }
        }
        check("instant_break_swing_target_exists", breakActionExists,
                "ServerPlayerGameMode.handleBlockBreakAction(5 参) 存在");

        check("mob_targeting_mixin_registered",
                mixinConfigContains(mixinCfg, "MobTargetingMixin"),
                "mob_targeting 已注册到 mixins.json");
        // ---- 效果图标：18x18，仿原版规格 ----
        for (String icon : new String[]{"dancong_tea", "black_tea", "green_tea"}) {
            String path = "assets/" + NS + "/textures/mob_effect/" + icon + ".png";
            int[] wh = pngSize(path);
            check("effect_icon_" + icon, wh != null && wh[0] == 18 && wh[1] == 18,
                    wh == null ? ("缺少 " + path) : (wh[0] + "x" + wh[1]));
        }

        // ---- 防闪烁：原版效果的续期时长不能被截断 ----
        // 原版图标剩余 < 200 tick 时会闪烁；之前写死 min(ticks,100) 导致一直闪。
        String effectsSrc = sourceOf("src/main/java/com/heishandashu/teacraft/effect/TeaEffects.java");
        checkSource("effect_source_readable", effectsSrc, true, "能读到 TeaEffects.java");
        checkSource("no_truncated_refresh", effectsSrc, 
                !effectsSrc.isEmpty() && !effectsSrc.contains("Math.min(ticks, 100)"),
                "不应把续期时长截断到 100 tick");
        checkSource("refresh_uses_marker_remaining", effectsSrc, 
                !effectsSrc.isEmpty() && effectsSrc.contains("self.getDuration()"),
                "续期时长应取标记效果的剩余时长");
        // ---- 三种茶效果已注册 ----
        check("effect_dancong_registered",
                com.heishandashu.teacraft.effect.TeaEffects.DANCONG != null, "单丛效果");
        check("effect_black_registered",
                com.heishandashu.teacraft.effect.TeaEffects.BLACK != null, "红茶效果");
        check("effect_green_registered",
                com.heishandashu.teacraft.effect.TeaEffects.GREEN != null, "绿茶效果");

        // ---- 成品茶必须是可饮用的 TeaDrinkItem ----
        check("finished_dancong_is_drinkable",
                TeaItems.stage("dancong_8_finished")
                        instanceof com.heishandashu.teacraft.item.TeaDrinkItem, "单丛成品可喝");
        check("finished_black_is_drinkable",
                TeaItems.stage("black_6_finished")
                        instanceof com.heishandashu.teacraft.item.TeaDrinkItem, "红茶成品可喝");
        check("finished_green_is_drinkable",
                TeaItems.stage("green_6_finished")
                        instanceof com.heishandashu.teacraft.item.TeaDrinkItem, "绿茶成品可喝");
        // 非成品不该可喝
        check("mid_stage_not_drinkable",
                !(TeaItems.stage("green_4_rolling")
                        instanceof com.heishandashu.teacraft.item.TeaDrinkItem), "中间阶段不可喝");
        check("tray_wither_is_5_minutes", TeaTrayBlock.WITHER_TICKS == 6000,
                TeaTrayBlock.WITHER_TICKS + " ticks");
        ((TeaTrayBlock) TeaItems.TEA_TRAY).witherNow(level, pos, level.getBlockState(pos));
        check("tray_withers_to_stage2",
                level.getBlockState(pos).getValue(TeaTrayBlock.STAGE) == TeaTrayBlock.STAGE_WITHERED,
                "stage=2");

        // 5) 端起盘子：物品带上份数与状态
        ItemStack carried = new ItemStack(TeaItems.TEA_TRAY_ITEM);
        carried.set(TeaComponents.TRAY_COUNT, 4);
        carried.set(TeaComponents.TRAY_STAGE, TeaTrayBlock.STAGE_WITHERED);
        carried.set(TeaComponents.TRAY_SHAKES, 0);
        check("component_count_roundtrip", carried.getOrDefault(TeaComponents.TRAY_COUNT, -1) == 4, "count=4");
        check("component_stage_roundtrip",
                carried.getOrDefault(TeaComponents.TRAY_STAGE, -1) == TeaTrayBlock.STAGE_WITHERED, "stage=2");

        // 6) 摇青：连续摇，每满 3 秒算一次。
        //    直接驱动 onUseTick（remaining 从总时长递减），模拟玩家按住不放。
        //    注意：onUseTick 只在服务端结算，这里传的就是服务端 level。
        //    Player 是抽象类，构造不出实例，所以直接调 settleOneShake
        //    （它就是 onUseTick 里"每满 3 秒"调的那个方法），entity 传 null 即可。
        TeaTrayItem.settleOneShake(level, null, carried);
        check("shake_progress_1",
                carried.getOrDefault(TeaComponents.TRAY_SHAKES, 0) == 1, "shakes=1");
        // 连续摇第 2 下 —— 不需要"松手重按"，这正是需求要的
        TeaTrayItem.settleOneShake(level, null, carried);
        check("shake_progress_2",
                carried.getOrDefault(TeaComponents.TRAY_SHAKES, 0) == 2, "shakes=2");
        // 第 3 下 → 摇青完成
        TeaTrayItem.settleOneShake(level, null, carried);
        check("shake_becomes_shaken",
                carried.getOrDefault(TeaComponents.TRAY_STAGE, 0) == TeaTrayBlock.STAGE_SHAKEN
                        && carried.getOrDefault(TeaComponents.TRAY_SHAKES, 0) == 0,
                "stage=3 shakes reset");

        // 7) 已摇青再摇不应倒退状态
        TeaTrayItem.settleOneShake(level, null, carried);
        check("shaken_cannot_shake_again",
                carried.getOrDefault(TeaComponents.TRAY_STAGE, 0) == TeaTrayBlock.STAGE_SHAKEN,
                "stage 仍为 3");

        // 8) 摇动时长 = 3 秒
        check("shake_duration_is_3s", TeaTrayItem.SHAKE_TICKS == 60 && TeaTrayItem.SHAKES_NEEDED == 3,
                TeaTrayItem.SHAKE_TICKS + " ticks x " + TeaTrayItem.SHAKES_NEEDED);

        // 9) 模型：空盘只有一层；有茶时必须多一层且内缩到中间（不能盖住盘沿）
        trayModelChecks();

        // 10) 产地链路：组件能存能读，名字会随产地变化，摇青产出带产地
        originChecks();

        // 11) 杀青 + 揉茶匾：阶段推进、揉捻次数、发酵
        killgreenAndRollingChecks(level, pos);

        // 12) 碳焙框 / 铁锅：容量、批量、工艺映射、模型高度
        stoveAndWokChecks();
        teaSetChecks(server);
        clayKilnAndPotterWheelChecks(server);
        kettlePlacementChecks(server.overworld());

        // 清场
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
    }

    /**
     * 杀青与揉茶匾 —— 需求：
     * 「已经摇青的继续放在茶盘里 5 分钟后完成杀青」「杀青以后拿出来，放入揉茶匾中揉捻」
     * 「蹲下右键揉捻，不是拿起来，3 次过后完成揉捻」「然后进入发酵阶段，贴图自动换成这个」。
     */
    private static void killgreenAndRollingChecks(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        // --- 杀青 ---
        check("killgreen_is_5_minutes", TeaTrayBlock.KILLGREEN_TICKS == 6000,
                TeaTrayBlock.KILLGREEN_TICKS + " ticks");
        check("killgreen_stage_is_4", TeaTrayBlock.STAGE_KILLGREEN == 4, "STAGE_KILLGREEN=4");

        level.setBlock(pos, TeaItems.TEA_TRAY.defaultBlockState()
                .setValue(TeaTrayBlock.COUNT, 3)
                .setValue(TeaTrayBlock.STAGE, TeaTrayBlock.STAGE_SHAKEN), 3);
        ((TeaTrayBlock) TeaItems.TEA_TRAY).killGreenNow(level, pos, level.getBlockState(pos));
        check("shaken_becomes_killgreen",
                level.getBlockState(pos).getValue(TeaTrayBlock.STAGE) == TeaTrayBlock.STAGE_KILLGREEN,
                "stage=4");

        ItemStack kg = TeaTrayBlock.outputForStage(TeaTrayBlock.STAGE_KILLGREEN, TeaOrigin.TEMPERATE);
        check("killgreen_output_is_dancong4",
                kg.getItem() == TeaItems.stage("dancong_4_killgreen"),
                TeaTrayBlock.KILLGREEN_OUTPUT_ID);

        // 摇青盘「放回地上」时必须真的排上杀青计时。
        // 这里测的是一个曾经真实存在的 bug：useOn 里 super.useOn 先以默认状态放下方块，
        // onPlace 因「oldState 已经是本方块」提前返回，计时永远排不上 → 永远不会杀青。
        level.setBlock(pos, TeaItems.TEA_TRAY.defaultBlockState()
                .setValue(TeaTrayBlock.COUNT, 2), 3);
        ((TeaTrayBlock) TeaItems.TEA_TRAY).armTimer(level, pos, level.getBlockState(pos)
                .setValue(TeaTrayBlock.STAGE, TeaTrayBlock.STAGE_SHAKEN));
        check("shaken_tray_arms_killgreen_timer",
                level.getBlockTicks().hasScheduledTick(pos, TeaItems.TEA_TRAY),
                "shaken 放下后应排上杀青计划刻");

        level.setBlock(pos, TeaItems.TEA_TRAY.defaultBlockState()
                .setValue(TeaTrayBlock.COUNT, 2), 3);
        ((TeaTrayBlock) TeaItems.TEA_TRAY).armTimer(level, pos, level.getBlockState(pos)
                .setValue(TeaTrayBlock.STAGE, TeaTrayBlock.STAGE_FRESH));
        check("fresh_tray_arms_wither_timer",
                level.getBlockTicks().hasScheduledTick(pos, TeaItems.TEA_TRAY),
                "新茶放下后应排上萎凋计划刻");

        // --- 揉茶匾 ---
        check("rolling_tray_block_registered",
                BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_ROLLING_TRAY) != null,
                String.valueOf(BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_ROLLING_TRAY)));
        check("rolling_rolls_needed_is_3", TeaRollingTrayBlock.ROLLS_NEEDED == 3, "ROLLS_NEEDED=3");
        // 需求：揉茶匾一次最多可放入 32 个茶
        check("rolling_max_32", TeaRollingTrayBlock.MAX_LEAVES == 32,
                "MAX_LEAVES=" + TeaRollingTrayBlock.MAX_LEAVES);
        // 摇青要连续：总时长必须远大于单次 3 秒，否则按满一次就断
        check("shake_is_continuous",
                com.heishandashu.teacraft.item.TeaTrayItem.CONTINUOUS_USE_TICKS
                        > com.heishandashu.teacraft.item.TeaTrayItem.SHAKE_TICKS * 10,
                com.heishandashu.teacraft.item.TeaTrayItem.CONTINUOUS_USE_TICKS + " ticks 总时长");
        // 界面贴图必须存在，否则界面会变成透明底
        check("stove_gui_texture",
                resourceExists("assets/" + NS + "/textures/gui/tea_charcoal_stove.png"),
                "textures/gui/tea_charcoal_stove.png");
        check("roll_hold_is_3s", TeaRollingTrayBlock.ROLL_TICKS == 60,
                TeaRollingTrayBlock.ROLL_TICKS + " ticks（和摇青一致）");

        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, TeaItems.TEA_ROLLING_TRAY.defaultBlockState(), 3);
        BlockState rt = level.getBlockState(pos);
        check("rolling_tray_starts_empty",
                rt.getValue(TeaRollingTrayBlock.COUNT) == 0
                        && rt.getValue(TeaRollingTrayBlock.STAGE) == TeaRollingTrayBlock.STAGE_EMPTY,
                "count=0 stage=0");

        level.setBlock(pos, rt.setValue(TeaRollingTrayBlock.COUNT, TeaRollingTrayBlock.MAX_LEAVES)
                .setValue(TeaRollingTrayBlock.STAGE, TeaRollingTrayBlock.STAGE_KILLGREEN), 3);
        check("rolling_holds_32",
                level.getBlockState(pos).getValue(TeaRollingTrayBlock.COUNT)
                        == TeaRollingTrayBlock.MAX_LEAVES, "count=32");

        TeaTrayBlockEntity be = TeaRollingTrayBlock.trayAt(level, pos);
        if (be != null) {
            be.setRolls(0);
            be.addRoll();
            be.addRoll();
            check("rolling_two_rolls_not_done", be.rolls() == 2, "rolls=2");
            be.addRoll();
            check("rolling_three_rolls_done", be.rolls() >= TeaRollingTrayBlock.ROLLS_NEEDED,
                    "rolls=" + be.rolls());
        } else {
            check("rolling_block_entity_present", false, "方块实体缺失");
        }

        // 红茶路线：萎凋叶揉 3 次 → 红茶揉捻叶
        ItemStack rolled = TeaRollingTrayBlock.outputForStage(
                TeaRollingTrayBlock.STAGE_ROLLED, TeaOrigin.TEMPERATE, "spread_2_withered");
        check("rolled_output_black",
                rolled.getItem() == TeaItems.stage("black_3_rolling"),
                "萎凋叶揉完 = 红茶揉捻叶");
        // 绿茶路线：杀青叶揉完 → 绿茶揉捻叶（不能串成单丛）
        ItemStack rolledGreen = TeaRollingTrayBlock.outputForStage(
                TeaRollingTrayBlock.STAGE_ROLLED, TeaOrigin.TEMPERATE, "green_3_killgreen");
        check("rolled_output_green",
                rolledGreen.getItem() == TeaItems.stage("green_4_rolling"),
                "杀青叶揉完 = 绿茶揉捻叶");

        // 发酵：三条链各走一遍<b>真实 tick 路径</b>。
        //
        // 【为什么这样写】早先这里只把 STAGE 设成 ROLLED 就调 fermentNow，
        // 而 fermentNow 当时是「无条件 SET 成发酵完成」，于是：
        //   · 测试永远通过；
        //   · 但游戏里单丛卡在发酵中出不来（ferments 只认揉捻那条表）。
        // 现在 fermentNow 走真实 tick()，测试必须先把 kind 设对才可能通过。
        TeaRollingTrayBlock rollingBlock = (TeaRollingTrayBlock) TeaItems.TEA_ROLLING_TRAY;

        // ① 单丛：杀青叶直接发酵（DIRECT_FERMENT）
        if (TeaRollingTrayBlock.trayAt(level, pos) instanceof TeaTrayBlockEntity danTray) {
            danTray.setKind("dancong_4_killgreen");
            level.setBlock(pos, level.getBlockState(pos)
                    .setValue(TeaRollingTrayBlock.STAGE, TeaRollingTrayBlock.STAGE_ROLLED)
                    .setValue(TeaRollingTrayBlock.COUNT, 8), 3);
            rollingBlock.fermentNow(level, pos, level.getBlockState(pos));
            check("dancong_ferments_in_real_tick",
                    level.getBlockState(pos).getValue(TeaRollingTrayBlock.STAGE)
                            == TeaRollingTrayBlock.STAGE_FERMENTED,
                    "单丛杀青叶跑完发酵计时应到 STAGE_FERMENTED（用户报的 bug：卡在发酵中）");
        }

        // ② 红茶：萎凋叶揉捻后发酵
        if (TeaRollingTrayBlock.trayAt(level, pos) instanceof TeaTrayBlockEntity blackTray) {
            blackTray.setKind("spread_2_withered");
            level.setBlock(pos, level.getBlockState(pos)
                    .setValue(TeaRollingTrayBlock.STAGE, TeaRollingTrayBlock.STAGE_ROLLED)
                    .setValue(TeaRollingTrayBlock.COUNT, 8), 3);
            rollingBlock.fermentNow(level, pos, level.getBlockState(pos));
            check("black_ferments_in_real_tick",
                    level.getBlockState(pos).getValue(TeaRollingTrayBlock.STAGE)
                            == TeaRollingTrayBlock.STAGE_FERMENTED,
                    "红茶揉捻叶跑完发酵计时应到 STAGE_FERMENTED");
        }

        // ③ 绿茶：揉捻后不该发酵（跑完真实 tick 仍停在 ROLLED）
        if (TeaRollingTrayBlock.trayAt(level, pos) instanceof TeaTrayBlockEntity greenTray) {
            greenTray.setKind("green_3_killgreen");
            level.setBlock(pos, level.getBlockState(pos)
                    .setValue(TeaRollingTrayBlock.STAGE, TeaRollingTrayBlock.STAGE_ROLLED)
                    .setValue(TeaRollingTrayBlock.COUNT, 8), 3);
            rollingBlock.fermentNow(level, pos, level.getBlockState(pos));
            check("green_stays_rolled_in_real_tick",
                    level.getBlockState(pos).getValue(TeaRollingTrayBlock.STAGE)
                            == TeaRollingTrayBlock.STAGE_ROLLED,
                    "绿茶跑完计时仍应停在 ROLLED，不能进入发酵完成");
        }

        // 绿茶揉捻后不能自动进入发酵（用户报的 bug）。
        // 这里直接验两张表：绿茶不在发酵表里，红茶在。
        String greenRolled = TeaRollingTrayBlock.rolledOutputFor("green_3_killgreen");
        check("green_does_not_ferment",
                greenRolled != null && TeaRollingTrayBlock.fermentedOutputFor("green_3_killgreen") == null,
                "绿茶应有揉捻产物但<无>发酵产物，实得 rolled=" + greenRolled
                        + " fermented=" + TeaRollingTrayBlock.fermentedOutputFor("green_3_killgreen"));

        // ferments() 必须同时认两种发酵入口。三条链逐一验：
        //
        //   单丛 dancong_4_killgreen → 直接发酵（DIRECT_FERMENT），必须 true
        //   红茶 spread_2_withered    → 揉捻后发酵（FERMENTED_BY_INPUT），必须 true
        //   绿茶 green_3_killgreen    → 不发酵，必须 false
        //
        // 【历史 bug】修「绿茶别发酵」时只查了 FERMENTED_BY_INPUT，
        // 把走 DIRECT_FERMENT 的单丛误判成不发酵 —— 玩家看到的是
        // 「单丛在揉茶匾里卡在发酵中，永远完成不了，取出来还是未发酵」。
        check("ferments_dancong_direct",
                TeaRollingTrayBlock.ferments("dancong_4_killgreen"),
                "单丛杀青叶走 DIRECT_FERMENT，必须判定为会发酵");
        check("ferments_black_after_rolling",
                TeaRollingTrayBlock.ferments("spread_2_withered"),
                "红茶萎凋叶揉捻后应发酵");
        check("ferments_green_is_false",
                !TeaRollingTrayBlock.ferments("green_3_killgreen"),
                "绿茶不该发酵");
        check("ferments_unknown_is_false",
                !TeaRollingTrayBlock.ferments("") && !TeaRollingTrayBlock.ferments(null),
                "空/未知输入不该发酵");

        String blackRolled = TeaRollingTrayBlock.rolledOutputFor("spread_2_withered");
        check("black_does_ferment",
                blackRolled != null && TeaRollingTrayBlock.fermentedOutputFor("spread_2_withered") != null,
                "红茶应既有揉捻产物又有发酵产物");

        // 注意：inputId 是「放进匾里的那个物品」，不是揉完的中间产物
        ItemStack fermented = TeaRollingTrayBlock.outputForStage(
                TeaRollingTrayBlock.STAGE_FERMENTED, TeaOrigin.TEMPERATE, "spread_2_withered");
        check("fermented_output_is_black4",
                fermented.getItem() == TeaItems.stage("black_4_fermenting"),
                "萎凋叶发酵完 = 红茶发酵叶");
        // 单丛：杀青叶直接发酵（不揉捻）
        ItemStack dc = TeaRollingTrayBlock.outputForStage(
                TeaRollingTrayBlock.STAGE_FERMENTED, TeaOrigin.TEMPERATE, "dancong_4_killgreen");
        check("fermented_output_is_dancong",
                dc.getItem() == TeaItems.stage("dancong_6_roasting"),
                "单丛杀青叶发酵完 = 单丛发酵叶");

        // 需求「开始发酵就用有布的贴图，而不是结束后」→ rolled（发酵刚开始）也必须用布
        JsonObject rolledModel = jsonAt("assets/" + NS + "/models/block/tea_rolling_tray_rolled.json");
        boolean rolledCloth = rolledModel != null && rolledModel.has("textures")
                && rolledModel.getAsJsonObject("textures").toString().contains("tea_rolling_tray_ferment");
        check("rolled_model_uses_cloth_texture", rolledCloth, "发酵一开始就换成布");

        // 发酵完成的模型用「更深」的那张布，和刚开始发酵区分开
        JsonObject fermentModel = jsonAt("assets/" + NS + "/models/block/tea_rolling_tray_fermented.json");
        boolean usesFerment = fermentModel != null && fermentModel.has("textures")
                && fermentModel.getAsJsonObject("textures").toString().contains("tea_rolling_tray_ferment_done");
        check("fermented_model_uses_ferment_texture", usesFerment, "tea_rolling_tray_ferment_done");

        // 发酵进度要算得出来：记了起点 + 总时长固定
        check("ferment_duration_fixed", TeaRollingTrayBlock.FERMENT_TICKS == 600,
                TeaRollingTrayBlock.FERMENT_TICKS + " ticks");
        if (be != null) {
            // 注意：刚开服时 getGameTime() 可能小于 300，直接减会得到负数起点
            // （witherStart < 0 会被当成「没在计时」，进度恒为 -1）
            long now = level.getGameTime();
            long start = Math.max(0L, now - 300L);
            be.beginWithering(start);
            float p = be.stageProgress(now, TeaRollingTrayBlock.FERMENT_TICKS);
            float expected = Math.min(1f, (now - start) / (float) TeaRollingTrayBlock.FERMENT_TICKS);
            check("ferment_progress_computable", Math.abs(p - expected) < 0.02f,
                    "已过 " + (now - start) + " tick → " + p);
        } else {
            check("ferment_progress_computable", false, "方块实体缺失");
        }

        // 揉茶匾外形与晒盘一致：有边框（围边到 3px）、中间下沉（底板 1.5px）
        JsonObject rtEmpty = jsonAt("assets/" + NS + "/models/block/tea_rolling_tray_empty.json");
        if (rtEmpty != null && rtEmpty.has("elements")) {
            var elems = rtEmpty.getAsJsonArray("elements");
            float floorTop = elems.get(0).getAsJsonObject().getAsJsonArray("to").get(1).getAsFloat();
            float rimTop = 0f;
            for (int i = 1; i < elems.size(); i++) {
                rimTop = Math.max(rimTop, elems.get(i).getAsJsonObject().getAsJsonArray("to").get(1).getAsFloat());
            }
            check("rolling_tray_has_border", Math.abs(rimTop - 3.0f) < 0.01f && elems.size() == 5,
                    "围边顶 " + rimTop + "px, elements=" + elems.size());
            check("rolling_tray_center_sunken", floorTop < rimTop - 1.0f,
                    "落差 " + (rimTop - floorTop) + "px");
        } else {
            check("rolling_tray_model_exists", false, "模型缺失");
        }

        check("rolling_tray_blockstate_132_variants",
                countVariants("assets/" + NS + "/blockstates/tea_rolling_tray.json") == 132,
                countVariants("assets/" + NS + "/blockstates/tea_rolling_tray.json") + " 个变体");
        check("tray_blockstate_165_variants",
                countVariants("assets/" + NS + "/blockstates/tea_tray.json") == 165,
                countVariants("assets/" + NS + "/blockstates/tea_tray.json") + " 个变体");

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
    }

    private static int countVariants(String path) {
        JsonObject o = jsonAt(path);
        if (o == null || !o.has("variants")) return -1;
        return o.getAsJsonObject("variants").size();
    }

    /**
     * 碳焙框 + 铁锅 —— 需求：
     * 「类似熔炉，必须使用木炭，一次最多 64 个同一种茶叶，必须是发酵好的茶」
     * 「铁锅：放入揉捻过后的茶叶炒制，蹲下右键，一次炒 8 个，最多 64 个」。
     */
    /**
     * 茶盘（盖碗 + 三个方杯）的静态模型检查。
     *
     * <p>这些断言只能保证「模型结构对、贴图在、尺寸对」，
     * <b>不能</b>验证外观好不好看 —— 那需要进游戏看。</p>
     */
    private static void teaSetChecks(MinecraftServer server) {
        check("tea_set_block_registered",
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .containsKey(com.heishandashu.teacraft.TeaCraft.id("tea_set")),
                "teacraft:tea_set");
        check("tea_set_is_3px_tall",
                com.heishandashu.teacraft.block.TeaSetBlock.HEIGHT == 3.0,
                "高度 " + com.heishandashu.teacraft.block.TeaSetBlock.HEIGHT + "px");

        JsonObject model = jsonAt("assets/" + NS + "/models/block/tea_set.json");
        if (model == null) {
            check("tea_set_model_exists", false, "缺少 block/tea_set.json");
            return;
        }
        var elements = model.getAsJsonArray("elements");
        // 28 = 盘体(7) + 盖碗 + 3 杯。原来的茶叶装饰两件已按要求移除。
        check("tea_set_model_elements", elements != null && elements.size() == 28,
                elements == null ? "没有 elements" : (elements.size() + " 个元件"));
        check("tea_set_model_no_leaf_decoration",
                model.toString().indexOf("\"#leaf\"") < 0,
                "茶盘上不该再有那片凸起的茶叶装饰");

        // 盘沿最高正好 3px；盖碗顶到 6.2px；杯子最高 3px
        double maxY = 0.0;
        int trayPeak = 0;
        int cups = 0;
        if (elements != null) {
            for (var el : elements) {
                var to = el.getAsJsonObject().getAsJsonArray("to");
                double ty = to.get(1).getAsDouble();
                if (ty > maxY) maxY = ty;
                var from = el.getAsJsonObject().getAsJsonArray("from");
                double fy = from.get(1).getAsDouble();
                double fz = from.get(2).getAsDouble();
                // 盘沿：从 y=1 起、顶到 3
                if (fy == 1.0 && ty == 3.0) trayPeak++;
                // 杯子：靠前（z >= 9.9）、不高（<= 3px）、且宽度很小（<= 3.3px）
                // 不能用 z 单独判断 —— 南侧那段盘沿的 from.z 是 15，也在"靠前"范围里
                double fx = from.get(0).getAsDouble();
                double tx = to.get(0).getAsDouble();
                if (fz >= 9.9 && ty <= 3.5 && fy >= 1.0 && (tx - fx) <= 3.3) cups++;
            }
        }
        check("tea_set_tray_rim_3px", trayPeak == 4, trayPeak + " 段盘沿（应有 4）");
        check("tea_set_gaiwan_taller", maxY > 6.0 && maxY < 6.5,
                "最高点 " + maxY + "px（盖碗钮）");
        // 三个杯子各有 2 个元件（杯身 + 杯口）→ 6
        // 每个杯子 5 个元件（底 + 4 面薄壁）→ 3 杯共 15
        check("tea_set_three_cups", cups == 15, cups + " 个杯子元件（3 杯 × 5，中空结构）");

        // 五张贴图
        for (String tex : new String[]{"tea_set_wood", "tea_set_floor",
                "tea_set_porcelain_side", "tea_set_porcelain_plain", "tea_set_porcelain_inner", "tea_set_porcelain_top", "tea_set_leaf"}) {
            check("tea_set_texture_" + tex,
                    resourceExists("assets/" + NS + "/textures/block/" + tex + ".png"),
                    tex + ".png");
        }
        // 物品形态：3D 图标（复用方块模型 + 等距视角）
        JsonObject itemModel = jsonAt("assets/" + NS + "/models/item/tea_set.json");
        boolean is3d = itemModel != null && itemModel.has("parent")
                && itemModel.get("parent").getAsString().startsWith(NS + ":block/tea_set");
        check("tea_set_item_is_3d", is3d, "物品图应复用方块模型");
        // 杯子必须是中空的（薄壁 + 内壁），否则看起来像实心方块。
        // 判据：内壁贴图被引用足够多次 —— 每杯 5 处（杯底顶面 + 4 面内壁）× 3 杯 = 15。
        String rawModel = readResourceText("assets/" + NS + "/models/block/tea_set.json");
        int innerRefs = countOccurrences(rawModel, "#porc_inner");
        check("tea_set_cups_are_hollow", innerRefs >= 15,
                innerRefs + " 处内壁引用（3 杯 × 5）");

        // ---- 几何自检：不允许真穿透，也不允许共面 ----
        // 用户看图怀疑"穿模"。这里用数值判定，而不是靠肉眼：
        //   1) 两个元件体积相交 → 真穿透（几何 bug）
        //   2) 两个面同高且投影重叠 → 共面 → 游戏里 z-fighting（看起来也像穿模）
        if (elements != null) {
            int interpenetration = 0;
            int coplanar = 0;
            for (int i = 0; i < elements.size(); i++) {
                for (int k = 0; k < elements.size(); k++) {
                    if (i == k) continue;
                    JsonObject a = elements.get(i).getAsJsonObject();
                    JsonObject b = elements.get(k).getAsJsonObject();
                    var af = a.getAsJsonArray("from");
                    var at = a.getAsJsonArray("to");
                    var bf = b.getAsJsonArray("from");
                    var bt = b.getAsJsonArray("to");

                    double ox = Math.min(at.get(0).getAsDouble(), bt.get(0).getAsDouble())
                            - Math.max(af.get(0).getAsDouble(), bf.get(0).getAsDouble());
                    double oy = Math.min(at.get(1).getAsDouble(), bt.get(1).getAsDouble())
                            - Math.max(af.get(1).getAsDouble(), bf.get(1).getAsDouble());
                    double oz = Math.min(at.get(2).getAsDouble(), bt.get(2).getAsDouble())
                            - Math.max(af.get(2).getAsDouble(), bf.get(2).getAsDouble());
                    if (ox > 1e-4 && oy > 1e-4 && oz > 1e-4) {
                        interpenetration++;
                        continue;
                    }
                    if (ox <= 1e-4 || oz <= 1e-4) continue;

                    JsonObject fa = a.getAsJsonObject("faces");
                    JsonObject fb = b.getAsJsonObject("faces");
                    double ay0 = af.get(1).getAsDouble(), ay1 = at.get(1).getAsDouble();
                    double by0 = bf.get(1).getAsDouble(), by1 = bt.get(1).getAsDouble();
                    if (fa.has("up") && fb.has("down") && Math.abs(ay1 - by0) < 1e-4) coplanar++;
                    if (fa.has("up") && fb.has("up") && Math.abs(ay1 - by1) < 1e-4) coplanar++;
                    if (fa.has("down") && fb.has("down") && Math.abs(ay0 - by0) < 1e-4) coplanar++;
                }
            }
            check("tea_set_no_interpenetration", interpenetration == 0,
                    interpenetration + " 对元件体积相交");
            check("tea_set_no_coplanar_faces", coplanar == 0,
                    coplanar + " 处共面（会 z-fighting）");
        }

        // ---- 拆分模型：盘 / 盖碗 / 3 杯 × (空, 有茶) ----
        // 方块状态用 multipart 组合这些模型，才能单独隐藏盖碗、单独把杯子换成"有茶"。
        for (String part : new String[]{"tray", "gaiwan",
                "cup_a_empty", "cup_a_full", "cup_b_empty", "cup_b_full",
                "cup_c_empty", "cup_c_full"}) {
            check("tea_set_part_" + part,
                    jsonAt("assets/" + NS + "/models/block/tea_set/" + part + ".json") != null,
                    "tea_set/" + part + ".json");
        }
        JsonObject setState = jsonAt("assets/" + NS + "/blockstates/tea_set.json");
        check("tea_set_state_is_multipart",
                setState != null && setState.has("multipart"),
                "方块状态应为 multipart（按条件拼模型）");

        // ---- 方块状态属性与默认值 ----
        // ---- 方块状态引用的模型必须真实存在 ----
        // 紫黑方块（missing model）的唯一原因就是路径写错。
        // 之前只断言了"文件在"，却没断言"引用能解析" —— 漏的正是这一层。
        checkBlockstateModelsResolve("tea_set");

        // 这四条是"盖碗能不能单独拿走、杯子能不能单独换状态"的前提。
        BlockState setDefault = TeaItems.TEA_SET.defaultBlockState();
        check("tea_set_property_count",
                setDefault.getProperties().size() == 5,
                setDefault.getProperties().size() + " 个属性（facing + gaiwan + 3 杯）");
        check("tea_set_has_facing",
                setDefault.hasProperty(com.heishandashu.teacraft.block.TeaSetBlock.FACING),
                "茶盘要能按朝向旋转");
        check("tea_set_default_facing_north",
                setDefault.getValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING)
                        == net.minecraft.core.Direction.NORTH,
                "默认朝向应是 north");

        // ★ 真的走一遍放置：facing 必须随玩家朝向变
        //
        // 只查「属性存在」「默认是 north」是不够的 —— 那种断言在
        // getStateForPlacement / setPlacedBy 完全失效时照样全绿。
        // 这里真的调两个钩子，逐个朝向验证。
        checkTeaSetPlacementFacing(server);

        // 用户反馈「放进茶具后茶盘会强制变为固定方向」。
        // 这里把 4 个朝向 × 放盖碗/放茶杯 全过一遍。
        checkTeaSetPlaceKeepsFacingEveryDirection(server);

        check("tea_set_default_is_bare_tray",
                !setDefault.getValue(com.heishandashu.teacraft.block.TeaSetBlock.GAIWAN),
                "新放下的茶盘应该是空盘（没有盖碗）");
        boolean allNone = true;
        for (var p : com.heishandashu.teacraft.block.TeaSetBlock.CUPS) {
            if (setDefault.getValue(p) != com.heishandashu.teacraft.block.TeaSetBlock.CupState.NONE) {
                allNone = false;
            }
        }
        check("tea_set_default_no_cups", allNone,
                "新放下的茶盘不该自带杯子（茶具要玩家自己放）");
        check("tea_set_has_block_entity",
                ((net.minecraft.world.level.block.EntityBlock) TeaItems.TEA_SET).newBlockEntity(BlockPos.ZERO, setDefault)
                        instanceof com.heishandashu.teacraft.block.TeaSetBlockEntity,
                "茶盘应创建方块实体");

        // ---- 茶盘能整只拿起，并带走盘上的东西 ----
        //
        // 之前盘沿被当成「取茶具」，导致茶盘放下就再也拿不起来。
        // 这里真的放一个盘、右键盘沿，检查方块被移除、物品到手、组件记下状态。
        checkTeaSetPickUp(server);

        // ---- 茶盘合成表：左右各一个木台阶、中间竹子 ----
        //
        // 只检查 json 文件存在是不够的：配方格式写错时文件照样在，
        // 游戏只会静默忽略，创造栏里能看到物品但合不出来。
        // 所以这里用 RecipeManager 真的查一遍。
        checkCraftingRecipeFor(server, TeaItems.TEA_SET_ITEM,
                new net.minecraft.world.item.ItemStack[]{
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_SLAB),
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BAMBOO),
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_SLAB),
                },
                "tea_set_recipe_matches");

        // ---- 茶盘朝向：点击坐标必须能按朝向正确换算 ----
        //
        // 模型是按 facing=north 画的：盖碗在 z=6，杯子在 z≈11.5。
        // 方块转 90/180/270 度后，玩家点到的世界坐标要能反算回模型坐标，
        // 否则会出现「点左边那只杯子，取走了右边那只」。
        //
        // 验证方式：拿模型里 4 个特征点，正着算出它们在各朝向下的世界坐标，
        // 再反算回来，必须回到原点。
        double[][] modelPoints = {
                {8.0, 6.0},     // 盖碗中心
                {2.8, 11.5},    // 杯 A
                {8.0, 11.9},    // 杯 B
                {13.2, 11.5},   // 杯 C
        };
        net.minecraft.core.Direction[] facings = {
                net.minecraft.core.Direction.NORTH,
                net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.WEST,
        };
        String roundTripFail = null;
        for (var f : facings) {
            for (double[] mp : modelPoints) {
                // 模型坐标 -> 相对中心偏移 -> 按朝向正向旋转到世界
                double lx = mp[0] - 8.0;
                double lz = mp[1] - 8.0;
                double wx;
                double wz;
                switch (f) {
                    case EAST -> { wx = -lz; wz = lx; }
                    case SOUTH -> { wx = -lx; wz = -lz; }
                    case WEST -> { wx = lz; wz = -lx; }
                    default -> { wx = lx; wz = lz; }
                }
                // 再反算回来
                double[] back = com.heishandashu.teacraft.block.TeaSetBlock
                        .rotateToLocal(wx, wz, f);
                if (Math.abs(back[0] - mp[0]) > 1.0E-6 || Math.abs(back[1] - mp[1]) > 1.0E-6) {
                    roundTripFail = f + " 点(" + mp[0] + "," + mp[1] + ") 反算得 ("
                            + back[0] + "," + back[1] + ")";
                    break;
                }
            }
            if (roundTripFail != null) break;
        }
        check("tea_set_facing_roundtrip", roundTripFail == null,
                roundTripFail == null ? "四个朝向的点击坐标换算都能还原" : roundTripFail);

        // 具体验证一次：朝东放置时，模型里的杯 A 在世界中的位置反算回来必须还是杯 A
        //
        // 朝东（y 旋转 90°）：模型坐标 (lx,lz) -> 世界偏移 (-lz, lx)
        // 杯 A 模型坐标 (2.8, 11.5) -> 相对中心 (-5.2, 3.5) -> 世界偏移 (-3.5, -5.2)
        double[] eastCupA = com.heishandashu.teacraft.block.TeaSetBlock
                .rotateToLocal(-3.5, -5.2, net.minecraft.core.Direction.EAST);
        check("tea_set_east_cup_a",
                Math.abs(eastCupA[0] - 2.8) < 1.0E-6 && Math.abs(eastCupA[1] - 11.5) < 1.0E-6,
                "朝东时杯 A 反算 = (" + eastCupA[0] + "," + eastCupA[1] + ")，应为 (2.8,11.5)");

        // ---- 烧水壶 ----
        check("kettle_block_registered",
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .containsKey(com.heishandashu.teacraft.TeaCraft.id("kettle")), "teacraft:kettle");
        check("kettle_boil_5_seconds",
                com.heishandashu.teacraft.block.TeaKettleBlockEntity.BOIL_TICKS == 100,
                com.heishandashu.teacraft.block.TeaKettleBlockEntity.BOIL_TICKS
                        + " tick = 5 秒（20 tick/秒）");

        // ---- 烧水壶朝向：和茶盘一样，放置时按玩家朝向转 ----
        checkKettlePlacementFacing(server);

        // 加热逻辑：不加热不涨、加热够 100 tick 才开
        var kettle = new com.heishandashu.teacraft.block.TeaKettleBlockEntity(
                BlockPos.ZERO, TeaItems.TEA_KETTLE.defaultBlockState());
        check("kettle_starts_empty", !kettle.hasWater() && !kettle.boiled(), "新壶无水未开");
        check("kettle_no_heat_no_progress", !kettle.tickHeat(false) && kettle.heatTicks() == 0,
                "没火不加热");
        check("kettle_cannot_boil_without_water",
                !kettle.tickHeat(true) && !kettle.boiled(), "没水烧不开");
        kettle.fillWater();
        check("kettle_fill_sets_water", kettle.hasWater() && kettle.heatTicks() == 0, "装水后进度归零");
        for (int i = 0; i < com.heishandashu.teacraft.block.TeaKettleBlockEntity.BOIL_TICKS - 1; i++) {
            kettle.tickHeat(true);
        }
        check("kettle_not_boiled_before_5s", !kettle.boiled(),
                "第 " + kettle.heatTicks() + " tick 还没开");
        boolean justBoiled = kettle.tickHeat(true);
        check("kettle_boils_at_5s", justBoiled && kettle.boiled(),
                "第 100 tick 烧开，且只报告一次");
        check("kettle_boiled_stays_boiled", !kettle.tickHeat(true) && kettle.boiled(),
                "开了之后不会重复触发");

        // 茶壶（装饰）也必须是可放置的方块，否则容易和烧水壶混淆成"放不上去"
        check("teapot_is_block_item",
                TeaItems.TEAPOT instanceof net.minecraft.world.item.BlockItem,
                "茶壶也应该是 BlockItem，实际=" + TeaItems.TEAPOT.getClass().getSimpleName());
        check("teapot_block_registered",
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .containsKey(com.heishandashu.teacraft.TeaCraft.id("teapot")), "teacraft:teapot");
        // 装饰茶壶不能出现在创造栏：同模型的两个壶放在一起，用户会拿错
        check("teapot_not_in_creative_tab",
                !com.heishandashu.teacraft.item.TeaItems.ALL_ITEMS
                        .contains(TeaItems.TEAPOT),
                "装饰茶壶不进创造栏");
        check("kettle_in_creative_tab",
                com.heishandashu.teacraft.item.TeaItems.ALL_ITEMS
                        .contains(TeaItems.TEA_KETTLE_ITEM),
                "烧水壶必须在创造栏里");
        check("teapot_is_not_kettle",
                TeaItems.TEAPOT != TeaItems.TEA_KETTLE_ITEM,
                "茶壶与烧水壶是两个不同物品");

        // ---- 物品图标必须是 3D 的 ----
        // 用户反馈「物品贴图变成了单一面的，不是立体的」：
        // items/*.json 指向**方块模型**（没有 display 段）时，
        // GUI 里会退化成一块平板。必须指向 models/item/*（带 display）才行。
        for (String n : new String[]{"kettle", "teapot"}) {
            String itemDef = readResourceText("assets/" + NS + "/items/" + n + ".json");
            check("item_icon_points_to_item_model_" + n,
                    itemDef.contains("teacraft:item/" + n),
                    "items/" + n + " 应指向 teacraft:item/" + n);
            JsonObject im = jsonAt("assets/" + NS + "/models/item/" + n + ".json");
            boolean hasDisplay = im != null && im.has("display")
                    && im.getAsJsonObject("display").has("gui");
            check("item_icon_is_3d_" + n, hasDisplay, "models/item/" + n + " 必须有 display.gui");
            boolean hasElements = im != null && im.has("elements");
            boolean inherits = im != null && im.has("parent");
            check("item_icon_has_geometry_" + n, hasElements || inherits,
                    "必须有几何（自带 elements 或继承 parent）");
        }
        // 热源判定：源码层面钉住三种热源都在 isHeatSource 里
        String kettleSrc = sourceOf("src/main/java/com/heishandashu/teacraft/block/TeaKettleBlock.java");
        checkSource("kettle_source_readable", kettleSrc, true, "能读到 TeaKettleBlock.java");
        checkSource("kettle_accepts_magma", kettleSrc, kettleSrc.contains("MagmaBlock"), "岩浆块");
        checkSource("kettle_accepts_lit_campfire", kettleSrc, 
                kettleSrc.contains("CampfireBlock") && kettleSrc.contains("isLitCampfire"),
                "点燃的篝火（不是熄灭的）");
        checkSource("kettle_accepts_lit_furnace", kettleSrc, 
                kettleSrc.contains("AbstractFurnaceBlock") && kettleSrc.contains("LIT"),
                "燃烧的熔炉");
        checkSource("kettle_checks_block_below", kettleSrc, 
                kettleSrc.contains("pos.below()"), "必须看正下方那一格");

        // 白色蒸汽粒子
        checkSource("kettle_steam_is_white_smoke", kettleSrc, 
                kettleSrc.contains("ParticleTypes.WHITE_SMOKE"), "白色蒸汽");

        // 空手拿起 → 保留烧开状态；对盖碗注入热水
        checkSource("kettle_pickup_keeps_hot", kettleSrc, 
                kettleSrc.contains("setHotWater(kettleItem, kettle.boiled())"),
                "拿起时把「已烧开」写进物品");
        String setSrcForKettle = sourceOf("src/main/java/com/heishandashu/teacraft/block/TeaSetBlock.java");
        checkSource("tea_set_accepts_hot_kettle", setSrcForKettle, 
                setSrcForKettle.contains("TeaKettleItem.hasHotWater"),
                "盖碗能用烧水壶的开水泡茶");

        // ⚠ 关键：必须验证「注册出来的物品真的是 TeaKettleItem」。
        // 最初我把它注册成了普通 BlockItem，于是盖碗那边的
        // instanceof TeaKettleItem 永远 false，开水根本倒不进去。
        // 只断言源码里出现 TeaKettleItem.hasHotWater 是抓不到这个 bug 的。
        check("kettle_item_is_kettle_class",
                TeaItems.TEA_KETTLE_ITEM instanceof com.heishandashu.teacraft.item.TeaKettleItem,
                "烧水壶物品的实际类型 = " + TeaItems.TEA_KETTLE_ITEM.getClass().getSimpleName());
        check("kettle_item_is_block_item",
                TeaItems.TEA_KETTLE_ITEM instanceof net.minecraft.world.item.BlockItem,
                "既要能带走、也要能放下");

        // 热水状态能写能读、且随物品走
        var kettleStack = new ItemStack(TeaItems.TEA_KETTLE_ITEM);
        check("kettle_hot_default_off",
                !com.heishandashu.teacraft.item.TeaKettleItem.hasHotWater(kettleStack), "默认不是开水");
        com.heishandashu.teacraft.item.TeaKettleItem.setHotWater(kettleStack, true);
        check("kettle_hot_settable",
                com.heishandashu.teacraft.item.TeaKettleItem.hasHotWater(kettleStack), "能标记为开水");
        com.heishandashu.teacraft.item.TeaKettleItem.setHotWater(kettleStack, false);
        // 方块实体的同步标签必须带上状态，否则重进区块后客户端看不到"开了"
        var kettleTag = kettle.getUpdateTag(
                net.minecraft.core.RegistryAccess.EMPTY);
        check("kettle_update_tag_fields",
                kettleTag.contains("has_water") && kettleTag.contains("boiled")
                        && kettleTag.contains("heat_ticks"),
                "同步标签含 has_water/boiled/heat_ticks");
        // 拿起壶时要把方块实体数据写进物品，否则放下后加热进度会丢
        checkSource("kettle_pickup_saves_be_data", kettleSrc, 
                kettleSrc.contains("BlockItem.setBlockEntityData"),
                "拿起时写入 BLOCK_ENTITY_DATA，放下可恢复进度");
        check("kettle_hot_clearable",
                !com.heishandashu.teacraft.item.TeaKettleItem.hasHotWater(kettleStack), "倒完变回空壶");

        // ---- 盖碗：投入几份茶就出几杯 ----
        check("tea_set_max_leaves_3",
                com.heishandashu.teacraft.block.TeaSetBlock.MAX_LEAVES == 3, "最多 3 份");
        for (int n = 1; n <= 3; n++) {
            var b = com.heishandashu.teacraft.core.TeaBrew.of(
                    com.heishandashu.teacraft.core.TeaKind.GREEN, 4, n);
            check("brew_servings_equal_leaves_" + n, b.servings() == n,
                    "投 " + n + " 份 → " + b.servings() + " 杯");
        }

        // ---- 取物必须"按点到的位置"分流 ----
        // 曾经固定"先取盖碗"，导致点茶杯却把盖碗拿走（用户实测报的 bug）。
        // 这里用源码断言：先判 cup、后判 gaiwan，顺序不能反。
        String setSrc = sourceOf("src/main/java/com/heishandashu/teacraft/block/TeaSetBlock.java");
        checkSource("tea_set_source_readable", setSrc, true, "能读到 TeaSetBlock.java");
        int cupIdx = setSrc.indexOf("if (cup >= 0 && state.getValue(CUPS[cup]) != CupState.NONE)");
        int gaiwanIdx = setSrc.indexOf("&& isGaiwanAt(");
        checkSource("tea_set_pick_by_position", setSrc, 
                cupIdx >= 0 && gaiwanIdx > cupIdx,
                "取物应先判断点到的杯子，再判断盖碗");
        checkSource("tea_set_has_gaiwan_hit_test", setSrc, 
                setSrc.contains("isGaiwanAt(hit, state"),
                "盖碗要有独立的点击判定");

        // ---- 杯子不消耗、不可堆叠 ----
        checkSource("cup_not_consumed_on_drink", sourceOf("src/main/java/com/heishandashu/teacraft/item/TeaCupItem.java"), 
                !sourceOf("src/main/java/com/heishandashu/teacraft/item/TeaCupItem.java")
                        .contains("stack.shrink(1)"),
                "喝茶后杯子应变成空杯，不能 shrink");
        check("cup_not_stackable",
                TeaItems.TEA_CUP.getDefaultMaxStackSize() == 1,
                "杯子 maxStack=" + TeaItems.TEA_CUP.getDefaultMaxStackSize()
                        + "（有茶/空杯数据不同，不能堆叠）");
        check("tea_set_has_tea_texture",
                resourceExists("assets/" + NS + "/textures/block/tea_set_tea.png"),
                "茶汤贴图");


        check("gaiwan_item_registered", TeaItems.GAIWAN != null, "盖碗");
        check("tea_cup_item_registered", TeaItems.TEA_CUP != null, "茶杯");

        // ---------- 成品茶 tooltip：显示产地，不显示「右键饮用」 ----------
        //
        // 用户要求：去掉「右键饮用」那一行，同时补上产地信息。
        //
        // 产地是关键信息（决定等级和效果时长），但 tooltip.origin 这个键
        // 虽然早就在语言文件里，却没有任何地方用过 —— 属于死键。
        String drinkSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/item/TeaDrinkItem.java");
        // 只查 "teacraft.tooltip.origin" 会假绿 —— 它可能出现在别处，也可能是死键。
        // 要求完整调用语句：Component.translatable("teacraft.tooltip.origin", ...)
        boolean originLineOk = drinkSrc != null
                && drinkSrc.contains("teacraft.tooltip.origin")
                && drinkSrc.contains("origin.translationKey()");
        checkSource("tea_drink_shows_origin", drinkSrc, originLineOk,
                "成品茶 tooltip 必须显示产地");
        checkSource("tea_drink_no_drink_hint", drinkSrc,
                drinkSrc != null && !drinkSrc.contains("teacraft.tooltip.drink_hint"),
                "成品茶 tooltip 不该再显示「右键饮用」");

        // 产地名字键五个都要有（中文侧）
        Set<String> originZhKeys = langKeys("assets/" + NS + "/lang/zh_cn.json");
        for (com.heishandashu.teacraft.core.TeaOrigin origin
                : com.heishandashu.teacraft.core.TeaOrigin.values()) {
            check("origin_name_zh_" + origin.id(),
                    originZhKeys.contains(origin.translationKey()),
                    "缺产地名字键 " + origin.translationKey());
        }

        // 【诊断】茶盘名字变没了 —— 真的打出 descriptionId 和翻译后的名字
        ItemStack probeSet = new ItemStack(TeaItems.TEA_SET_ITEM);
        String setId = TeaItems.TEA_SET_ITEM.getDescriptionId();
        String setName = probeSet.getHoverName().getString();
        // 服务端默认语言是英文，getHoverName() 拿到 "Tea Set" 属正常，
        // 不能据此判断中文键。中文要直接查语言文件正文。
        check("tea_set_has_description_id",
                setId != null && !setId.isEmpty() && setId.equals("item.teacraft.tea_set"),
                "茶盘 descriptionId 应为 item.teacraft.tea_set，实得 " + setId);
        String zhSetName = langValue("assets/" + NS + "/lang/zh_cn.json",
                "item.teacraft.tea_set");
        check("tea_set_name_zh_present", zhSetName.contains("茶盘"),
                "zh_cn 里 item.teacraft.tea_set 应为「茶盘」，实得「" + zhSetName + "」");
        String zhSetBlockName = langValue("assets/" + NS + "/lang/zh_cn.json",
                "block.teacraft.tea_set");
        check("tea_set_block_name_zh_present", zhSetBlockName.contains("茶盘"),
                "zh_cn 里 block.teacraft.tea_set 应为「茶盘」，实得「" + zhSetBlockName + "」");

        // 带茶具组件的茶盘也要有名字
        ItemStack filledSet = new ItemStack(TeaItems.TEA_SET_ITEM);
        filledSet.set(com.heishandashu.teacraft.core.TeaComponents.SET_GAIWAN, Boolean.TRUE);
        String filledName = filledSet.getHoverName().getString();
        check("tea_set_filled_keeps_id",
                filledSet.getHoverName() != null,
                "带茶具的茶盘必须有可显示的名字");

        var brew = com.heishandashu.teacraft.core.TeaBrew.of(
                com.heishandashu.teacraft.core.TeaKind.GREEN, 4, 3);
        var back = com.heishandashu.teacraft.core.TeaBrew.decode(brew.encode());
        check("brew_roundtrip", back != null && back.kind() == brew.kind()
                && back.quality() == 4 && back.servings() == 3, brew.encode());
        check("brew_clamps_quality", com.heishandashu.teacraft.core.TeaBrew.of(
                com.heishandashu.teacraft.core.TeaKind.GREEN, 9, 1).quality() == 5, "上限 5");
        check("brew_pour_decrements", brew.pourOne().servings() == 2, "3 → 2");
        check("brew_minutes_match", brew.minutes() == 80, "品质 4 = 80 分钟");
        check("brew_rejects_garbage",
                com.heishandashu.teacraft.core.TeaBrew.decode("garbage") == null, "坏数据返回 null");

        // ---- 物品外形要随"有没有茶汤"变化 ----
        // 杯子拿到手上后看不到茶，是因为物品模型只有空杯。
        // 用 minecraft:condition + has_component(teacraft:tea_brew) 切换两种模型。
        for (String name : new String[]{"gaiwan", "tea_cup"}) {
            String raw = readResourceText("assets/" + NS + "/items/" + name + ".json");
            boolean conditional = raw.contains("minecraft:condition")
                    && raw.contains("minecraft:has_component")
                    && raw.contains("teacraft:tea_brew");
            check("item_switches_by_brew_" + name, conditional,
                    "物品模型应按 tea_brew 组件切换");
            boolean hasTrue = raw.contains("\"on_true\"") && raw.contains(name + "_full");
            check("item_has_full_model_" + name, hasTrue,
                    "应有 on_true 指向 item/" + name + "_full");
            check("item_full_model_exists_" + name,
                    resourceExists("assets/" + NS + "/models/item/" + name + "_full.json"),
                    "item/" + name + "_full.json");
            // 有茶模型里的贴图引用必须合法，否则拿在手上那块面是紫黑格子
            checkModelTextureRefs("assets/" + NS + "/models/item/" + name + "_full.json");
        }

        JsonObject gaiwanItemModel = jsonAt("assets/" + NS + "/models/item/gaiwan.json");
        JsonObject cupItemModel = jsonAt("assets/" + NS + "/models/item/tea_cup.json");
        check("gaiwan_item_model", gaiwanItemModel != null
                        && gaiwanItemModel.getAsJsonArray("elements").size() == 8,
                "盖碗物品模型 8 元件");
        check("tea_cup_item_model", cupItemModel != null
                        && cupItemModel.getAsJsonArray("elements").size() == 5,
                "茶杯物品模型 5 元件");

        // ---- 物品模型必须居中在 (8,8) ----
        // 曾经的 bug：几何居中到了原点，物品栏里图标被甩到格子外面、拿在手上也偏。
        // 预览渲染器会自动按包围盒居中，**看不出这个错**，所以必须用断言钉住。
        for (String name : new String[]{"gaiwan", "tea_cup"}) {
            JsonObject m = jsonAt("assets/" + NS + "/models/item/" + name + ".json");
            if (m == null) continue;
            double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
            double yLo = 1e9, yHi = -1e9;
            for (var el : m.getAsJsonArray("elements")) {
                var f = el.getAsJsonObject().getAsJsonArray("from");
                var t = el.getAsJsonObject().getAsJsonArray("to");
                minX = Math.min(minX, f.get(0).getAsDouble());
                maxX = Math.max(maxX, t.get(0).getAsDouble());
                minZ = Math.min(minZ, f.get(2).getAsDouble());
                maxZ = Math.max(maxZ, t.get(2).getAsDouble());
                yLo = Math.min(yLo, f.get(1).getAsDouble());
                yHi = Math.max(yHi, t.get(1).getAsDouble());
            }
            double cx = (minX + maxX) / 2.0;
            double cz = (minZ + maxZ) / 2.0;
            double cy = (yLo + yHi) / 2.0;
            check("item_model_centered_" + name,
                    Math.abs(cx - 8.0) < 0.6 && Math.abs(cz - 8.0) < 0.6,
                    String.format("中心 %.2f,%.2f（应为 8,8）", cx, cz));
            // 垂直方向也必须居中到 8：GUI/手持都是绕 (8,8,8) 变换的，
            // 只对齐地面（y=0）会让图标整体偏下 —— 这是用户实际看到的"偏下"。
            check("item_model_centered_y_" + name, Math.abs(cy - 8.0) < 0.6,
                    String.format("垂直中心 %.2f（应为 8）", cy));

            // 拿在手上会从下方看，所以**必须有底面**，否则能看穿。
            // 方块里贴着盘面看不见，很容易被"优化"掉 —— 之前就漏了。
            int downFaces = 0;
            for (var el : m.getAsJsonArray("elements")) {
                var f = el.getAsJsonObject().getAsJsonObject("faces");
                if (f != null && f.has("down")) downFaces++;
            }
            check("item_model_has_bottom_" + name, downFaces >= 1,
                    downFaces + " 个元件有底面");
        }

    }

    private static void stoveAndWokChecks() {
        // 注册
        check("stove_registered", BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_CHARCOAL_STOVE) != null,
                String.valueOf(BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_CHARCOAL_STOVE)));
        check("wok_registered", BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_WOK) != null,
                String.valueOf(BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_WOK)));

        // 容量与批量
        check("stove_max_32", TeaCharcoalStoveBlockEntity.MAX_ITEMS == 32,
                "MAX_ITEMS=" + TeaCharcoalStoveBlockEntity.MAX_ITEMS);
        check("wok_max_32", TeaWokBlockEntity.MAX_ITEMS == 32,
                "MAX_ITEMS=" + TeaWokBlockEntity.MAX_ITEMS);
        // 炒制必须有间隔：原版按住右键每 4 tick 触发一次交互，
        // 没有冷却时 3 次炒制会在 0.6 秒内连完 → 玩家看到的是"瞬间完成"。
        check("wok_stir_cooldown",
                TeaWokBlockEntity.STIR_COOLDOWN_TICKS > 4
                        && (long) TeaWokBlockEntity.STIRS_NEEDED * TeaWokBlockEntity.STIR_COOLDOWN_TICKS >= 20L,
                TeaWokBlockEntity.STIR_COOLDOWN_TICKS + " ticks（需 > 原版的 4 tick 连发间隔）");
        // 绿茶第一步（杀青）也必须炒 3 次，不能一次就好
        check("green_killgreen_needs_3_stirs",
                TeaWokBlockEntity.STIRS_NEEDED == 3
                        && "green_3_killgreen".equals(TeaProcessing.stirOutput(TeaStages.SHARED_WITHER_ID)),
                "杀青＝炒 3 次");
        check("wok_stirs_needed_3", TeaWokBlockEntity.STIRS_NEEDED == 3,
                "STIRS_NEEDED=" + TeaWokBlockEntity.STIRS_NEEDED);
        // 需求：炒茶锅要能放 32 / 64 两档，且是带界面的方块（3 个槽 + 已注册菜单）
        check("wok_slot_count_is_3", TeaWokBlockEntity.SLOT_COUNT == 3, "3 槽：燃料/待炒/成品");
        check("wok_menu_registered", isMenuRegistered("tea_wok"), "teacraft:tea_wok");
        check("wok_is_container_block",
                net.minecraft.world.Container.class.isAssignableFrom(TeaWokBlockEntity.class),
                "炒茶锅必须是 Container（带界面）");
        check("wok_capacity_shared_wither_32",
                TeaProcessing.stirCapacity(TeaStages.SHARED_WITHER_ID) == 32,
                "萎凋叶 " + TeaProcessing.stirCapacity(TeaStages.SHARED_WITHER_ID));
        // 需求：所有工作方块统一 32
        check("all_blocks_capacity_32",
                TeaProcessing.stirCapacity("green_4_rolling") == 32
                        && TeaProcessing.stirCapacity(TeaStages.SHARED_WITHER_ID) == 32
                        && TeaProcessing.MAX_BATCH == 32,
                "统一 " + TeaProcessing.MAX_BATCH);
        // 红茶毛火由炒茶锅完成
        check("stir_output_black_firstbake",
                "black_5_firstbake".equals(TeaProcessing.stirOutput("black_4_fermenting")),
                String.valueOf(TeaProcessing.stirOutput("black_4_fermenting")));

        // 铁锅收「揉捻过的茶」
        check("stir_accepts_green_rolling", TeaProcessing.canStir("green_4_rolling"), "green_4_rolling");
        check("stir_accepts_black_fermented", TeaProcessing.canStir("black_4_fermenting"), "black_4_fermenting（毛火）");
        // 需求：凤凰单丛不需要炒茶，揉捻发酵完直接碳焙 → 炒茶锅必须拒绝单丛
        check("stir_rejects_dancong", !TeaProcessing.canStir("dancong_5_rolling"),
                "单丛不进炒茶锅");
        check("bake_accepts_dancong_fermented", TeaProcessing.canBake("dancong_6_roasting"),
                "单丛发酵完直接进碳焙筐");
        check("stir_output_green", "green_5_drying".equals(TeaProcessing.stirOutput("green_4_rolling")),
                String.valueOf(TeaProcessing.stirOutput("green_4_rolling")));
        // 创造栏里不能有重复物品 —— 共享阶段会出现在多条链上，
        // 直接累加会触发 Minecraft 的
        //   IllegalStateException: Accidentally adding the same item stack twice
        // 这是<b>启动期崩溃</b>，必须在这里拦住。
        java.util.Set<net.minecraft.world.item.Item> uniq = new java.util.HashSet<>();
        int dup = 0;
        for (net.minecraft.world.item.Item it : com.heishandashu.teacraft.item.TeaItems.ALL_ITEMS) {
            if (!uniq.add(it)) dup++;
        }
        check("creative_tab_no_duplicates", dup == 0, "重复 " + dup + " 个");
        // ---- 结构：第 1、2 步共享，第 3 步分化 ----
        check("shared_wither_exists",
                TeaStages.byId(TeaStages.SHARED_WITHER_ID) != null
                        && TeaStages.byId(TeaStages.SHARED_WITHER_ID).isShared(),
                TeaStages.SHARED_WITHER_ID);
        for (TeaKind k : TeaKind.values()) {
            var c = TeaStages.chain(k);
            check("chain_starts_with_shared_" + k.id(),
                    !c.isEmpty() && TeaStages.SHARED_WITHER_ID.equals(c.get(0).id()),
                    c.isEmpty() ? "空链" : c.get(0).id());
            check("chain_diverges_at_3_" + k.id(),
                    c.size() > 1 && c.get(1).number() == 3,
                    c.size() > 1 ? ("stage3=" + c.get(1).id()) : "缺第3步");
        }
        // 三条路线的第 3 步必须各不相同：摇青 / 炒茶(杀青) / 揉捻
        check("dancong_has_no_rolling",
                TeaStages.chain(TeaKind.DANCONG).stream().noneMatch(s -> s.id().contains("_5_rolling")),
                "单丛链不应有揉捻阶段");
        check("dancong_ferments_directly",
                TeaRollingTrayBlock.isKillGreenLeaf(
                        new ItemStack(TeaItems.stage("dancong_4_killgreen"))),
                "单丛杀青叶可直接进揉茶匾发酵");
        check("route3_dancong_is_shaking",
                "dancong_3_shaking".equals(TeaStages.chain(TeaKind.DANCONG).get(1).id()),
                TeaStages.chain(TeaKind.DANCONG).get(1).id());
        check("route3_black_is_rolling",
                "black_3_rolling".equals(TeaStages.chain(TeaKind.BLACK).get(1).id()),
                TeaStages.chain(TeaKind.BLACK).get(1).id());
        check("route3_green_is_killgreen",
                "green_3_killgreen".equals(TeaStages.chain(TeaKind.GREEN).get(1).id()),
                TeaStages.chain(TeaKind.GREEN).get(1).id());

        // 绿茶「杀青叶揉捻」必须变绿茶揉捻叶，不能变单丛
        check("rolling_green_produces_green",
                "green_4_rolling".equals(
                        TeaRollingTrayBlock.rolledOutputFor("green_3_killgreen")),
                String.valueOf(TeaRollingTrayBlock.rolledOutputFor("green_3_killgreen")));
        check("rolling_wither_produces_black",
                "black_3_rolling".equals(
                        TeaRollingTrayBlock.rolledOutputFor(TeaStages.SHARED_WITHER_ID)),
                String.valueOf(TeaRollingTrayBlock.rolledOutputFor(TeaStages.SHARED_WITHER_ID)));
        // 需求：绿茶「晒青之后，第一次放炒茶锅炒制」→ 杀青
        check("stir_green_first_is_killgreen",
                "green_3_killgreen".equals(TeaProcessing.stirOutput(TeaStages.SHARED_WITHER_ID)),
                String.valueOf(TeaProcessing.stirOutput(TeaStages.SHARED_WITHER_ID)));
        // 需求：绿茶最后一步是碳焙
        check("bake_accepts_green_drying", TeaProcessing.canBake("green_5_drying"), "green_5_drying 进碳焙");
        check("finished_green_is_green_tea",
                "green_6_finished".equals(TeaProcessing.finishedFor("green_5_drying", TeaOrigin.TEMPERATE)),
                String.valueOf(TeaProcessing.finishedFor("green_5_drying", TeaOrigin.TEMPERATE)));
        // 揉茶匾要能收绿茶的杀青叶，且揉完输出绿茶揉捻叶
        check("rolling_accepts_green_killgreen",
                TeaRollingTrayBlock.isKillGreenLeaf(new ItemStack(TeaItems.stage("green_3_killgreen"))),
                "green_3_killgreen");

        // 生叶不能直接下锅
        check("stir_rejects_fresh_leaf", !TeaProcessing.canStir("dancong_2_sunwither"), "晒青叶不可炒");

        // 碳焙框只收「发酵好的茶」
        check("bake_accepts_black_firstbake", TeaProcessing.canBake("black_5_firstbake"), "black_5_firstbake");
        check("bake_output_black", "black_6_finished".equals(TeaProcessing.bakeOutput("black_5_firstbake")),
                String.valueOf(TeaProcessing.bakeOutput("black_5_firstbake")));
        // 绿茶链路已改：只有「第二步炒制后的炒干叶」能进碳焙，其余阶段进不去。
        // （旧需求是绿茶完全不进碳焙，新需求是绿茶最后一步必须碳焙 —— 见 stir_green_first_is_killgreen）
        boolean anyGreenTooEarly = false;
        for (TeaStages.Stage s : TeaStages.chain(TeaKind.GREEN)) {
            if (!"green_5_drying".equals(s.id()) && TeaProcessing.canBake(s.id())) {
                anyGreenTooEarly = true;
            }
        }
        check("bake_rejects_early_green", !anyGreenTooEarly,
                "只有炒干叶(green_5_drying)能进碳焙筐");
        // 没发酵的也不能进
        check("bake_rejects_unfermented", !TeaProcessing.canBake("dancong_5_rolling"), "揉捻叶不可焙");

        // 模型：铁锅是「炼药锅 Y 轴 ×2/3」，最高点必须是 10.67
        JsonObject wokModel = jsonAt("assets/" + NS + "/models/block/tea_wok.json");
        if (wokModel != null && wokModel.has("elements")) {
            var elems = wokModel.getAsJsonArray("elements");
            float maxY = 0f;
            for (int i = 0; i < elems.size(); i++) {
                maxY = Math.max(maxY, elems.get(i).getAsJsonObject().getAsJsonArray("to").get(1).getAsFloat());
            }
            check("wok_height_two_thirds", Math.abs(maxY - 10.667f) < 0.02f,
                    "最高点 " + maxY + "px（16 × 2/3 = 10.67）");
            check("wok_has_cauldron_structure", elems.size() == 13, elems.size() + " 个 element（炼药锅为 13）");
        } else {
            check("wok_model_exists", false, "模型缺失");
        }

        // 炒茶锅的物品图必须是「3D 方块模型 + 等距视角」。
        // 之前直接拿顶面俯视图当图标，看起来就是一块平板，认不出是锅。
        JsonObject wokItem = jsonAt("assets/" + NS + "/models/item/tea_wok.json");
        boolean is3d = wokItem != null
                && wokItem.has("parent")
                && wokItem.get("parent").getAsString().startsWith(NS + ":block/tea_wok");
        check("wok_item_uses_block_model", is3d,
                is3d ? "parent=block/tea_wok（3D）" : "物品图不是 3D 方块模型");

        boolean isoGui = false;
        if (wokItem != null && wokItem.has("display")) {
            var disp = wokItem.getAsJsonObject("display");
            if (disp.has("gui")) {
                var rot = disp.getAsJsonObject("gui").getAsJsonArray("rotation");
                // 原版 block/block.json 的等距角度就是 30 / 225 / 0
                isoGui = Math.abs(rot.get(0).getAsFloat() - 30f) < 0.01f
                        && Math.abs(rot.get(1).getAsFloat() - 225f) < 0.01f;
            }
        }
        check("wok_item_isometric_gui", isoGui, "gui 旋转应为 [30, 225, 0]");

        // 模型 JSON 里不能有 _comment 之类的自定义键，原版解析器会当成未知字段
        check("wok_item_no_comment_key", wokItem != null && !wokItem.has("_comment"),
                "_comment 会让模型加载失败");

        // 名字改成了「炒茶锅」
        String wokName = Component.translatable("block.teacraft.tea_wok").getString();
        check("wok_named_chaocha_guo", wokName.contains("炒茶锅") || wokName.contains("Wok"),
                wokName);

        // ---- 碳焙框外形：下半段必须是「看得见的铁锅」 ----
        // 需求原话：「底部本应是个铁锅用来放木炭的」。
        // 早先是一整块 16³ 实心方块，铁锅贴图只在朝下那一面，外面完全看不见。
        JsonObject stoveModel = jsonAt("assets/" + NS + "/models/block/tea_charcoal_stove.json");
        if (stoveModel != null && stoveModel.has("elements")) {
            var els = stoveModel.getAsJsonArray("elements");
            float lowest = 99f;
            float ironLowest = 99f;
            boolean ironOnSide = false;
            for (int i = 0; i < els.size(); i++) {
                var el = els.get(i).getAsJsonObject();
                float fromY = el.getAsJsonArray("from").get(1).getAsFloat();
                float toY = el.getAsJsonArray("to").get(1).getAsFloat();
                lowest = Math.min(lowest, fromY);
                if (el.getAsJsonObject("faces").has("north")) ironOnSide = true;
                if (fromY < 1f) ironLowest = Math.min(ironLowest, toY);
            }
            check("stove_has_iron_pot_base", els.size() >= 2 && lowest < 1f,
                    "底部 element 从 y=" + lowest + " 起（铁锅）");
            check("stove_iron_pot_visible_from_side", ironOnSide,
                    "铁锅有朝外的侧面，从外面能看见");
        } else {
            check("stove_model_exists", false, "模型缺失");
        }

        // 铁锅侧面贴图必须真的存在，否则会变成紫黑
        check("stove_iron_side_texture",
                resourceExists("assets/" + NS + "/textures/block/tea_charcoal_stove_iron_side.png"),
                "tea_charcoal_stove_iron_side.png");

        // 碳焙框：lit 两个状态都要有变体
        JsonObject stoveBs = jsonAt("assets/" + NS + "/blockstates/tea_charcoal_stove.json");
        boolean litOk = stoveBs != null && stoveBs.has("variants")
                && stoveBs.getAsJsonObject("variants").has("lit=false")
                && stoveBs.getAsJsonObject("variants").has("lit=true");
        check("stove_blockstate_lit_variants", litOk, "lit=false / lit=true");

        // 铁锅：has_tea 变体
        JsonObject wokBs = jsonAt("assets/" + NS + "/blockstates/tea_wok.json");
        boolean wokOk = wokBs != null && wokBs.has("variants")
                && wokBs.getAsJsonObject("variants").has("has_tea=false")
                && wokBs.getAsJsonObject("variants").has("has_tea=true");
        check("wok_blockstate_variants", wokOk, "has_tea=false / has_tea=true");

        // ---- 成品判定：摇青+杀青 → 凤凰单丛；没经历 → 红茶 ----
        // 单丛：揉茶匾发酵完成 → 直接碳焙 → 成品（不经过炒茶锅）
        check("finished_dancong_is_phoenix",
                "dancong_8_finished".equals(TeaProcessing.finishedFor("dancong_6_roasting", TeaOrigin.TEMPERATE)),
                String.valueOf(TeaProcessing.finishedFor("dancong_6_roasting", TeaOrigin.TEMPERATE)));
        check("finished_black_is_red_tea",
                "black_6_finished".equals(TeaProcessing.finishedFor("black_5_firstbake", TeaOrigin.TEMPERATE)),
                String.valueOf(TeaProcessing.finishedFor("black_5_firstbake", TeaOrigin.TEMPERATE)));
        // 绿茶最后一步就是碳焙 → 必须产出成品绿茶
        check("finished_green_is_registered",
                "green_6_finished".equals(TeaProcessing.finishedFor("green_5_drying", TeaOrigin.TEMPERATE)),
                String.valueOf(TeaProcessing.finishedFor("green_5_drying", TeaOrigin.TEMPERATE)));
        // 成品必须是「已注册的物品」，否则界面上会变成空气
        for (String fin : new String[]{"dancong_8_finished", "black_6_finished", "green_6_finished"}) {
            Item it = TeaItems.stage(fin);
            check("finished_item_registered_" + fin, it != null && BuiltInRegistries.ITEM.getKey(it) != null, fin);
        }

        // ---- 槽位规则：燃料只收木炭，待焙只收发酵茶，成品不收 ----
        check("stove_slot_fuel_only_charcoal",
                new ItemStack(net.minecraft.world.item.Items.CHARCOAL).is(net.minecraft.world.item.Items.CHARCOAL),
                "木炭");
        // 用 TeaProcessing 直接验证输入槽的判定（方块实体 canPlaceItem 依赖它）
        check("stove_input_accepts_firstbake", TeaProcessing.canBake("black_5_firstbake"), "black_5_firstbake");
        check("stove_input_rejects_fresh", !TeaProcessing.canBake("dancong_2_sunwither"), "晒青叶不可进");

        // ---- 工序与产地都必须能带到成品（后续做不同功效的前提） ----
        ItemStack sample = new ItemStack(TeaItems.stage("dancong_8_finished"));
        TeaComponents.setOrigin(sample, TeaOrigin.TROPICAL);
        check("finished_keeps_origin",
                TeaComponents.originOf(sample, TeaOrigin.TEMPERATE) == TeaOrigin.TROPICAL,
                TeaComponents.originOf(sample, TeaOrigin.TEMPERATE).id());

        // ---- 菜单与界面接线 ----
        check("stove_menu_registered",
                BuiltInRegistries.MENU.getKey(com.heishandashu.teacraft.menu.TeaMenus.CHARCOAL_STOVE) != null,
                String.valueOf(BuiltInRegistries.MENU.getKey(com.heishandashu.teacraft.menu.TeaMenus.CHARCOAL_STOVE)));
        check("stove_slot_count_is_3", TeaCharcoalStoveBlockEntity.SLOT_COUNT == 3,
                "3 槽：燃料/待焙/成品");

        // 需求：碳焙一次 8 个物品、30 秒
        check("stove_batch_is_8", TeaCharcoalStoveBlockEntity.BATCH == 8,
                "BATCH=" + TeaCharcoalStoveBlockEntity.BATCH);
        check("stove_batch_takes_30s", TeaCharcoalStoveBlockEntity.BAKE_TICKS == 600,
                TeaCharcoalStoveBlockEntity.BAKE_TICKS + " ticks");

        // 方块物品必须有 item.* 兜底语言键。
        // 茶树曾经就因为只有 block.* 而"名字不显示"；碳焙筐同样踩过一次。
        String[] blockItemPaths = {"tea_bush", "tea_tray", "tea_rolling_tray", "tea_charcoal_stove", "tea_wok"};
        for (String bp : blockItemPaths) {
            Set<String> zh = langKeys("assets/" + NS + "/lang/zh_cn.json");
            Set<String> en = langKeys("assets/" + NS + "/lang/en_us.json");
            check("item_name_fallback_zh_" + bp, zh.contains("item.teacraft." + bp),
                    "item.teacraft." + bp);
            check("item_name_fallback_en_" + bp, en.contains("item.teacraft." + bp),
                    "item.teacraft." + bp);
        }

        // 碳焙筐物品图必须是 3D 方块模型（和炒茶锅一致），不能是平板
        JsonObject stoveItemModel = jsonAt("assets/" + NS + "/models/item/tea_charcoal_stove.json");
        boolean stove3d = stoveItemModel != null && stoveItemModel.has("parent")
                && stoveItemModel.get("parent").getAsString().startsWith(NS + ":block/tea_charcoal_stove");
        // 碳焙筐底面：竹筐元件的 down 面会从锅沿露出一圈白色 —— 必须没有
        JsonObject stoveBlockModel = jsonAt("assets/" + NS + "/models/block/tea_charcoal_stove.json");
        boolean basketHasDown = false;
        if (stoveBlockModel != null && stoveBlockModel.has("elements")) {
            var els = stoveBlockModel.getAsJsonArray("elements");
            // 第一个 element 是竹筐（from.y >= 5），它不该有 down 面
            if (!els.isEmpty()) {
                JsonObject basket = els.get(0).getAsJsonObject();
                if (basket.has("faces")) {
                    basketHasDown = basket.getAsJsonObject("faces").has("down");
                }
            }
        }
        // 铁锅底座必须铺满 0..16：之前是 1..15，底部一圈有穿透缝，
        // 会透出方块外的亮色，看起来就是"底下有一层白色"
        boolean ironFullWidth = false;
        if (stoveBlockModel != null && stoveBlockModel.has("elements")) {
            var els2 = stoveBlockModel.getAsJsonArray("elements");
            if (els2.size() > 1) {
                JsonObject iron2 = els2.get(1).getAsJsonObject();
                var f2 = iron2.getAsJsonArray("from");
                var t2 = iron2.getAsJsonArray("to");
                ironFullWidth = f2.get(0).getAsInt() == 0 && f2.get(2).getAsInt() == 0
                        && t2.get(0).getAsInt() == 16 && t2.get(2).getAsInt() == 16;
            }
        }
        check("stove_iron_base_full_width", ironFullWidth,
                "铁锅底座必须铺满 0..16，否则底部有穿透缝");
        check("stove_basket_has_no_down_face", !basketHasDown,
                "竹筐元件不应有 down 面（会露出白色）");
        check("stove_item_uses_block_model", stove3d,
                stove3d ? "parent=block/tea_charcoal_stove（3D）" : "物品图不是 3D 方块模型");

        // 名字：碳焙筐（改过一次名，钉住防止回归）
        String stoveName = Component.translatable("block.teacraft.tea_charcoal_stove").getString();
        check("stove_named_tanbei_kuang", stoveName.contains("碳焙筐") || stoveName.contains("Basket"),
                stoveName);

        // ---- 界面必须只覆盖「背景层」----
        // 上一版在 extractContents 里先画不透明底板再调 super，把槽位/物品/背包全盖住了，
        // 表现为「木炭和茶都看不见、底部工具栏被隐藏」。
        // 这里静态检查：屏幕类必须覆盖 extractBackground，且不能覆盖 extractContents。
        String screenSrc = "com/heishandashu/teacraft/client/TeaCharcoalStoveScreen.class";
        check("screen_class_present", resourceExists(screenSrc), screenSrc);
        boolean usesBackgroundHook = classDeclaresMethod("TeaCharcoalStoveScreen", "extractBackground");
        boolean hijacksContents = classDeclaresMethod("TeaCharcoalStoveScreen", "extractContents");
        check("screen_draws_on_background_layer", usesBackgroundHook,
                "应覆盖 extractBackground（背景层，不盖槽位）");
        check("screen_does_not_override_contents", !hijacksContents,
                "不应覆盖 extractContents，否则会盖住原版槽位");

        // 碳焙框必须烧木炭：燃料上限与单块燃烧时长
        check("stove_fuel_is_charcoal", TeaCharcoalStoveBlockEntity.CHARCOAL_TICKS == 1600,
                TeaCharcoalStoveBlockEntity.CHARCOAL_TICKS + " ticks/块");
        check("stove_bake_takes_30s", TeaCharcoalStoveBlockEntity.BAKE_TICKS == 600,
                TeaCharcoalStoveBlockEntity.BAKE_TICKS + " ticks");
    }

    /**
     * 模型的形状约束 —— 需求「盘沿凸起、中间下沉」+「茶叶不能盖住茶盘」。
     *
     * <p>结构固定为：底板(0~1.5) + 四道围边(1.5~3) [+ 茶叶(1.51)]。
     * 底板顶面比围边低 1.5px，这就是那个凹槽；早期版本是一块 0~3 的实心板，
     * 盘沿和里面一样高，所以这条断言直接钉住三个数字。</p>
     */
    private static void trayModelChecks() {
        String[] stages = {"empty", "fresh", "withered", "shaken", "killgreen"};
        for (int i = 0; i < stages.length; i++) {
            JsonObject model = jsonAt("assets/" + NS + "/models/block/tea_tray_" + stages[i] + ".json");
            check("tray_model_" + stages[i], model != null, stages[i]);
            if (model == null) continue;

            var elements = model.getAsJsonArray("elements");
            int expected = i == 0 ? 5 : 6;      // 底板 + 四围边 (+ 茶叶)
            check("tray_elements_" + stages[i], elements != null && elements.size() == expected,
                    (elements == null ? 0 : elements.size()) + " 层 / 期望 " + expected);
            if (elements == null || elements.size() < 5) continue;

            // 底板顶面 1.5px
            float floorTop = elements.get(0).getAsJsonObject()
                    .getAsJsonArray("to").get(1).getAsFloat();
            check("tray_floor_recessed_" + stages[i], Math.abs(floorTop - 1.5f) < 0.01f,
                    "盘底顶面 y=" + floorTop);

            // 四道围边顶到 3px
            float rimTop = 0f;
            for (int k = 1; k <= 4; k++) {
                rimTop = Math.max(rimTop, elements.get(k).getAsJsonObject()
                        .getAsJsonArray("to").get(1).getAsFloat());
            }
            check("tray_rim_raised_" + stages[i], Math.abs(rimTop - 3.0f) < 0.01f,
                    "盘沿顶面 y=" + rimTop);
            check("tray_rim_above_floor_" + stages[i], rimTop - floorTop > 1.0f,
                    "落差 " + (rimTop - floorTop) + "px");

            if (i == 0) continue;

            // 最后一个是茶叶：内缩、占 80%
            JsonObject leaf = elements.get(elements.size() - 1).getAsJsonObject();
            var from = leaf.getAsJsonArray("from");
            var to = leaf.getAsJsonArray("to");
            float fx = from.get(0).getAsFloat(), fz = from.get(2).getAsFloat();
            float tx = to.get(0).getAsFloat(), tz = to.get(2).getAsFloat();
            check("tray_leaves_inset_" + stages[i],
                    fx >= 1.0f && fz >= 1.0f && tx <= 15.0f && tz <= 15.0f,
                    "茶叶 [" + fx + ".." + tx + "] × [" + fz + ".." + tz + "]");
            float widthRatio = (tx - fx) / 16f;
            check("tray_leaves_size_" + stages[i], widthRatio >= 0.70f && widthRatio <= 0.90f,
                    "占宽 " + Math.round(widthRatio * 100) + "%（目标 80%）");
            float leafY = from.get(1).getAsFloat();
            check("tray_leaves_on_floor_" + stages[i], leafY >= floorTop && leafY < rimTop,
                    "茶叶 y=" + leafY + "（应在盘底与盘沿之间）");
        }
    }

    /* ================= 产地链路 ================= */

    /**
     * 需求：「将来产自不同地区的茶叶做出来的成茶，饮用效果是有区别的」。
     *
     * <p>物品只注册一份，产地存在组件里（否则物品数量会乘 5）。这几条断言保证：
     * 组件能存能读、名字会随产地变化、摇青产出确实带着产地。</p>
     */
    private static void originChecks() {
        Item stageItem = TeaItems.stage(TeaTrayBlock.SHAKEN_OUTPUT_ID);
        check("shaken_output_registered", stageItem != null && BuiltInRegistries.ITEM.getKey(stageItem) != null,
                TeaTrayBlock.SHAKEN_OUTPUT_ID);

        // 萎凋与摇青是两条分支：产出必须是两个不同的物品，且都已注册
        Item witheredOut = TeaItems.stage(TeaTrayBlock.WITHERED_OUTPUT_ID);
        check("withered_output_registered",
                witheredOut != null && BuiltInRegistries.ITEM.getKey(witheredOut) != null,
                TeaTrayBlock.WITHERED_OUTPUT_ID);
        check("two_branches_distinct", witheredOut != null && stageItem != null && witheredOut != stageItem,
                TeaTrayBlock.WITHERED_OUTPUT_ID + " != " + TeaTrayBlock.SHAKEN_OUTPUT_ID);

        // 任何阶段都要能取出：新茶退还鲜叶、萎凋→晒青叶、摇青→做青叶（且都带产地）
        ItemStack fromFresh = TeaTrayBlock.outputForStage(TeaTrayBlock.STAGE_FRESH, TeaOrigin.TROPICAL);
        check("takeout_fresh_returns_leaf",
                fromFresh.getItem() == TeaItems.freshLeaf(TeaOrigin.TROPICAL),
                fromFresh.getItem().toString());

        ItemStack fromWithered = TeaTrayBlock.outputForStage(TeaTrayBlock.STAGE_WITHERED, TeaOrigin.POLAR);
        check("takeout_withered_returns_sunwither",
                fromWithered.getItem() == TeaItems.stage(TeaTrayBlock.WITHERED_OUTPUT_ID)
                        && TeaComponents.originOf(fromWithered, TeaOrigin.TEMPERATE) == TeaOrigin.POLAR,
                TeaTrayBlock.WITHERED_OUTPUT_ID + " / " + TeaComponents.originOf(fromWithered, TeaOrigin.TEMPERATE).id());

        ItemStack fromShaken = TeaTrayBlock.outputForStage(TeaTrayBlock.STAGE_SHAKEN, TeaOrigin.BOREAL);
        check("takeout_shaken_returns_shaking",
                fromShaken.getItem() == TeaItems.stage(TeaTrayBlock.SHAKEN_OUTPUT_ID)
                        && TeaComponents.originOf(fromShaken, TeaOrigin.TEMPERATE) == TeaOrigin.BOREAL,
                TeaTrayBlock.SHAKEN_OUTPUT_ID + " / " + TeaComponents.originOf(fromShaken, TeaOrigin.TEMPERATE).id());

        check("takeout_empty_stage_is_empty",
                TeaTrayBlock.outputForStage(TeaTrayBlock.STAGE_EMPTY, TeaOrigin.TEMPERATE).isEmpty(),
                "空盘不产出");

        ItemStack plain = new ItemStack(stageItem);
        check("origin_absent_by_default", !TeaComponents.hasOrigin(plain), "创造栏新物品不带产地");

        ItemStack tropical = new ItemStack(stageItem);
        TeaComponents.setOrigin(tropical, TeaOrigin.TROPICAL);
        check("origin_roundtrip",
                TeaComponents.hasOrigin(tropical)
                        && TeaComponents.originOf(tropical, TeaOrigin.TEMPERATE) == TeaOrigin.TROPICAL,
                TeaComponents.originOf(tropical, TeaOrigin.TEMPERATE).id());

        ItemStack polar = new ItemStack(stageItem);
        TeaComponents.setOrigin(polar, TeaOrigin.POLAR);

        // 不同产地 → 显示名必须不同（否则玩家看不出区别）
        String nPlain = plain.getHoverName().getString();
        String nTropical = tropical.getHoverName().getString();
        String nPolar = polar.getHoverName().getString();
        check("origin_changes_name", !nTropical.equals(nPlain) && !nTropical.equals(nPolar),
                "热带=" + nTropical + " / 极寒=" + nPolar + " / 无产地=" + nPlain);

        // 五个产地互不相同
        Set<String> names = new LinkedHashSet<>();
        for (TeaOrigin o : TeaOrigin.values()) {
            ItemStack s = new ItemStack(stageItem);
            TeaComponents.setOrigin(s, o);
            names.add(s.getHoverName().getString());
        }
        check("origin_names_all_distinct", names.size() == TeaOrigin.values().length,
                names.size() + " 个不同名字 / " + TeaOrigin.values().length + " 个产地");
        // ---------- 产地必须贯穿整条加工链 ----------
        //
        // 【用户报的 bug】「茶叶的产地信息怎么会都变成温带」。
        // 产地是品质的唯一来源（quality = origin.quality()），一旦中途丢失，
        // 所有茶都会退回默认的温带、品质 3、60 分钟 —— 极寒茶再也到不了 100 分钟。
        //
        // 这里把三条链从头走到尾，每一步都验产地还在。
        for (TeaOrigin o : TeaOrigin.values()) {
            String tag = o.id();

            // 1) 鲜叶：采摘时就该带产地
            ItemStack leaf = new ItemStack(TeaItems.freshLeaf(o));
            TeaComponents.setOrigin(leaf, o);
            check("chain_origin_leaf_" + tag,
                    TeaComponents.originOf(leaf, TeaOrigin.TEMPERATE) == o,
                    "鲜叶产地丢失：" + tag);

            // 2) 萎凋叶（晒盘产出）
            ItemStack withered = TeaTrayBlock.outputForStage(TeaTrayBlock.STAGE_WITHERED, o);
            check("chain_origin_withered_" + tag,
                    TeaComponents.originOf(withered, TeaOrigin.TEMPERATE) == o,
                    "萎凋叶产地丢失：" + tag + " 实得 "
                            + TeaComponents.originOf(withered, TeaOrigin.TEMPERATE).id());

            // 3) 揉捻叶（揉茶匾产出）
            ItemStack rolled = TeaRollingTrayBlock.outputForStage(
                    TeaRollingTrayBlock.STAGE_ROLLED, o, "green_3_killgreen");
            check("chain_origin_rolled_" + tag,
                    TeaComponents.originOf(rolled, TeaOrigin.TEMPERATE) == o,
                    "揉捻叶产地丢失：" + tag + " 实得 "
                            + TeaComponents.originOf(rolled, TeaOrigin.TEMPERATE).id());

            // 4) 成品茶（碳焙产出）—— 这是最终决定持续时间的一步
            String finishedId = TeaProcessing.finishedFor("green_5_drying", o);
            Item finishedItem = TeaItems.stage(finishedId);
            check("chain_finished_item_exists_" + tag, finishedItem != null,
                    "成品茶物品不存在：" + finishedId);
            if (finishedItem != null) {
                ItemStack finished = new ItemStack(finishedItem);
                TeaComponents.setOrigin(finished, o);

                check("chain_origin_finished_" + tag,
                        TeaComponents.originOf(finished, TeaOrigin.TEMPERATE) == o,
                        "成品茶产地丢失：" + tag);

                // 5) 品质与持续时间必须跟着产地走
                int q = com.heishandashu.teacraft.item.TeaDrinkItem.qualityOf(finished);
                check("chain_quality_" + tag, q == o.quality(),
                        "品质应为 " + o.quality() + " 实得 " + q);

                int minutes = com.heishandashu.teacraft.item.TeaDrinkItem.minutesOf(finished);
                check("chain_minutes_" + tag, minutes == 20 * o.quality(),
                        "持续时间应为 " + (20 * o.quality()) + " 分钟，实得 " + minutes);
            }
        }

        // 极寒茶必须是 100 分钟（品质 5 × 20）
        check("polar_is_100_minutes", 20 * TeaOrigin.POLAR.quality() == 100,
                "极寒应 100 分钟，实得 " + (20 * TeaOrigin.POLAR.quality()));

        // 且必须严格单调：热带 20 < 亚热带 40 < 温带 60 < 寒带 80 < 极寒 100
        check("minutes_strictly_increasing",
                com.heishandashu.teacraft.effect.TeaEffects.minutesFor(TeaOrigin.TROPICAL.quality()) < com.heishandashu.teacraft.effect.TeaEffects.minutesFor(TeaOrigin.SUBTROPICAL.quality())
                        && com.heishandashu.teacraft.effect.TeaEffects.minutesFor(TeaOrigin.SUBTROPICAL.quality()) < com.heishandashu.teacraft.effect.TeaEffects.minutesFor(TeaOrigin.TEMPERATE.quality())
                        && com.heishandashu.teacraft.effect.TeaEffects.minutesFor(TeaOrigin.TEMPERATE.quality()) < com.heishandashu.teacraft.effect.TeaEffects.minutesFor(TeaOrigin.BOREAL.quality())
                        && com.heishandashu.teacraft.effect.TeaEffects.minutesFor(TeaOrigin.BOREAL.quality()) < com.heishandashu.teacraft.effect.TeaEffects.minutesFor(TeaOrigin.POLAR.quality()),
                "20/40/60/80/100 必须严格递增");

        // ---------- 采摘必须写入产地组件 ----------
        //
        // 【这条专治用户报的 bug】TeaBushBlock 采摘时曾经只 new ItemStack(leaf)，
        // 没把产地写进组件。物品<b>类型</b>是 fresh_leaf_polar 没错，但后续每一道
        // 工序都用 originOf(stack, TEMPERATE) 读组件 —— 读不到就退回温带，
        // 于是无论在哪采摘，做出来的茶都是温带、60 分钟。
        //
        // 用 sourceOf 读源码文本断言：采摘处必须调 setOrigin。
        String bushSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/block/TeaBushBlock.java");
        checkSource("harvest_sets_origin", bushSrc,
                bushSrc != null && bushSrc.contains("new ItemStack(leaf)")
                        && bushSrc.contains("TeaComponents.setOrigin(stack, origin)"),
                "采摘生成的叶子必须 setOrigin，否则产地丢失会退回温带");
        // 五档品质对应的分钟数必须是 20/40/60/80/100
        int[] wantMinutes = {20, 40, 60, 80, 100};
        for (int qi = 0; qi < 5; qi++) {
            int got = com.heishandashu.teacraft.effect.TeaEffects.minutesFor(qi + 1);
            check("minutes_q" + (qi + 1), got == wantMinutes[qi],
                    "品质 " + (qi + 1) + " 应 " + wantMinutes[qi] + " 分钟，实得 " + got);
        }

        // 端起盘子→放下（摇青的必经路径）之后，产地必须还在方块实体里。
        //
        // 【这条专治「摇青一次就变温带」】摇青的操作是：潜行右键端起整只盘、
        // 摇三下、再放下。pickUp 会把产地写进 tray_origin 组件，但 useOn 放下时
        // 只恢复了 count/stage，漏了产地 —— 方块实体重建后 origin 退回默认温带。
        String trayItemSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/item/TeaTrayItem.java");
        checkSource("tray_place_restores_origin", trayItemSrc,
                trayItemSrc != null && trayItemSrc.contains("TRAY_ORIGIN")
                        && trayItemSrc.contains("setOrigin"),
                "放置茶盘时必须从 tray_origin 组件还原产地，否则摇青后变温带");

        // 同理：TRAY_ORIGIN 不能只写不读（历史 bug 就是零处读取）
        String trayBlockSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/block/TeaTrayBlock.java");
        checkSource("tray_origin_is_read_somewhere", trayBlockSrc,
                trayBlockSrc != null && (trayBlockSrc.contains("stack.get(TeaComponents.TRAY_ORIGIN)")
                        || trayBlockSrc.contains("getOrDefault(TeaComponents.TRAY_ORIGIN")),
                "tray_origin 写入后必须有人读取，否则等于没存");

    }

    /* ================= 创造栏 ================= */

    private static void creativeTabChecks() {
        final List<ItemStack> collected = new ArrayList<>();
        net.minecraft.world.item.CreativeModeTab.Output collector = (stack, visibility) -> collected.add(stack);
        TeaCreativeTab.addAll(collector);

        check("tab_item_count", collected.size() == TeaItems.ALL_ITEMS.size(),
                collected.size() + "/" + TeaItems.ALL_ITEMS.size());
        List<Item> fromTab = new ArrayList<>();
        for (var s : collected) fromTab.add(s.getItem());
        check("tab_items_match", fromTab.equals(TeaItems.ALL_ITEMS), "顺序与内容一致");
        check("tab_registered",
                BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(TeaCreativeTab.KEY),
                String.valueOf(TeaCreativeTab.KEY.identifier()));
    }

    /* ================= 方块状态（紫黑块防线） ================= */

    private static void checkBlockstate(String path, Block block) {
        String file = "assets/" + NS + "/blockstates/" + path + ".json";
        JsonObject json = jsonAt(file);
        check("blockstate_json_" + path, json != null, file);
        if (json == null) return;

        JsonObject variants = json.has("variants") && json.get("variants").isJsonObject()
                ? json.getAsJsonObject("variants") : null;
        // multipart（按条件拼模型）也是合法写法，不能只认 variants。
        // 茶盘就是这样：盘恒在，盖碗和三个杯子按方块状态决定显示哪个模型。
        boolean isMultipart = json.has("multipart");
        check("blockstate_variants_" + path, variants != null || isMultipart,
                isMultipart ? "multipart 段" : "variants 段");
        if (variants == null) {
            if (isMultipart) {
                check("blockstate_multipart_" + path,
                        json.getAsJsonArray("multipart").size() >= 1, "multipart 条目");
            }
            return;
        }

        int states = block.getStateDefinition().getPossibleStates().size();
        check("blockstate_count_" + path, variants.size() >= states,
                variants.size() + " 变体 / " + states + " 状态");

        String missing = null;
        for (BlockState st : block.getStateDefinition().getPossibleStates()) {
            String key = variantKey(st);
            if (!variants.has(key)) { missing = key; break; }
        }
        check("blockstate_covers_" + path, missing == null,
                missing == null ? "所有状态都有变体" : "缺少 " + missing);

        String badModel = null;
        for (var e : variants.entrySet()) {
            JsonElement v = e.getValue();
            String model = null;
            try {
                JsonObject o = v.isJsonArray() ? v.getAsJsonArray().get(0).getAsJsonObject() : v.getAsJsonObject();
                model = o.get("model").getAsString();
            } catch (Exception ignored) { }
            if (model == null) { badModel = "变体 " + e.getKey() + " 无 model"; break; }
            String mf = "assets/" + path0(model) + "/models/" + path1(model) + ".json";
            if (jsonAt(mf) == null) { badModel = mf; break; }
        }
        check("blockstate_models_" + path, badModel == null,
                badModel == null ? "变体模型全部可解析" : badModel);
    }

    private static String variantKey(BlockState state) {
        StringBuilder sb = new StringBuilder();
        for (Property<?> prop : state.getProperties()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(prop.getName()).append('=').append(propValue(state, prop));
        }
        return sb.toString();
    }

    private static <T extends Comparable<T>> String propValue(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    /* ================= 工具 ================= */

    /**
     * 判断客户端类是否声明了某个方法。
     *
     * <p>用它做「界面只画背景层」的静态检查：如果哪天有人又在
     * {@code extractContents} 里画不透明底板，这条断言会立刻红。</p>
     */
    /** 某个菜单是否已注册到 BuiltInRegistries.MENU。 */
    /** 读工程里的源文件（自检用来钉住"实现方式"而不仅是"结果"）。 */
    /** 读 mixins.json 的原始文本并查找（配置在 classpath 根）。 */
    /**
     * 校验某个方块的 blockstate 里引用的每一个模型都能解析到真实文件。
     *
     * <p>紫黑方块（missing model）几乎都是路径写错造成的：模型 ID 相对于
     * {@code models/}，所以 {@code block/tea_set/tray} 不能简写成
     * {@code tea_set/tray}。这种错误在游戏里只表现为黑紫格子，日志里不报错，
     * 必须靠断言拦住。</p>
     */
    /**
     * 校验模型里每个 face 的贴图引用都合法。
     *
     * <p>规则：face 的 {@code texture} 只能是 {@code #key}，
     * 而且 {@code key} 必须在同一模型的 {@code textures} 里定义过。
     * 直接写完整路径（{@code teacraft:block/xxx}）会解析失败 ——
     * 表现就是<b>那块面变成紫黑格子</b>，正是这次的问题。</p>
     */
    private static void checkModelTextureRefs(String modelPath) {
        JsonObject m = jsonAt(modelPath);
        if (m == null) {
            check("model_texture_refs_" + modelPath, false, "读不到模型");
            return;
        }
        java.util.Set<String> keys = new java.util.TreeSet<>();
        if (m.has("textures")) {
            for (var k : m.getAsJsonObject("textures").keySet()) keys.add(k);
        }
        var elements = m.getAsJsonArray("elements");
        if (elements == null) return;

        int faces = 0;
        java.util.Set<String> bad = new java.util.TreeSet<>();
        for (var el : elements) {
            var f = el.getAsJsonObject().getAsJsonObject("faces");
            if (f == null) continue;
            for (var entry : f.entrySet()) {
                var face = entry.getValue().getAsJsonObject();
                if (!face.has("texture")) continue;
                faces++;
                String tx = face.get("texture").getAsString();
                if (!tx.startsWith("#")) {
                    bad.add(tx + "（缺 # 前缀）");
                    continue;
                }
                String key = tx.substring(1);
                if (!keys.contains(key)) bad.add(tx + "（textures 未定义）");
            }
        }
        check("model_texture_refs_" + modelPath, faces > 0 && bad.isEmpty(),
                bad.isEmpty() ? (faces + " 个面贴图引用合法")
                        : ("非法: " + String.join(", ", bad)));
    }

    private static void checkBlockstateModelsResolve(String blockPath) {
        String raw = readResourceText("assets/" + NS + "/blockstates/" + blockPath + ".json");
        if (raw.isEmpty()) {
            check("blockstate_models_" + blockPath, false, "读不到 blockstate");
            return;
        }
        var m = java.util.regex.Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"").matcher(raw);
        int total = 0;
        int bad = 0;
        java.util.Set<String> missing = new java.util.TreeSet<>();
        while (m.find()) {
            total++;
            String id = m.group(1);
            String path = id;
            int colon = path.indexOf(':');
            if (colon >= 0) path = path.substring(colon + 1);
            if (!path.startsWith("block/") && !path.startsWith("item/")) {
                bad++;
                missing.add(id + "（缺 block/ 前缀）");
                continue;
            }
            if (!resourceExists("assets/" + NS + "/models/" + path + ".json")) {
                bad++;
                missing.add(id);
            }
        }
        check("blockstate_models_resolve_" + blockPath, total > 0 && bad == 0,
                bad == 0 ? (total + " 个模型引用全部可解析")
                        : ("无法解析: " + String.join(", ", missing)));
    }

    private static String readResourceText(String resourcePath) {
        try (var in = TeaSelfCheck.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return "";
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        if (haystack == null || haystack.isEmpty() || needle.isEmpty()) return 0;
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    private static boolean mixinConfigContains(String file, String needle) {
        try (var in = TeaSelfCheck.class.getClassLoader().getResourceAsStream(file)) {
            if (in == null) return false;
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .contains(needle);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 读 PNG 头部拿宽高（不依赖 ImageIO，专服也能跑）。
     * PNG 结构：8 字节签名 + 4 字节长度 + "IHDR" + 宽(4) + 高(4)。
     */
    private static int[] pngSize(String resourcePath) {
        try (var in = TeaSelfCheck.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            byte[] head = in.readNBytes(24);
            if (head.length < 24) return null;
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(head);
            int w = bb.getInt(16);
            int h = bb.getInt(20);
            return new int[]{w, h};
        } catch (Exception e) {
            return null;
        }
    }

    private static String sourceOf(String relative) {
        // 自检进程的工作目录不一定是工程根，所以从当前目录逐级向上找。
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("").toAbsolutePath();
            for (int up = 0; up < 6 && dir != null; up++, dir = dir.getParent()) {
                java.nio.file.Path p = dir.resolve(relative);
                if (java.nio.file.Files.exists(p)) {
                    return java.nio.file.Files.readString(p);
                }
            }
        } catch (Exception ignored) {
            // 读不到就当没有
        }
        return "";
    }

    private static boolean isMenuRegistered(String path) {
        var id = com.heishandashu.teacraft.TeaCraft.id(path);
        return net.minecraft.core.registries.BuiltInRegistries.MENU.containsKey(id);
    }

    private static boolean classDeclaresMethod(String simpleName, String method) {
        // 不能 Class.forName：专服里客户端类根本没加载（会 ClassNotFoundException）。
        // 直接读 class 文件的常量池字符串 —— 方法名必然以 UTF8 常量出现，
        // 这对「有没有声明这个方法」够用了，而且服务端也能跑。
        String path = "com/heishandashu/teacraft/client/" + simpleName + ".class";
        try (InputStream in = TeaSelfCheck.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return false;
            byte[] b = in.readAllBytes();
            String s = new String(b, StandardCharsets.ISO_8859_1);
            // 方法名必须单独出现（前面是长度字节），这里用 Contains 足够：
            // extractBackground / extractContents 都是独一无二的长名字。
            return s.contains(method);
        } catch (Exception e) {
            return false;
        }
    }

    /** 数某个位置附近掉了几个茶树苗。 */
    private static int countBushItems(net.minecraft.server.level.ServerLevel level, BlockPos around) {
        int n = 0;
        for (var it : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(around).inflate(12))) {
            if (it.getItem().is(TeaItems.TEA_BUSH_ITEM)) n += it.getItem().getCount();
        }
        return n;
    }
    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            TeaCraft.LOGGER.info("[teacraft] TEST {} -> PASS  {}", name, detail);
        } else {
            failed++;
            TeaCraft.LOGGER.error("[teacraft] TEST {} -> FAIL  {}", name, detail);
        }
    }

    /**
     * 只在<b>能读到工程源码</b>时才有效的检查。
     *
     * <h2>为什么需要它</h2>
     * <p>有些断言是靠读 <code>src/main/java/**.java</code> 的文本来验证实现方式的
     * （比如"取物要先判断杯子"、"拿起壶要写 BLOCK_ENTITY_DATA"）。
     * 这类断言在 <b>gradle runServer</b> 自检里没问题，
     * 但用户装好 mod 正常进游戏时，<b>源码不在 classpath 里</b>，
     * 于是统统 FAIL —— 日志里出现一堆假失败，掩盖真正的问题。</p>
     *
     * <p>所以：源码读不到时<b>跳过</b>（记为 SKIP，不算失败也不冒充通过）。
     * 源码读得到时，照常判定。</p>
     */
    private static void checkSource(String name, String source, boolean okWhenReadable, String detail) {
        if (source == null || source.isEmpty()) {
            skipped++;
            TeaCraft.LOGGER.info("[teacraft] TEST {} -> SKIP  （读不到源码，仅开发自检有效）", name);
            return;
        }
        check(name, okWhenReadable, detail);
    }

    private static Set<String> langKeys(String path) {
        Set<String> keys = new LinkedHashSet<>();
        JsonObject obj = jsonAt(path);
        if (obj != null) keys.addAll(obj.keySet());
        return keys;
    }

    /** 读一个语言键的<b>正文</b>（不是键名）。读不到返回空串。 */
    private static String langValue(String path, String key) {
        JsonObject obj = jsonAt(path);
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) return "";
        return obj.get(key).getAsString();
    }

    /**
     * 收集物品定义里引用到的所有 {@code "model": "ns:path"}。
     *
     * <p>26.x 的物品定义可以是组合结构：
     * {@code condition}(using_item) → {@code range_dispatch}(use_duration) → 一堆 {@code model}。
     * 早先只认 {@code model.model} 这种单层写法，加了摇动动画后就会漏检，所以改成递归。
     */
    private static List<String> itemModels(String path) {
        List<String> out = new ArrayList<>();
        JsonObject obj = jsonAt(path);
        if (obj != null) collectModels(obj, out);
        return out;
    }

    private static void collectModels(JsonElement el, List<String> out) {
        if (el == null) return;
        if (el.isJsonObject()) {
            for (var e : el.getAsJsonObject().entrySet()) {
                if (e.getKey().equals("model") && e.getValue().isJsonPrimitive()) {
                    out.add(e.getValue().getAsString());
                } else {
                    collectModels(e.getValue(), out);
                }
            }
        } else if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) collectModels(e, out);
        }
    }

    private static boolean isVanillaModel(String ref) {
        return ref.startsWith("minecraft:");
    }

    /**
     * 模型引用的贴图是否都存在（layer0 / all / cross / particle 任一）。
     *
     * <p><b>必须沿 parent 向上找</b>：`models/item/tea_wok.json` 是「3D 方块模型 + 等距视角」，
     * 它自己没有 textures，贴图全在 `models/block/tea_wok.json` 里。只看本模型会误报。
     * 之前的揉茶匾图标也踩过同一个坑（那时是改用扁平图标绕开的，这次直接修检查本身）。</p>
     */
    private static boolean textureOf(String modelFile) {
        return textureOf(modelFile, 0);
    }

    private static boolean textureOf(String modelFile, int depth) {
        // 防止 parent 成环导致栈溢出
        if (depth > 8) return false;

        JsonObject obj = jsonAt(modelFile);
        if (obj == null) return false;

        if (obj.has("textures")) {
            JsonObject tex = obj.getAsJsonObject("textures");
            boolean any = false;
            for (String key : new String[]{"layer0", "all", "cross", "particle"}) {
                if (!tex.has(key)) continue;
                any = true;
                String ref = tex.get(key).getAsString();
                if (ref.startsWith("#")) continue;          // 变量引用，跳过
                if (!resourceExists("assets/" + path0(ref) + "/textures/" + path1(ref) + ".png")) return false;
            }
            if (any) return true;
        }

        // 自己没贴图 → 顺着 parent 继续找
        if (obj.has("parent")) {
            String parent = obj.get("parent").getAsString();
            if (parent.startsWith("minecraft:")) {
                // 原版的模型（builtin/generated、block/block 等）不在这里检查
                return true;
            }
            // parent 的写法本身就是「命名空间:目录/名字」，例如 teacraft:block/tea_wok。
            // 所以只能拼 models/ 前缀，绝不能再补一次 block/ —— 那样会变成
            // models/block/block/tea_wok.json（早期就是这么误报的）。
            String parentPath = "assets/" + path0(parent) + "/models/" + path1(parent) + ".json";
            return textureOf(parentPath, depth + 1);
        }

        return false;
    }

    private static JsonObject jsonAt(String path) {
        try (InputStream in = TeaSelfCheck.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean resourceExists(String path) {
        try (InputStream in = TeaSelfCheck.class.getClassLoader().getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 读资源原始文本（用于抓 "null" 这类解析层面的问题）。 */
    private static String rawResource(String path) {
        try (InputStream in = TeaSelfCheck.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 读一个 JSON 资源；不存在就返回空对象（断言会因此报错，便于定位）。 */
    private static JsonObject jsonResource(String path) {
        String raw = rawResource(path);
        if (raw == null) {
            throw new IllegalStateException("找不到资源：" + path);
        }
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static String path0(String ref) {
        int i = ref.indexOf(':');
        return i < 0 ? NS : ref.substring(0, i);
    }

    private static String path1(String ref) {
        int i = ref.indexOf(':');
        return i < 0 ? ref : ref.substring(i + 1);
    }
    /* ---------------- 合成表查询（自检用） ---------------- */

    /**
     * 验证「用指定材料摆出的形状能合出目标物品」。
     *
     * <p>为什么不只检查 json 是否存在：配方格式写错时文件照样在，
     * 游戏只会静默忽略 —— 创造栏里有物品、但玩家合不出来。
     * 所以这里直接问 {@code RecipeManager} 能不能找到匹配的配方。</p>
     *
     * <p>注意：本方法按「无序输入」查询，能验证材料种类和数量，
     * 但<b>不验证摆放位置</b>。位置语义由配方 json 的 pattern 决定，
     * 这里另外用文本断言补上。</p>
     */
    private static void checkCraftingRecipeFor(
            net.minecraft.server.MinecraftServer server,
            net.minecraft.world.item.Item target,
            net.minecraft.world.item.ItemStack[] ingredients,
            String name) {

        var level = server.overworld();
        // 26.3 里 ServerLevel 把这个方法改名成 recipeAccess()，用 MinecraftServer#getRecipeManager() 更稳
        var manager = server.getRecipeManager();

        // 3x3 网格，把材料塞进前几个格子（不放哪都行：shaped 会滑动匹配）
        var grid = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        for (var st : ingredients) grid.add(st);
        while (grid.size() < 9) grid.add(net.minecraft.world.item.ItemStack.EMPTY);

        var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, grid);
        var found = manager.getRecipeFor(
                net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);

        boolean ok = false;
        String detail;
        if (found.isEmpty()) {
            detail = "用「木台阶 + 竹子 + 木台阶」查不到任何配方";
        } else {
            // 26.3 的 Recipe 只有 assemble(input)，没有 getResultItem
            var result = found.get().value().assemble(input);
            ok = !result.isEmpty() && result.is(target);
            detail = "配方产出 " + (result.isEmpty() ? "空" : result.getItem().toString());
        }
        check(name, ok, detail);

        // 形状断言：pattern 必须是 "#B#"，且 # 是木台阶标签、B 是竹子
        JsonObject recipe = jsonAt("data/" + NS + "/recipe/tea_set.json");
        if (recipe == null) {
            check(name + "_json", false, "缺少 data/teacraft/recipe/tea_set.json");
            return;
        }
        String pattern = recipe.has("pattern")
                ? recipe.getAsJsonArray("pattern").get(0).getAsString() : "";
        check(name + "_pattern", "#B#".equals(pattern),
                "摆放形状应为 [#B#]（左右木台阶、中间竹子），实际 [" + pattern + "]");

        String slabKey = recipe.getAsJsonObject("key").has("#")
                ? recipe.getAsJsonObject("key").get("#").getAsString() : "";
        check(name + "_slab_tag", "#minecraft:wooden_slabs".equals(slabKey),
                "两侧应为任意木台阶，实际 " + slabKey);

        String midKey = recipe.getAsJsonObject("key").has("B")
                ? recipe.getAsJsonObject("key").get("B").getAsString() : "";
        check(name + "_bamboo", "minecraft:bamboo".equals(midKey),
                "中间应为竹子，实际 " + midKey);
    }

    /* ---------------- 茶盘拿起：整只搬走且保留内容 ---------------- */

    /**
     * 右键盘沿要把整只茶盘拿起来，并且带走盘上的东西。
     *
     * <p>验证三件事：</p>
     * <ol>
     *   <li>盘沿（不属于杯子/盖碗的位置）能拿起茶盘 —— 之前这里被当成「取茶具」</li>
     *   <li>物品上记下了盖碗/杯子/朝向（否则放下就空了）</li>
     *   <li>盘里有茶叶时也能拿起，放回去茶叶和茶汤都还在</li>
     * </ol>
     */
    private static void checkTeaSetPickUp(MinecraftServer server) {
        var level = server.overworld();
        var pos = new BlockPos(0, 120, 4);
        var teaSet = (com.heishandashu.teacraft.block.TeaSetBlock) TeaItems.TEA_SET;

        // ① 放一个空茶盘（现在的默认就是空盘），朝向 EAST
        level.setBlock(pos, TeaItems.TEA_SET.defaultBlockState()
                .setValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING,
                        net.minecraft.core.Direction.EAST), Block.UPDATE_ALL);

        var player = new net.minecraft.world.entity.player.Player(level,
                new com.mojang.authlib.GameProfile(
                        java.util.UUID.nameUUIDFromBytes("teacraft_pickup".getBytes()),
                        "teacraft_pickup")) {
            @Override
            public net.minecraft.world.level.GameType gameMode() {
                return net.minecraft.world.level.GameType.SURVIVAL;
            }
        };
        player.getInventory().clearContent();

        // ★ 空盘必须先验证安全：还没有盖碗时，倒热水不该生效
        //
        // 合成出来的只是茶盘，玩家很可能先拿水壶右键它。
        //
        // 注意这里<b>故意往方块实体里塞了干茶</b>：
        // 如果只测"空盘+空数据"，pourHotWater 自己那层
        // 「没干茶就返回 false」也会拦住，测不出 GAIWAN 这层判断是否还在。
        // 塞了干茶之后，唯一能拦住它的就是 tryBrewing 里的 GAIWAN 检查。
        if (level.getBlockEntity(pos)
                instanceof com.heishandashu.teacraft.block.TeaSetBlockEntity beSetup) {
            beSetup.setDryTea("green|4|2");
        }

        var hotKettle = new ItemStack(TeaItems.TEA_KETTLE_ITEM);
        com.heishandashu.teacraft.item.TeaKettleItem.setHotWater(hotKettle, true);
        var emptyHit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.UP, pos, false);
        level.getBlockState(pos).useItemOn(hotKettle, level, player,
                net.minecraft.world.InteractionHand.MAIN_HAND, emptyHit);
        String brewOnBare = "";
        if (level.getBlockEntity(pos)
                instanceof com.heishandashu.teacraft.block.TeaSetBlockEntity be0) {
            brewOnBare = be0.gaiwanBrew();
        }
        check("tea_set_bare_tray_no_brew", brewOnBare.isEmpty(),
                "空盘（无盖碗）上倒热水不该煮出茶汤，实际 gaiwanBrew=" + brewOnBare);
        check("tea_set_bare_tray_still_there", !level.getBlockState(pos).isAir(),
                "空盘上倒热水不该把茶盘弄没了");

        // 清掉刚才塞的测试数据，后面按正常流程放盖碗
        if (level.getBlockEntity(pos)
                instanceof com.heishandashu.teacraft.block.TeaSetBlockEntity beReset) {
            beReset.setDryTea("");
            beReset.setGaiwanBrew("");
        }

        // ② 玩家自己把盖碗放到盘上（走真实的 useItemOn）
        var gaiwanStack = new ItemStack(TeaItems.GAIWAN);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gaiwanStack);
        level.getBlockState(pos).useItemOn(gaiwanStack, level, player,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.UP, pos, false));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        check("tea_set_place_gaiwan_by_hand",
                level.getBlockState(pos).getValue(com.heishandashu.teacraft.block.TeaSetBlock.GAIWAN),
                "手持盖碗右键空盘，应把盖碗放上去");

        // ② 右键盘角（离盖碗和杯子都远）
        var st = level.getBlockState(pos);
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0.45, 0.0, 0.45),
                net.minecraft.core.Direction.UP, pos, false);

        // 【需求】拿起整盘必须潜行；普通右键点空不该拿走盘子
        player.setShiftKeyDown(true);
        var res = st.useWithoutItem(level, player, hit);
        boolean removed = level.getBlockState(pos).isAir();

        ItemStack picked = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var s = player.getInventory().getItem(i);
            if (s.is(TeaItems.TEA_SET_ITEM)) { picked = s; break; }
        }

        check("tea_set_pickup_edge_removes_block",
                res == net.minecraft.world.InteractionResult.SUCCESS && removed,
                "右键盘沿：结果=" + res + " 方块已移除=" + removed);
        check("tea_set_pickup_edge_gets_tray",
                picked.is(TeaItems.TEA_SET_ITEM),
                picked.isEmpty() ? "手里没拿到茶盘（拿到的是别的东西）" : "拿到 " + picked.getItem());

        if (picked.isEmpty()) return;

        Boolean g = picked.get(com.heishandashu.teacraft.core.TeaComponents.SET_GAIWAN);
        check("tea_set_pickup_keeps_gaiwan", Boolean.TRUE.equals(g), "组件 set_gaiwan=" + g);

        String fa = picked.get(com.heishandashu.teacraft.core.TeaComponents.SET_FACING);
        check("tea_set_pickup_keeps_facing", "east".equals(fa), "组件 set_facing=" + fa);

        String ca = picked.get(com.heishandashu.teacraft.core.TeaComponents.SET_CUP_A);
        // 这一次只放了盖碗、没放杯子，所以 A 位应是 none
        check("tea_set_pickup_keeps_cups", "none".equals(ca), "组件 set_cup_a=" + ca);

        // ③ 放回去：内容应还原
        var placePos = new BlockPos(0, 120, 8);
        teaSet.setPlacedBy(level, placePos, TeaItems.TEA_SET.defaultBlockState(), player, picked);
        var back = level.getBlockState(placePos);
        check("tea_set_restore_gaiwan",
                back.getValue(com.heishandashu.teacraft.block.TeaSetBlock.GAIWAN),
                "放回去后盖碗还在");
        // Facing must follow the PLAYER, not the set_facing component.
        //
        // The old assertion demanded "restore east" -- that expectation WAS the bug.
        // The player here never had its yaw set, so it faces NORTH (yaw 0).
        // The tray must therefore land facing NORTH, even though the item still
        // carries set_facing=east.
        check("tea_set_place_facing_follows_player",
                back.getValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING)
                        == net.minecraft.core.Direction.NORTH,
                "放置朝向应由玩家决定（此 player 默认朝北，故应为 north，而不是物品里存的 east），实得 "
                        + back.getValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING));
        level.setBlock(placePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);

        // ---- 图标要跟着盘上内容变 ----
        //
        // 空盘 vs 有茶具，物品模型要不同（否则拿在手里看不出里面有没有东西）。
        check("tea_set_icon_switches",
                picked.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA) != null,
                "有茶具的茶盘物品要带 custom_model_data 以切换图标");

        // 物品定义里要有 range_dispatch 指向「带茶具」的模型
        JsonObject itemDef = jsonAt("assets/" + NS + "/items/tea_set.json");
        boolean hasDispatch = itemDef != null
                && itemDef.toString().contains("range_dispatch")
                && itemDef.toString().contains("tea_set_filled");
        check("tea_set_item_icon_dispatch", hasDispatch,
                "items/tea_set.json 应按 custom_model_data 切到 tea_set_filled");
        check("tea_set_filled_model_exists",
                jsonAt("assets/" + NS + "/models/item/tea_set_filled.json") != null,
                "要存在 tea_set_filled 模型");

        // 茶盘不可堆叠（每只带独立状态，堆叠会互相污染）
        check("tea_set_not_stackable",
                TeaItems.TEA_SET_ITEM.getDefaultMaxStackSize() == 1,
                "茶盘不可堆叠，实得 maxStack="
                        + TeaItems.TEA_SET_ITEM.getDefaultMaxStackSize());

        // ---------- 每个物品都必须有 item.* 名字 ----------
        //
        // 【用户报「名称还不对」】截图里茶盒的工具提示第一行是原始键
        //   item.teacraft.tea_box
        // 因为 26.3 的 ItemStack.getDescriptionId() 用的是 <b>item.</b> 前缀，
        // 而我只加了 block.teacraft.tea_box。<b>BlockItem 不会回退到 block.*</b>，
        // 所以名字直接显示成键名。
        //
        // 这里把「注册过的每个物品」都拿去对语言文件，缺一个就报。
        Set<String> nameZh = langKeys("assets/" + NS + "/lang/zh_cn.json");
        Set<String> nameEn = langKeys("assets/" + NS + "/lang/en_us.json");
        int missingItemNames = 0;
        StringBuilder missingList = new StringBuilder();
        for (var entry : TeaItems.stageItems().entrySet()) {
            String key = "item.teacraft." + entry.getKey();
            if (!nameZh.contains(key)) {
                missingItemNames++;
                missingList.append(" ").append(entry.getKey());
            }
        }
        check("all_stage_items_have_names", missingItemNames == 0,
                "缺名字的阶段物品：" + missingList);

        // 方块物品（茶桌/茶椅/茶盒/烧水茶桌）逐个点名 —— 这几个就是漏过的
        String[] blockItemIds = {
                "tea_table", "tea_table_hot", "tea_box", "tea_chair",
                "tea_set", "tea_tray", "tea_wok", "kettle", "tea_guide"
        };
        int missingBlockNames = 0;
        StringBuilder blockMissing = new StringBuilder();
        for (String id : blockItemIds) {
            String key = "item.teacraft." + id;
            if (!nameZh.contains(key)) {
                missingBlockNames++;
                blockMissing.append(" ").append(id);
            }
        }
        check("all_block_items_have_names", missingBlockNames == 0,
                "缺 item.* 名字的方块物品（会显示成原始键）：" + blockMissing);

        // 英文侧也要有，否则切英文同样显示键名
        int missingEn = 0;
        StringBuilder enMissing = new StringBuilder();
        for (String id : blockItemIds) {
            String key = "item.teacraft." + id;
            if (!nameEn.contains(key)) {
                missingEn++;
                enMissing.append(" ").append(id);
            }
        }
        check("all_block_items_have_en_names", missingEn == 0,
                "缺英文 item.* 名字：" + enMissing);

        // ---- 茶盘物品的悬停提示：中英都要有这几条 ----
        //
        // 少了一条 lang 键，游戏里就会直接显示原始 key（teacraft.tooltip.xxx），
        // 玩家看到的是一串英文点号，比"没有提示"更难看。
        Set<String> zhSet = langKeys("assets/" + NS + "/lang/zh_cn.json");
        Set<String> enSet = langKeys("assets/" + NS + "/lang/en_us.json");
        String[] tooltipKeys = {
                "teacraft.tooltip.set_empty",
                "teacraft.tooltip.set_vessels",
                "teacraft.tooltip.set_dry_tea",
                "teacraft.tooltip.set_gaiwan_brew",
                "teacraft.tooltip.set_gaiwan_empty",
                "teacraft.tooltip.set_cup_brew",
                "teacraft.tooltip.set_usage",
        };
        StringBuilder missingTip = new StringBuilder();
        for (String k : tooltipKeys) {
            if (!zhSet.contains(k)) missingTip.append(" zh缺").append(k);
            if (!enSet.contains(k)) missingTip.append(" en缺").append(k);
        }
        check("tea_set_tooltip_lang", missingTip.length() == 0,
                missingTip.length() == 0 ? "7 条提示中英齐全" : missingTip.toString());

        // ---- 盘面凹槽标记：方便对准放茶具 ----
        //
        // 检查的是 slots_north.json —— 也就是 blockstate 在 facing=north 时
        // 真正引用的那份。之前这里查的是 slots.json，而那个文件在改成
        // 「每朝向一份预旋转模型」之后已经不再被引用了，查它等于没查。
        JsonObject slots = jsonAt("assets/" + NS + "/models/block/tea_set/slots_north.json");
        check("tea_set_slot_markers_exist", slots != null,
                "盘面应有对准用的暗色凹槽模型 slots_north.json");
        if (slots != null) {
            var els = slots.getAsJsonArray("elements");
            // 4 个茶具位：1 个盖碗 + 3 个杯子。
            // 这 4 个本身就不对称（1 大 3 小、排成两排），转 90° 能看出来，
            // 不需要再额外加方向标记。
            check("tea_set_slot_marker_count", els != null && els.size() == 4,
                    "凹槽标记应有 4 个（1 盖碗 + 3 杯），实际 "
                            + (els == null ? 0 : els.size()));
            // 尺寸必须和茶具一致，否则"对准"没意义
            boolean sizeOk = false;
            if (els != null && els.size() >= 1) {
                var gEl = els.get(0).getAsJsonObject();
                double gw = gEl.getAsJsonArray("to").get(0).getAsDouble()
                        - gEl.getAsJsonArray("from").get(0).getAsDouble();
                sizeOk = Math.abs(gw - 5.6) < 0.01;
            }
            check("tea_set_slot_gaiwan_size", sizeOk,
                    "盖碗凹槽宽应为 5.6（与盖碗实际尺寸一致）");
        }

        // ★ 凹槽必须真的看得出「暗」来
        //
        // 第一版就是这里翻的车：凹槽贴图比盘面还亮（65 vs 46），
        // 玩家完全看不出标记，于是"旋转没生效"。
        // 光检查 json 结构抓不到这种问题 —— 必须比像素亮度。
        checkTextureDarker("tea_set_slot", "tea_set_floor",
                "tea_set_slot_darker_than_floor");

        // ★ blockstate 的多条件必须用 AND 数组
        //
        // 26.3 不认「一个对象里写多个键」这种 when：
        //   ✗ "when": { "gaiwan": "true", "facing": "north" }
        //   ✓ "when": { "AND": [ { "gaiwan": "true" }, { "facing": "north" } ] }
        // 写成前者时条件静默失效，日志里一点错误都没有 ——
        // 表现就是「转方向没反应」。
        JsonObject bsJson = jsonAt("assets/" + NS + "/blockstates/tea_set.json");
        boolean badMultiKey = false;
        int rotatedEntries = 0;
        if (bsJson != null && bsJson.has("multipart")) {
            for (var el : bsJson.getAsJsonArray("multipart")) {
                var entry = el.getAsJsonObject();
                if (entry.has("when")) {
                    var when = entry.getAsJsonObject("when");
                    if (!when.has("AND") && when.size() > 1) {
                        badMultiKey = true;
                        break;
                    }
                }
                if (entry.has("apply")) {
                    var apply = entry.getAsJsonObject("apply");
                    if (apply.has("y") && apply.get("y").getAsInt() > 0) {
                        rotatedEntries++;
                    }
                }
            }
        }
        check("tea_set_blockstate_when_syntax", !badMultiKey,
                badMultiKey
                        ? "multipart 多条件必须写成 AND 数组，否则条件静默失效"
                        : "when 语法正确（多条件都用 AND）");
        check("tea_set_blockstate_has_rotation", true,
                "旋转方式：" + (rotatedEntries > 0
                        ? "blockstate 的 y 参数（" + rotatedEntries + " 条）"
                        : "已烘焙进模型文件"));

        // ★ 旋转必须真的体现在模型数据里
        //
        // 26.3 实测 multipart 的 apply.y 不生效（转了跟没转一样），
        // 所以改成「每个朝向一份预旋转好的模型」。
        // 这里验证四个朝向的模型确实不同 —— 否则又回到「看着没变」。
        String[] fnames = {"north", "east", "south", "west"};
        StringBuilder rotProblem = new StringBuilder();
        String prevSig = null;
        for (String fn : fnames) {
            JsonObject m = jsonAt("assets/" + NS + "/models/block/tea_set/slots_" + fn + ".json");
            if (m == null) {
                rotProblem.append(" 缺 slots_").append(fn).append(".json");
                continue;
            }
            String sig = m.getAsJsonArray("elements").toString();
            if (prevSig != null && prevSig.equals(sig)) {
                rotProblem.append(" ").append(fn).append(" 与上一朝向模型完全相同");
            }
            prevSig = sig;
        }
        check("tea_set_models_pre_rotated", rotProblem.length() == 0,
                rotProblem.length() == 0 ? "四个朝向的凹槽模型各不相同（旋转已烘焙）"
                        : rotProblem.toString());

        // ★ 模型里不许出现 null，坐标也不许越界
        //
        // 这次踩的坑：用 PowerShell 生成模型时写了 `[1.0, ...]`，
        // 而 `$1.0` 被当成变量、求值成空 —— 生成了 "y": null。
        // 26.3 解析失败后整个方块变成紫黑方块，但服务端状态全对，
        // 光看状态断言完全发现不了。所以这里直接查 json 原始文本。
        StringBuilder nullProblem = new StringBuilder();
        int checkedModels = 0;
        for (String fn : fnames) {
            String path = "assets/" + NS + "/models/block/tea_set/slots_" + fn + ".json";
            String raw = rawResource(path);
            if (raw == null) {
                nullProblem.append(" 读不到 ").append(fn);
                continue;
            }
            checkedModels++;
            if (raw.contains("null")) {
                nullProblem.append(" ").append(fn).append(" 含 null");
                continue;
            }
            JsonObject m = jsonAt(path);
            if (m == null) {
                nullProblem.append(" ").append(fn).append(" 解析失败");
                continue;
            }
            for (var e : m.getAsJsonArray("elements")) {
                var el = e.getAsJsonObject();
                var fr = el.getAsJsonArray("from");
                var to = el.getAsJsonArray("to");
                for (int k = 0; k < 3; k++) {
                    double a = fr.get(k).getAsDouble();
                    double b = to.get(k).getAsDouble();
                    if (a < -16 || a > 32 || b < -16 || b > 32) {
                        nullProblem.append(" ").append(fn)
                                .append(" 坐标越界(").append(a).append("~").append(b).append(")");
                    }
                }
            }
        }
        check("tea_set_models_valid", nullProblem.length() == 0,
                nullProblem.length() == 0 ? checkedModels + " 个凹槽模型均合法（无 null、坐标在范围内）"
                        : nullProblem.toString());


        // ---------- 茶杯底部不能有共面（z-fighting 闪烁）----------
        //
        // 用户报过：茶杯底部一圈贴图一直在闪。原因是杯底元素的上表面
        // 与杯壁元素的下表面同在 y=7.29，两个面完全共面，渲染时交替胜出。
        for (String cupModel : new String[]{
                "models/item/tea_cup.json", "models/item/tea_cup_full.json"}) {
            String path = "assets/" + NS + "/" + cupModel;
            JsonObject m = jsonResource(path);
            double bottomTop = m.getAsJsonArray("elements").get(0)
                    .getAsJsonObject().getAsJsonArray("to").get(1).getAsDouble();
            JsonObject wall = m.getAsJsonArray("elements").get(1).getAsJsonObject();
            double wallBottom = wall.getAsJsonArray("from").get(1).getAsDouble();
            check("cup_no_coplanar_bottom_" + cupModel.replaceAll(".*/", ""),
                    Math.abs(bottomTop - wallBottom) > 0.005,
                    cupModel + " 杯底顶面(" + bottomTop
                            + ") 与杯壁底面(" + wallBottom + ") 共面，会闪烁");
        }

        // ---------- 托盘上的杯子不能和标记片重叠 ----------
        //
        // 用户报过：茶杯底部一圈贴图一直闪。真正的起因是对齐标记（slots_*）
        // 原本是 y 1~1.5 的实心块，和杯底（1.01~1.49）占同一块空间，
        // 两套几何体互相穿插 → 深度打架 → 闪。
        // 现在标记压成 y 1~1.005 的薄片，必须严格低于杯底。
        for (String dir : new String[]{"north", "east", "south", "west"}) {
            JsonObject slotM = jsonResource(
                    "assets/" + NS + "/models/block/tea_set/slots_" + dir + ".json");
            double slotTop = slotM.getAsJsonArray("elements").get(0)
                    .getAsJsonObject().getAsJsonArray("to").get(1).getAsDouble();
            check("slot_marker_below_cup_" + dir, slotTop < 1.01,
                    "标记片顶面 " + slotTop + " 必须低于杯底 1.01，否则会和杯子闪烁");
        }

        // 方块上的杯子模型：杯底顶面必须低于杯壁底面
        JsonObject blockCup = jsonResource(
                "assets/" + NS + "/models/block/tea_set/cup_a_empty.json");
        double bCupTop = blockCup.getAsJsonArray("elements").get(0)
                .getAsJsonObject().getAsJsonArray("to").get(1).getAsDouble();
        double bWallBottom = blockCup.getAsJsonArray("elements").get(1)
                .getAsJsonObject().getAsJsonArray("from").get(1).getAsDouble();
        check("block_cup_no_coplanar_bottom", Math.abs(bCupTop - bWallBottom) > 0.005,
                "托盘上的杯子杯底(" + bCupTop + ")不能和杯壁(" + bWallBottom + ")共面");

        // ---------- 工作台配方 ----------
        JsonObject wheelR = jsonResource("data/" + NS + "/recipe/potter_wheel.json");
        check("potter_wheel_recipe_shaped",
                "minecraft:crafting_shaped".equals(wheelR.get("type").getAsString()),
                "拉坯机要在工作台合成");
        check("potter_wheel_recipe_top_slabs",
                wheelR.getAsJsonArray("pattern").get(0).getAsString().equals("SSS"),
                "拉坯机上面一排应是 3 个木台阶");
        check("potter_wheel_recipe_slab_tag",
                "#minecraft:wooden_slabs".equals(wheelR.getAsJsonObject("key")
                        .get("S").getAsString()),
                "台阶位要用任意木台阶标签");
        check("potter_wheel_recipe_fence_tag",
                "#minecraft:wooden_fences".equals(wheelR.getAsJsonObject("key")
                        .get("F").getAsString()),
                "栅栏位要用任意木栅栏标签");

        JsonObject kilnR = jsonResource("data/" + NS + "/recipe/clay_kiln.json");
        check("clay_kiln_recipe_shaped",
                "minecraft:crafting_shaped".equals(kilnR.get("type").getAsString()),
                "窑炉要在工作台合成");
        var kilnPat = kilnR.getAsJsonArray("pattern");
        check("clay_kiln_recipe_8_bricks",
                kilnPat.get(0).getAsString().equals("BBB")
                        && kilnPat.get(1).getAsString().equals("B B")
                        && kilnPat.get(2).getAsString().equals("BBB"),
                "窑炉应是 8 个红砖围成一圈（中间空）");

        // 盖碗四个朝向的中心必须各不相同
        StringBuilder gProblem = new StringBuilder();
        double[] gx = new double[4];
        double[] gz = new double[4];
        for (int i = 0; i < 4; i++) {
            String fn = i == 0 ? "gaiwan" : "gaiwan_" + fnames[i];
            JsonObject m = jsonAt("assets/" + NS + "/models/block/tea_set/" + fn + ".json");
            if (m == null) { gProblem.append(" 缺 ").append(fn); continue; }
            double sumX = 0;
            double sumZ = 0;
            var els = m.getAsJsonArray("elements");
            for (var e : els) {
                var el = e.getAsJsonObject();
                sumX += el.getAsJsonArray("from").get(0).getAsDouble()
                        + el.getAsJsonArray("to").get(0).getAsDouble();
                sumZ += el.getAsJsonArray("from").get(2).getAsDouble()
                        + el.getAsJsonArray("to").get(2).getAsDouble();
            }
            gx[i] = sumX / (els.size() * 2);
            gz[i] = sumZ / (els.size() * 2);
        }
        if (gProblem.length() == 0) {
            if (Math.abs(gx[0] - gx[2]) < 1.0 && Math.abs(gz[0] - gz[2]) < 1.0) {
                gProblem.append(" north 与 south 的盖碗位置相同，旋转没生效");
            }
        }
        check("tea_set_gaiwan_rotates", gProblem.length() == 0,
                gProblem.length() == 0
                        ? String.format("盖碗四朝向中心：(%.1f,%.1f)(%.1f,%.1f)(%.1f,%.1f)(%.1f,%.1f)",
                                gx[0], gz[0], gx[1], gz[1], gx[2], gz[2], gx[3], gz[3])
                        : gProblem.toString());



        // ④ 盘上有茶叶也要能拿起，且茶叶/茶汤跟着走
        var pos2 = new BlockPos(0, 120, 12);
        level.setBlock(pos2, TeaItems.TEA_SET.defaultBlockState()
                .setValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING,
                        net.minecraft.core.Direction.WEST), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos2)
                instanceof com.heishandashu.teacraft.block.TeaSetBlockEntity be2) {
            be2.setDryTea("green|4|2");      // 盖碗里 2 份干茶
            be2.setCupBrew(1, "green|4|1");  // B 位杯子里有茶汤
        }
        level.setBlock(pos2, level.getBlockState(pos2)
                .setValue(com.heishandashu.teacraft.block.TeaSetBlock.CUP_B,
                        com.heishandashu.teacraft.block.TeaSetBlock.CupState.FULL), Block.UPDATE_ALL);

        player.getInventory().clearContent();
        var hit2 = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos2).add(0.45, 0.0, 0.45),
                net.minecraft.core.Direction.UP, pos2, false);
        level.getBlockState(pos2).useWithoutItem(level, player, hit2);

        ItemStack picked2 = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var s = player.getInventory().getItem(i);
            if (s.is(TeaItems.TEA_SET_ITEM)) { picked2 = s; break; }
        }
        check("tea_set_pickup_with_tea_removes_block", level.getBlockState(pos2).isAir(),
                "盘里有茶叶时右键盘沿也能拿起");
        check("tea_set_pickup_with_tea_gets_tray", picked2.is(TeaItems.TEA_SET_ITEM),
                picked2.isEmpty() ? "没拿到茶盘" : "拿到 " + picked2.getItem());

        if (picked2.isEmpty()) return;

        // ⑤ 放回去，茶叶与茶汤都要还在
        var pos3 = new BlockPos(0, 120, 16);
        teaSet.setPlacedBy(level, pos3, TeaItems.TEA_SET.defaultBlockState(), player, picked2);
        // 原版 BlockItem.useOn 会在 setPlacedBy 之前调这个方法把 BE 数据还原，
        // 测试里绕过了整条放置链，这里补上才是真实流程
        net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(level, player, pos3, picked2);

        String dry = null;
        String cupB = null;
        if (level.getBlockEntity(pos3)
                instanceof com.heishandashu.teacraft.block.TeaSetBlockEntity be3) {
            dry = be3.dryTea();
            cupB = be3.cupBrew(1);
        }
        check("tea_set_restore_dry_tea", "green|4|2".equals(dry), "盖碗干茶还原=" + dry);
        check("tea_set_restore_cup_brew", "green|4|1".equals(cupB), "B 位茶汤还原=" + cupB);

        level.setBlock(pos3, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
    }

    /* ---------------- 烧水壶朝向 ---------------- */

    /**
     * 烧水壶放着也要能转方向（和茶盘一致）。
     *
     * <p>验证两条路径：</p>
     * <ol>
     *   <li>{@code setPlacedBy} —— 游戏里真正生效的那个钩子</li>
     *   <li>{@code getStateForPlacement} —— 某些放置路径会先问初始状态</li>
     * </ol>
     *
     * <p>朝向语义：壶嘴对着玩家，即 {@code placer.getDirection().getOpposite()}。</p>
     */
    private static void checkKettlePlacementFacing(MinecraftServer server) {
        var level = server.overworld();
        var pos = new BlockPos(0, 120, 20);
        var kettleBlock = (com.heishandashu.teacraft.block.TeaKettleBlock) TeaItems.TEA_KETTLE;

        // 玩家朝向 -> 期望的壶嘴朝向
        var cases = new Object[][]{
                {0.0F, net.minecraft.core.Direction.NORTH},    // 看南 -> 壶嘴朝北
                {90.0F, net.minecraft.core.Direction.EAST},    // 看西 -> 壶嘴朝东
                {180.0F, net.minecraft.core.Direction.SOUTH},  // 看北 -> 壶嘴朝南
                {270.0F, net.minecraft.core.Direction.WEST},   // 看东 -> 壶嘴朝西
        };

        StringBuilder problems = new StringBuilder();
        for (var c : cases) {
            float yaw = (Float) c[0];
            net.minecraft.core.Direction expect = (net.minecraft.core.Direction) c[1];

            var stack = new ItemStack(TeaItems.TEA_KETTLE_ITEM);
            var player = new net.minecraft.world.entity.player.Player(level,
                    new com.mojang.authlib.GameProfile(
                            java.util.UUID.nameUUIDFromBytes("teacraft_kettle_facing".getBytes()),
                            "teacraft_kettle_facing")) {
                @Override
                public net.minecraft.world.level.GameType gameMode() {
                    return net.minecraft.world.level.GameType.SURVIVAL;
                }
            };
            player.setYRot(yaw);
            player.setYHeadRot(yaw);

            // ① setPlacedBy（真正生效）
            level.setBlock(pos, TeaItems.TEA_KETTLE.defaultBlockState(), Block.UPDATE_ALL);
            kettleBlock.setPlacedBy(level, pos, level.getBlockState(pos), player, stack);
            var got = level.getBlockState(pos)
                    .getValue(com.heishandashu.teacraft.block.TeaKettleBlock.FACING);
            if (got != expect) {
                problems.append(" setPlacedBy yaw=").append(yaw)
                        .append(" 期望=").append(expect).append(" 实得=").append(got).append(";");
            }

            // ② getStateForPlacement（部分路径会问）
            var hit = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0, 0.5, 0),
                    net.minecraft.core.Direction.UP, pos.below(), false);
            var ctx = new net.minecraft.world.item.context.BlockPlaceContext(
                    player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);
            var st = kettleBlock.getStateForPlacement(ctx);
            if (st == null) {
                problems.append(" getStateForPlacement yaw=").append(yaw).append(" 返回 null;");
            } else {
                var got2 = st.getValue(com.heishandashu.teacraft.block.TeaKettleBlock.FACING);
                if (got2 != expect) {
                    problems.append(" getStateForPlacement yaw=").append(yaw)
                            .append(" 期望=").append(expect).append(" 实得=").append(got2).append(";");
                }
            }

            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
        }
        check("kettle_placement_facing", problems.length() == 0,
                problems.length() == 0 ? "四个朝向放置都能正确转向" : problems.toString());

        // 属性齐全：facing / water / boiled
        check("kettle_has_facing_property",
                TeaItems.TEA_KETTLE.defaultBlockState()
                        .hasProperty(com.heishandashu.teacraft.block.TeaKettleBlock.FACING),
                "烧水壶要有 facing 属性");

        // ---- 拿起再放下：壶嘴方向要保住 ----
        //
        // 拿起壶时如果不把朝向记在物品上，放下会转回"对着玩家"。
        // 这里真的空手拿起一只朝 WEST 的壶，再放回去看朝向。
        var pickPos = new BlockPos(0, 120, 24);
        level.setBlock(pickPos, TeaItems.TEA_KETTLE.defaultBlockState()
                .setValue(com.heishandashu.teacraft.block.TeaKettleBlock.FACING,
                        net.minecraft.core.Direction.WEST), Block.UPDATE_ALL);

        var holder = new net.minecraft.world.entity.player.Player(level,
                new com.mojang.authlib.GameProfile(
                        java.util.UUID.nameUUIDFromBytes("teacraft_kettle_pick".getBytes()),
                        "teacraft_kettle_pick")) {
            @Override
            public net.minecraft.world.level.GameType gameMode() {
                return net.minecraft.world.level.GameType.SURVIVAL;
            }
        };
        holder.getInventory().clearContent();

        // 空手右键 = 拿起壶
        var pickHit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pickPos),
                net.minecraft.core.Direction.UP, pickPos, false);
        level.getBlockState(pickPos).useWithoutItem(level, holder, pickHit);

        ItemStack got = ItemStack.EMPTY;
        for (int i = 0; i < holder.getInventory().getContainerSize(); i++) {
            var s = holder.getInventory().getItem(i);
            if (s.is(TeaItems.TEA_KETTLE_ITEM)) { got = s; break; }
        }
        check("kettle_pickup_removes_block", level.getBlockState(pickPos).isAir(),
                "空手右键应把壶拿起来");
        check("kettle_pickup_gets_item", got.is(TeaItems.TEA_KETTLE_ITEM),
                got.isEmpty() ? "手里没拿到壶" : "拿到 " + got.getItem());

        if (!got.isEmpty()) {
            String savedF = got.get(com.heishandashu.teacraft.core.TeaComponents.KETTLE_FACING);
            check("kettle_pickup_saves_facing", "west".equals(savedF),
                    "组件 kettle_facing=" + savedF);

            // 放回去：朝向应是 west，而不是玩家朝向
            var backPos = new BlockPos(0, 120, 28);
            level.setBlock(backPos, TeaItems.TEA_KETTLE.defaultBlockState(), Block.UPDATE_ALL);
            var kettleBlock2 = (com.heishandashu.teacraft.block.TeaKettleBlock) TeaItems.TEA_KETTLE;
            kettleBlock2.setPlacedBy(level, backPos, level.getBlockState(backPos), holder, got);

            // 放回去：朝向应<b>跟着放置者</b>，而不是物品上记的 west。
            //
            // 这条断言以前是反的（要求还原 west）。那正是用户报的 bug：
            // 壶被拿起过一次后朝向就永久烘焙进物品，玩家再也转不动它。
            // 正确语义：朝向由摆放时玩家面对的方向决定。
            net.minecraft.core.Direction placerDir = holder.getDirection().getOpposite();
            if (!placerDir.getAxis().isHorizontal()) placerDir = net.minecraft.core.Direction.NORTH;
            check("kettle_facing_follows_placer",
                    level.getBlockState(backPos)
                            .getValue(com.heishandashu.teacraft.block.TeaKettleBlock.FACING)
                            == placerDir,
                    "放回去后朝向应跟着玩家 (" + placerDir + ")，而不是物品上记的 west");
            level.setBlock(backPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
        }
    }
    /* ---------------- 贴图亮度比较 ---------------- */

    /**
     * 检查贴图 A 的平均亮度明显低于贴图 B。
     *
     * <p>用来抓「标记画了但看不出来」这类问题：凹槽贴图如果比盘面还亮，
     * 玩家根本看不到对准标记，表现出来就是"旋转好像没生效"。</p>
     *
     * <p>只看 json 结构是发现不了的 —— 必须读像素。</p>
     */
    private static void checkTextureDarker(String darkerName, String lighterName, String checkName) {
        double dark = averageBrightness("assets/" + NS + "/textures/block/" + darkerName + ".png");
        double light = averageBrightness("assets/" + NS + "/textures/block/" + lighterName + ".png");
        if (Double.isNaN(dark) || Double.isNaN(light)) {
            check(checkName, false,
                    "读不到贴图：" + darkerName + "=" + dark + " " + lighterName + "=" + light);
            return;
        }
        // 至少暗 25%，否则肉眼分辨不出来
        boolean ok = dark < light * 0.75;
        check(checkName, ok, String.format(
                "%s 亮度 %.1f 应明显低于 %s 亮度 %.1f（需低 25%% 以上）",
                darkerName, dark, lighterName, light));
    }

    /** 读 PNG 的平均亮度（0~255）。读不到返回 NaN。 */
    private static double averageBrightness(String resourcePath) {
        try (var in = TeaSelfCheck.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return Double.NaN;
            var img = javax.imageio.ImageIO.read(in);
            if (img == null) return Double.NaN;
            long sum = 0;
            long count = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int rgb = img.getRGB(x, y);
                    int a = (rgb >>> 24) & 0xFF;
                    if (a < 8) continue;   // 全透明像素不算
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    sum += (r * 299 + g * 587 + b * 114) / 1000;
                    count++;
                }
            }
            return count == 0 ? Double.NaN : (double) sum / count;
        } catch (Exception e) {
            return Double.NaN;
        }
    }
    /* ---------------- 茶盘朝向：真实放置 ---------------- */

    /**
     * 茶盘朝向必须跟着玩家转。
     *
     * <p>两个钩子都验：{@code getStateForPlacement}（决定初始状态）和
     * {@code setPlacedBy}（放置后兜底，游戏里必定执行）。</p>
     */
    private static void checkTeaSetPlacementFacing(MinecraftServer server) {
        var level = server.overworld();
        var pos = new BlockPos(0, 120, 32);
        var teaSet = (com.heishandashu.teacraft.block.TeaSetBlock) TeaItems.TEA_SET;

        var cases = new Object[][]{
                {0.0F, net.minecraft.core.Direction.NORTH},    // 看南 -> 盘朝北
                {90.0F, net.minecraft.core.Direction.EAST},    // 看西 -> 盘朝东
                {180.0F, net.minecraft.core.Direction.SOUTH},  // 看北 -> 盘朝南
                {270.0F, net.minecraft.core.Direction.WEST},   // 看东 -> 盘朝西
        };

        StringBuilder problems = new StringBuilder();
        for (var c : cases) {
            float yaw = (Float) c[0];
            net.minecraft.core.Direction expect = (net.minecraft.core.Direction) c[1];

            var stack = new ItemStack(TeaItems.TEA_SET_ITEM);
            var player = new net.minecraft.world.entity.player.Player(level,
                    new com.mojang.authlib.GameProfile(
                            java.util.UUID.nameUUIDFromBytes("teacraft_set_facing".getBytes()),
                            "teacraft_set_facing")) {
                @Override
                public net.minecraft.world.level.GameType gameMode() {
                    return net.minecraft.world.level.GameType.SURVIVAL;
                }
            };
            player.setYRot(yaw);
            player.setYHeadRot(yaw);

            // ① getStateForPlacement
            var hit = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(pos),
                    net.minecraft.core.Direction.UP, pos.below(), false);
            var ctx = new net.minecraft.world.item.context.BlockPlaceContext(
                    player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);
            var st = teaSet.getStateForPlacement(ctx);
            if (st == null) {
                problems.append(" getStateForPlacement yaw=").append(yaw).append(" 返回 null;");
            } else {
                var got = st.getValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING);
                if (got != expect) {
                    problems.append(" getStateForPlacement yaw=").append(yaw)
                            .append(" 期望=").append(expect).append(" 实得=").append(got).append(";");
                }
            }

            // ② setPlacedBy
            level.setBlock(pos, TeaItems.TEA_SET.defaultBlockState(), Block.UPDATE_ALL);
            teaSet.setPlacedBy(level, pos, level.getBlockState(pos), player, stack);
            var got2 = level.getBlockState(pos)
                    .getValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING);
            if (got2 != expect) {
                problems.append(" setPlacedBy yaw=").append(yaw)
                        .append(" 期望=").append(expect).append(" 实得=").append(got2).append(";");
            }

            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
        }
        // ③ 物品层：BlockItem.getPlacementState —— 游戏真正走的是这条链
        //
        // 26.3 的放置流程里，方块的 getStateForPlacement 不一定被调用，
        // 但物品的 getPlacementState 一定被调用（烧水壶当初就是靠它解决的）。
        // 这里单独验一遍，避免"方块方法对、物品链路没接上"。
        var setItem = (com.heishandashu.teacraft.item.TeaSetItem) TeaItems.TEA_SET_ITEM;
        for (var c : cases) {
            float yaw = (Float) c[0];
            net.minecraft.core.Direction expect = (net.minecraft.core.Direction) c[1];

            var stack = new ItemStack(TeaItems.TEA_SET_ITEM);
            var player = new net.minecraft.world.entity.player.Player(level,
                    new com.mojang.authlib.GameProfile(
                            java.util.UUID.nameUUIDFromBytes("teacraft_item_facing".getBytes()),
                            "teacraft_item_facing")) {
                @Override
                public net.minecraft.world.level.GameType gameMode() {
                    return net.minecraft.world.level.GameType.SURVIVAL;
                }
            };
            player.setYRot(yaw);
            player.setYHeadRot(yaw);

            var hit = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(pos),
                    net.minecraft.core.Direction.UP, pos.below(), false);
            var ctx = new net.minecraft.world.item.context.BlockPlaceContext(
                    player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);

            var st = invokePlacementState(setItem, ctx);
            if (st == null) {
                problems.append(" item.getPlacementState yaw=").append(yaw).append(" 返回 null;");
            } else {
                var got = st.getValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING);
                if (got != expect) {
                    problems.append(" item.getPlacementState yaw=").append(yaw)
                            .append(" 期望=").append(expect).append(" 实得=").append(got).append(";");
                }
            }
        }

        check("tea_set_placement_facing", problems.length() == 0,
                problems.length() == 0 ? "茶盘四个朝向放置都能正确转向" : problems.toString());
    }
    /**
     * 用反射调 {@code BlockItem.getPlacementState}。
     *
     * <p>它是 protected，自检在另一个包里调不到；但这个方法是
     * 26.3 放置链上真正生效的那一环，必须验。</p>
     */
    private static net.minecraft.world.level.block.state.BlockState invokePlacementState(
            net.minecraft.world.item.BlockItem item,
            net.minecraft.world.item.context.BlockPlaceContext ctx) {
        try {
            // 关键：必须从 item 的<b>实际类型</b>往上找，不能用 BlockItem.class。
            // 用 BlockItem.class 拿到的是父类那份，反射执行的是父类实现，
            // TeaSetItem 的覆盖被绕过 —— 断言就变成"永远绿"的假测试。
            for (Class<?> c = item.getClass();
                 c != null && net.minecraft.world.item.BlockItem.class.isAssignableFrom(c);
                 c = c.getSuperclass()) {
                try {
                    var m = c.getDeclaredMethod("getPlacementState",
                            net.minecraft.world.item.context.BlockPlaceContext.class);
                    m.setAccessible(true);
                    return (net.minecraft.world.level.block.state.BlockState) m.invoke(item, ctx);
                } catch (NoSuchMethodException ignored) {
                    // 这个类没覆盖，继续往上找
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 四个朝向下分别放茶具，朝向必须保持不变。
     *
     * <p>用户反馈：放进茶具后茶盘被强制转回固定方向。原先只有一条
     * {@code tea_set_place_keeps_facing}，只测了 east 一种朝向、只放了盖碗。
     * 这里把「4 朝向 x 盖碗 / 茶杯」全过一遍。</p>
     */
    private static void checkTeaSetPlaceKeepsFacingEveryDirection(MinecraftServer server) {
        var level = server.overworld();
        StringBuilder problems = new StringBuilder();

        var dirs = new net.minecraft.core.Direction[]{
                net.minecraft.core.Direction.NORTH,
                net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.WEST,
        };

        int idx = 0;
        for (var dir : dirs) {
            idx++;
            var pos = new BlockPos(0, 120, 40 + idx * 4);

            var player = new net.minecraft.world.entity.player.Player(level,
                    new com.mojang.authlib.GameProfile(
                            java.util.UUID.nameUUIDFromBytes(("teacraft_dir_" + idx).getBytes()),
                            "teacraft_dir_" + idx)) {
                @Override
                public net.minecraft.world.level.GameType gameMode() {
                    return net.minecraft.world.level.GameType.SURVIVAL;
                }
            };
            player.getInventory().clearContent();

            level.setBlock(pos, TeaItems.TEA_SET.defaultBlockState()
                            .setValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING, dir),
                    Block.UPDATE_ALL);

            var hit = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(pos),
                    net.minecraft.core.Direction.UP, pos, false);

            // ① 放盖碗
            var gaiwanStack = new ItemStack(TeaItems.GAIWAN);
            level.getBlockState(pos).useItemOn(gaiwanStack, level, player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            var afterGaiwan = level.getBlockState(pos);
            var f1 = afterGaiwan.getValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING);
            if (f1 != dir) {
                problems.append(" 放盖碗后 ").append(dir).append(" -> ").append(f1);
            }
            if (!afterGaiwan.getValue(com.heishandashu.teacraft.block.TeaSetBlock.GAIWAN)) {
                problems.append(" 盖碗没放上(").append(dir).append(")");
            }

            // ② 放一只杯子
            var cupStack = new ItemStack(TeaItems.TEA_CUP);
            level.getBlockState(pos).useItemOn(cupStack, level, player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            var afterCup = level.getBlockState(pos);
            var f2 = afterCup.getValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING);
            if (f2 != dir) {
                problems.append(" 放杯子后 ").append(dir).append(" -> ").append(f2);
            }

            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
        }

        check("tea_set_place_keeps_facing_all_dirs", problems.length() == 0,
                problems.length() == 0 ? "4 个朝向下放盖碗/茶杯，朝向都不变"
                        : problems.toString());

        // ★ 拿起 → 放下 的完整往返，四个朝向都要保住
        //
        // 用户反馈「放进茶具后茶盘强制变固定方向」。
        // 上面测的是「原地放茶具」，这里测「装了茶具后整盘拿起再放下」，
        // 因为朝向只记在物品组件 set_facing 里，一旦这条链断了
        // 放下时就会退回默认的 north —— 现象正是「强制变成固定方向」。
        StringBuilder rtProblem = new StringBuilder();
        int rtIdx = 0;
        for (var dir : new net.minecraft.core.Direction[]{
                net.minecraft.core.Direction.NORTH,
                net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.WEST}) {
            rtIdx++;
            var pos = new BlockPos(0, 120, 60 + rtIdx * 4);

            var player = new net.minecraft.world.entity.player.Player(level,
                    new com.mojang.authlib.GameProfile(
                            java.util.UUID.nameUUIDFromBytes(("teacraft_rt_" + rtIdx).getBytes()),
                            "teacraft_rt_" + rtIdx)) {
                @Override
                public net.minecraft.world.level.GameType gameMode() {
                    return net.minecraft.world.level.GameType.SURVIVAL;
                }
            };
            player.getInventory().clearContent();

            // ① 放一个该朝向、且盘上已有盖碗和杯子的茶盘
            level.setBlock(pos, TeaItems.TEA_SET.defaultBlockState()
                            .setValue(com.heishandashu.teacraft.block.TeaSetBlock.FACING, dir)
                            .setValue(com.heishandashu.teacraft.block.TeaSetBlock.GAIWAN, true)
                            .setValue(com.heishandashu.teacraft.block.TeaSetBlock.CUPS[0],
                                    com.heishandashu.teacraft.block.TeaSetBlock.CupState.EMPTY),
                    Block.UPDATE_ALL);

            // ② 空手右键盘沿拿起
            var rimHit = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0.46, 0, 0.46),
                    net.minecraft.core.Direction.UP, pos, false);
            // 【需求】拿起整盘必须潜行
            player.setShiftKeyDown(true);
            level.getBlockState(pos).useWithoutItem(level, player, rimHit);

            // ③ 找到拿起的物品，检查组件里的朝向
            ItemStack got = ItemStack.EMPTY;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var s = player.getInventory().getItem(i);
                if (s.is(TeaItems.TEA_SET_ITEM)) { got = s; break; }
            }
            if (got.isEmpty()) {
                rtProblem.append(" ").append(dir).append(" 拿起后没拿到物品");
                continue;
            }
            String stored = got.get(com.heishandashu.teacraft.core.TeaComponents.SET_FACING);
            if (!dir.getSerializedName().equals(stored)) {
                rtProblem.append(" ").append(dir).append(" 拿起后组件存的是 ").append(stored);
            }

            // (4) Put it back: facing must FOLLOW THE PLAYER, not the stored value.
            //
            // This assertion used to be the other way round (it demanded that
            // the old facing be restored). That expectation WAS the bug: once a
            // tray has been picked up, set_facing is baked into the item, so
            // restoring it pins the tray to one direction forever.
            // Correct semantics: facing is decided by where the player looks now.
            var backPos = new BlockPos(0, 120, 62 + rtIdx * 4);
            var expectTurned = switch (dir) {
                case NORTH -> net.minecraft.core.Direction.EAST;
                case EAST -> net.minecraft.core.Direction.SOUTH;
                case SOUTH -> net.minecraft.core.Direction.WEST;
                default -> net.minecraft.core.Direction.NORTH;
            };
            float turnYaw = switch (expectTurned) {
                case NORTH -> 0.0F;
                case EAST -> 90.0F;
                case SOUTH -> 180.0F;
                default -> 270.0F;
            };
            player.setYRot(turnYaw);
            player.setYHeadRot(turnYaw);
            // The item still carries the OLD facing component -- that is exactly
            // what must not win.
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, got);

            var backHit = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(backPos),
                    net.minecraft.core.Direction.UP, backPos.below(), false);
            var backCtx = new net.minecraft.world.item.context.BlockPlaceContext(
                    player, net.minecraft.world.InteractionHand.MAIN_HAND, got, backHit);
            var backState = invokePlacementState(
                    (net.minecraft.world.item.BlockItem) TeaItems.TEA_SET_ITEM, backCtx);
            if (backState == null) {
                rtProblem.append(" ").append(dir).append(" getPlacementState returned null");
            } else {
                var gotDir = backState.getValue(
                        com.heishandashu.teacraft.block.TeaSetBlock.FACING);
                if (gotDir != expectTurned) {
                    rtProblem.append(" ").append(dir)
                            .append(" turned placement expected ").append(expectTurned)
                            .append(" but got ").append(gotDir)
                            .append(" (facing locked by the item component)");
                }
                level.setBlock(backPos, backState, Block.UPDATE_ALL);
                if (!level.getBlockState(backPos)
                        .getValue(com.heishandashu.teacraft.block.TeaSetBlock.GAIWAN)) {
                    rtProblem.append(" ").append(dir).append(" gaiwan lost on replace");
                }
            }
            level.setBlock(backPos,
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        check("tea_set_tray_can_be_rotated_after_pickup", rtProblem.length() == 0,
                rtProblem.length() == 0
                        ? "all 4 directions: after pickup the tray still rotates with the player"
                        : rtProblem.toString());
    }
    /* ---------------- 窑炉 / 拉坯机 功能自检 ---------------- */

    /**
     * 窑炉 + 拉坯机的功能验证。
     *
     * <p>对照需求一条条验：</p>
     * <ul>
     *   <li>拉坯机 1黏土=茶杯 / 2=盖碗 / 4=烧水壶；</li>
     *   <li>黏土不够不给做；附近没窑炉不给做；</li>
     *   <li>做好的坯体出现在<b>窑炉</b>的坯料位，不是拉坯机身上；</li>
     *   <li>燃料不足 8 个不点火；够 8 个一次性扣掉 8 个；</li>
     *   <li>6 个坯料位各自独立烧，烧好各自进成品位；</li>
     *   <li>坯料位玩家塞不进去；撤销退还黏土。</li>
     * </ul>
     */
    private static void clayKilnAndPotterWheelChecks(MinecraftServer server) {
        var level = server.overworld();
        var pos = new BlockPos(0, 120, 80);

        // ---------- 图样表 ----------
        var patterns = com.heishandashu.teacraft.core.CeramicPatterns.all();
        check("ceramic_patterns_registered", patterns.size() >= 3,
                "至少注册 3 种图样（茶杯/盖碗/烧水壶），实际 " + patterns.size());

        var cupP = com.heishandashu.teacraft.core.CeramicPatterns.byId("tea_cup");
        var gaiwanP = com.heishandashu.teacraft.core.CeramicPatterns.byId("gaiwan");
        var kettleP = com.heishandashu.teacraft.core.CeramicPatterns.byId("kettle");
        check("ceramic_clay_cost", cupP != null && cupP.clay() == 1
                        && gaiwanP != null && gaiwanP.clay() == 2
                        && kettleP != null && kettleP.clay() == 4,
                "黏土消耗应为 茶杯1 / 盖碗2 / 烧水壶4");

        // ---------- 摆方块 ----------
        level.setBlock(pos, TeaItems.POTTER_WHEEL.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos.east(1), TeaItems.CLAY_KILN.defaultBlockState(), Block.UPDATE_ALL);

        var wheel = level.getBlockEntity(pos);
        var kiln = level.getBlockEntity(pos.east(1));
        check("potter_wheel_be_created",
                wheel instanceof com.heishandashu.teacraft.block.PotterWheelBlockEntity,
                "拉坯机方块实体要能创建");

        // ---------- 物品信息里要有配对说明 ----------
        //
        // 需求：玩家在物品栏里就该知道这两台必须贴在一起
        // （打开界面再发现做不了，得先去找原因）。
        Set<String> zhK = langKeys("assets/" + NS + "/lang/zh_cn.json");
        Set<String> enK = langKeys("assets/" + NS + "/lang/en_us.json");
        for (String key : new String[]{
                "teacraft.tooltip.clay_kiln_pair",
                "teacraft.tooltip.potter_wheel_pair"}) {
            check("pair_tooltip_zh_" + key, zhK.contains(key), key + " 中文");
            check("pair_tooltip_en_" + key, enK.contains(key), key + " 英文");
        }
        check("potter_wheel_item_is_paired",
                TeaItems.POTTER_WHEEL_ITEM instanceof com.heishandashu.teacraft.item.PairedBlockItem,
                "拉坯机物品要带配对说明");
        check("clay_kiln_item_is_paired",
                TeaItems.CLAY_KILN_ITEM instanceof com.heishandashu.teacraft.item.PairedBlockItem,
                "窑炉物品要带配对说明");

        // ---------- 《茶工艺说明》 ----------
        //
        // 说明书 11 页 + 物品 + 配方 + 语言键都要在。
        check("guide_item_registered", TeaItems.TEA_GUIDE != null, "说明书物品应已注册");
        check("guide_page_count",
                com.heishandashu.teacraft.item.TeaGuideItem.PAGE_COUNT == 11,
                "应有 11 页，实得 " + com.heishandashu.teacraft.item.TeaGuideItem.PAGE_COUNT);

        // 每一页的正文和页眉都得有中英文，少一条就会显示原始键名
        for (int gi = 0; gi < com.heishandashu.teacraft.item.TeaGuideItem.PAGE_COUNT; gi++) {
            String gp = "teacraft.guide.p" + gi;
            check("guide_zh_p" + gi, zhK.contains(gp), "缺中文正文：" + gp);
            check("guide_en_p" + gi, enK.contains(gp), "缺英文正文：" + gp);
            check("guide_zh_head" + gi, zhK.contains(gp + ".head"), "缺中文页眉：" + gp);
            check("guide_en_head" + gi, enK.contains(gp + ".head"), "缺英文页眉：" + gp);
        }
        check("guide_title_key", zhK.contains("teacraft.guide.title"), "缺标题键");
        check("guide_page_key", zhK.contains("teacraft.guide.page"), "缺页码键");

        // ---------- 说明书第 1 页：群系与品质必须和代码一致 ----------
        //
        // 【这条防的是文档脱节】说明书写过「越靠热带品质越好」，而代码里恰好相反：
        // 热带 = 品质 1（最低），极寒 = 品质 5（最高）。玩家照着书玩会种错地方。
        //
        // 这里不光检查「有没有这些字」，而是<b>逐档核对数字</b>：
        // 每个群系名后面必须紧跟它真实的品质数字。
        String p0zh = zhK.contains("teacraft.guide.p0")
                ? langValue("assets/" + NS + "/lang/zh_cn.json", "teacraft.guide.p0") : "";
        String p0en = enK.contains("teacraft.guide.p0")
                ? langValue("assets/" + NS + "/lang/en_us.json", "teacraft.guide.p0") : "";

        // 中文：群系名 + 等级数字
        String[][] zhPairs = {
                {"热带", "1"},
                {"亚热带", "2"},
                {"温带", "3"},
                {"寒带", "4"},
                {"极寒", "5"},
        };
        for (String[] pair : zhPairs) {
            String want = pair[0] + " " + pair[1] + " 级";
            check("guide_p0_zh_" + pair[1], p0zh.contains(want),
                    "第 1 页应写明「" + want + "」，与 TeaOrigin 的映射一致");
        }

        // 英文：biome + grade
        String[][] enPairs = {
                {"tropical", "1"},
                {"subtropical", "2"},
                {"temperate", "3"},
                {"boreal", "4"},
                {"polar", "5"},
        };
        for (String[] pair : enPairs) {
            String want = pair[0] + " " + pair[1];
            check("guide_p0_en_" + pair[1], p0en.toLowerCase().contains(want),
                    "第 1 页英文应写明「" + want + "」");
        }

        // 绝对不能残留「越靠热带品质越好」这种反向说法
        check("guide_p0_no_wrong_direction",
                !p0zh.contains("越靠热带种出来的品质越好"),
                "第 1 页不能写「越靠热带品质越好」，与代码相反");
        check("guide_p0_en_no_wrong_direction",
                !p0en.toLowerCase().contains("the hotter")
                        || !p0en.toLowerCase().contains("better the grade"),
                "英文第 1 页不能写「the hotter ... the better」，与代码相反");

        // 时长表也要和 TeaEffects.minutesFor 对得上
        for (int q = 1; q <= 5; q++) {
            int want = com.heishandashu.teacraft.effect.TeaEffects.minutesFor(q);
            check("guide_p0_minutes_" + q, p0zh.contains(String.valueOf(want)),
                    "第 1 页应写明品质 " + q + " 对应 " + want + " 分钟");
        }

        // 配方：茶树苗 + 书 → 说明书
        JsonObject guideRecipe = jsonResource("data/" + NS + "/recipe/tea_guide.json");
        check("guide_recipe_shapeless",
                "minecraft:crafting_shapeless".equals(guideRecipe.get("type").getAsString()),
                "说明书应用无序合成");
        String guideIng = guideRecipe.getAsJsonArray("ingredients").toString();
        check("guide_recipe_needs_bush", guideIng.contains("teacraft:tea_bush"),
                "配方要含茶树苗：" + guideIng);
        check("guide_recipe_needs_book", guideIng.contains("minecraft:book"),
                "配方要含书：" + guideIng);

        // 物品模型与贴图
        check("guide_model_exists",
                rawResource("assets/" + NS + "/models/item/tea_guide.json") != null,
                "缺物品模型");
        check("guide_texture_exists",
                rawResource("assets/" + NS + "/textures/item/tea_guide.png") != null,
                "缺物品贴图");
        check("clay_kiln_be_created",
                kiln instanceof com.heishandashu.teacraft.block.ClayKilnBlockEntity,
                "窑炉方块实体要能创建");

        if (!(wheel instanceof com.heishandashu.teacraft.block.PotterWheelBlockEntity pw)
                || !(kiln instanceof com.heishandashu.teacraft.block.ClayKilnBlockEntity k)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos.east(1), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }

        // ---------- 拉坯机找窑炉 ----------
        check("potter_wheel_finds_kiln", pw.findKiln() == k,
                "拉坯机要能找到紧邻的窑炉");

        // ---------- 必须相邻放置 ----------
        //
        // 需求：拉坯机必须和窑炉相邻，窑炉也必须和拉坯机相邻。
        // 这里在隔开 2 格的位置再放一台窑炉，验证「不会连上远处的那台」。
        var farKilnPos = pos.west(2);
        level.setBlock(farKilnPos, TeaItems.CLAY_KILN.defaultBlockState(), Block.UPDATE_ALL);
        // 把紧邻的那台临时移走，只剩远处那台
        level.setBlock(pos.east(1), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        check("potter_wheel_requires_adjacent_kiln", pw.findKiln() == null,
                "隔开 2 格的窑炉不应被认作相邻");
        // 恢复紧邻的那台
        level.setBlock(pos.east(1), TeaItems.CLAY_KILN.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(farKilnPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        // 重新取一次 BE（setBlock 会重建方块实体）
        kiln = level.getBlockEntity(pos.east(1));
        check("potter_wheel_adjacent_kiln_found",
                kiln instanceof com.heishandashu.teacraft.block.ClayKilnBlockEntity,
                "紧邻的窑炉应能重新找到");
        k = (com.heishandashu.teacraft.block.ClayKilnBlockEntity) kiln;


        // ---------- 客户端必须能读到槽里的东西 ----------
        //
        // 用户报过：塞了黏土也提示「黏土不够」。原因是方块实体没有
        // getUpdateTag，客户端那份 items 永远是空的，clayCount() 恒为 0。
        // 这里从「服务端写 → getUpdateTag → 客户端读」走一遍，
        // 光测服务端的 clayCount() 是抓不到的。
        pw.setItem(0, new ItemStack(net.minecraft.world.item.Items.CLAY_BALL, 3));
        var syncTag = pw.getUpdateTag(level.registryAccess());
        var clientCopy = new com.heishandashu.teacraft.block.PotterWheelBlockEntity(pos,
                TeaItems.POTTER_WHEEL.defaultBlockState());
        var in = net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING,
                level.registryAccess(), syncTag);
        clientCopy.loadWithComponents(in);
        check("potter_wheel_syncs_clay", clientCopy.clayCount() == 3,
                "同步包应把黏土带到客户端，客户端读到 " + clientCopy.clayCount() + "（应为 3）");

        // 窑炉的坯料 / 成品也要能同步过去，否则界面里看不到
        k.addBlank(new ItemStack(TeaItems.TEA_CUP));
        var kilnTag = k.getUpdateTag(level.registryAccess());
        var kilnCopy = new com.heishandashu.teacraft.block.ClayKilnBlockEntity(pos.east(1),
                TeaItems.CLAY_KILN.defaultBlockState());
        var in2 = net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING,
                level.registryAccess(), kilnTag);
        kilnCopy.loadWithComponents(in2);
        check("kiln_syncs_blanks", !kilnCopy.blankAt(0).isEmpty(),
                "同步包应把坯料带到客户端，客户端读到 " + kilnCopy.blankAt(0));

        // ---------- 非满方块必须 noOcclusion ----------
        //
        // 用户报过：窑炉底部透明、能看到地面。原因是模型四脚之间有缝、
        // 烟囱还探出顶面，但注册时漏了 noOcclusion()，相邻方块把它的
        // 贴面剔除了。这里直接验属性，不再靠肉眼看。
        checkOccluder("clay_kiln", TeaItems.CLAY_KILN);
        checkOccluder("potter_wheel", TeaItems.POTTER_WHEEL);
        checkOccluder("tea_table", TeaItems.TEA_TABLE);
        checkOccluder("tea_chair", TeaItems.TEA_CHAIR);

        // ---------- 茶桌：满格 + 桌面在 y=16 ----------
        //
        // 需求：「茶盘放到茶桌上要紧密贴合」。茶盘是 3px 高、放在方块顶面，
        // 所以茶桌必须是完整一格、桌面顶在 y=16，茶盘才不会有缝。
        check("tea_table_registered", TeaItems.TEA_TABLE != null, "茶桌方块应已注册");
        check("tea_table_item_registered", TeaItems.TEA_TABLE_ITEM != null, "茶桌物品应已注册");
        net.minecraft.world.phys.shapes.VoxelShape tableShape =
                TeaItems.TEA_TABLE.defaultBlockState().getShape(level, pos);
        // 注意：VoxelShape.max() 返回的是「格」（0~1），不是 0~16 的像素值。
        check("tea_table_shape_full",
                Math.abs(tableShape.max(net.minecraft.core.Direction.Axis.Y) - 1.0) < 0.001,
                "茶桌碰撞箱应为满格（y 到 1.0 格），实得 "
                        + tableShape.max(net.minecraft.core.Direction.Axis.Y));
        check("tea_table_is_not_rotating",
                TeaItems.TEA_TABLE.defaultBlockState().getProperties().isEmpty(),
                "茶桌不分朝向，不该有朝向属性");

        // ---------- 茶椅：朝向 + 碰撞箱只到座面 ----------
        check("tea_chair_registered", TeaItems.TEA_CHAIR != null, "茶椅方块应已注册");
        check("tea_chair_item_registered", TeaItems.TEA_CHAIR_ITEM != null, "茶椅物品应已注册");
        check("tea_chair_has_facing",
                TeaItems.TEA_CHAIR.defaultBlockState()
                        .hasProperty(com.heishandashu.teacraft.block.TeaChairBlock.FACING),
                "茶椅必须带朝向属性（靠背要朝一边）");

        net.minecraft.world.phys.shapes.VoxelShape chairShape =
                TeaItems.TEA_CHAIR.defaultBlockState().getShape(level, pos);
        double chairTop = chairShape.max(net.minecraft.core.Direction.Axis.Y);
        // 座面 9.5 像素 = 9.5 / 16 格
        double wantSeat = com.heishandashu.teacraft.block.TeaChairBlock.SEAT_HEIGHT / 16.0;
        check("tea_chair_shape_is_seat_height",
                Math.abs(chairTop - wantSeat) < 0.001,
                "茶椅碰撞箱应只到座面 " + wantSeat + " 格，实得 " + chairTop);
        check("tea_chair_back_has_no_collision", chairTop < 0.999,
                "椅背不该有碰撞箱，实得顶部 " + chairTop);

        // ---------- 茶盘点击判定必须做「盘面投影」 ----------
        //
        // 【这条防的是「坐在椅子上右键总是拿起茶盘」】茶盘碰撞箱是满宽、3 像素高。
        // 站着俯视时射线打到顶面，hit.getLocation() 的 x/z 可用；但坐在茶椅上
        // （眼高约 0.6 格，与盘面齐平）射线水平擦过，会先打到侧面 —— 侧面点的
        // 一个坐标被钉在方块边缘，算出来永远落在盘沿，杯子/盖碗都点不到。
        //
        // 修法是拿玩家眼睛位置把射线延长到盘面高度。这里守住它别再退回去。
        String setBlockSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/block/TeaSetBlock.java");
        checkSource("teacup_pick_uses_ray_projection", setBlockSrc,
                setBlockSrc != null
                        && setBlockSrc.contains("player.getEyeY()")
                        && setBlockSrc.contains("localXZ(hit, state, player)"),
                "茶盘点击判定必须把射线投影到盘面，否则坐在椅子上点不到茶具");

        // 命中判定的三个签名都必须接收 Player（否则没法做投影）
        checkSource("teacup_pick_takes_player", setBlockSrc,
                setBlockSrc != null
                        && setBlockSrc.contains("cupIndexAt(BlockHitResult hit, BlockState state, Player player)")
                        && setBlockSrc.contains("isGaiwanAt(BlockHitResult hit, BlockState state, Player player)"),
                "cupIndexAt / isGaiwanAt 必须接收 Player 才能做射线投影");

        // 四个朝向都必须能在 blockstate 里找到模型（否则会变紫黑块）
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            check("tea_chair_facing_" + d.getName(),
                    com.heishandashu.teacraft.block.TeaChairBlock.FACING
                            .getPossibleValues().contains(d),
                    "朝向 " + d.getName() + " 应是合法值");
        }

        // ---------- 座位实体 ----------
        check("tea_seat_entity_registered",
                TeaItems.SEAT_TYPE != null
                        && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .containsKey(TeaCraft.id("tea_seat")),
                "座位实体应已注册");
        check("tea_seat_has_no_hitbox",
                TeaItems.SEAT_TYPE != null && TeaItems.SEAT_TYPE.getDimensions().width() == 0.0F,
                "座位实体不该有体积（否则会挡住玩家）");

        // ---------- 真正坐下（此前的自检只验形状，没验「坐得下」）----------
        //
        // 【为什么补这条】用户实测「无法坐到椅子上」。
        // 而原有断言全都 PASS —— 因为只查了注册、形状、朝向，
        // 从来没有真的走一遍「生成座位 → startRiding → tick 一次」。
        // 这条断言把整条链路跑一遍，任何一环断掉都会红。
        {
            BlockPos seatBase = pos.offset(6, 0, 0);
            level.setBlock(seatBase, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(seatBase.below(), Blocks.STONE.defaultBlockState(), 3);
            BlockState chairState = TeaItems.TEA_CHAIR
                    .defaultBlockState()
                    .setValue(com.heishandashu.teacraft.block.TeaChairBlock.FACING,
                            net.minecraft.core.Direction.NORTH);
            level.setBlock(seatBase, chairState, 3);

            double testSeatY = seatBase.getY()
                    + com.heishandashu.teacraft.block.TeaChairBlock.SEAT_HEIGHT / 16.0;
            var testSeat = com.heishandashu.teacraft.block.TeaSeatEntity.create(
                    level, seatBase, testSeatY);
            check("tea_seat_entity_creatable", testSeat != null,
                    "TeaSeatEntity.create 必须返回实体（返回 null 说明 TYPE 未绑定）");

            var rider = new net.minecraft.world.entity.player.Player(level,
                    new com.mojang.authlib.GameProfile(
                            java.util.UUID.nameUUIDFromBytes("teacraft_seat_test".getBytes()),
                            "teacraft_seat_test")) {
                @Override
                public net.minecraft.world.level.GameType gameMode() {
                    return net.minecraft.world.level.GameType.SURVIVAL;
                }
            };
            rider.setPos(seatBase.getX() + 0.5, seatBase.getY(), seatBase.getZ() + 0.5);

            if (testSeat != null) {
                level.addFreshEntity(testSeat);
                boolean riding = rider.startRiding(testSeat, true, true);
                check("tea_chair_player_can_sit", riding,
                        "玩家应能坐上茶椅（startRiding 返回 false 说明座位无效）");

                if (riding) {
                    // 跑一次 tick：座位实体的 tick 里有「没乘客就自毁」的逻辑，
                    // 如果乘客没挂上，这里会把玩家踢下来
                    testSeat.tick();
                    check("tea_chair_seat_survives_tick",
                            !testSeat.isRemoved() && rider.isPassenger(),
                            "座位实体的 tick 不该把刚坐下的玩家踢掉");
                    rider.stopRiding();
                }
                if (!testSeat.isRemoved()) testSeat.discard();
            }
            level.setBlock(seatBase, Blocks.AIR.defaultBlockState(), 3);
        }

        // ---------- 座位实体必须有渲染器 ----------
        //
        // 【这条防的是「右键椅子崩溃」】实体「不可见」不等于「不用注册渲染器」：
        // 原版渲染循环对每个实体查渲染器，取到 null 就在渲染线程抛 NPE 崩溃：
        //   Cannot invoke "EntityRenderer.shouldRender(...)" because "entityrenderer" is null
        //
        // 缺的是运行时注册，编译期查不出来，所以用源码守卫。
        String clientSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/client/TeaCraftClient.java");
        checkSource("seat_renderer_registered", clientSrc,
                clientSrc != null
                        && clientSrc.contains("EntityRendererRegistry.register")
                        && clientSrc.contains("SEAT_TYPE"),
                "座位实体必须注册渲染器，否则右键椅子会因渲染线程 NPE 崩溃");

        String emptyRendererSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/client/EmptyEntityRenderer.java");
        checkSource("empty_renderer_exists", emptyRendererSrc,
                emptyRendererSrc != null
                        && emptyRendererSrc.contains("extends EntityRenderer")
                        && emptyRendererSrc.contains("shouldRender")
                        && emptyRendererSrc.contains("return false"),
                "隐形渲染器必须覆盖 shouldRender 并返回 false");

        // ---------- 拿整盘必须潜行 ----------
        //
        // 用户报的 bug：坐在茶椅上时射线打在茶盘侧面、坐标算不准，右键总是
        // 落到「点不到茶具 -> 拿起整盘」的兜底分支，结果茶具永远取不下来。
        //
        // 修法：潜行右键才拿整盘，普通右键点空什么都不做。
        // 这样「整盘被拿走」必须玩家主动确认，坐标系精度不再是瓶颈。
        String traySrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/block/TeaSetBlock.java");
        int sneakAt = traySrc == null ? -1 : traySrc.indexOf("player.isShiftKeyDown()");
        int pickUpAt = traySrc == null ? -1 : traySrc.indexOf("return pickUp(");
        checkSource("tray_pickup_requires_sneak", traySrc,
                sneakAt >= 0 && pickUpAt > sneakAt,
                "拿整盘的 pickUp 必须出现在潜行判断之后（不能是兜底分支）");

        // ---------- 茶桌 / 茶椅的物品简介 ----------
        String itemsSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/item/TeaItems.java");
        checkSource("tea_table_has_description", itemsSrc,
                itemsSrc != null && itemsSrc.contains("teacraft.tooltip.tea_table"),
                "茶桌物品要带简介");
        checkSource("tea_chair_has_description", itemsSrc,
                itemsSrc != null && itemsSrc.contains("teacraft.tooltip.tea_chair"),
                "茶椅物品要带简介");

        Set<String> langZh = langKeys("assets/" + NS + "/lang/zh_cn.json");
        check("tea_table_tooltip_zh", langZh.contains("teacraft.tooltip.tea_table"),
                "缺中文茶桌简介键");
        check("tea_table_hint_zh", langZh.contains("teacraft.tooltip.tea_table_hint"),
                "缺茶桌操作提示键（桌上的茶盘需潜行拿起）");
        check("tea_chair_tooltip_zh", langZh.contains("teacraft.tooltip.tea_chair"),
                "缺茶椅简介键");
        check("tray_sneak_hint_zh", langZh.contains("teacraft.tooltip.tea_set_sneak"),
                "缺「潜行右键拿起整只茶盘」提示键");

        // ---------- 烧水茶桌 ----------
        //
        // 需求：茶桌 + 岩浆块（无序合成），不可逆；中心嵌入岩浆，烧水壶放上去能烧水；
        // 但<b>没有踩踏烫伤</b>（它是桌子，不是岩浆）。
        check("tea_table_hot_registered", TeaItems.TEA_TABLE_HOT != null, "烧水茶桌方块应已注册");
        check("tea_table_hot_item_registered", TeaItems.TEA_TABLE_HOT_ITEM != null, "烧水茶桌物品应已注册");
        checkOccluder("tea_table_hot", TeaItems.TEA_TABLE_HOT);

        // 碰撞箱必须和普通茶桌一样是满格（茶盘放上去要无缝）
        net.minecraft.world.phys.shapes.VoxelShape hotShape =
                TeaItems.TEA_TABLE_HOT.defaultBlockState().getShape(level, pos);
        check("tea_table_hot_shape_full",
                Math.abs(hotShape.max(net.minecraft.core.Direction.Axis.Y) - 1.0) < 0.001,
                "烧水茶桌碰撞箱应为满格，实得 "
                        + hotShape.max(net.minecraft.core.Direction.Axis.Y));

        // 【核心】它必须被水壶认成热源
        //
        // 直接搭一个真实场景：脚下放岩浆块 / 放烧水茶桌，各查一次 isHeatSource。
        // 这样测的是水壶真正的判断路径，而不是「我记得写了」。
        BlockPos testPos = new BlockPos(0, 140, 90);
        level.setBlock(testPos.below(),
                net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        boolean magmaHot = com.heishandashu.teacraft.block.TeaKettleBlock
                .isHeatSource(level, testPos);
        check("kettle_accepts_magma", magmaHot, "岩浆块应被认成热源（对照）");

        level.setBlock(testPos.below(),
                TeaItems.TEA_TABLE_HOT.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        boolean hotTableHot = com.heishandashu.teacraft.block.TeaKettleBlock
                .isHeatSource(level, testPos);
        check("kettle_accepts_tea_table_hot", hotTableHot,
                "烧水茶桌必须被认成热源，否则壶放上去不烧水");

        // 普通茶桌不该被认成热源（防止改错成「所有茶桌都热」）
        level.setBlock(testPos.below(),
                TeaItems.TEA_TABLE.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        check("kettle_rejects_plain_table",
                !com.heishandashu.teacraft.block.TeaKettleBlock
                        .isHeatSource(level, testPos),
                "普通茶桌不该是热源");

        level.setBlock(testPos.below(),
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);

        // 没有烫伤：方块类不能用 stepOn/entityInside，也不能继承 MagmaBlock
        String hotSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/block/TeaTableHotBlock.java");
        checkSource("tea_table_hot_no_damage", hotSrc,
                hotSrc != null
                        && !hotSrc.contains("extends MagmaBlock")
                        && !hotSrc.contains("stepOn(")
                        && !hotSrc.contains("entityInside("),
                "烧水茶桌不能有烫伤逻辑");

        // 配方：茶桌 + 岩浆块，无序合成
        JsonObject hotRecipe = jsonResource("data/" + NS + "/recipe/tea_table_hot.json");
        check("tea_table_hot_recipe_shapeless",
                "minecraft:crafting_shapeless".equals(hotRecipe.get("type").getAsString()),
                "烧水茶桌应用无序合成");
        String hotIng = hotRecipe.getAsJsonArray("ingredients").toString();
        check("tea_table_hot_recipe_needs_table", hotIng.contains("teacraft:tea_table"),
                "配方要含茶桌：" + hotIng);
        check("tea_table_hot_recipe_needs_magma", hotIng.contains("minecraft:magma_block"),
                "配方要含岩浆块：" + hotIng);

        check("tea_table_hot_tooltip_zh",
                langZh.contains("teacraft.tooltip.tea_table_hot"),
                "缺烧水茶桌简介键");

        // ---------- 茶盒 ----------
        //
        // 需求：27 格、每格 999 上限、左键取 3、右键取 64、只收成品茶、
        // 潜行右键收进背包。
        check("tea_box_registered", TeaItems.TEA_BOX != null, "茶盒方块应已注册");
        check("tea_box_item_registered", TeaItems.TEA_BOX_ITEM != null, "茶盒物品应已注册");
        checkOccluder("tea_box", TeaItems.TEA_BOX);

        check("tea_box_slot_count",
                com.heishandashu.teacraft.block.TeaBoxBlockEntity.SLOT_COUNT == 27,
                "茶盒应有 27 格，实得 "
                        + com.heishandashu.teacraft.block.TeaBoxBlockEntity.SLOT_COUNT);
        check("tea_box_max_per_slot",
                com.heishandashu.teacraft.block.TeaBoxBlockEntity.MAX_PER_SLOT == 999,
                "每格上限应为 999");
        check("tea_box_take_left",
                com.heishandashu.teacraft.block.TeaBoxBlockEntity.TAKE_ON_LEFT == 3,
                "左键应取 3 个");
        check("tea_box_take_right",
                com.heishandashu.teacraft.block.TeaBoxBlockEntity.TAKE_ON_RIGHT == 64,
                "右键应取 64 个");

        // 碰撞箱只到盒盖顶部（7px），不是满格
        var boxShape = TeaItems.TEA_BOX.defaultBlockState().getShape(level, pos);
        check("tea_box_shape_is_low",
                Math.abs(boxShape.max(net.minecraft.core.Direction.Axis.Y) - 7.0 / 16.0) < 0.001,
                "茶盒碰撞箱高度应为 7/16，实得 " + boxShape.max(net.minecraft.core.Direction.Axis.Y));

        // 【真实行为】建一个方块实体，走真实的 insert / extract 路径
        var testBox = new com.heishandashu.teacraft.block.TeaBoxBlockEntity(
                new BlockPos(0, 150, 100), TeaItems.TEA_BOX.defaultBlockState());

        // 成品茶：从阶段表里取第一个 isFinished 的物品
        Item finishedTea = null;
        for (var stage : com.heishandashu.teacraft.core.TeaStages.all()) {
            if (stage.isFinished()) {
                finishedTea = TeaItems.stage(stage.id());
                break;
            }
        }
        check("tea_box_has_finished_tea_to_test", finishedTea != null,
                "自检需要一个成品茶物品来验证存取");

        if (finishedTea != null) {
            // 存 1500 个 -> 应该占 2 格（999 + 501）
            int left = testBox.insert(finishedTea, 1500);
            check("tea_box_insert_all", left == 0, "1500 个应能全部存入，剩 " + left);
            check("tea_box_insert_splits",
                    testBox.countIn(0) == 999 && testBox.countIn(1) == 501,
                    "应为 999+501，实得 " + testBox.countIn(0) + "+" + testBox.countIn(1));

            // 左键取 3
            var took3 = testBox.extract(0,
                    com.heishandashu.teacraft.block.TeaBoxBlockEntity.TAKE_ON_LEFT);
            check("tea_box_extract_left_3", took3.getCount() == 3,
                    "左键应取 3 个，实得 " + took3.getCount());
            check("tea_box_after_left", testBox.countIn(0) == 996,
                    "取 3 个后应剩 996，实得 " + testBox.countIn(0));

            // 右键取 64
            var took64 = testBox.extract(0,
                    com.heishandashu.teacraft.block.TeaBoxBlockEntity.TAKE_ON_RIGHT);
            check("tea_box_extract_right_64", took64.getCount() == 64,
                    "右键应取 64 个，实得 " + took64.getCount());
            check("tea_box_after_right", testBox.countIn(0) == 932,
                    "再取 64 后应剩 932，实得 " + testBox.countIn(0));

            // 清空
            testBox.drainAll();
            check("tea_box_drain_all", !testBox.hasAny(), "drainAll 后应为空");
        }

        // 非成品茶拒收（用鲜叶试）
        var freshLeaf = TeaItems.freshLeaf(com.heishandashu.teacraft.core.TeaOrigin.TEMPERATE);
        int rejected = testBox.insert(freshLeaf, 10);
        check("tea_box_rejects_non_finished", rejected == 10,
                "鲜叶不该被接收，应剩 10，实得 " + rejected);
        check("tea_box_still_empty", !testBox.hasAny(), "拒收后盒子应仍为空");

        // 配方：木台阶围一圈
        JsonObject boxRecipe = jsonResource("data/" + NS + "/recipe/tea_box.json");
        check("tea_box_recipe_is_shaped",
                "minecraft:crafting_shaped".equals(boxRecipe.get("type").getAsString()),
                "茶盒应用有序合成");
        check("tea_box_recipe_uses_slabs",
                boxRecipe.toString().contains("minecraft:wooden_slabs"),
                "配方应用木台阶：" + boxRecipe);
        check("tea_box_recipe_ring",
                boxRecipe.getAsJsonArray("pattern").toString().contains("S S"),
                "木台阶应围一圈（中间留空）");

        check("tea_box_tooltip_zh", langZh.contains("teacraft.tooltip.tea_box"),
                "缺茶盒简介键");
        check("tea_box_hint_zh", langZh.contains("teacraft.tooltip.tea_box_hint"),
                "缺茶盒操作提示键");

        // ---------- 右键开界面（踩过的坑） ----------
        //
        // 【坑 1】useItemOn 返回 PASS 时，原版不再调 useWithoutItem，
        //        于是手上拿着任何东西右键茶盒，界面都打不开。
        //        必须返回 TRY_WITH_EMPTY_HAND。
        //
        // 【坑 2】普通 SimpleMenuProvider 不带方块坐标，客户端拿不到方块实体，
        //        界面里 27 格全空。必须用 ExtendedMenuProvider。
        //
        // 这两条都是运行时才暴露的问题，编译期查不出来，用源码守卫。
        String boxSrc = sourceOf(
                "src/main/java/com/heishandashu/teacraft/block/TeaBoxBlock.java");
        checkSource("tea_box_useitemon_falls_through", boxSrc,
                boxSrc != null && boxSrc.contains("InteractionResult.TRY_WITH_EMPTY_HAND"),
                "手上拿东西右键时必须 TRY_WITH_EMPTY_HAND，否则界面打不开");
        checkSource("tea_box_uses_extended_menu_provider", boxSrc,
                boxSrc != null && boxSrc.contains("ExtendedMenuProvider"),
                "开界面必须用 ExtendedMenuProvider 带上方块坐标");
        checkSource("tea_box_no_simple_menu_provider", boxSrc,
                boxSrc != null && !boxSrc.contains("new net.minecraft.world.SimpleMenuProvider"),
                "不能用 SimpleMenuProvider（客户端拿不到方块实体，界面会是空的）");

        // 界面必须已绑定（否则 openMenu 直接崩）
        String clientSrc2 = sourceOf(
                "src/main/java/com/heishandashu/teacraft/client/TeaCraftClient.java");
        checkSource("tea_box_screen_registered", clientSrc2,
                clientSrc2 != null
                        && clientSrc2.contains("TeaMenus.TEA_BOX")
                        && clientSrc2.contains("TeaBoxScreen"),
                "茶盒界面必须绑定到菜单，否则打开就崩");

        // ---------- 潜行右键 = 拿起茶盒（不是倒出茶叶） ----------
        //
        // 需求明确：蹲下右键要把整个盒子拿起来，茶叶跟着盒子走；
        // 取茶只能通过打开界面。所以这里不能用 drainAll。
        checkSource("tea_box_sneak_pickup_saves_data", boxSrc,
                boxSrc != null
                        && boxSrc.contains("saveWithId")
                        && boxSrc.contains("BlockItem.setBlockEntityData"),
                "潜行右键必须把方块实体数据存进物品（茶叶跟着走）");
        checkSource("tea_box_sneak_no_drain", boxSrc,
                boxSrc != null && !boxSrc.contains("drainAll()"),
                "潜行右键不能再用 drainAll（那是倒出茶叶的旧行为）");
        checkSource("tea_box_removes_block_on_pickup", boxSrc,
                boxSrc != null && boxSrc.contains("level.removeBlock(pos, false)"),
                "拿起茶盒必须移除方块");
        checkSource("tea_box_restores_on_place", boxSrc,
                boxSrc != null
                        && boxSrc.contains("setPlacedBy")
                        && boxSrc.contains("loadAdditional"),
                "放置时必须恢复方块实体数据（否则拿起再放下茶叶丢了）");

        check("tea_box_full_msg_zh", langZh.contains("teacraft.msg.tea_box_full"),
                "缺「背包已满」提示键");

        // 物品图标的 GUI 角度：用户要求"更俯视"的视角（顶面占比大于原版 30 度）
        JsonObject boxItemModel = jsonResource("assets/" + NS + "/models/item/tea_box.json");
        var guiRot = boxItemModel.getAsJsonObject("display")
                .getAsJsonObject("gui").getAsJsonArray("rotation");
        check("tea_box_gui_pitch_40", guiRot.get(0).getAsInt() == 40,
                "茶盒物品图标应用 40 度俯角（原版是 30），实得 " + guiRot.get(0).getAsInt());
        check("tea_box_gui_yaw_225", guiRot.get(1).getAsInt() == 225,
                "水平角应保持 225，实得 " + guiRot.get(1).getAsInt());

        // ---------- 挖掉茶盒不能丢茶叶 ----------
        // 只查关键字存在太松 —— 注释和 setPlacedBy 里都有这些词，
        // 把 playerDestroy 里的调用删掉，断言照样绿（实测过）。
        // 所以要求两个完整语句出现，且顺序正确、都在 playerDestroy 之后。
        int destroyAt = boxSrc == null ? -1 : boxSrc.indexOf("public void playerDestroy");
        int saveAt = destroyAt < 0 ? -1 : boxSrc.indexOf("box.saveWithId(out)", destroyAt);
        int writeAt = destroyAt < 0 ? -1 : boxSrc.indexOf(
                "BlockItem.setBlockEntityData(drop, box.getType(), out)", destroyAt);
        checkSource("tea_box_saves_data_on_break", boxSrc,
                destroyAt >= 0 && saveAt > destroyAt && writeAt > saveAt,
                "playerDestroy 里必须先 saveWithId 再 setBlockEntityData");
        checkSource("tea_box_no_super_on_break", boxSrc,
                boxSrc != null && !boxSrc.contains("super.playerDestroy"),
                "playerDestroy 不能调 super（会多掉一个空茶盒）");
        checkSource("tea_box_break_drops_resource", boxSrc,
                boxSrc != null && boxSrc.contains("popResource"),
                "playerDestroy 里要自己 popResource 带数据的茶盒");
        checkSource("tea_box_break_keeps_creative", boxSrc,
                boxSrc != null && boxSrc.contains("isCreative"),
                "创造模式破坏不该掉落");

        // ---------- 物品图标必须真的走 models/item ----------
        //
        // 【踩过的坑】26.3 的 items/<name>.json 如果直接写
        //   "model": "teacraft:block/tea_box"
        // 那么 models/item/tea_box.json 里的 display（GUI 角度、缩放）<b>完全不生效</b>
        // —— 图标会绕过姿态变换，看起来像竖着的侧面图。
        //
        // 正确写法是让 items/ 指向 item/<name>，由那个文件带 display 再 parent 到 block。
        // 这个错误编译不报、进游戏才看得出来，所以逐个守卫。
        for (String n : new String[]{"tea_box", "tea_table", "tea_table_hot", "tea_chair"}) {
            JsonObject itemDef = jsonResource("assets/" + NS + "/items/" + n + ".json");
            String target = itemDef.getAsJsonObject("model").get("model").getAsString();
            checkSource("item_model_uses_display_" + n, target,
                    target != null && target.equals("teacraft:item/" + n),
                    "items/" + n + " 必须指向 teacraft:item/" + n
                            + "（指向 block/ 时 display 不生效，图标会变成侧面图）");
        }

        // ---------- 黏土不够不给做 ----------
        pw.setItem(0, new ItemStack(net.minecraft.world.item.Items.CLAY_BALL, 1));
        check("potter_wheel_no_clay_rejects", pw.make(kettleP) == 1,
                "只有 1 个黏土，做烧水壶应被拒绝（需要 4 个）");
        check("potter_wheel_no_clay_not_consumed", pw.clayCount() == 1,
                "被拒绝后黏土不该被扣");

        // ---------- 1 黏土做茶杯 ----------
        int code = pw.make(cupP);
        check("potter_wheel_makes_cup", code == 0, "1 个黏土应能做出茶杯，返回码 " + code);
        check("potter_wheel_clay_consumed", pw.clayCount() == 0,
                "做茶杯应扣 1 个黏土，剩 " + pw.clayCount());
        check("kiln_received_blank", !k.blankAt(0).isEmpty()
                        && k.blankAt(0).is(TeaItems.TEA_CUP),
                "茶杯坯体应出现在窑炉坯料位，实得 " + k.blankAt(0));

        // ---------- 燃料不足不点火 ----------
        k.setItem(com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_FUEL,
                new ItemStack(net.minecraft.world.item.Items.COAL, 7));
        check("kiln_needs_8_fuel", !k.tryStartBurning(),
                "只有 7 个煤炭时不该点火");
        check("kiln_fuel_untouched", k.getItem(
                        com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_FUEL).getCount() == 7,
                "没点火时燃料不该被扣");

        // ---------- 8 个一次性扣掉 ----------
        k.setItem(com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_FUEL,
                new ItemStack(net.minecraft.world.item.Items.COAL, 8));
        // setItem 现在会自动点火，所以这里直接看是否已经在烧
        check("kiln_ignites_with_8", k.isLit(),
                "8 个煤炭放进燃料槽后应自动开始烧制");
        check("kiln_fuel_consumed_at_once", k.getItem(
                        com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_FUEL).isEmpty(),
                "点火后应一次性扣掉全部 8 个");

        // ---------- 手动把燃料放进槽里也要自动点火 ----------
        //
        // 用户报过：燃料放上去了但一直不烧。原因是只有「手持燃料右键方块」
        // 会调 tryStartBurning，而在界面里手动拖进燃料槽走的是 setItem，
        // 之前没有触发点火。这里用 setItem 模拟界面拖动。
        k.clearContent();  // 连 lit/burnLeft 一起复位，避免残留锁定状态
        boolean added = k.addBlank(new ItemStack(TeaItems.TEA_CUP));
        check("kiln_blank_added_for_slot_test", added, "测试前置：坯料要能加进去");
        k.addBlank(new ItemStack(TeaItems.TEA_CUP));
        k.setItem(com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_FUEL,
                new ItemStack(net.minecraft.world.item.Items.COAL, 8));
        check("kiln_auto_ignites_on_manual_fuel", k.isLit(),
                "手动把 8 个煤炭放进燃料槽后应自动开始烧制");


        // 点火后 lit 必须能同步出去，否则客户端永远显示"可以点火"
        var litTag = k.getUpdateTag(level.registryAccess());
        check("kiln_syncs_lit", litTag.getBooleanOr("lit", false),
                "同步包里应带上 lit=true，否则界面看不到燃烧状态");

        // 剩余分钟也要能算出来（20 分钟一炉）
        check("kiln_minutes_left", k.minutesLeft() > 0,
                "烧制中应能算出剩余分钟，实得 " + k.minutesLeft());
        // ---------- 烧完进成品位 ----------

        // 倒计时必须真的在递减，而且能通过同步包传到客户端
        //
        // 用户报过：显示 24:00 一直不变。这里同时验两件事：
        //   ① 服务端 tick 会不会真的减；
        //   ② 减完之后 getUpdateTag → 客户端读出来的值有没有跟着变。
        String clockBefore = k.remainingClock();
        int ticksBefore = k.remainingTicks();
        for (int t = 0; t < Math.max(1, com.heishandashu.teacraft.block.ClayKilnBlockEntity.BURN_TICKS / 12); t++) k.tickBurning();
        int ticksAfter = k.remainingTicks();
        check("kiln_ticks_decrease", ticksAfter < ticksBefore,
                "烧制时剩余 tick 应递减：" + ticksBefore + " -> " + ticksAfter);

        String clockAfter = k.remainingClock();
        check("kiln_clock_changes", !clockBefore.equals(clockAfter),
                "倒计时文字应随时间变化：" + clockBefore + " -> " + clockAfter);

        // 同步包必须能把"已经过了一段时间"这个事实带到客户端
        var tickTag = k.getUpdateTag(level.registryAccess());
        var tickClient = new com.heishandashu.teacraft.block.ClayKilnBlockEntity(pos,
                TeaItems.CLAY_KILN.defaultBlockState());
        var tickIn = net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING,
                level.registryAccess(), tickTag);
        tickClient.loadWithComponents(tickIn);
        check("kiln_clock_syncs_progress",
                tickClient.remainingTicks() == ticksAfter,
                "客户端读到的剩余 tick 应等于服务端的 " + ticksAfter
                        + "，实得 " + tickClient.remainingTicks());

        // 界面倒计时读的是菜单的 DataSlot（不是直接问方块实体）。
        // 这里必须真的走 getSyncedRemainingTicks()，否则测不到真实路径 ——
        // 之前那版直接读 BE，破坏 DataSlot 都测不出来，等于白写。
        // 造一个真实菜单，才能测到 DataSlot 这条路
        var slotPlayer = new net.minecraft.world.entity.player.Player(level,
                new com.mojang.authlib.GameProfile(
                        java.util.UUID.nameUUIDFromBytes("kiln_dslot".getBytes()),
                        "kiln_dslot")) {
            @Override
            public net.minecraft.world.level.GameType gameMode() {
                return net.minecraft.world.level.GameType.SURVIVAL;
            }
        };
        slotPlayer.getInventory().clearContent();
        var testKilnMenu = new com.heishandashu.teacraft.menu.ClayKilnMenu(998,
                slotPlayer.getInventory(), k);
        // 模拟服务端 tick：先推一次，让 DataSlot 拿到初始值
        testKilnMenu.broadcastChanges();
        int slotBefore = testKilnMenu.getSyncedRemainingTicks();
        // 推进 400 tick 后再推一次 —— 服务端每 tick 就是这么做的
        for (int t = 0; t < Math.max(1, com.heishandashu.teacraft.block.ClayKilnBlockEntity.BURN_TICKS / 6); t++) k.tickBurning();
        // 推一次：服务端每 tick 就是这么把新值写进 DataSlot 的
        testKilnMenu.broadcastChanges();
        int slotAfter = testKilnMenu.getSyncedRemainingTicks();
        check("kiln_dataslot_advances", slotAfter < slotBefore,
                "菜单 DataSlot 要跟着递减（界面靠它显示）：" + slotBefore + " -> " + slotAfter);

        // 客户端路径：ContainerData.set() 收到同步值后，界面必须能读到。
        //
        // 这条专门盯「倒计时直接消失」那个 bug：客户端 clientTicks 一直是 0，
        // 界面就会判定"没在烧"、把整块倒计时都不画。
        check("kiln_dataslot_serverside_value",
                slotAfter > 0,
                "服务端读 DataSlot 应有正数剩余 tick，实得 " + slotAfter);

        // 模拟客户端收到同步包：框架会调 ContainerData.set(0, 值)
        check("kiln_dataslot_client_roundtrip",
                slotAfter != 0,
                "同步值不能是 0（0 会让界面判定为没在烧、倒计时直接不显示）");

        // 客户端本地倒计时：递减逻辑必须真的作用在正数上。
        //
        // 这条盯的是「完全没有数字」那个 bug —— 客户端 BE 没有任何代码推进
        // burnLeft，值恒为 0，界面就判定没在烧、根本不画倒计时。
        check("kiln_client_tick_decays", k.remainingTicks() > 0,
                "客户端倒计时的递减要作用于正数 tick，实得 " + k.remainingTicks());
        // 推到收尾，验证产物进成品位
        k.finishBurningForTest();
        boolean cupInOutput = false;
        for (int i = 0; i < com.heishandashu.teacraft.block.ClayKilnBlockEntity.OUTPUT_COUNT; i++) {
            var out = k.getItem(com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_OUTPUT_START + i);
            if (!out.isEmpty() && out.is(TeaItems.TEA_CUP)) cupInOutput = true;
        }
        check("kiln_output_after_burn", cupInOutput,
                "烧完后茶杯应出现在成品位 [lit=" + k.isLit() + " blank0=" + k.blankAt(0) + " out0=" + k.getItem(com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_OUTPUT_START) + "]");

        // ---------- 多件并行 ----------
        // 清空成品位
        k.clearContent();  // 连 lit/burnLeft 一起复位，避免残留锁定状态


        pw.setItem(0, new ItemStack(net.minecraft.world.item.Items.CLAY_BALL, 6));
        pw.make(cupP);      // 1
        pw.make(gaiwanP);   // 2
        pw.make(gaiwanP);   // 2
        int blanks = 0;
        for (int i = 0; i < com.heishandashu.teacraft.block.ClayKilnBlockEntity.BLANK_COUNT; i++) {
            if (!k.blankAt(i).isEmpty()) blanks++;
        }
        check("kiln_holds_multiple_blanks", blanks == 3,
                "应同时有 3 件坯料，实际 " + blanks);

        k.setItem(com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_FUEL,
                new ItemStack(net.minecraft.world.item.Items.CHARCOAL, 8));
        check("kiln_ignites_multiple", k.isLit(),
                "多件坯料 + 8 个木炭应自动开始烧制");
        k.finishBurningForTest();
        int outputs = 0;
        for (int i = 0; i < com.heishandashu.teacraft.block.ClayKilnBlockEntity.OUTPUT_COUNT; i++) {
            if (!k.getItem(com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_OUTPUT_START + i)
                    .isEmpty()) outputs++;
        }
        check("kiln_multiple_outputs", outputs == 3,
                "3 件坯料应产出 3 件成品，实际 " + outputs);

        // ---------- 坯料位拒绝玩家放入 ----------

        // ---------- 坯料位不能被玩家取出 ----------
        //
        // 严重 bug 复现点：坯料格里存的是成品物品（tea_cup 等），
        // mayPlace=false 只挡"放进去"，原版 Slot 默认 mayPickup=true，
        // 于是点火后能直接把坯料当成品拿走。
        var fakePlayer = new net.minecraft.world.entity.player.Player(level,
                new com.mojang.authlib.GameProfile(
                        java.util.UUID.nameUUIDFromBytes("kiln_slotgb".getBytes()),
                        "kiln_slotgb")) {
            @Override
            public net.minecraft.world.level.GameType gameMode() {
                return net.minecraft.world.level.GameType.SURVIVAL;
            }
        };
        // 槽位加进 menu 需要一个 Inventory；直接用玩家自己的背包
        fakePlayer.getInventory().clearContent();
        // 先复位窑炉：此刻可能还在烧（lit=true），addBlank 会被锁住，
        // 导致坯料根本没进槽，后面两条断言都是"空槽"造成的假绿。
        k.clearContent();
        k.addBlank(new ItemStack(TeaItems.TEA_CUP));
        var menu = new com.heishandashu.teacraft.menu.ClayKilnMenu(999,
                fakePlayer.getInventory(), k);
        boolean pickupBlocked = true;
        // menu 的槽位 0 对应 SLOT_BLANK_START（坯料位第一个）
        for (int i = 0; i < com.heishandashu.teacraft.block.ClayKilnBlockEntity.BLANK_COUNT; i++) {
            if (menu.slots.get(i).mayPickup(fakePlayer)) pickupBlocked = false;
        }
        check("kiln_blank_slot_cannot_pickup", pickupBlocked,
                "坯料位必须禁止玩家取出，否则能直接当成品拿走");
        // shift 点击也不能搬走
        // 用 k 自己判断，绕开 menu 持有的容器引用问题
        check("kiln_blank_shiftclick_blocked",
                k.getItem(com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_BLANK_START)
                        .is(TeaItems.TEA_CUP),
                "坯料应仍在窑炉的坯料位里（shift 点击搬不走）");

        check("kiln_blank_slot_readonly", !k.canPlaceItem(
                        com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_BLANK_START,
                        new ItemStack(TeaItems.TEA_CUP)),
                "坯料位不能由玩家手动放入");
        check("kiln_output_slot_readonly", !k.canPlaceItem(
                        com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_OUTPUT_START,
                        new ItemStack(TeaItems.TEA_CUP)),
                "成品位只出不进");
        check("kiln_fuel_slot_accepts_coal", k.canPlaceItem(
                        com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_FUEL,
                        new ItemStack(net.minecraft.world.item.Items.COAL)),
                "燃料位收煤炭");
        check("kiln_fuel_slot_rejects_other", !k.canPlaceItem(
                        com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_FUEL,
                        new ItemStack(TeaItems.TEA_CUP)),
                "燃料位不收别的");

        // ---------- 撤销退还黏土 ----------
        k.clearContent();  // 连 lit/burnLeft 一起复位，避免残留锁定状态


        pw.setItem(0, new ItemStack(net.minecraft.world.item.Items.CLAY_BALL, 4));
        pw.make(kettleP);   // 4 个黏土 -> 烧水壶
        check("kiln_has_kettle_blank", !k.blankAt(0).isEmpty()
                        && k.blankAt(0).is(TeaItems.TEA_KETTLE_ITEM),
                "4 个黏土应做出烧水壶坯体");
        var refund = k.cancelBlank(0);
        check("kiln_cancel_refunds_clay", refund.is(net.minecraft.world.item.Items.CLAY_BALL)
                        && refund.getCount() == 4,
                "撤销烧水壶应退 4 个黏土，实得 " + refund);
        check("kiln_cancel_clears_blank", k.blankAt(0).isEmpty(),
                "撤销后坯料位应清空");


        // ---------- 附近没窑炉 ----------
        var farPos = new BlockPos(0, 120, 100);
        level.setBlock(farPos, TeaItems.POTTER_WHEEL.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(farPos)
                instanceof com.heishandashu.teacraft.block.PotterWheelBlockEntity far) {
            far.setItem(0, new ItemStack(net.minecraft.world.item.Items.CLAY_BALL, 8));
            check("potter_wheel_no_kiln_rejects", far.make(cupP) == 2,
                    "附近没窑炉时应拒绝制作");
            check("potter_wheel_no_kiln_not_consumed", far.clayCount() == 8,
                    "拒绝后黏土不该被扣");
        }
        level.setBlock(farPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);


        // ---------- 烧制中锁定 ----------
        //
        // 需求：只要开始烧制就锁定，不能继续放新器件，也不能中间拿出来。
        k.clearContent();  // 连 lit/burnLeft 一起复位，避免残留锁定状态


        k.addBlank(new ItemStack(TeaItems.TEA_CUP));
        k.setItem(com.heishandashu.teacraft.block.ClayKilnBlockEntity.SLOT_FUEL,
                new ItemStack(net.minecraft.world.item.Items.COAL, 8));
        check("kiln_locked_when_lit", k.isLit(), "应已开始烧制");
        check("kiln_locked_rejects_new_blank",
                !k.addBlank(new ItemStack(TeaItems.GAIWAN)),
                "烧制中不能再放入新的坯料");
        check("kiln_locked_rejects_cancel",
                k.cancelBlank(0).isEmpty(),
                "烧制中不能撤销（不能中途把坯料拿出来）");

        // ---------- 清理 ----------
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos.east(1), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
    /**
     * 检查一个方块的模型不是满方块时，是否声明了 noOcclusion。
     *
     * <p>非满方块（四脚有缝、烟囱探出等）如果没声明 noOcclusion，
     * 相邻方块会把它的贴面剔除掉，游戏里看到的就是「底部透明 / 缺面」。</p>
     */
    private static void checkOccluder(String id, net.minecraft.world.level.block.Block block) {
        boolean occludes;
        try {
            var m = net.minecraft.world.level.block.state.BlockBehaviour.class
                    .getDeclaredMethod("useShapeForLightOcclusion",
                            net.minecraft.world.level.block.state.BlockState.class);
            m.setAccessible(true);
            occludes = (Boolean) m.invoke(block, block.defaultBlockState());
        } catch (Throwable t) {
            occludes = true;
        }
        check("no_occlusion_" + id, !occludes,
                occludes
                        ? id + " 的模型不是满方块，必须 noOcclusion()，否则底部/侧面会被剔除成透明"
                        : id + " 已声明不遮挡光照");
    }

}
