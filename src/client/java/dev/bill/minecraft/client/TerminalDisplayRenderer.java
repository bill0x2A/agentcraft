package dev.bill.minecraft.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.bill.minecraft.TerminalDisplay;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Draws the shared frame of a terminal as text on the same 4 × 2.25 face as a browser display. */
final class TerminalDisplayRenderer extends EntityRenderer<TerminalDisplay,TerminalDisplayRenderer.State> {
    private static final Identifier FRAME=Identifier.fromNamespaceAndPath("modding_workspace","textures/entity/browser-frame.png");
    private static final int LIGHT=0xF000F0;
    private static final float FACE_W=4, FACE_H=2.25F, FACE_Y=.125F;
    static final class State extends EntityRenderState { float yaw; TerminalDisplays.Layout layout; String note; }
    TerminalDisplayRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(TerminalDisplay entity,State state,float delta) {
        super.extractRenderState(entity,state,delta); state.yaw=entity.getYRot();
        state.layout=TerminalDisplays.layout(entity.terminal(),getFont());
        state.note=state.layout==null?TerminalDisplays.status(entity.terminal()):null;
    }
    @Override public void submit(State state,PoseStack poses,SubmitNodeCollector collector,CameraRenderState camera) {
        super.submit(state,poses,collector,camera);
        poses.pushPose(); poses.rotateDegrees(Axis.YP,-state.yaw);
        // The same bezel as the browser display, with a dark face in place of a page.
        collector.submitCustomGeometry(poses,RenderTypes.entityCutout(FRAME),(pose,v)-> {
            colorQuad(pose,v,-2.1F,0,2.1F,2.5F,.015F,0xff172a34);
            colorQuad(pose,v,2.1F,0,-2.1F,2.5F,.015F,0xff172a34);
            colorQuad(pose,v,-.5F,-.25F,.5F,0,0,0xff304d57);
            colorQuad(pose,v,-2,FACE_Y,2,FACE_Y+FACE_H,0,TerminalDisplays.BACKGROUND);
        });
        if(state.layout!=null) {
            var frame=state.layout.frame();
            float gridW=frame.width()*TerminalDisplays.CELL_W, gridH=frame.height()*TerminalDisplays.CELL_H;
            float scale=Math.min(FACE_W/gridW,FACE_H/gridH)*.97F;
            // The viewer looks along +Z, so the left edge of the text is at +X. Text space has Y down.
            poses.translate(gridW*scale/2,FACE_Y+FACE_H/2+gridH*scale/2,-.01F);
            poses.rotateDegrees(Axis.YP,180); poses.scale(scale,-scale,scale);
            grid(state.layout,poses,collector);
        } else if(state.note!=null) {
            var text=Component.literal(state.note).getVisualOrderText();
            float scale=.025F;
            poses.translate(getFont().width(text)*scale/2,FACE_Y+FACE_H/2+4*scale,-.01F);
            poses.rotateDegrees(Axis.YP,180); poses.scale(scale,-scale,scale);
            collector.submitText(poses,0,0,text,false,Font.DisplayMode.POLYGON_OFFSET,LIGHT,0xffa8bad2,0,0);
        }
        poses.popPose();
    }
    /** The cells and the cursor of a frame in text space: X right, Y down, the origin at the top left of the grid. */
    static void grid(TerminalDisplays.Layout layout,PoseStack poses,SubmitNodeCollector collector) {
        for(TerminalDisplays.Run run:layout.runs()) {
            float x=run.x()*TerminalDisplays.CELL_W, y=run.y()*TerminalDisplays.CELL_H;
            if(run.background()!=TerminalDisplays.BACKGROUND)
                collector.submitTextBackground(poses,x,y,x+run.cells()*TerminalDisplays.CELL_W,y+TerminalDisplays.CELL_H,run.background(),Font.DisplayMode.POLYGON_OFFSET,LIGHT);
            if(run.text()==null)continue;
            collector.submitText(poses,x,y,run.text(),false,Font.DisplayMode.POLYGON_OFFSET,LIGHT,run.foreground(),0,0);
            // Bold as in the terminal screen: the glyph again, half a cell unit to the right.
            if(run.bold())collector.submitText(poses,x+.5F,y,run.text(),false,Font.DisplayMode.POLYGON_OFFSET,LIGHT,run.foreground(),0,0);
        }
        var frame=layout.frame();
        if(frame.cursorVisible()) {
            float x=frame.cursorX()*TerminalDisplays.CELL_W, y=frame.cursorY()*TerminalDisplays.CELL_H;
            collector.submitTextBackground(poses,x,y,x+TerminalDisplays.CELL_W,y+TerminalDisplays.CELL_H,0x99b5e890,Font.DisplayMode.POLYGON_OFFSET,LIGHT);
        }
    }
    private static void colorQuad(PoseStack.Pose p,VertexConsumer v,float l,float b,float r,float t,float z,int color) {
        vertex(p,v,l,b,z,color);vertex(p,v,r,b,z,color);vertex(p,v,r,t,z,color);vertex(p,v,l,t,z,color);
    }
    private static void vertex(PoseStack.Pose p,VertexConsumer v,float x,float y,float z,int color) {
        v.addVertex(p,x,y,z).setColor(color).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(p,0,0,-1);
    }
}
