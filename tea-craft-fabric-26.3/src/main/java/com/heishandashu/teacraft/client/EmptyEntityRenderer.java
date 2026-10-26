package com.heishandashu.teacraft.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * 隐形实体渲染器 —— 什么也不画。
 *
 * <h2>为什么必须有它</h2>
 * <p>座位实体（{@code TeaSeatEntity}）是玩家坐下时骑乘的不可见载具。它「看不见」
 * 不代表可以不注册渲染器：原版渲染循环会对<b>每一个</b>实体查询渲染器，</p>
 *
 * <pre>EntityRenderDispatcher.shouldRender(...)</pre>
 *
 * <p>内部直接取渲染器再调 {@code .shouldRender(...)}。没注册就是 null →</p>
 *
 * <pre>NullPointerException: Cannot invoke "EntityRenderer.shouldRender(...)"
 * because "entityrenderer" is null</pre>
 *
 * <p>这是<b>渲染线程致命异常</b>，直接崩溃 —— 也就是「右键椅子崩溃」。</p>
 *
 * <h2>26.3 的渲染器长什么样</h2>
 * <ul>
 *   <li>类型参数有两个：{@code EntityRenderer<T, S extends EntityRenderState>}；</li>
 *   <li>必须实现 {@code createRenderState()}（返回一个状态对象）；</li>
 *   <li>没有 {@code getTextureLocation}（贴图在 submit 阶段自己管）；</li>
 *   <li>{@code shouldRender} 多一个 {@code float} 参数；</li>
 *   <li>真正画东西的入口是 {@code submit(...)} —— 这里不覆盖，等于不画。</li>
 * </ul>
 */
public class EmptyEntityRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {

    public EmptyEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    /**
     * 永远不渲染。
     *
     * <p>返回 false 后，渲染循环连包围盒裁剪都跳过 —— 比让它画一个空模型更省。</p>
     *
     * <p><b>这个方法必须覆盖</b>：即使实体看不见，只要返回 true，
     * 渲染循环还会继续走 submit 流程。</p>
     */
    @Override
    public boolean shouldRender(T entity, Frustum frustum,
                                double camX, double camY, double camZ, float partialTick) {
        return false;
    }
}
