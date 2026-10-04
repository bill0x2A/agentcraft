package dev.bill.minecraft.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.bill.minecraft.BrowserDisplay;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

final class BrowserDisplayRenderer extends EntityRenderer<BrowserDisplay,BrowserDisplayRenderer.State> {
    private static final Identifier FRAME=Identifier.fromNamespaceAndPath("modding_workspace","textures/entity/browser-frame.png");
    static final class State extends EntityRenderState { float yaw, scale; int frameColor; Identifier texture; }
    BrowserDisplayRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(BrowserDisplay entity,State state,float delta) {
        super.extractRenderState(entity,state,delta); state.yaw=entity.getYRot(); state.scale=entity.displayScale(); state.texture=null; state.frameColor=0xff172a34;
        if (entity.displayScale()<1 && entity.level().getBlockState(entity.blockPosition()).getBlock() instanceof dev.bill.minecraft.OfficeFurnitureBlock furniture) {
            state.frameColor=switch(furniture.style) {
                case "birch" -> 0xffe8d6ac;
                case "dark_oak" -> 0xff493325;
                case "industrial" -> 0xff8b979e;
                default -> 0xff172a34;
            };
        }
        if(BrowserEngine.available()) {
            BrowserSurface surface=BrowserEngine.surface(entity);
            if(surface!=null && surface.ready())state.texture=surface.textureId;
        }
    }
    @Override public void submit(State state,PoseStack poses,SubmitNodeCollector collector,CameraRenderState camera) {
        super.submit(state,poses,collector,camera);
        poses.pushPose(); poses.rotateDegrees(Axis.YP,-state.yaw); poses.scale(state.scale,state.scale,state.scale);
        // A solid, double-sided bezel remains visible while Chromium initializes.
        collector.submitCustomGeometry(poses,RenderTypes.entityCutout(FRAME),(pose,v)-> {
            colorQuad(pose,v,-2.1F,0,2.1F,2.5F,.015F,state.frameColor);
            colorQuad(pose,v,2.1F,0,-2.1F,2.5F,.015F,state.frameColor);
            colorQuad(pose,v,-.5F,-.25F,.5F,0,0,0xff304d57);
        });
        if(state.texture!=null)collector.submitCustomGeometry(poses,RenderTypes.entityCutout(state.texture),(pose,v)-> {
            vertex(pose,v,-2,.125F,0,0,1); vertex(pose,v,2,.125F,0,1,1);
            vertex(pose,v,2,2.375F,0,1,0); vertex(pose,v,-2,2.375F,0,0,0);
        });
        poses.popPose();
    }
    private static void colorQuad(PoseStack.Pose p,VertexConsumer v,float l,float b,float r,float t,float z,int color) {
        frameVertex(p,v,l,b,z,color);frameVertex(p,v,r,b,z,color);frameVertex(p,v,r,t,z,color);frameVertex(p,v,l,t,z,color);
    }
    private static void frameVertex(PoseStack.Pose p,VertexConsumer v,float x,float y,float z,int color) {
        v.addVertex(p,x,y,z).setColor(color).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(p,0,0,-1);
    }
    private static void vertex(PoseStack.Pose p,VertexConsumer v,float x,float y,float z,float u,float vv) {
        v.addVertex(p,x,y,z).setColor(-1).setUv(1-u,vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(p,0,0,-1);
    }
}
