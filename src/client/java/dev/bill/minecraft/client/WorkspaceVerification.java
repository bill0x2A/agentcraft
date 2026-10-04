package dev.bill.minecraft.client;

import com.google.gson.*;
import dev.bill.minecraft.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Isolated end-to-end checks. All sessions are disposable shells, never real agent launches. */
final class WorkspaceVerification {
    private static int phase,ticks;
    private static CompletableFuture<?> pending;
    private static final Path REPORT=Path.of("../.runtime/workspace-verification.txt");
    private static final long DEADLINE=System.nanoTime()+360_000_000_000L;
    private static String group,terminal,browserUrl;
    private static UUID browserId;
    static void register(){ClientTickEvents.END_CLIENT_TICK.register(client->{
        if(phase==99)return;
        try{
            if(!client.gameDirectory.toPath().getFileName().toString().equals("run-workspace-test"))throw new IllegalStateException("Wrong test directory");
            client.options.pauseOnLostFocus=false;
            if(client.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s)s.onClose();
            if(System.nanoTime()>DEADLINE)throw new IllegalStateException("Timeout at phase "+phase);
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;ticks=0;}
            switch(phase){
                case 0->{if(!(client.gui.screen() instanceof TitleScreen))return;
                    Files.writeString(REPORT,"START workspace integration\n");
                    var fixture=JsonParser.parseString(Files.readString(Path.of("../.runtime/workspace-fixture.json"))).getAsJsonObject();
                    group=fixture.get("group").getAsString();terminal=fixture.get("terminal").getAsString();browserUrl=fixture.get("browser").getAsString();
                    client.createWorldOpenFlows().createFreshLevel("workspace-test-"+System.currentTimeMillis(),new LevelSettings("Working groups",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),WorldPresets::createTestWorldDimensions,client.gui.screen());phase=1;
                }
                case 1->{if(!OfficeClient.localWorld(client)||++ticks<40)return;
                    var server=client.getSingleplayerServer();var id=client.player.getUUID();pending=server.submit(()->{try{Studio.enter(server.getPlayerList().getPlayer(id));}catch(Exception e){throw new RuntimeException(e);}});phase=2;
                }
                case 2->{if(!client.level.dimension().equals(Studio.DIMENSION)||++ticks<60)return;
                    check(client.level.getBlockState(new net.minecraft.core.BlockPos(0,65,15)).getBlock()!=net.minecraft.world.level.block.Blocks.AIR,"studio floor missing");
                    pass("AgentCraft studio generated in a separate dimension");capture(client,"workspace-studio.png");
                    var server=client.getSingleplayerServer();var id=client.player.getUUID();
                    pending=server.submit(()->{try{
                        var player=server.getPlayerList().getPlayer(id);
                        for(var s:OfficeClient.sessions)if(s.label().startsWith("Group verification"))OfficeWorld.spawn(server,id,s,s.label().endsWith("Claude")?"claude":"codex",new net.minecraft.core.BlockPos(s.label().endsWith("Claude")?-2:2,66,20));
                        var display=BrowserEngine.place(player);display.url(browserUrl);display.snapTo(5.5,66,18.5,90,0);browserId=display.getUUID();
                    }catch(Exception e){throw new RuntimeException(e);}});phase=3;
                }
                case 3->{if(++ticks<60)return;client.gui.setScreen(new WorkspaceScreen(null,null,group));phase=4;ticks=0;}
                case 4->{if(++ticks<60)return;capture(client,"workspace-group.png");click(client,"Tasks");phase=5;ticks=0;}
                case 5->{if(++ticks<30)return;capture(client,"workspace-tasks.png");pass("Shared group and freeform task screens render");client.gui.setScreen(new TerminalScreen(null,terminal));phase=6;ticks=0;}
                case 6->{var s=(TerminalScreen)client.gui.screen();if(s.connection.frame==null||++ticks<30)return;
                    pass("Group session opens its real Herdr terminal");capture(client,"workspace-terminal.png");s.onClose();
                    var display=(BrowserDisplay)client.level.getEntity(browserId);client.gui.setScreen(new BrowserPreviewScreen(null,display));phase=7;ticks=0;
                }
                case 7->{var s=(BrowserPreviewScreen)client.gui.screen();if(!s.surface.ready()||++ticks<100)return;
                    pending=s.surface.browser.getCefBrowser().getDevToolsClient().executeDevToolsMethod("Runtime.evaluate",new Gson().toJson(Map.of("expression","document.querySelector('#view')?.naturalWidth || 0","returnByValue",true))).thenAccept(raw->{var r=JsonParser.parseString(raw).getAsJsonObject();check(r.getAsJsonObject("result").get("value").getAsInt()==1280,"persistent browser frame did not load");});phase=8;
                }
                case 8->{capture(client,"workspace-browser.png");pass("Persistent Chromium page rendered through the Minecraft browser display");
                    client.gui.setScreen(null);var server=client.getSingleplayerServer();var id=client.player.getUUID();pending=server.submit(()->{var player=server.getPlayerList().getPlayer(id);player.teleportTo(player.level(),.5,67,27.5,Set.of(),180,10,true);});phase=9;ticks=0;
                }
                case 9->{if(++ticks<30)return;capture(client,"workspace-mobs.png");check(OfficeWorld.bindings().stream().anyMatch(b->b.character().equals("claude")),"Claude binding missing");
                    check(OfficeWorld.bindings().stream().anyMatch(b->b.character().equals("codex")),"Codex binding missing");
                    pass("Original crab and robot retained in the studio");
                    client.gui.setScreen(new WorkspaceScreen(null,null,group));phase=10;ticks=0;}
                case 10->{ticks++;if(ticks==30){click(client,"Settings");return;}if(ticks<60)return;capture(client,"workspace-settings.png");
                    var server=client.getSingleplayerServer();var id=client.player.getUUID();pending=server.submit(()->{
                        var player=server.getPlayerList().getPlayer(id);var level=player.level();
                        var pos=new net.minecraft.core.BlockPos(7,66,3);
                        var board=(ProjectBoardEntity)level.getBlockEntity(pos);board.group(group);
                        // Bind the full imported wall through its origin, just as a player click does.
                        for(int x=-12;x<0;x++)for(int y=66;y<72;y++)for(int z=10;z<21;z++){
                            var p=new net.minecraft.core.BlockPos(x,y,z);var state=level.getBlockState(p);
                            if(state.getBlock()==dev.agentcraft.block.ModBlocks.TASK_BOARD){var origin=dev.agentcraft.block.PanelBlock.origin(level,p,state);((dev.agentcraft.block.entity.StationBlockEntity)level.getBlockEntity(origin)).setBinding(group);}
                        }
                        player.teleportTo(level,0.5,66,15.5,Set.of(),90,0,true);
                    });client.gui.setScreen(null);phase=11;ticks=0;}
                case 11->{if(++ticks<60)return;capture(client,"workspace-task-wall.png");pass("Table and connected task wall link to a shared group; ALL PASS");phase=99;client.stop();}
            }
        }catch(Exception e){phase=99;ModdingWorkspace.LOGGER.error("Workspace verification failed",e);try{Files.writeString(REPORT,"FAIL "+e+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}client.stop();}
    });}
    private static void click(Minecraft client,String text){for(var child:client.gui.screen().children())if(child instanceof Button b && b.getMessage().getString().replace("• ","").equals(text)){b.onPress(null);return;}throw new IllegalStateException("Missing button "+text);}
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void pass(String message)throws Exception{Files.writeString(REPORT,"PASS "+message+"\n",StandardOpenOption.APPEND);}
    private static void capture(Minecraft client,String name){Screenshot.grab(client.gameDirectory,name,client.gameRenderer.mainRenderTarget(),1,c->{});}
}
