package dev.bill.minecraft.client;

import dev.agentcraft.block.MonitorBlock;
import dev.agentcraft.block.PanelBlock;
import dev.agentcraft.block.entity.ModBlockEntities;
import dev.bill.minecraft.OfficeBlocks;
import dev.bill.minecraft.OfficeFurnitureBlock;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The monitor panel of an agent desk shows the terminal of the agent at that desk. The desk assignment
 * of the binding is the only record: no monitor block saves a terminal. Client-thread owned.
 */
final class MonitorPanels {
    /** The panel is straight behind the desk as the agent sees it, at most this many blocks away and this many blocks up. */
    private static final int REACH = 3;
    private record Shown(String terminal, int distance) {}
    private static Map<BlockPos, Shown> shown = Map.of();

    static void register() {
        BlockEntityRenderers.register(ModBlockEntities.MONITOR, context -> new MonitorPanelRenderer());
    }

    /** The terminal that the panel with this origin shows, or null. */
    static String terminal(BlockPos origin) {
        Shown found = shown.get(origin);
        return found == null ? null : found.terminal();
    }

    static void clear() { shown = Map.of(); }

    /** Finds the panel of each desk agent in the loaded part of the client world. Gives the terminals to keep attached. */
    static Set<String> refresh(Minecraft client) {
        var level = client.level;
        String dimension = level.dimension().identifier().toString();
        Map<BlockPos, Shown> found = new HashMap<>();
        Set<String> terminals = new HashSet<>();
        for (OfficeWorld.Binding binding : OfficeWorld.bindings()) {
            if (binding.archived() || binding.desk() == null || !binding.dimension().equals(dimension)) continue;
            BlockPos desk = BlockPos.of(binding.desk());
            var deskState = level.getBlockState(desk);
            if (!OfficeBlocks.is(deskState, OfficeFurnitureBlock.Kind.DESK)) continue;
            // The agent sits on the front side of the desk, so a screen that faces the agent has the facing of the desk.
            Direction facing = deskState.getValue(OfficeFurnitureBlock.FACING);
            search:
            for (int back = 0; back <= REACH; back++) for (int up = back == 0 ? 1 : 0; up <= REACH; up++) {
                BlockPos pos = desk.relative(facing.getOpposite(), back).above(up);
                var state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof MonitorBlock) || state.getValue(PanelBlock.FACING) != facing) continue;
                // Two desks can find one panel. The nearer desk has it; the terminal ID decides between equal desks.
                found.merge(PanelBlock.origin(level, pos, state), new Shown(binding.terminal(), back), (old, next) ->
                    next.distance() < old.distance() || next.distance() == old.distance() && next.terminal().compareTo(old.terminal()) < 0 ? next : old);
                break search;
            }
        }
        for (Shown panel : found.values()) terminals.add(panel.terminal());
        shown = found;
        return terminals;
    }
}
