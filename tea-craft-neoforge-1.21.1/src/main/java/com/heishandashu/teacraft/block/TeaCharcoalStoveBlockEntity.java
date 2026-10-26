package com.heishandashu.teacraft.block;

import com.heishandashu.teacraft.core.TeaComponents;
import com.heishandashu.teacraft.core.TeaOrigin;
import com.heishandashu.teacraft.core.TeaProcessing;
import com.heishandashu.teacraft.item.TeaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 碳焙框 —— 类似熔炉，用木炭对<b>发酵过的茶</b>做炭焙，产出成品茶。
 *
 * <h2>产出规则（需求核心）</h2>
 * <ul>
 *   <li>经历了<b>摇青 + 杀青</b>的茶 → <b>凤凰单丛</b>（{@code dancong_8_finished}）；</li>
 *   <li>没经历过的（红茶路线） → <b>红茶</b>（{@code black_6_finished}）。</li>
 * </ul>
 * <p>判定不靠猜，靠「这一份茶的工序链」：每个阶段物品都自带所属链条，
 * 见 {@link TeaProcessing#finishedFor}。产地则一路用 {@code teacraft:tea_origin} 组件带着。</p>
 *
 * <h2>槽位</h2>
 * <p>0 燃料（木炭） / 1 待焙 / 2 成品。用 {@link Container} 实现，
 * 这样原版的槽位、shift 搬运、漏斗都能直接用。</p>
 */
public class TeaCharcoalStoveBlockEntity extends BlockEntity implements Container {

    /** 由 TeaItems 在注册后回填。 */
    public static BlockEntityType<TeaCharcoalStoveBlockEntity> TYPE;

    public static final int SLOT_FUEL = 0;
    public static final int SLOT_INPUT = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    /** 一次最多焙多少（需求：最多 64 个同一种茶叶）。 */
    /** 一次最多放 32 份（需求：所有工作方块统一 32）。 */
    public static final int MAX_ITEMS = 32;
    /** 一次焙多少份。 */
    public static final int BATCH = 8;
    /** 焙一批要多久（30 秒焙 8 个）。 */
    public static final int BAKE_TICKS = 30 * 20;
    /** 一块木炭烧多久（与原版熔炉一致）。 */
    public static final int CHARCOAL_TICKS = 1600;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private int burnLeft = 0;
    private int burnTotal = 0;
    private int progress = 0;
    /** 本次焙制对应的产物 id（进入时算好，避免中途换茶导致产物漂移）。 */
    private String pendingOutput = "";
    /** 这一批茶的产地，随产物一起带走。 */
    private TeaOrigin origin = TeaOrigin.TEMPERATE;

    public TeaCharcoalStoveBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    /* ---------------- 进度查询（界面画火焰/箭头） ---------------- */

    public int progress() { return progress; }
    public int burnLeft() { return burnLeft; }
    public int burnTotal() { return burnTotal; }
    public boolean isBurning() { return burnLeft > 0; }

    /** 0~1 的焙制进度。 */
    public float bakeProgress() {
        return Math.min(1f, progress / (float) BAKE_TICKS);
    }

    /** 燃料剩余比例（界面画火焰）。 */
    public float burnProgress() {
        if (burnLeft <= 0 || burnTotal <= 0) return 0f;
        return Math.min(1f, burnLeft / (float) burnTotal);
    }

    public TeaOrigin origin() { return origin; }

    /* ---------------- Container ---------------- */

    @Override
    public int getContainerSize() { return SLOT_COUNT; }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) setChanged();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize(stack)) stack.setCount(getMaxStackSize(stack));
        setChanged();
    }

    /** 槽位规则：燃料只收木炭，待焙只收发酵茶，成品不收。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_FUEL -> stack.is(Items.CHARCOAL);
            case SLOT_INPUT -> TeaProcessing.canBake(idOf(stack));
            default -> false;
        };
    }

    @Override
    public int getMaxStackSize() { return MAX_ITEMS; }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    public static String idOf(ItemStack stack) {
        if (stack.getItem() instanceof com.heishandashu.teacraft.item.TeaStageItem s) {
            return s.stage().id();
        }
        return "";
    }

    /* ---------------- 每 tick ---------------- */

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean changed = false;

        ItemStack input = items.get(SLOT_INPUT);
        boolean hasInput = !input.isEmpty() && TeaProcessing.canBake(idOf(input));
        String product = hasInput
                ? TeaProcessing.finishedFor(idOf(input), TeaComponents.originOf(input, TeaOrigin.TEMPERATE))
                : null;
        boolean canWork = product != null && outputFits(product);

        // 1) 点火 / 续燃料
        if (canWork && burnLeft <= 0) {
            ItemStack fuel = items.get(SLOT_FUEL);
            if (!fuel.isEmpty() && fuel.is(Items.CHARCOAL)) {
                fuel.shrink(1);
                burnLeft = CHARCOAL_TICKS;
                burnTotal = CHARCOAL_TICKS;
                changed = true;
            }
        }

        // 2) 烧燃料
        if (burnLeft > 0) {
            burnLeft--;
            changed = true;
        }

        // 3) 推进焙制
        if (canWork && burnLeft > 0) {
            if (pendingOutput.isEmpty()) {
                pendingOutput = product;
                origin = TeaComponents.originOf(input, TeaOrigin.TEMPERATE);
            }
            progress++;
            changed = true;
            if (progress >= BAKE_TICKS) {
                finishBatch();
                progress = 0;
                pendingOutput = "";
                changed = true;
            }
        } else if (!canWork && progress > 0) {
            // 和熔炉一样：条件不满足时进度回落，避免卡住"半成品"
            progress = Math.max(0, progress - 2);
            pendingOutput = "";
            changed = true;
        }

        boolean lit = burnLeft > 0;
        if (state.getValue(TeaCharcoalStoveBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(TeaCharcoalStoveBlock.LIT, lit), 3);
        }
        if (changed) setChanged();
    }

    /** 产物槽能否再放一份这个产物。 */
    private boolean outputFits(String outputId) {
        if (outputId == null) return false;
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) return true;
        if (!(out.getItem() instanceof com.heishandashu.teacraft.item.TeaStageItem s)) return false;
        if (!s.stage().id().equals(outputId)) return false;
        return out.getCount() < MAX_ITEMS;
    }

    /**
     * 焙好<b>一批</b>：一次最多 {@link #BATCH} 份。
     *
     * <p>需求：「碳焙一次8个物品，30秒」。所以 30 秒到了就一次性把 8 份转成成品，
     * 不足 8 份就焙剩下的。</p>
     */
    private void finishBatch() {
        ItemStack input = items.get(SLOT_INPUT);
        if (input.isEmpty()) return;

        String outputId = pendingOutput.isEmpty()
                ? TeaProcessing.finishedFor(idOf(input), TeaComponents.originOf(input, TeaOrigin.TEMPERATE))
                : pendingOutput;
        if (outputId == null) return;

        // 成品槽还能再放几份，就焙几份
        int room = outputRoom(outputId);
        int n = Math.min(Math.min(BATCH, input.getCount()), room);
        if (n <= 0) return;

        input.shrink(n);
        if (input.isEmpty()) items.set(SLOT_INPUT, ItemStack.EMPTY);

        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) {
            ItemStack created = new ItemStack(TeaItems.stage(outputId), n);
            // 产地跟着成品走：将来不同产地做不同功效就靠这个判断
            TeaComponents.setOrigin(created, origin);
            items.set(SLOT_OUTPUT, created);
        } else {
            out.grow(n);
        }
    }

    /** 成品槽还能放几份这个产物。 */
    private int outputRoom(String outputId) {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) return MAX_ITEMS;
        if (!(out.getItem() instanceof com.heishandashu.teacraft.item.TeaStageItem s)) return 0;
        if (!s.stage().id().equals(outputId)) return 0;
        return MAX_ITEMS - out.getCount();
    }

    /* ---------------- 存档 ---------------- */

    @Override
    protected void saveAdditional(CompoundTag out, HolderLookup.Provider registries) {
        super.saveAdditional(out, registries);
        ContainerHelper.saveAllItems(out, items, registries);
        out.putInt("burn_left", burnLeft);
        out.putInt("burn_total", burnTotal);
        out.putInt("progress", progress);
        out.putString("pending_output", pendingOutput);
        out.putString("origin", origin.id());
    }

    @Override
    protected void loadAdditional(CompoundTag in, HolderLookup.Provider registries) {
        super.loadAdditional(in, registries);
        items.clear();
        ContainerHelper.loadAllItems(in, items, registries);
        burnLeft = in.getInt("burn_left");
        burnTotal = in.getInt("burn_total");
        progress = in.getInt("progress");
        pendingOutput = in.getString("pending_output");
        origin = TeaOrigin.byId(in.getString("origin"));
    }

    /**
     * 同步给客户端的数据。
     *
     * <p>界面要显示槽里的物品，所以必须带上完整 ItemStack ——
     * 只发 id/数量的话，客户端那份 items 是空的，界面里看不到任何物品。</p>
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("burn_left", burnLeft);
        tag.putInt("burn_total", burnTotal);
        tag.putInt("progress", progress);
        tag.putString("origin", origin.id());
        // 1.21.1 直接往 CompoundTag 里写，不需要 TagValueOutput
        ContainerHelper.saveAllItems(tag, items, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 界面标题。 */
    public Component title() {
        return Component.translatable("block.teacraft.tea_charcoal_stove");
    }
}
