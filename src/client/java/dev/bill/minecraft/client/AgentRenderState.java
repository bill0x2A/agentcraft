package dev.bill.minecraft.client;

import dev.bill.minecraft.AgentActivity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

final class AgentRenderState extends LivingEntityRenderState {
    boolean seated;
    AgentActivity activity = AgentActivity.UNKNOWN;
}
