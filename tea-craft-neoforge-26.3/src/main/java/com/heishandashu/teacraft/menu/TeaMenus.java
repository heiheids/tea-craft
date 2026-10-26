package com.heishandashu.teacraft.menu;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.block.TeaCharcoalStoveBlockEntity;
import com.heishandashu.teacraft.block.TeaWokBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的菜单注册（26.3 / NeoForge）。
 *
 * <p>NeoForge 里 {@code MenuType} 的构造函数是私有的，扩展数据要用
 * {@link IMenuTypeExtension#create} 配合 {@code IContainerFactory}：
 * 工厂拿到的 {@code RegistryFriendlyByteBuf} 里直接读方块坐标，
 * 客户端再自己去 {@code level.getBlockEntity(pos)} 取同步好的数据。</p>
 *
 * <p>⚠ 配套的 {@code openMenu} 调用必须传 {@code buf -> buf.writeBlockPos(pos)}，
 * 否则这里 {@code buf.readBlockPos()} 会 NPE（buf 为 null）。</p>
 */
public final class TeaMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TeaCraft.MOD_ID);

    /** 碳焙筐。 */
    public static final DeferredHolder<MenuType<?>, MenuType<TeaCharcoalStoveMenu>> CHARCOAL_STOVE =
            MENUS.register("tea_charcoal_stove", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> {
                        BlockPos pos = buf.readBlockPos();
                        var be = inv.player.level().getBlockEntity(pos);
                        if (be instanceof TeaCharcoalStoveBlockEntity stove) {
                            return new TeaCharcoalStoveMenu(id, inv, stove);
                        }
                        // 方块没了（例如被拆）→ 给一个空容器顶上，避免客户端崩
                        return new TeaCharcoalStoveMenu(id, inv,
                                TeaCharcoalStoveMenu.clientContainer(), null);
                    }));

    /** 炒茶锅。 */
    public static final DeferredHolder<MenuType<?>, MenuType<TeaWokMenu>> WOK =
            MENUS.register("tea_wok", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> {
                        BlockPos pos = buf.readBlockPos();
                        var be = inv.player.level().getBlockEntity(pos);
                        if (be instanceof TeaWokBlockEntity wok) {
                            return new TeaWokMenu(id, inv, wok);
                        }
                        return new TeaWokMenu(id, inv, TeaWokMenu.clientContainer(), null);
                    }));

    /** 窑炉：坯料 + 6 成品 + 燃料。 */
    public static final DeferredHolder<MenuType<?>, MenuType<ClayKilnMenu>> CLAY_KILN =
            MENUS.register("clay_kiln", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> {
                        BlockPos pos = buf.readBlockPos();
                        var be = inv.player.level().getBlockEntity(pos);
                        if (be instanceof com.heishandashu.teacraft.block.ClayKilnBlockEntity kiln) {
                            return new ClayKilnMenu(id, inv, kiln);
                        }
                        return new ClayKilnMenu(id, inv, ClayKilnMenu.clientContainer(), null);
                    }));

    /** 拉坯机：黏土球 + 图样列表。 */
    public static final DeferredHolder<MenuType<?>, MenuType<ClayPotterWheelMenu>> POTTER_WHEEL =
            MENUS.register("potter_wheel", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> {
                        BlockPos pos = buf.readBlockPos();
                        var be = inv.player.level().getBlockEntity(pos);
                        if (be instanceof com.heishandashu.teacraft.block.PotterWheelBlockEntity wheel) {
                            return new ClayPotterWheelMenu(id, inv, wheel);
                        }

                        return new ClayPotterWheelMenu(id, inv,
                                ClayPotterWheelMenu.clientContainer(), null);
                    }));

    /** 茶盒。 */
    public static final DeferredHolder<MenuType<?>, MenuType<TeaBoxMenu>> TEA_BOX =
            MENUS.register("tea_box", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> {
                        BlockPos pos = buf.readBlockPos();
                        var be = inv.player.level().getBlockEntity(pos);
                        if (be instanceof com.heishandashu.teacraft.block.TeaBoxBlockEntity box) {
                            return new TeaBoxMenu(id, inv, box);
                        }
                        // 方块没了（例如被拆）→ 给一个空盒子顶上，避免客户端崩
                        return new TeaBoxMenu(id, inv);
                    }));

    private TeaMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
        TeaCraft.LOGGER.info(
                "[teacraft] 菜单注册器已挂载：tea_charcoal_stove / tea_wok / clay_kiln / potter_wheel / tea_box");
    }
}
