package dev.bill.minecraft.client;

import dev.bill.minecraft.AgentMob;
import dev.bill.minecraft.ModdingWorkspace;
import dev.bill.minecraft.OfficeEntities;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

final class AgentRenderer extends MobRenderer<AgentMob, AgentRenderState, AgentModel> {
    private static final ModelLayerLocation CODEX_LAYER=layer("codex");
    private static final ModelLayerLocation CLAUDE_LAYER=layer("claude");
    private final String character;
    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID,name),"main");
    }
    private AgentRenderer(EntityRendererProvider.Context context,boolean claude) {
        super(context,new AgentModel(context.bakeLayer(claude?CLAUDE_LAYER:CODEX_LAYER),claude),claude?.65F:.4F);
        character=claude?"claude":"codex";
    }
    static void register() {
        ModelLayerRegistry.registerModelLayer(CODEX_LAYER,()->AgentModel.layer(false));
        ModelLayerRegistry.registerModelLayer(CLAUDE_LAYER,()->AgentModel.layer(true));
        EntityRendererRegistry.register(OfficeEntities.CODEX,context->new AgentRenderer(context,false));
        EntityRendererRegistry.register(OfficeEntities.CLAUDE,context->new AgentRenderer(context,true));
    }
    @Override public AgentRenderState createRenderState() { return new AgentRenderState(); }
    @Override public Identifier getTextureLocation(AgentRenderState state) {
        return Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID,"textures/entity/"+character+"_"+state.activity.name().toLowerCase(Locale.ROOT)+".png");
    }
    @Override public void extractRenderState(AgentMob mob,AgentRenderState state,float partialTicks) {
        super.extractRenderState(mob,state,partialTicks); state.activity=mob.activity();
    }
}
