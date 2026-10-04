package dev.bill.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.agentcraft.block.entity.TaskBoardBlockEntity;
import dev.agentcraft.client.world.StationRenderer;
import dev.agentcraft.client.world.StationRenderState;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;

/** The studio's connected task wall reads the same tracker-optional group state as the CLI. */
final class WorkspaceBoardRenderer extends StationRenderer<TaskBoardBlockEntity,WorkspaceBoardRenderer.State> {
    record Line(String text,int color){}
    static final class State extends StationRenderState {List<Line> lines=List.of();}
    @Override public State createRenderState(){return new State();}
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override protected void extractStation(TaskBoardBlockEntity board,State state,float delta){
        if(!state.panelOrigin)return;
        var lines=new ArrayList<Line>();var data=WorkspaceClient.snapshot;
        var group=WorkspaceScreen.objects(data,"groups").stream().filter(g->WorkspaceScreen.str(g,"id").equals(state.binding)).findFirst().orElse(null);
        if(group==null){lines.add(new Line("WORKING GROUPS",0xff20362e));lines.add(new Line("Right-click to link a group",0xff38554c));lines.add(new Line("Issues are optional",0xff38554c));}
        else{
            lines.add(new Line(WorkspaceScreen.str(group,"name"),0xff163c30));
            lines.add(new Line(WorkspaceScreen.objects(group,"members").size()+" agents · "+(WorkspaceClient.stale?"Offline snapshot":"Live workspace"),0xff426052));
            var tasks=WorkspaceScreen.objects(data,"tasks").stream().filter(t->WorkspaceScreen.str(t,"group").equals(state.binding)).toList();
            if(tasks.isEmpty()){lines.add(new Line("No tasks required. Talk to your agents.",0xff426052));}
            for(var task:tasks){String status=WorkspaceScreen.str(task,"status");int color=switch(status){case "blocked"->0xff9d4025;case "done"->0xff397843;case "working"->0xff176d75;default->0xff253c35;};
                String badge=task.has("issue")?WorkspaceScreen.str(task.getAsJsonObject("issue"),"identifier")+" · ":"";
                lines.add(new Line("["+status+"] "+badge+WorkspaceScreen.str(task,"title"),color));}
        }
        state.lines=List.copyOf(lines);
    }
    @Override public void submit(State s,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(!s.panelOrigin)return;
        pose.pushPose();toFace(pose,s.facing,14f/16f-.004f,64);pose.translate(0,-(s.panelHeight-1)*64,0);
        Font font=Minecraft.getInstance().font;int y=8;
        for(var line:s.lines){if(y>s.panelHeight*64-14)break;String text=font.plainSubstrByWidth(line.text(),s.panelWidth*64-16);
            collector.submitText(pose,8,y,Component.literal(text).getVisualOrderText(),false,Font.DisplayMode.POLYGON_OFFSET,0xF000F0,line.color(),0,0);y+=16;}
        pose.popPose();
    }
}
