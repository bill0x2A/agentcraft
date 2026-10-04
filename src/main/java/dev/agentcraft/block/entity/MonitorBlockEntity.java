package dev.agentcraft.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Monitor screen. The client's MonitorPanelRenderer draws the terminal of the agent at the desk in front of
 * the panel, from the panel origin. The office finds that desk in the world; the binding (the studio's desk
 * id) is not used.
 */
public class MonitorBlockEntity extends StationBlockEntity {
	public MonitorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MONITOR, pos, state);
	}
}
