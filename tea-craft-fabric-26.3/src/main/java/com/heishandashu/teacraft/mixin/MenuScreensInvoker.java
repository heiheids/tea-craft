package com.heishandashu.teacraft.mixin;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 打开 {@code MenuScreens.register} 的访问口子。
 *
 * <p><b>为什么要 Mixin</b>：26.3 里 {@code MenuScreens.register} 是 <b>private static</b>，
 * 只有原版自己的静态块在调它；Fabric 的 screen-api 只提供事件（ScreenEvents），
 * 没有提供「给自定义 MenuType 绑定 Screen」的入口。查过
 * {@code fabric-screen-api-v1} 和 {@code fabric-menu-api-v1} 的全部公开类，确实没有。
 * 所以只能用一个 {@link Invoker} 把那个私有方法暴露出来。</p>
 *
 * <p>这是本项目<b>唯一</b>的一处 Mixin —— 之前 {@code fabric.mod.json} 里
 * {@code "mixins": []} 是空的，现在必须加配置文件。</p>
 */
@Mixin(MenuScreens.class)
public interface MenuScreensInvoker {

    @Invoker("register")
    static <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void
            teacraft$register(MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> constructor) {
        throw new AssertionError("mixin not applied");
    }
}