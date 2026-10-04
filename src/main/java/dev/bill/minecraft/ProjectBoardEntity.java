package dev.bill.minecraft;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class ProjectBoardEntity extends BlockEntity {
    private String group = "";
    public String group() { return group; }
    public void group(String id) { group=id; updated(); }
    private String title = "My project";
    private List<String> terminals = List.of();
    public ProjectBoardEntity(BlockPos pos, BlockState state) { super(OfficeBlocks.PROJECT_BOARD, pos, state); }
    public String title() { return title; }
    public List<String> terminals() { return terminals; }
    public void title(String value) {
        value = value.strip();
        if (value.isEmpty() || value.length() > 48 || value.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException("Use a project name of 1–48 characters");
        title = value; updated();
    }
    public void toggle(String terminal) {
        var next = new LinkedHashSet<>(terminals);
        if (!next.remove(terminal)) {
            if (next.size() >= 200) throw new IllegalStateException("This project already has 200 sessions");
            next.add(terminal);
        }
        terminals = List.copyOf(next); updated();
    }
    private void updated() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); output.putString("Group", group); output.putString("Project", title); output.store("Terminals", Codec.STRING.listOf(), terminals);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); group=input.getStringOr("Group", ""); title = input.getStringOr("Project", "My project");
        terminals = input.read("Terminals", Codec.STRING.listOf()).orElse(List.of()).stream().distinct().limit(200).toList();
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
}
