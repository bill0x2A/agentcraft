package dev.bill.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.agentcraft.block.ModBlocks;
import dev.agentcraft.block.entity.StationBlockEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

final class WorkspaceClient {
    static com.google.gson.JsonObject snapshot=new com.google.gson.JsonObject();
    static boolean stale=true,polling;
    static int ticks;
    static void register(){
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(dev.agentcraft.block.entity.ModBlockEntities.TASK_BOARD, context->new WorkspaceBoardRenderer());
        boolean[] held={false};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(OfficeClient.localWorld(client) && !polling && ++ticks%40==0){
                polling=true;WorkspaceScreen.call("snapshot",new com.google.gson.JsonObject()).whenComplete((data,error)->client.execute(()->{polling=false;stale=error!=null;if(data!=null)snapshot=data;}));
            }
            boolean down=InputConstants.isKeyDown(InputConstants.KEY_F6);
            if(down && !held[0] && OfficeClient.localWorld(client) && client.gui.screen()==null)client.gui.setScreen(new WorkspaceScreen(null));
            held[0]=down;
        });
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            var block=level.getBlockState(hit.getBlockPos()).getBlock();
            if(block!=ModBlocks.TASK_BOARD && block!=ModBlocks.CONSOLE_TERMINAL && block!=ModBlocks.DECISION_PODIUM)return InteractionResult.PASS;
            var client=Minecraft.getInstance();if(!OfficeClient.localWorld(client))return InteractionResult.PASS;
            if(level.isClientSide()){
                var pos=hit.getBlockPos();
                if(block instanceof dev.agentcraft.block.PanelBlock)pos=dev.agentcraft.block.PanelBlock.origin(level,pos,level.getBlockState(pos));
                var be=level.getBlockEntity(pos);
                String id=be instanceof StationBlockEntity station?station.binding():"";
                client.gui.setScreen(new WorkspaceScreen(client.gui.screen(),pos,id.startsWith("group_")?id:""));
            }
            return InteractionResult.SUCCESS;
        });
    }
}
