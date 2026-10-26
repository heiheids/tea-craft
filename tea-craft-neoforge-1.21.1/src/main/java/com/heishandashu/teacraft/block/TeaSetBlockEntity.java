package com.heishandashu.teacraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 茶盘上的茶汤数据。
 *
 * <h2>为什么这里只存"茶汤"，不存"有没有杯子"</h2>
 * <p>「盖碗在不在、每个杯子是空还是满」这些决定<b>渲染</b>，
 * 已经由方块状态（multipart 条件）表达了；
 * 方块实体只存<b>看不见的数据</b>：盖碗里的茶汤、每个杯子里的茶汤。</p>
 *
 * <p>两者要一起改：改方块状态才能换模型，改方块实体才能记住茶是什么。
 * 所以下面每个 setter 都会顺手 {@code setChanged()}。</p>
 */
public class TeaSetBlockEntity extends BlockEntity {

    /** 方块实体类型；方块注册后由 {@code TeaItems} 填进来。 */
    public static net.minecraft.world.level.block.entity.BlockEntityType<TeaSetBlockEntity> TYPE;

    /**
     * 盖碗里的干茶，编码 {@code "kind|quality|count"}。
     *
     * <p>第三个字段是<b>投入的茶叶份数</b>。泡茶时按份数出茶：
     * 投 3 份出 3 杯，投 1 份出 1 杯 —— 投入与产出对等。</p>
     */
    private String dryTea = "";

    /** 盖碗里泡好的茶汤（编码 {@code "kind|quality|servings"}，空串表示没有）。 */
    private String gaiwanBrew = "";

    /** 三个杯子里的茶汤，下标 0/1/2 对应 a/b/c。 */
    private final String[] cupBrews = new String[]{"", "", ""};

    public TeaSetBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public String dryTea() { return dryTea; }

    public void setDryTea(String tea) {
        this.dryTea = tea == null ? "" : tea;
        setChanged();
    }

    public String gaiwanBrew() { return gaiwanBrew; }

    public void setGaiwanBrew(String brew) {
        this.gaiwanBrew = brew == null ? "" : brew;
        setChanged();
    }

    public String cupBrew(int index) {
        return (index < 0 || index > 2) ? "" : cupBrews[index];
    }

    public void setCupBrew(int index, String brew) {
        if (index < 0 || index > 2) return;
        cupBrews[index] = brew == null ? "" : brew;
        setChanged();
    }

    /* ---------------- 存盘 / 同步 ---------------- */

    @Override
    protected void saveAdditional(CompoundTag out, HolderLookup.Provider registries) {
        super.saveAdditional(out, registries);
        out.putString("dry_tea", dryTea);
        out.putString("gaiwan", gaiwanBrew);
        out.putString("cup0", cupBrews[0]);
        out.putString("cup1", cupBrews[1]);
        out.putString("cup2", cupBrews[2]);
    }

    @Override
    protected void loadAdditional(CompoundTag in, HolderLookup.Provider registries) {
        super.loadAdditional(in, registries);
        this.dryTea = in.getString("dry_tea");
        this.gaiwanBrew = in.getString("gaiwan");
        cupBrews[0] = in.getString("cup0");
        cupBrews[1] = in.getString("cup1");
        cupBrews[2] = in.getString("cup2");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dry_tea", dryTea);
        tag.putString("gaiwan", gaiwanBrew);
        tag.putString("cup0", cupBrews[0]);
        tag.putString("cup1", cupBrews[1]);
        tag.putString("cup2", cupBrews[2]);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
