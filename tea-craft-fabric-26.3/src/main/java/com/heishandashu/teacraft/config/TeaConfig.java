package com.heishandashu.teacraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.heishandashu.teacraft.TeaCraft;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 茶工艺的配置文件（{@code config/teacraft.json}）。
 *
 * <p>需求里所有「数值」都放在这里，而不是写死在代码里：</p>
 * <ul>
 *   <li>最热群系成熟天数（默认 3 个游戏日）、最冷群系成熟天数（默认 8 个游戏日）</li>
 *   <li>「最热 / 最冷」以哪个温度为基准</li>
 *   <li>禁止种植的群系 id（恶地另有一条原版标签规则，见 {@code TeaClimate}）</li>
 *   <li>每次采摘掉落数量范围</li>
 * </ul>
 *
 * <p><b>关于 {@code hotTemperature} 默认值 2.0 的由来（实测）：</b>
 * 26.3 里热带草原（savanna）的基准温度是 <b>2.0</b>，与沙漠、恶地相同；
 * 次热的是丛林 0.95。既然沙漠/恶地被排除，剩下最热的就是热带草原，
 * 所以「最热群系 = 3 个游戏日」这个基准锚在 <b>2.0</b>。
 * 这个锚点由启动自检 {@code hottest_anchor_matches_registry} 自动核对 ——
 * 将来原版改温度，自检会立刻报出来。</p>
 */
public final class TeaConfig {

    /** 成熟时间：最热群系 / 最冷群系各要多少个游戏日（1 个游戏日 = 24000 tick）。 */
    public float hotDays = 3.0f;
    public float coldDays = 8.0f;

    /**
     * 「最热 / 最冷」的基准温度。
     * 2.0 = 26.3 的热带草原（最热的<b>可种植</b>群系）；-0.5 = 积雪山坡/冻洋。
     */
    public float hotTemperature = 2.0f;
    public float coldTemperature = -0.5f;

    /** 每次采摘掉落的最少 / 最多茶叶数。 */
    public int minLeaves = 1;
    public int maxLeaves = 3;

    /**
     * 禁止种植的群系 id 列表。
     *
     * <p>恶地三个变体由原版标签 {@code #minecraft:is_badlands} 自动挡住，
     * 这里列出来只是「白纸黑字的意图」，顺便让整合包作者有个显式开关。
     * 沙漠没有对应标签，只能靠这份名单。</p>
     */
    public List<String> bannedBiomes = new ArrayList<>(List.of(
            "minecraft:desert",
            "minecraft:desert_lakes",
            "minecraft:badlands",
            "minecraft:eroded_badlands",
            "minecraft:wooded_badlands"
    ));

    /** 一个游戏日的 tick 数。 */
    public static final int TICKS_PER_DAY = 24000;

    private static TeaConfig instance;

    public static TeaConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** 重新读盘（自检/调试用）。 */
    public static void reload() {
        instance = load();
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("teacraft.json");
    }

    private static TeaConfig load() {
        Path p = path();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!Files.exists(p)) {
            TeaConfig def = new TeaConfig();
            def.save(p, gson);
            TeaCraft.LOGGER.info("[teacraft] 已生成默认配置 {}", p);
            return def;
        }
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            TeaConfig cfg = gson.fromJson(r, TeaConfig.class);
            if (cfg == null) cfg = new TeaConfig();
            cfg.sanitize();
            return cfg;
        } catch (Exception e) {
            TeaCraft.LOGGER.error("[teacraft] 配置读取失败，改用默认值: {}", e.toString());
            return new TeaConfig();
        }
    }

    private void save(Path p, Gson gson) {
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                gson.toJson(this, w);
            }
        } catch (IOException e) {
            TeaCraft.LOGGER.error("[teacraft] 配置写入失败: {}", e.toString());
        }
    }

    /** 把明显不合理的值拉回可用范围，避免一份手改坏的配置让茶永远长不熟。 */
    private void sanitize() {
        if (hotDays <= 0.05f) hotDays = 0.05f;
        if (coldDays < hotDays) coldDays = hotDays;
        if (hotTemperature <= coldTemperature) {
            hotTemperature = 2.0f;
            coldTemperature = -0.5f;
        }
        if (minLeaves < 1) minLeaves = 1;
        if (maxLeaves < minLeaves) maxLeaves = minLeaves;
        if (bannedBiomes == null) bannedBiomes = new ArrayList<>();
    }

    /** 成熟需要的 tick 数：在 hotDays 与 coldDays 之间按温度线性插值。 */
    public long growthTicks(float biomeTemperature) {
        float span = hotTemperature - coldTemperature;
        float frac = span <= 0.0001f ? 1.0f : (hotTemperature - biomeTemperature) / span;
        if (frac < 0.0f) frac = 0.0f;
        if (frac > 1.0f) frac = 1.0f;
        float days = hotDays + (coldDays - hotDays) * frac;
        return Math.max(1L, Math.round(days * TICKS_PER_DAY));
    }
}
