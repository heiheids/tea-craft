package com.heishandashu.teacraft.client;

import com.heishandashu.teacraft.TeaCraft;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.component.CustomModelData;

/**
 * 物品模型谓词注册（NeoForge 1.21.1）。
 *
 * <h2>为什么需要这个</h2>
 * <p>晒盘物品要知道「盘里装的是新茶 / 萎凋 / 摇青」，才能换对应的图标。</p>
 *
 * <p>26.3 的做法是在 {@code assets/teacraft/items/tea_tray.json} 里直接写
 * 一棵 {@code range_dispatch} 条件树，把 {@code custom_model_data} 映射到
 * 不同模型。而 <b>1.21.1 没有 items/ 这层定义</b>，只认
 * {@code models/item/*.json} 里的 {@code overrides} 数组 ——
 * 而 {@code overrides} 要生效，必须先用 {@link ItemProperties#register}
 * 登记一个同名的谓词函数。</p>
 *
 * <p>所以这里把 {@link CustomModelData} 组件的整数值暴露成
 * {@code minecraft:custom_model_data} 谓词，{@code tea_tray.json} 里的
 * 三个 override 才会真正被查询。</p>
 *
 * <h2>注意</h2>
 * <p>1.21.1 的 {@link CustomModelData} 只有单个 int 构造器
 * （26.3 是四个 List 参数），所以这里读 {@code value()} 即可。</p>
 */
public final class TeaItemProperties {

    private TeaItemProperties() {
    }

    /** 谓词名必须与 {@code models/item/tea_tray.json} 里的 predicate 键一致。 */
    private static final net.minecraft.resources.ResourceLocation CUSTOM_MODEL_DATA =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("custom_model_data");

    public static void register() {
        // 晒盘：0 空 / 1 新茶 / 2 萎凋 / 3 摇青 —— 与 TeaTrayBlock.STAGE_* 一一对应
        ItemProperties.register(TeaItems.TEA_TRAY_ITEM.get(), CUSTOM_MODEL_DATA,
                (stack, level, entity, seed) -> {
                    CustomModelData data =
                            stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
                    return data == null ? 0.0F : (float) data.value();
                });

        // 揉捻匾：图标按内部阶段变化（发酵前后）
        ItemProperties.register(TeaItems.TEA_ROLLING_TRAY_ITEM.get(), CUSTOM_MODEL_DATA,
                (stack, level, entity, seed) -> {
                    CustomModelData data =
                            stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
                    return data == null ? 0.0F : (float) data.value();
                });

        // 茶盘：0 = 空盘 / 1 = 盘上有茶具 —— 和 TeaSetBlock.pickUp 写入值一致
        ItemProperties.register(TeaItems.TEA_SET_ITEM.get(), CUSTOM_MODEL_DATA,
                (stack, level, entity, seed) -> {
                    CustomModelData data =
                            stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
                    return data == null ? 0.0F : (float) data.value();
                });

        TeaCraft.LOGGER.info("[teacraft] 物品模型谓词已登记：晒盘 / 揉捻匾（custom_model_data）");
    }
}
