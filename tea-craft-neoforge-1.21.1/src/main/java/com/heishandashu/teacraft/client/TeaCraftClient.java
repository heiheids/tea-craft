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
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端入口：HUD 面板 + 自定义界面的 Screen 绑定（NeoForge 1.21.1）。
 *
 * <h2>26.3(Fabric) → 1.21.1(NeoForge) 的入口差异</h2>
 * <table border="1">
 *   <tr><th>26.3 / Fabric</th><th>1.21.1 / NeoForge</th></tr>
 *   <tr><td>{@code ClientModInitializer#onInitializeClient}</td>
 *       <td>{@code @Mod(value = MOD_ID, dist = Dist.CLIENT)} 的构造方法</td></tr>
 *   <tr><td>{@code HudElementRegistry.addLast(...)}</td>
 *       <td>{@code NeoForge.EVENT_BUS.addListener(XxxHud::onRenderGui)}（{@code RenderGuiEvent.Post}）</td></tr>
 *   <tr><td>{@code ClientTickEvents.END_CLIENT_TICK}</td>
 *       <td>{@code ClientTickEvent.Post}（游戏总线）</td></tr>
 *   <tr><td>{@code MenuScreensInvoker}（Mixin 打开私有 register）</td>
 *       <td>{@code RegisterMenuScreensEvent}（模组总线，公开事件）</td></tr>
 * </table>
 *
 * <p><b>注意</b>：{@code MenuScreensInvoker} 这个 Invoker Mixin 已经<b>删除</b> ——
 * 1.21.1 的 NeoForge 提供了 {@link RegisterMenuScreensEvent}，
 * 不再需要反射/Invoker 去碰原版的私有方法。</p>
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

        // ★ 渲染层与物品模型谓词要等注册完成后再登记。
        //
        // 这里有坑：构造模组时 DeferredHolder 还没绑定，
        // 直接调 TeaItems.TEA_TRAY_ITEM.get() 会抛
        // "Trying to access unbound value"。
        // FMLClientSetupEvent 在所有 DeferredRegister 完成之后才发，
        // 是官方推荐的客户端登记时机。
        modBus.addListener(TeaCraftClient::clientSetup);

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

    /**
     * 客户端初始化（注册期结束后触发）。
     *
     * <p>渲染层与物品模型谓词都要读 {@code DeferredHolder.get()}，
     * 必须等到这个时机 —— 在模组构造里调用会抛
     * {@code Trying to access unbound value}。</p>
     */
    private static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        // 实体渲染器：座位实体虽然不可见，但渲染循环仍会查它的渲染器，
        // 查不到就是渲染线程 NPE 崩溃（【右键椅子崩溃】就是这个）。
        event.enqueueWork(() -> {
            try {
                net.minecraft.client.renderer.entity.EntityRenderers.register(
                        TeaItems.SEAT_TYPE.get(),
                        EmptyEntityRenderer::new);
                TeaCraft.LOGGER.info("[teacraft] 客户端已注册：茶椅座位渲染器（隐形）");
            } catch (Throwable t) {
                TeaCraft.LOGGER.error("[teacraft] 座位渲染器注册失败（右键椅子会崩溃）", t);
            }
        });

        // 方块渲染层：1.21.1 默认 SOLID，带镂空的贴图会变黑，必须显式登记
        event.enqueueWork(() -> {
            try {
                TeaRenderTypes.register();
            } catch (Throwable t) {
                TeaCraft.LOGGER.error("[teacraft] 渲染层登记失败（方块可能显示为黑块）", t);
            }
        });

        // 物品模型谓词：晒盘图标要跟着盘里的茶叶阶段变
        event.enqueueWork(() -> {
            try {
                TeaItemProperties.register();
            } catch (Throwable t) {
                TeaCraft.LOGGER.error("[teacraft] 物品模型谓词登记失败（晒盘图标不会变）", t);
            }
        });
    }

    /**
     * 给碳焙筐 / 炒茶锅的 MenuType 绑上界面。
     *
     * <p>1.21.1 的 {@code MenuScreens.register} 仍然是 private，
     * 但 NeoForge 已经在 {@code MenuScreens.init()} 里发出了
     * {@link RegisterMenuScreensEvent}，它的 {@code register} 就是那个私有方法的公开包装
     * （原版自己的静态块也是这么被调用起来的）。所以这里<b>不需要任何 Mixin</b>。</p>
     */
    private static void registerScreens(RegisterMenuScreensEvent event) {
        try {
            event.register(TeaMenus.CHARCOAL_STOVE.get(), TeaCharcoalStoveScreen::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：碳焙筐");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 碳焙筐界面绑定失败", t);
        }

        try {
            event.register(TeaMenus.WOK.get(), TeaWokScreen::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：炒茶锅");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 炒茶锅界面绑定失败", t);
        }


        try {
            event.register(TeaMenus.TEA_BOX.get(), TeaBoxScreen::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：茶盒");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 茶盒界面绑定失败", t);
        }
        // 《茶工艺说明》打开器 —— 物品类两端共用，不能直接引用客户端 Screen。
        try {
            com.heishandashu.teacraft.item.TeaGuideItem.setOpener(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) mc.setScreen(new TeaGuideScreen());
            });
            TeaCraft.LOGGER.info("[teacraft] 客户端已注册：茶工艺说明打开器");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 说明书打开器注册失败", t);
        }

        try {
            event.register(TeaMenus.CLAY_KILN.get(), ClayKilnScreen::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：窑炉");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 窑炉界面绑定失败", t);
        }

        try {
            event.register(TeaMenus.POTTER_WHEEL.get(), ClayPotterWheelScreen::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：拉坯机");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 拉坯机界面绑定失败", t);
        }
    }

    /**
     * 每个客户端 tick 结束时补一次挥手判定。
     *
     * <p>1.21.1 用 {@code ClientTickEvent.Post}；26.3 的 Fabric 是
     * {@code ClientTickEvents.END_CLIENT_TICK}。</p>
     */
    private static void onClientTick(ClientTickEvent.Post event) {
        try {
            TeaMiningSwing.tick(Minecraft.getInstance());
        } catch (Throwable ignored) {
            // 挥手补帧失败最多是动画不好看，绝不能影响 tick
        }
    }
}
