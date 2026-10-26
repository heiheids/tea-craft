package com.heishandashu.teacraft;

import com.heishandashu.teacraft.config.TeaConfig;
import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.item.TeaCreativeTab;
import com.heishandashu.teacraft.item.TeaItems;
import com.heishandashu.teacraft.menu.TeaMenus;
import com.heishandashu.teacraft.selfcheck.TeaSelfCheck;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 茶工艺 / Tea Craft —— 模组入口。
 *
 * <p>作者：bilibili 黑山大叔。</p>
 *
 * <p>本类只做「装配」，按依赖顺序把各模块装起来：</p>
 * <ol>
 *   <li>配置（后面注册与生长都要读）</li>
 *   <li>数据组件（物品要用到，必须先注册）</li>
 *   <li>物品 / 方块（静态初始化里完成注册）</li>
 *   <li>创造栏（要读物品清单，必须在物品之后）</li>
 *   <li>启动自检（只在专用服务端可见）</li>
 * </ol>
 */
public final class TeaCraft implements ModInitializer {

    public static final String MOD_ID = "teacraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 统一的命名空间工厂。
     *
     * <p>26.x 的工厂方法是 {@code Identifier.fromNamespaceAndPath}；
     * 1.21.10 及更早叫 {@code ResourceLocation.fromNamespaceAndPath}。
     * 全模组只走这一个方法，将来跨版本只需改这里（见 mod_context.md）。</p>
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[teacraft] 茶工艺 loading (Minecraft 26.3 / Fabric)");

        TeaConfig.get();           // 1. 配置（不存在就生成默认的）
        TeaComponents.register();  // 2. 数据组件
        // 3. 茶效果：必须早于物品注册（成品茶的 tooltip 要读品质→时长）
        com.heishandashu.teacraft.effect.TeaEffects.register();
        TeaItems.register();       // 4. 物品与方块
        // 4.1 陶瓷图样表：必须在物品注册之后（要拿物品实例）
        com.heishandashu.teacraft.core.CeramicPatterns.bootstrap();
        com.heishandashu.teacraft.block.KettleFillHandler.register(); // 4.5 烧水壶装水兜底事件
        com.heishandashu.teacraft.world.ZombieSpawnEquipment.register(); // 4.6 僵尸手持茶树苗
        TeaMenus.register();       // 5. 菜单（碳焙筐 / 炒茶锅界面）
        TeaCreativeTab.register(); // 6. 创造栏
        TeaSelfCheck.register();   // 7. 启动自检

        LOGGER.info("[teacraft] 就绪：{} 个物品", TeaItems.ALL_ITEMS.size());
    }
}
