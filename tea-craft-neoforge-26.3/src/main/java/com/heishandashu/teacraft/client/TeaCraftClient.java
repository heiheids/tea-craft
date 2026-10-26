package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.item.TeaItems;
import com.heishandashu.teacraft.menu.TeaMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端入口：HUD 面板 + 自定义界面的 Screen 绑定（26.3 NeoForge 版）。
 *
 * <h2>26.3(Fabric) → 本支 的入口差异</h2>
 * <table border="1">
 *   <tr><th>26.3 / Fabric</th><th>本支 / NeoForge</th></tr>
 *   <tr><td>{@code ClientModInitializer#onInitializeClient}</td>
 *       <td>{@code @Mod(value = MOD_ID, dist = Dist.CLIENT)} 的构造方法</td></tr>
 *   <tr><td>{@code HudElementRegistry.addLast(...)}</td>
 *       <td>{@code NeoForge.EVENT_BUS.addListener(XxxHud::onRenderGui)}（{@code RenderGuiEvent.Post}）</td></tr>
 *   <tr><td>{@code ClientTickEvents.END_CLIENT_TICK}</td>
 *       <td>{@code ClientTickEvent.Post}（游戏总线）</td></tr>
 *   <tr><td>{@code MenuScreensInvoker}（Mixin 打开私有 register）</td>
 *       <td>{@code RegisterMenuScreensEvent}（模组总线，公开事件）</td></tr>
 *   <tr><td>{@code EntityRendererRegistry.register(...)}</td>
 *       <td>{@code EntityRenderersEvent.RegisterRenderers}</td></tr>
 * </table>
 *
 * <p>注意：{@code MenuScreensInvoker} 这个 Invoker Mixin 已经删除 ——
 * NeoForge 提供了 {@link RegisterMenuScreensEvent}，不再需要反射去碰原版私有方法。</p>
 *
 * <p>所有注册都包在 try/catch 里：HUD 与界面只是附加内容，
 * 任何异常都不该让客户端起不来。</p>
 */
@Mod(value = TeaCraft.MOD_ID, dist = Dist.CLIENT)
public final class TeaCraftClient {

    /**
     * @param modBus    模组事件总线（{@link RegisterMenuScreensEvent} 这类「注册期」事件走它）
     * @param container 本模组的容器，暂时用不到，但 NeoForge 允许注入
     */
    public TeaCraftClient(IEventBus modBus, ModContainer container) {
        // 界面绑定必须在注册期完成，所以走模组总线
        modBus.addListener(TeaCraftClient::registerScreens);

        // 实体渲染器要等注册完成后再登记。
        // 构造函数里 DeferredHolder 还没绑定，直接 get() 会抛
        // Trying to access unbound value。
        modBus.addListener(TeaCraftClient::registerEntityRenderers);

        // HUD：只是附加信息，任何异常都不该让客户端起不来
        try {
            NeoForge.EVENT_BUS.addListener(TeaTrayHud::onRenderGui);
            NeoForge.EVENT_BUS.addListener(TeaSetHud::onRenderGui);
            TeaCraft.LOGGER.info("[teacraft] 客户端 HUD 已注册：晒盘/揉茶匾信息面板 + 盖碗/烧水壶内容提示");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 客户端 HUD 注册失败（不影响游戏启动）", t);
        }

        // 急迫等级很高时补挥手动画（原版会在「一击破」时漏掉挥手）
        try {
            NeoForge.EVENT_BUS.addListener(TeaCraftClient::onClientTick);
            TeaCraft.LOGGER.info("[teacraft] 客户端已注册：采矿挥手补帧");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 采矿挥手补帧注册失败", t);
        }
    }

    /** 座位实体的渲染器。它完全不可见，但缺渲染器会在渲染线程 NPE 崩溃。 */
    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        try {
            event.registerEntityRenderer(TeaItems.SEAT_TYPE.get(), EmptyEntityRenderer::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端已注册：茶椅座位渲染器（隐形）");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 座位渲染器注册失败（右键椅子会崩溃）", t);
        }
    }

    /**
     * 给碳焙筐 / 炒茶锅 / 窑炉 / 拉坯机 / 茶盒的 MenuType 绑上界面。
     */
    private static void registerScreens(RegisterMenuScreensEvent event) {
        register(event, "碳焙筐", () ->
                event.register(TeaMenus.CHARCOAL_STOVE.get(), TeaCharcoalStoveScreen::new));
        register(event, "炒茶锅", () ->
                event.register(TeaMenus.WOK.get(), TeaWokScreen::new));
        register(event, "窑炉", () ->
                event.register(TeaMenus.CLAY_KILN.get(), ClayKilnScreen::new));
        register(event, "拉坯机", () ->
                event.register(TeaMenus.POTTER_WHEEL.get(), ClayPotterWheelScreen::new));
        register(event, "茶盒", () ->
                event.register(TeaMenus.TEA_BOX.get(), TeaBoxScreen::new));

        // 《茶工艺说明》打开器 —— 物品类两端共用，不能直接引用客户端 Screen。
        // 这里把真正的开界面动作注入进去。
        register(event, "茶工艺说明打开器", () ->
                com.heishandashu.teacraft.item.TeaGuideItem.setOpener(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) mc.setScreenAndShow(new TeaGuideScreen());
                }));
    }

    /** 小工具：单个界面绑定失败不影响其它界面。 */
    private static void register(RegisterMenuScreensEvent event, String what, Runnable action) {
        try {
            action.run();
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：{}", what);
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] {} 界面绑定失败", what, t);
        }
    }

    /** 每个客户端 tick 结束时补一次挥手判定。 */
    private static void onClientTick(ClientTickEvent.Post event) {
        try {
            TeaMiningSwing.tick(Minecraft.getInstance());
        } catch (Throwable ignored) {
            // 挥手补帧失败最多是动画不好看，绝不能影响 tick
        }
    }
}
