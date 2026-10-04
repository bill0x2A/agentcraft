package dev.bill.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.agentcraft.block.entity.MonitorBlockEntity;
import dev.agentcraft.client.world.StationRenderer;
import dev.agentcraft.client.world.StationRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;

/** Draws the shared frame of a terminal on the screen of a monitor panel, as a terminal display does on its face. */
final class MonitorPanelRenderer extends StationRenderer<MonitorBlockEntity,MonitorPanelRenderer.State> {
    private static final int LIGHT=0xF000F0;
    // The screen of the model is 12 pixels deep. A bezel of 2 pixels covers the outer edges of a panel.
    private static final float DEPTH=12/16F-.004F, BEZEL=2/16F, NOTE_PIXELS=40;
    static final class State extends StationRenderState { TerminalDisplays.Layout layout; String note; }
    @Override public State createRenderState(){return new State();}
    // The origin block draws the whole panel, also when only other blocks of the panel are in view.
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override protected void extractStation(MonitorBlockEntity monitor,State state,float delta) {
        state.layout=null; state.note=null;
        String terminal=state.panelOrigin?MonitorPanels.terminal(monitor.getBlockPos()):null;
        if(terminal==null)return;
        state.layout=TerminalDisplays.layout(terminal,Minecraft.getInstance().font);
        if(state.layout==null)state.note=TerminalDisplays.status(terminal);
    }
    @Override public void submit(State state,PoseStack poses,SubmitNodeCollector collector,CameraRenderState camera) {
        if(state.layout==null && state.note==null)return;
        Font font=Minecraft.getInstance().font;
        var note=state.note==null?null:Component.literal(state.note).getVisualOrderText();
        float w=note==null?state.layout.frame().width()*TerminalDisplays.CELL_W:font.width(note);
        float h=note==null?state.layout.frame().height()*TerminalDisplays.CELL_H:8;
        // Text pixels in one block. The whole grid must fit the screen; a note stays as small as on a terminal display.
        float pixels=Math.max(w/(state.panelWidth-2*BEZEL),h/(state.panelHeight-2*BEZEL))/.97F;
        if(note!=null)pixels=Math.max(pixels,NOTE_PIXELS);
        poses.pushPose();
        toFace(poses,state.facing,DEPTH,pixels);
        // toFace gives the top left of the origin block, the lowest row of the panel. Go to the centred content.
        poses.translate((state.panelWidth*pixels-w)/2,(state.panelHeight*pixels-h)/2-(state.panelHeight-1)*pixels,0);
        if(note==null)TerminalDisplayRenderer.grid(state.layout,poses,collector);
        else collector.submitText(poses,0,0,note,false,Font.DisplayMode.POLYGON_OFFSET,LIGHT,0xffa8bad2,0,0);
        poses.popPose();
    }
}
