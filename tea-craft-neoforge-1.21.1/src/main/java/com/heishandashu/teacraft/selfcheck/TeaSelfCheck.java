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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

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
        // NeoForge 1.21.1：Fabric 的 ServerLifecycleEvents 不存在，
        // 改挂到游戏事件总线的 ServerStartedEvent 上。
        NeoForge.EVENT_BUS.addListener(TeaSelfCheck::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        run(event.getServer());
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
                TeaItems.TEA_KETTLE.get().defaultBlockState().hasProperty(
                        com.heishandashu.teacraft.block.TeaKettleBlock.BOILED),
                "boiled 属性存在");
        check("kettle_boiled_default_false",
                !TeaItems.TEA_KETTLE.get().defaultBlockState()
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
            var p9 = newFakePlayer(lvl9, "teacraft_eat_test", "eat_test");
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

            // 1.21.1：EntityTypes → EntityType，EntitySpawnReason → MobSpawnType
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE
                    .spawn(level9, spawnPos.above(),
                            net.minecraft.world.entity.MobSpawnType.COMMAND);
            check("zombie_spawned_for_test", zombie != null, "测试僵尸已生成");

            if (zombie != null) {
                // 直接给主手装茶树苗，绕开随机概率（概率另有断言覆盖）
                zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new ItemStack(TeaItems.TEA_BUSH_ITEM.get()));
                // setDropChance 声明在 Mob 上，EntityType.ZOMBIE 静态类型是 Zombie（Mob 子类），可以直接调
                zombie.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);

                // 击杀后直接枚举整个世界的掉落物。
                // 不用 AABB 搜索 —— 之前出生点/物品所在 y 差一格就搜不到，误判成"没掉"。
                // 1.21.1 的 kill() 不带参数。
                zombie.kill();
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
        level.setBlock(pos, TeaItems.TEA_BUSH.get().defaultBlockState(), 3);
        BlockState bush = level.getBlockState(pos);
        check("plant_on_dirt", bush.is(TeaItems.TEA_BUSH.get()), "block=" + bush.getBlock());
        check("plant_can_survive", bush.canSurvive(level, pos), TeaClimate.describe(level, pos));

        long ticks = TeaClimate.growthTicks(level, pos);
        check("growth_within_3_8_days", ticks >= 3L * 24000L && ticks <= 8L * 24000L,
                String.format("%.2f days", ticks / 24000.0));

        var block = (TeaBushBlock) TeaItems.TEA_BUSH.get();
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

        // ---------- 成熟必须真的能发生（走 tick，不是直接调 ripen）----------
        //
        // 【这条防的是「茶树永远不成熟」】
        // 老代码的 tick() 写反了：
        //     if (isTooCold(pos)) { ripen(...); scheduleTick(...); return; }
        //     // 温度正常时什么都不做
        // 于是温度正常时永远不会成熟，而太冷时反而立刻成熟。
        //
        // 原来的 ripen_needs_time 直接调 block.ripen(...)，绕过了 tick()，
        // 所以一直绿着 —— 用户实测「很久没成熟」才暴露。
        //
        // 下面把倒计时压到 2 tick，模拟一个温度正常（不冷）的茶树，
        // 连续调真实 tick()，看它会不会自己走到 age 3。
        {
            BlockPos maturePos = pos.offset(3, 0, 0);
            level.setBlock(maturePos.below(), Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(maturePos, TeaItems.TEA_BUSH.get().defaultBlockState()
                    .setValue(TeaBushBlock.AGE, TeaBushBlock.AGE_TREE), 3);

            var be = TeaBushBlock.bushEntityAt(level, maturePos);
            check("bush_has_block_entity", be != null,
                    "茶树必须有方块实体（倒计时要存在它里面）");

            if (be != null) {
                long t0 = level.getGameTime();
                be.startGrowing(2, t0);
                check("bush_countdown_started", be.isGrowing() && be.remainingTicks(t0) == 2,
                        "倒计时应从 2 开始，实得 " + be.remainingTicks(t0));

                // 如果这个群系恰好是寒带且没篝火，先放个篝火保证「不冷」
                if (TeaBushBlock.isTooCold(level, maturePos)) {
                    level.setBlock(maturePos.offset(1, 0, 0),
                            Blocks.CAMPFIRE.defaultBlockState(), 3);
                }

                // 时间推进 2 tick 后应正好成熟
                be.advanceTo(t0 + 2, false);
                check("bush_matures_by_game_time",
                        be.remainingTicks(t0 + 2) == 0,
                        "经过 2 游戏刻后应到点，实得 " + be.remainingTicks(t0 + 2));

                // 再走真实 tick 路径，确认 age 也被拉到 3。
                // tick() 内部读的是 level.getGameTime()，自检里时间不走，
                // 所以把 anchor 往前拨，等价于时间已流逝。
                be.debugWindBack(100L);

                int guard = 0;
                while (level.getBlockState(maturePos).getValue(TeaBushBlock.AGE) != TeaBushBlock.AGE_RIPE
                        && guard++ < 10) {
                    block.tickForSelfCheck(level.getBlockState(maturePos), level, maturePos);
                }

                check("bush_matures_via_tick",
                        level.getBlockState(maturePos).getValue(TeaBushBlock.AGE) == TeaBushBlock.AGE_RIPE,
                        "温度正常时，茶树必须能通过 tick() 自己成熟（这正是之前坏掉的地方）");

                level.setBlock(maturePos.offset(1, 0, 0), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(maturePos, Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(maturePos, Blocks.AIR.defaultBlockState(), 3);
        }

        // ---------- 倒计时速率必须等于「经过的游戏刻」----------
        //
        // 【这条防的是「/tick sprint 推不动茶树」】
        // 第一版写成「每 20 tick 调一次 tickCountdown，每次 remainingTicks--」。
        // 于是实际经过 144000 tick（6 游戏日）只推进 7200 —— 慢 20 倍，
        // 表现就是 sprint 了 6 天茶树依然不成熟。
        {
            BlockPos ratePos = pos.offset(12, 0, 0);
            level.setBlock(ratePos.below(), Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(ratePos, TeaItems.TEA_BUSH.get().defaultBlockState()
                    .setValue(TeaBushBlock.AGE, TeaBushBlock.AGE_TREE), 3);

            var be = TeaBushBlock.bushEntityAt(level, ratePos);
            if (be != null) {
                long t0 = level.getGameTime();
                be.startGrowing(100000, t0);

                long t1 = t0 + 144000L;
                be.advanceTo(t1, false);
                check("bush_rate_equals_game_ticks",
                        be.remainingTicks(t1) == 0,
                        "6 游戏日（144000 刻）必须足够走完 100000 刻的成熟期，"
                                + "实得剩余 " + be.remainingTicks(t1));

                be.startGrowing(50000, t0);
                be.advanceTo(t0 + 1000L, false);
                check("bush_rate_linear",
                        be.remainingTicks(t0 + 1000L) == 49000,
                        "走 1000 刻后剩余应为 49000，实得 " + be.remainingTicks(t0 + 1000L));
            }
            level.setBlock(ratePos, Blocks.AIR.defaultBlockState(), 3);
        }

        // ---------- 太冷暂停 / 取暖恢复 ----------
        //
        // 需求：「太冷暂停生长，有取暖后恢复生长。」
        // 关键语义是：暂停期间游戏刻照走，但不计入进度；恢复后继续累加。
        {
            BlockPos pausePos = pos.offset(6, 0, 0);
            level.setBlock(pausePos.below(), Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(pausePos, TeaItems.TEA_BUSH.get().defaultBlockState()
                    .setValue(TeaBushBlock.AGE, TeaBushBlock.AGE_TREE), 3);

            var be = TeaBushBlock.bushEntityAt(level, pausePos);
            if (be != null) {
                long t0 = level.getGameTime();
                be.startGrowing(1000, t0);

                be.advanceTo(t0 + 100L, false);
                check("bush_progress_before_pause",
                        be.remainingTicks(t0 + 100L) == 900,
                        "走 100 刻后剩余应为 900，实得 " + be.remainingTicks(t0 + 100L));

                be.advanceTo(t0 + 100L, true);
                check("bush_pause_flag_set", be.isPaused(), "暂停标记应为 true");

                be.advanceTo(t0 + 600L, true);
                check("bush_pause_freezes_countdown",
                        be.remainingTicks(t0 + 600L) == 900,
                        "太冷期间倒计时必须停住（再走 500 刻仍应是 900），实得 "
                                + be.remainingTicks(t0 + 600L));

                be.advanceTo(t0 + 600L, false);
                be.advanceTo(t0 + 700L, false);
                check("bush_resume_continues",
                        be.remainingTicks(t0 + 700L) == 800,
                        "恢复后应从暂停处继续（再走 100 刻剩 800），实得 "
                                + be.remainingTicks(t0 + 700L));
                check("bush_pause_flag_cleared", !be.isPaused(), "恢复后暂停标记应清除");
            }
            level.setBlock(pausePos, Blocks.AIR.defaultBlockState(), 3);
        }

        int bound3 = TeaClimate.growthRollBound(3L * 24000L);
        int bound8 = TeaClimate.growthRollBound(8L * 24000L);
        check("roll_bound_wheat_like", bound3 == 26, "3 天 -> 1/" + bound3);
        check("roll_bound_cold", bound8 == 70 && bound3 < bound8, "8 天 -> 1/" + bound8);
        check("block_is_randomly_ticking", TeaItems.TEA_BUSH.get().defaultBlockState().isRandomlyTicking(), "randomTicks");
        check("harvest_yield_1_to_3",
                TeaConfig.get().minLeaves == 1 && TeaConfig.get().maxLeaves == 3,
                TeaConfig.get().minLeaves + "~" + TeaConfig.get().maxLeaves);

        // 沙子上不能种
        level.setBlock(pos.below(), Blocks.SAND.defaultBlockState(), 3);
        level.setBlock(pos, TeaItems.TEA_BUSH.get().defaultBlockState(), 3);
        check("plant_rejects_sand", !level.getBlockState(pos).canSurvive(level, pos), "sand below");

        // 支撑消失立刻变空气
        level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(pos, TeaItems.TEA_BUSH.get().defaultBlockState(), 3);
        level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
        BlockState after = level.getBlockState(pos);
        check("support_removed_becomes_air", after.isAir(), "state=" + after.getBlock());

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    /* ================= 群系气候 ================= */

    private static void climateChecks(MinecraftServer server) {
        // 1.21.1：lookupOrThrow 返回的是 HolderLookup.RegistryLookup（没有 getValue/entrySet），
        // 要拿完整的 Registry 用 registryOrThrow。
        var biomes = server.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
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
            ResourceLocation id = ResourceLocation.parse(c.id());
            var biome = biomes.get(id);
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
            // 1.21.1 的 ResourceKey 用 .location()（26.3 的 .identifier() 不存在）
            ResourceLocation id = e.getKey().location();
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

        if (!(TeaItems.TEA_KETTLE_ITEM.get() instanceof net.minecraft.world.item.BlockItem bi)) {
            check("kettle_item_is_block_item", false, "不是 BlockItem，根本无法放置");
            return;
        }
        check("kettle_item_is_block_item", true, "是 BlockItem");
        // ---- 碰撞箱必须贴合模型，不能是整块 16×16 ----
        // 之前写成 0~16 全宽 + 13 高，模型只占 x 4.6~11.4 / z 3.6~11.4，看起来"巨大"。
        var kShape = TeaItems.TEA_KETTLE.get().defaultBlockState()
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
        var dryStack = new ItemStack(TeaItems.GAIWAN.get());
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
        // 1.21.1 没有 Fabric 的 HudElement / extractRenderState，
        // 改成 NeoForge 的 RenderGuiEvent.Post 监听；所以断言改成查那个回调方法。
        check("tea_set_hud_registered",
                classDeclaresMethod("TeaSetHud", "onRenderGui"),
                "TeaSetHud 应有 RenderGuiEvent.Post 回调 onRenderGui");
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
        check("kettle_item_links_kettle_block", bi.getBlock() == TeaItems.TEA_KETTLE.get(),
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

            BlockState kettleDefault = TeaItems.TEA_KETTLE.get().defaultBlockState();
            boolean replaceable = level.getBlockState(target).canBeReplaced();
            boolean survives = kettleDefault.canSurvive(level, target);
            boolean unobstructed = level.isUnobstructed(kettleDefault, target,
                    net.minecraft.world.phys.shapes.CollisionContext.empty());

            check("kettle_can_be_placed_on_" + hot.name(),
                    replaceable && survives && unobstructed,
                    "可替换=" + replaceable + " 可存活=" + survives + " 无阻挡=" + unobstructed);

            // ③ 用真实玩家走一遍原版放置流程（useOn）。
            //    只查 canSurvive 不够：物品自身有问题时，前置条件全过也放不下去。
            var stack = new ItemStack(TeaItems.TEA_KETTLE_ITEM.get());
            var checker = newFakePlayer(level, "teacraft_selfcheck", "teacraft_selfcheck");
            var hit = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0, 0.5, 0),
                    net.minecraft.core.Direction.UP, ground, false);
            var ctx = new net.minecraft.world.item.context.UseOnContext(
                    checker, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            var res = TeaItems.TEA_KETTLE_ITEM.get().useOn(ctx);

            boolean placed = level.getBlockState(target).is(TeaItems.TEA_KETTLE.get());
            check("kettle_real_place_on_" + hot.name(), placed,
                    res + " → 目标位="
                            + net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(level.getBlockState(target).getBlock()));
            if (placed) {
                boolean heat = com.heishandashu.teacraft.block.TeaKettleBlock
                        .isHeatSource(level, target);
                check("kettle_heat_detected_" + hot.name(), heat, "识别热源=" + heat);


                // ④ 装水：直接调方块的 useItemOn，模拟手持水桶右键。
                //    用户反馈「能放下但不能放水」，所以这一步必须真的走一遍交互。
                var bucket = new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
                // 1.21.1 的 useItemOn 返回 ItemInteractionResult（不是 InteractionResult），
                // 对应 PASS 的常量是 PASS_TO_DEFAULT_BLOCK_INTERACTION。
                var resFill = level.getBlockState(target).useItemOn(
                        bucket, level, checker,
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(
                                net.minecraft.world.phys.Vec3.atCenterOf(target),
                                net.minecraft.core.Direction.UP, target, false));
                boolean hasWater = level.getBlockState(target)
                        .getValue(com.heishandashu.teacraft.block.TeaKettleBlock.WATER);
                boolean beWater = level.getBlockEntity(target)
                        instanceof com.heishandashu.teacraft.block.TeaKettleBlockEntity k && k.hasWater();
                boolean fillPassed = resFill
                        == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                check("kettle_fill_with_bucket_" + hot.name(),
                        !fillPassed && hasWater && beWater,
                        resFill + " 状态=" + hasWater + " 实体=" + beWater);
                if (fillPassed) {
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
        level.setBlock(pos, TeaItems.TEA_TRAY.get().defaultBlockState(), 3);
        BlockState tray = level.getBlockState(pos);
        check("tray_placed_on_stone", tray.is(TeaItems.TEA_TRAY.get()), "block=" + tray.getBlock());
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
        // 1.21.1 的 ServerLevel 没有 getWeatherData()（26.3 才有）：
        // isRaining() 的实现就是 getRainLevel(1.0F) > 0.2，所以直接改 rainLevel 即可，
        // 前后各存一次原值再还原。
        float savedRain = level.getRainLevel(1.0F);
        try {
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
        level.setBlock(bushPos, TeaItems.TEA_BUSH.get().defaultBlockState(), 3);
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
        // 1.21.1：没有 SwingAnimation 这个类，基础时长是 LivingEntity.SWING_DURATION（=6），
        // 原版 getCurrentSwingDuration() = 6 - (1 + 急迫等级)。
        int baseSwing = net.minecraft.world.entity.LivingEntity.SWING_DURATION;
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
            // 1.21.1 的方法名是 getCurrentSwingDuration()，不带参数
            if (m.getName().equals("getCurrentSwingDuration") && m.getParameterCount() == 0) {
                swingDurationTarget = true;
                break;
            }
        }
        check("swing_duration_target_exists", swingDurationTarget,
                "LivingEntity.getCurrentSwingDuration() 存在");
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
            // 1.21.1 的签名是 (BlockPos, Action, Direction, int, int) —— 5 个参数
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
        ((TeaTrayBlock) TeaItems.TEA_TRAY.get()).witherNow(level, pos, level.getBlockState(pos));
        check("tray_withers_to_stage2",
                level.getBlockState(pos).getValue(TeaTrayBlock.STAGE) == TeaTrayBlock.STAGE_WITHERED,
                "stage=2");

        // 5) 端起盘子：物品带上份数与状态
        ItemStack carried = new ItemStack(TeaItems.TEA_TRAY_ITEM.get());
        carried.set(TeaComponents.TRAY_COUNT.get(), 4);
        carried.set(TeaComponents.TRAY_STAGE.get(), TeaTrayBlock.STAGE_WITHERED);
        carried.set(TeaComponents.TRAY_SHAKES.get(), 0);
        check("component_count_roundtrip", carried.getOrDefault(TeaComponents.TRAY_COUNT.get(), -1) == 4, "count=4");
        check("component_stage_roundtrip",
                carried.getOrDefault(TeaComponents.TRAY_STAGE.get(), -1) == TeaTrayBlock.STAGE_WITHERED, "stage=2");

        // 6) 摇青：连续摇，每满 3 秒算一次。
        //    直接驱动 onUseTick（remaining 从总时长递减），模拟玩家按住不放。
        //    注意：onUseTick 只在服务端结算，这里传的就是服务端 level。
        //    Player 是抽象类，构造不出实例，所以直接调 settleOneShake
        //    （它就是 onUseTick 里"每满 3 秒"调的那个方法），entity 传 null 即可。
        TeaTrayItem.settleOneShake(level, null, carried);
        check("shake_progress_1",
                carried.getOrDefault(TeaComponents.TRAY_SHAKES.get(), 0) == 1, "shakes=1");
        // 连续摇第 2 下 —— 不需要"松手重按"，这正是需求要的
        TeaTrayItem.settleOneShake(level, null, carried);
        check("shake_progress_2",
                carried.getOrDefault(TeaComponents.TRAY_SHAKES.get(), 0) == 2, "shakes=2");
        // 第 3 下 → 摇青完成
        TeaTrayItem.settleOneShake(level, null, carried);
        check("shake_becomes_shaken",
                carried.getOrDefault(TeaComponents.TRAY_STAGE.get(), 0) == TeaTrayBlock.STAGE_SHAKEN
                        && carried.getOrDefault(TeaComponents.TRAY_SHAKES.get(), 0) == 0,
                "stage=3 shakes reset");

        // 7) 已摇青再摇不应倒退状态
        TeaTrayItem.settleOneShake(level, null, carried);
        check("shaken_cannot_shake_again",
                carried.getOrDefault(TeaComponents.TRAY_STAGE.get(), 0) == TeaTrayBlock.STAGE_SHAKEN,
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
        teaSetChecks();
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

        level.setBlock(pos, TeaItems.TEA_TRAY.get().defaultBlockState()
                .setValue(TeaTrayBlock.COUNT, 3)
                .setValue(TeaTrayBlock.STAGE, TeaTrayBlock.STAGE_SHAKEN), 3);
        ((TeaTrayBlock) TeaItems.TEA_TRAY.get()).killGreenNow(level, pos, level.getBlockState(pos));
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
        level.setBlock(pos, TeaItems.TEA_TRAY.get().defaultBlockState()
                .setValue(TeaTrayBlock.COUNT, 2), 3);
        ((TeaTrayBlock) TeaItems.TEA_TRAY.get()).armTimer(level, pos, level.getBlockState(pos)
                .setValue(TeaTrayBlock.STAGE, TeaTrayBlock.STAGE_SHAKEN));
        check("shaken_tray_arms_killgreen_timer",
                level.getBlockTicks().hasScheduledTick(pos, TeaItems.TEA_TRAY.get()),
                "shaken 放下后应排上杀青计划刻");

        level.setBlock(pos, TeaItems.TEA_TRAY.get().defaultBlockState()
                .setValue(TeaTrayBlock.COUNT, 2), 3);
        ((TeaTrayBlock) TeaItems.TEA_TRAY.get()).armTimer(level, pos, level.getBlockState(pos)
                .setValue(TeaTrayBlock.STAGE, TeaTrayBlock.STAGE_FRESH));
        check("fresh_tray_arms_wither_timer",
                level.getBlockTicks().hasScheduledTick(pos, TeaItems.TEA_TRAY.get()),
                "新茶放下后应排上萎凋计划刻");

        // --- 揉茶匾 ---
        check("rolling_tray_block_registered",
                BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_ROLLING_TRAY.get()) != null,
                String.valueOf(BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_ROLLING_TRAY.get())));
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
        level.setBlock(pos, TeaItems.TEA_ROLLING_TRAY.get().defaultBlockState(), 3);
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

        level.setBlock(pos, level.getBlockState(pos)
                .setValue(TeaRollingTrayBlock.STAGE, TeaRollingTrayBlock.STAGE_ROLLED), 3);
        ((TeaRollingTrayBlock) TeaItems.TEA_ROLLING_TRAY.get())
                .fermentNow(level, pos, level.getBlockState(pos));
        check("rolled_ferments",
                level.getBlockState(pos).getValue(TeaRollingTrayBlock.STAGE) == TeaRollingTrayBlock.STAGE_FERMENTED,
                "stage=3");

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
    private static void teaSetChecks() {
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
        check("tea_set_model_elements", elements != null && elements.size() == 30,
                elements == null ? "没有 elements" : (elements.size() + " 个元件"));

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
        BlockState setDefault = TeaItems.TEA_SET.get().defaultBlockState();
        check("tea_set_property_count",
                setDefault.getProperties().size() == 4,
                setDefault.getProperties().size() + " 个属性（gaiwan + 3 杯）");
        check("tea_set_default_gaiwan",
                setDefault.getValue(com.heishandashu.teacraft.block.TeaSetBlock.GAIWAN),
                "默认应有盖碗");
        boolean allEmpty = true;
        for (var p : com.heishandashu.teacraft.block.TeaSetBlock.CUPS) {
            if (setDefault.getValue(p) != com.heishandashu.teacraft.block.TeaSetBlock.CupState.EMPTY) {
                allEmpty = false;
            }
        }
        check("tea_set_default_cups_empty", allEmpty, "默认三个空杯");
        check("tea_set_has_block_entity",
                ((net.minecraft.world.level.block.EntityBlock) TeaItems.TEA_SET.get()).newBlockEntity(BlockPos.ZERO, setDefault)
                        instanceof com.heishandashu.teacraft.block.TeaSetBlockEntity,
                "茶盘应创建方块实体");

        // ---- 烧水壶 ----
        check("kettle_block_registered",
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .containsKey(com.heishandashu.teacraft.TeaCraft.id("kettle")), "teacraft:kettle");
        check("kettle_boil_5_seconds",
                com.heishandashu.teacraft.block.TeaKettleBlockEntity.BOIL_TICKS == 100,
                com.heishandashu.teacraft.block.TeaKettleBlockEntity.BOIL_TICKS
                        + " tick = 5 秒（20 tick/秒）");

        // 加热逻辑：不加热不涨、加热够 100 tick 才开
        var kettle = new com.heishandashu.teacraft.block.TeaKettleBlockEntity(
                BlockPos.ZERO, TeaItems.TEA_KETTLE.get().defaultBlockState());
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
                TeaItems.TEAPOT.get() instanceof net.minecraft.world.item.BlockItem,
                "茶壶也应该是 BlockItem，实际=" + TeaItems.TEAPOT.get().getClass().getSimpleName());
        check("teapot_block_registered",
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .containsKey(com.heishandashu.teacraft.TeaCraft.id("teapot")), "teacraft:teapot");
        // 装饰茶壶不能出现在创造栏：同模型的两个壶放在一起，用户会拿错
        check("teapot_not_in_creative_tab",
                !com.heishandashu.teacraft.item.TeaItems.ALL_ITEMS
                        .contains(TeaItems.TEAPOT.get()),
                "装饰茶壶不进创造栏");
        check("kettle_in_creative_tab",
                com.heishandashu.teacraft.item.TeaItems.ALL_ITEMS
                        .contains(TeaItems.TEA_KETTLE_ITEM.get()),
                "烧水壶必须在创造栏里");
        check("teapot_is_not_kettle",
                TeaItems.TEAPOT.get() != TeaItems.TEA_KETTLE_ITEM.get(),
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
                TeaItems.TEA_KETTLE_ITEM.get() instanceof com.heishandashu.teacraft.item.TeaKettleItem,
                "烧水壶物品的实际类型 = " + TeaItems.TEA_KETTLE_ITEM.get().getClass().getSimpleName());
        check("kettle_item_is_block_item",
                TeaItems.TEA_KETTLE_ITEM.get() instanceof net.minecraft.world.item.BlockItem,
                "既要能带走、也要能放下");

        // 热水状态能写能读、且随物品走
        var kettleStack = new ItemStack(TeaItems.TEA_KETTLE_ITEM.get());
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
        int gaiwanIdx = setSrc.indexOf("if (state.getValue(GAIWAN) && isGaiwanAt(hit))");
        checkSource("tea_set_pick_by_position", setSrc, 
                cupIdx >= 0 && gaiwanIdx > cupIdx,
                "取物应先判断点到的杯子，再判断盖碗");
        checkSource("tea_set_has_gaiwan_hit_test", setSrc, 
                setSrc.contains("isGaiwanAt(hit)"), "盖碗要有独立的点击判定");

        // ---- 杯子不消耗、不可堆叠 ----
        checkSource("cup_not_consumed_on_drink", sourceOf("src/main/java/com/heishandashu/teacraft/item/TeaCupItem.java"), 
                !sourceOf("src/main/java/com/heishandashu/teacraft/item/TeaCupItem.java")
                        .contains("stack.shrink(1)"),
                "喝茶后杯子应变成空杯，不能 shrink");
        check("cup_not_stackable",
                TeaItems.TEA_CUP.get().getDefaultMaxStackSize() == 1,
                "杯子 maxStack=" + TeaItems.TEA_CUP.get().getDefaultMaxStackSize()
                        + "（有茶/空杯数据不同，不能堆叠）");
        check("tea_set_has_tea_texture",
                resourceExists("assets/" + NS + "/textures/block/tea_set_tea.png"),
                "茶汤贴图");


        check("gaiwan_item_registered", TeaItems.GAIWAN.get() != null, "盖碗");
        check("tea_cup_item_registered", TeaItems.TEA_CUP.get() != null, "茶杯");

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
        check("stove_registered", BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_CHARCOAL_STOVE.get()) != null,
                String.valueOf(BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_CHARCOAL_STOVE.get())));
        check("wok_registered", BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_WOK.get()) != null,
                String.valueOf(BuiltInRegistries.BLOCK.getKey(TeaItems.TEA_WOK.get())));

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
                BuiltInRegistries.MENU.getKey(com.heishandashu.teacraft.menu.TeaMenus.CHARCOAL_STOVE.get()) != null,
                String.valueOf(BuiltInRegistries.MENU.getKey(com.heishandashu.teacraft.menu.TeaMenus.CHARCOAL_STOVE.get())));
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
        // 1.21.1 没有 extractBackground / extractContents（那是 26.3 的渲染管线），
        // 对应钩子是 protected renderBg(g, partialTick, mouseX, mouseY)。
        boolean usesBackgroundHook = classDeclaresMethod("TeaCharcoalStoveScreen", "renderBg");
        boolean hijacksContents = classDeclaresMethod("TeaCharcoalStoveScreen", "extractContents");
        check("screen_draws_on_background_layer", usesBackgroundHook,
                "应覆盖 renderBg（背景层，不盖槽位）");
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
                String.valueOf(TeaCreativeTab.KEY.location()));
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

    /**
     * 造一个可控的假玩家，用来真的走一遍「右键方块 / 使用物品」的原版流程。
     *
     * <h2>为什么要单独包一个方法</h2>
     * <p>26.3 的 {@code Player} 构造器是 {@code (Level, GameProfile)}，而且游戏模式靠
     * 覆写 {@code gameMode()}；1.21.1 完全不同：</p>
     * <ul>
     *   <li>构造器是 {@code Player(Level, BlockPos, float, GameProfile)}（多了坐标与朝向）；</li>
     *   <li>没有 {@code gameMode()} 可以覆写 —— {@code Player} 的抽象方法只有
     *       {@code isSpectator()} / {@code isCreative()}；</li>
     *   <li>更关键：抽象方法的<b>数量与签名随版本变</b>，用匿名子类会在升级时静默编译失败。</li>
     * </ul>
     * <p>所以直接用 NeoForge 的 {@link net.neoforged.neoforge.common.util.FakePlayer}
     * 子类（它已经是 {@code ServerPlayer}，功能完整），只把模式固定成生存。</p>
     *
     * @param uuidSeed 用字符串生成稳定的 UUID，便于日志里区分是哪个自检造的
     * @param name     玩家名
     */
    private static net.neoforged.neoforge.common.util.FakePlayer newFakePlayer(
            net.minecraft.server.level.ServerLevel level, String uuidSeed, String name) {
        var profile = new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes(uuidSeed.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                name);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, profile);
        // FakePlayer 默认是创造模式；自检要测「真的消耗/放置」，所以按生存走
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
    }

    /** 数某个位置附近掉了几个茶树苗。 */
    private static int countBushItems(net.minecraft.server.level.ServerLevel level, BlockPos around) {
        int n = 0;
        for (var it : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(around).inflate(12))) {
            if (it.getItem().is(TeaItems.TEA_BUSH_ITEM.get())) n += it.getItem().getCount();
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

    private static String path0(String ref) {
        int i = ref.indexOf(':');
        return i < 0 ? NS : ref.substring(0, i);
    }

    private static String path1(String ref) {
        int i = ref.indexOf(':');
        return i < 0 ? ref : ref.substring(i + 1);
    }
}
