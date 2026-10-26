package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.menu.TeaMenus;
import com.heishandashu.teacraft.mixin.MenuScreensInvoker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/**
 * 客户端入口：HUD 面板 + 自定义界面的 Screen 绑定。
 */
public final class TeaCraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 实体渲染器：座位实体虽然不可见，但渲染循环仍会查它的渲染器，
        // 查不到就是渲染线程 NPE 崩溃（【右键椅子崩溃】就是这个）。
        try {
            net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                    com.heishandashu.teacraft.item.TeaItems.SEAT_TYPE,
                    EmptyEntityRenderer::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端已注册：茶椅座位渲染器（隐形）");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 座位渲染器注册失败（右键椅子会崩溃）", t);
        }

        // HUD 只是附加信息，任何异常都不该让客户端起不来
        try {
            HudElementRegistry.addLast(TeaCraft.id("tray_hud"), new TeaTrayHud());
            HudElementRegistry.addLast(TeaCraft.id("tea_set_hud"), new TeaSetHud());
            TeaCraft.LOGGER.info("[teacraft] 客户端 HUD 已注册：晒盘/揉茶匾信息面板 + 盖碗/烧水壶内容提示");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 客户端 HUD 注册失败（不影响游戏启动）", t);
        }

        // 急迫等级很高时补挥手动画（原版会在"一击破"时漏掉挥手）
        try {
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
                    .END_CLIENT_TICK.register(TeaMiningSwing::tick);
            TeaCraft.LOGGER.info("[teacraft] 客户端已注册：采矿挥手补帧");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 采矿挥手补帧注册失败", t);
        }

        //《茶工艺说明》打开器 —— 物品类是两端共用的，不能直接引用客户端 Screen。
        // 这里把真正的开界面动作注入进去。
        try {
            com.heishandashu.teacraft.item.TeaGuideItem.setOpener(() -> {
                net.minecraft.client.Minecraft mc =
                        net.minecraft.client.Minecraft.getInstance();
                if (mc != null) mc.setScreenAndShow(new TeaGuideScreen());
            });
            TeaCraft.LOGGER.info("[teacraft] 客户端已注册：茶工艺说明打开器");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 说明书打开器注册失败", t);
        }

        // 给碳焙框的 MenuType 绑上界面。
        // 26.3 里 MenuScreens.register 是私有的，只能通过 Invoker Mixin 调用。
        try {
            MenuScreensInvoker.teacraft$register(TeaMenus.CHARCOAL_STOVE,
                    TeaCharcoalStoveScreen::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：碳焙筐");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 碳焙筐界面绑定失败", t);
        }

        // 炒茶锅
        try {
            MenuScreensInvoker.teacraft$register(TeaMenus.WOK, TeaWokScreen::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：炒茶锅");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 炒茶锅界面绑定失败", t);
        }

        // 窑炉
        try {
            MenuScreensInvoker.teacraft$register(TeaMenus.CLAY_KILN, ClayKilnScreen::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：窑炉");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 窑炉界面绑定失败", t);
        }

        // 拉坯机
        try {
            MenuScreensInvoker.teacraft$register(TeaMenus.POTTER_WHEEL,
                    ClayPotterWheelScreen::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：拉坯机");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 拉坯机界面绑定失败", t);
        }

        // 茶盒
        try {
            MenuScreensInvoker.teacraft$register(TeaMenus.TEA_BOX, TeaBoxScreen::new);
            TeaCraft.LOGGER.info("[teacraft] 客户端界面已绑定：茶盒");
        } catch (Throwable t) {
            TeaCraft.LOGGER.error("[teacraft] 茶盒界面绑定失败", t);
        }
    }
}
