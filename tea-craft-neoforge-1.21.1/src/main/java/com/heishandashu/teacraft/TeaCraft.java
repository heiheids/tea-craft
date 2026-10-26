package com.heishandashu.teacraft;

import com.heishandashu.teacraft.config.TeaConfig;
import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.effect.TeaEffects;
import com.heishandashu.teacraft.item.TeaCreativeTab;
import com.heishandashu.teacraft.item.TeaItems;
import com.heishandashu.teacraft.menu.TeaMenus;
import com.heishandashu.teacraft.selfcheck.TeaSelfCheck;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 茶工艺 / Tea Craft —— 模组入口（NeoForge 1.21.1）。
 *
 * <p>作者：bilibili 黑山大叔。</p>
 *
 * <p>本类只做「装配」，按依赖顺序把各模块装起来：</p>
 * <ol>
 *   <li>配置（后面注册与生长都要读）</li>
 *   <li>数据组件（物品要用到，必须先注册）</li>
 *   <li>茶效果（成品茶的 tooltip 要读品质）</li>
 *   <li>物品 / 方块（DeferredRegister 注册）</li>
 *   <li>创造栏（要读物品清单，必须在物品之后）</li>
 *   <li>启动自检（只在设了 TEACRAFT_SELFCHECK 时运行）</li>
 * </ol>
 */
@Mod(TeaCraft.MOD_ID)
public final class TeaCraft {

    public static final String MOD_ID = "teacraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 统一的命名空间工厂。
     *
     * <p>1.21.1 里叫 {@code ResourceLocation.fromNamespaceAndPath}；
     * 26.x 改名为 {@code ResourceLocation}。全模组只走这一处，
     * 将来跨版本只需要改这里。</p>
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public TeaCraft(IEventBus modBus, ModContainer container) {
        LOGGER.info("[teacraft] 茶工艺 loading (Minecraft 1.21.1 / NeoForge)");

        TeaConfig.get();                 // 1. 配置
        TeaComponents.register(modBus);  // 2. 数据组件
        TeaEffects.register();           // 3. 茶效果（建立 DeferredRegister 条目）
        TeaEffects.EFFECTS.register(modBus);  // 3.5 挂到事件总线
        TeaItems.register(modBus);       // 4. 物品与方块
        TeaMenus.register(modBus);       // 5. 菜单
        TeaCreativeTab.register(modBus); // 6. 创造栏

        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.heishandashu.teacraft.block.KettleFillHandler.register();
            com.heishandashu.teacraft.world.ZombieSpawnEquipment.register();
            TeaSelfCheck.register();
            LOGGER.info("[teacraft] 就绪：{} 个物品", TeaItems.ALL_ITEMS.size());
        });
    }
}
