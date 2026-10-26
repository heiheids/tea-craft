package com.heishandashu.teacraft;

import com.heishandashu.teacraft.config.TeaConfig;
import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.item.TeaCreativeTab;
import com.heishandashu.teacraft.item.TeaItems;
import com.heishandashu.teacraft.menu.TeaMenus;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 茶工艺 / Tea Craft —— 模组入口（26.3 NeoForge 版）。
 *
 * <p>作者：bilibili 黑山大叔。</p>
 *
 * <h2>这支是怎么来的</h2>
 * <p>用户要求把模组也放到 NeoForge 26.3 的实例
 * （{@code D:\26.3F\.minecraft\versions\26.3-NeoForge086}）。
 * 另两支都不能直接用：26.3 Fabric 支的 jar 在 NeoForge 下不加载；
 * 1.21.1 NeoForge 支的 {@code neoforge.mods.toml} 写死
 * {@code minecraft = "[1.21.1,1.22)"}。</p>
 *
 * <p>做法是「以 26.3 Fabric 支为骨架」：MC 那一层的代码同一代、可以直接用，
 * 只把<b>加载器层</b>（本类、注册表、事件、网络、客户端钩子）换成 NeoForge 写法。</p>
 *
 * <h2>两条总线的区别（挂错不报错、只是不触发）</h2>
 * <ul>
 *   <li><b>mod 总线</b>：注册表（{@code DeferredRegister}）、{@code FMLCommonSetupEvent}、
 *       网络包注册 —— 本类构造参数里的 {@code modBus}。</li>
 *   <li><b>游戏总线</b>：tick / 玩家进服 / 死亡 / 世界启动等运行期事件 ——
 *       {@code NeoForge.EVENT_BUS.addListener(...)}。</li>
 * </ul>
 */
@Mod(TeaCraft.MOD_ID)
public final class TeaCraft {

    public static final String MOD_ID = "teacraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 统一的命名空间工厂。
     *
     * <p>26.x 的工厂方法是 {@code Identifier.fromNamespaceAndPath}，
     * 1.21.10 及更早叫 {@code ResourceLocation.fromNamespaceAndPath}。
     * 全模组只走这一个方法，将来跨版本只需改这里。</p>
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * 构造函数 —— 注册的唯一入口。
     *
     * <p>⚠ 顺序很重要，原因写在每一行旁边。特别注意：
     * 这里不能调任何 {@code .get()}。延迟注册在这一刻还没绑定，
     * 取值会抛 {@code NullPointerException: Trying to access unbound value}。</p>
     */
    public TeaCraft(IEventBus modBus, ModContainer container) {
        LOGGER.info("[teacraft] 茶工艺 loading (Minecraft 26.3 / NeoForge)");

        // 1. 配置（后面注册与生长都要读）
        TeaConfig.get();

        // 2. 数据组件：必须早于物品注册（物品要挂组件）
        TeaComponents.register(modBus);

        // 3. 茶效果：必须早于物品注册（成品茶的 tooltip 要读品质→时长）
        com.heishandashu.teacraft.effect.TeaEffects.register(modBus);

        // 4. 物品与方块（含方块实体、实体类型）
        TeaItems.register(modBus);

        // 5. 菜单（碳焙筐 / 炒茶锅 / 茶盒 界面）
        TeaMenus.register(modBus);

        // 6. 创造栏（内容在栏构建时才读 allItems()，那时已绑定）
        TeaCreativeTab.register(modBus);

        // 7. 注册完成后的清点 + 后续初始化（陶瓷图样等）
        modBus.addListener(this::commonSetup);

        // ---- 游戏总线：运行期行为 ----
        // 烧水壶装水兜底事件
        com.heishandashu.teacraft.block.KettleFillHandler.register();
        // 僵尸手持茶树苗
        com.heishandashu.teacraft.world.ZombieSpawnEquipment.register();
        // 启动自检
        com.heishandashu.teacraft.selfcheck.TeaSelfCheck.register();
    }

    /**
     * 注册已绑定后的初始化。
     *
     * <p>放到这里而不是构造函数里，是因为只有此事件触发时
     * {@code DeferredHolder.get()} 才安全。</p>
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        // 陶瓷图样表：要拿物品实例，必须等注册完成
        event.enqueueWork(() -> {
            com.heishandashu.teacraft.core.CeramicPatterns.bootstrap();
            TeaItems.logRegisteredContent();
        });
    }
}
