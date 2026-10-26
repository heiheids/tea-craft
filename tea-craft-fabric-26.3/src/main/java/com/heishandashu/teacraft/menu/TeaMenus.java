package com.heishandashu.teacraft.menu;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.block.TeaCharcoalStoveBlockEntity;
import com.heishandashu.teacraft.block.TeaWokBlockEntity;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

/**
 * 本模组的菜单注册。
 *
 * <p>26.x 里 {@code MenuType} 的构造函数是私有的，只能用 {@link ExtendedMenuType}：
 * 它把「屏幕打开时额外要传的数据」也一起编解码，正好用来传方块坐标，
 * 客户端拿到坐标后自己去 {@code level.getBlockEntity(pos)} 取同步好的数据。</p>
 */
public final class TeaMenus {

    /** 碳焙筐。 */
    public static MenuType<TeaCharcoalStoveMenu> CHARCOAL_STOVE;

    /** 炒茶锅。 */
    public static MenuType<TeaWokMenu> WOK;

    /** 窑炉。 */
    public static MenuType<ClayKilnMenu> CLAY_KILN;

    /** 拉坯机。 */
    public static MenuType<ClayPotterWheelMenu> POTTER_WHEEL;

    /** 茶盒。 */
    public static MenuType<TeaBoxMenu> TEA_BOX;

    private TeaMenus() {
    }

    public static void register() {
        // 茶盒：方块实体直接传，不需要额外数据包
        TEA_BOX = Registry.register(
                BuiltInRegistries.MENU,
                TeaCraft.id("tea_box"),
                new ExtendedMenuType<TeaBoxMenu, TeaBoxMenu.OpenData>(
                        (id, inv, data) -> {
                            var be = inv.player.level().getBlockEntity(data.pos());
                            if (be instanceof com.heishandashu.teacraft.block.TeaBoxBlockEntity box) {
                                return new TeaBoxMenu(id, inv, box);
                            }
                            return new TeaBoxMenu(id, inv);
                        },
                        TeaBoxMenu.OpenData.CODEC));

        CHARCOAL_STOVE = Registry.register(
                BuiltInRegistries.MENU,
                TeaCraft.id("tea_charcoal_stove"),
                new ExtendedMenuType<TeaCharcoalStoveMenu, TeaCharcoalStoveMenu.OpeningData>(
                        (id, inv, data) -> {
                            var be = inv.player.level().getBlockEntity(data.pos());
                            if (be instanceof TeaCharcoalStoveBlockEntity stove) {
                                return new TeaCharcoalStoveMenu(id, inv, stove);
                            }
                            // 方块没了（例如被拆）→ 给一个空容器顶上，避免客户端崩
                            return new TeaCharcoalStoveMenu(id, inv,
                                    TeaCharcoalStoveMenu.clientContainer(), null);
                        },
                        TeaCharcoalStoveMenu.OpeningData.CODEC));

        WOK = Registry.register(
                BuiltInRegistries.MENU,
                TeaCraft.id("tea_wok"),
                new ExtendedMenuType<TeaWokMenu, TeaWokMenu.OpeningData>(
                        (id, inv, data) -> {
                            var be = inv.player.level().getBlockEntity(data.pos());
                            if (be instanceof TeaWokBlockEntity wok) {
                                return new TeaWokMenu(id, inv, wok);
                            }
                            return new TeaWokMenu(id, inv, TeaWokMenu.clientContainer(), null);
                        },
                        TeaWokMenu.OpeningData.CODEC));

        TeaCraft.LOGGER.info("[teacraft] 菜单就绪：tea_charcoal_stove / tea_wok");

        CLAY_KILN = Registry.register(
                BuiltInRegistries.MENU,
                TeaCraft.id("clay_kiln"),
                new ExtendedMenuType<ClayKilnMenu, ClayKilnMenu.OpeningData>(
                        (id, inv, data) -> {
                            var be = inv.player.level().getBlockEntity(data.pos());
                            if (be instanceof com.heishandashu.teacraft.block.ClayKilnBlockEntity kiln) {
                                return new ClayKilnMenu(id, inv, kiln);
                            }
                            return new ClayKilnMenu(id, inv, ClayKilnMenu.clientContainer(), null);
                        },
                        ClayKilnMenu.OpeningData.CODEC));

        POTTER_WHEEL = Registry.register(
                BuiltInRegistries.MENU,
                TeaCraft.id("potter_wheel"),
                new ExtendedMenuType<ClayPotterWheelMenu, ClayPotterWheelMenu.OpeningData>(
                        (id, inv, data) -> {
                            var be = inv.player.level().getBlockEntity(data.pos());
                            if (be instanceof com.heishandashu.teacraft.block.PotterWheelBlockEntity wheel) {
                                return new ClayPotterWheelMenu(id, inv, wheel);
                            }
                            return new ClayPotterWheelMenu(id, inv,
                                    ClayPotterWheelMenu.clientContainer(), null);
                        },
                        ClayPotterWheelMenu.OpeningData.CODEC));

        TeaCraft.LOGGER.info("[teacraft] 菜单就绪：clay_kiln / potter_wheel");
    }
}
