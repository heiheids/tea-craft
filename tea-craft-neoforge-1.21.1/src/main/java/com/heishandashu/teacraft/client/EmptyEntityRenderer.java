package com.heishandashu.teacraft.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 隐形实体渲染器 —— 什么也不画。
 *
 * <h2>为什么必须有它</h2>
 * <p>座位实体（{@code TeaSeatEntity}）是玩家坐下时骑乘的不可见载具。它「看不见」
 * 不代表可以不注册渲染器：原版渲染循环会对<b>每一个</b>实体调用</p>
 *
 * <pre>EntityRenderDispatcher.shouldRender(...)</pre>
 *
 * <p>而该方法内部直接取 {@code this.renderers.get(entity.getType())} 再调用
 * {@code .shouldRender(...)}。如果没注册，取到 null 就是：</p>
 *
 * <pre>NullPointerException: Cannot invoke "EntityRenderer.shouldRender(...)"
 * because "entityrenderer" is null</pre>
 *
 * <p>这是<b>渲染线程的致命异常</b>，直接导致游戏崩溃 —— 也就是「右键椅子崩溃」。
 * 所以哪怕是完全隐形的实体，也必须注册一个什么都不做的渲染器。</p>
 */
public class EmptyEntityRenderer<T extends Entity> extends EntityRenderer<T> {

    /** 这个贴图永远不会被用到（{@link #shouldRender} 返回 false），但不能为 null。 */
    private static final ResourceLocation DUMMY =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public EmptyEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return DUMMY;
    }

    /**
     * 永远不渲染。
     *
     * <p>返回 false 后，渲染循环连包围盒裁剪都跳过，比让它画一个空模型更省。</p>
     */
    @Override
    public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return false;
    }
}
