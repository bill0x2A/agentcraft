package dev.bill.minecraft.client;

import dev.bill.minecraft.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Live furniture, movement, browser and world-reload checks in an isolated game directory. */
final class FurnitureVerification {
    private static final Path REPORT=Path.of("../.runtime/furniture-verification.txt");
    private static final String WORLD="furniture-test-"+System.currentTimeMillis();
    private static final BlockPos[] DESKS={new BlockPos(-3,65,2),new BlockPos(3,65,2)};
    private static final BlockPos BOARD=new BlockPos(-6,65,2), CABINET=new BlockPos(6,65,2), MONITOR=DESKS[0].above();
    private static final String SAVED_URL="http://127.0.0.1:9/furniture-verification";
    private static final long DEADLINE=System.nanoTime()+300_000_000_000L;
    private static final OfficeWorld.Binding[] bindings=new OfficeWorld.Binding[2];
    private static UUID monitor;
    private static int phase,ticks;
    private static boolean backupPressed;
    private static volatile boolean attention;
    private static CompletableFuture<Void> pending;
    private static CompletableFuture<String> pageText;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!attention) return;
            for (var b:bindings) if (b!=null && server.overworld().getEntity(b.mob()) instanceof AgentMob mob) mob.activity(AgentActivity.ATTENTION);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(phase==99)return;
            try {
                if(!client.gameDirectory.toPath().getFileName().toString().equals("run-furniture-test"))throw new IllegalStateException("Wrong verification directory");
                client.options.pauseOnLostFocus=false;
                if(client.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen)screen.onClose();
                if(System.nanoTime()>DEADLINE)throw new IllegalStateException("Timeout at phase "+phase);
                if(pending!=null) { if(!pending.isDone())return; pending.join();pending=null;ticks=0; }
                tick(client);
            }catch(Exception e){
                attention=false;phase=99;ModdingWorkspace.LOGGER.error("Furniture verification failed",e);
                try{Files.writeString(REPORT,"FAIL "+e+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}
                client.stop();
            }
        });
    }
    private static void tick(Minecraft client)throws Exception {
        switch(phase) {
            case 0 -> {
                if(!(client.gui.screen() instanceof TitleScreen))return;
                Files.createDirectories(REPORT.getParent());Files.writeString(REPORT,"START furniture verification\n");phase=1;
                client.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Agent office furniture",GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L,false,false),WorldPresets::createTestWorldDimensions,client.gui.screen());
            }
            case 1 -> {
                if(!OfficeClient.localWorld(client)||++ticks<50)return;
                run(client,owner -> {
                    var level=owner.level();var server=level.getServer();
                    for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++) {
                        level.setBlock(new BlockPos(x,64,z),Blocks.STONE_BRICKS.defaultBlockState(),3);
                        for(int y=65;y<=70;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                    }
                    for(int i=0;i<2;i++) {
                        level.setBlock(DESKS[i],OfficeBlocks.DESK.defaultBlockState(),3);
                        level.setBlock(DESKS[i].north(2),OfficeBlocks.CHAIR.defaultBlockState().setValue(OfficeFurnitureBlock.FACING,Direction.SOUTH),3);
                        owner.teleportTo(-8+i*3,65,-5);owner.setYRot(0);
                        var session=new OfficeBridge.Session("furniture-fixture-"+i,i==0?"Codex furniture":"Claude furniture",i==0?"codex":"claude","Idle","idle");
                        bindings[i]=OfficeWorld.spawn(server,owner.getUUID(),session);
                        bindings[i]=OfficeWorld.assignDesk(server,owner,DESKS[i],session.terminal());
                    }
                    level.setBlock(BOARD,OfficeBlocks.BOARD.defaultBlockState(),3);
                    level.setBlock(CABINET,OfficeBlocks.CABINET.defaultBlockState(),3);
                    level.setBlock(MONITOR,OfficeBlocks.MONITOR.defaultBlockState(),3);
                    var board=(ProjectBoardEntity)level.getBlockEntity(BOARD);board.title("Furniture project");
                    for(var b:bindings)board.toggle(b.terminal());
                    monitor=BrowserEngine.placeMonitor(owner,MONITOR).getUUID();
                    check(monitor.equals(BrowserEngine.placeMonitor(owner,MONITOR).getUUID()),"monitor duplicated on reopen");
                    owner.teleportTo(8,65,-6);owner.setYRot(40);owner.setXRot(-4);
                    pass("five furniture blocks placed; desks link both characters; project membership and monitor identity set");
                });phase=2;
            }
            case 2 -> {
                client.player.setYRot(40);client.player.setXRot(10);
                if(++ticks<60)return;
                run(client,owner -> {
                    for(var b:bindings)check(((AgentMob)owner.level().getEntity(b.mob())).seated(),"agent did not sit");
                    pass("both characters sit at their assigned work spots");
                });phase=3;
            }
            case 3 -> {
                if(++ticks<30)return;
                capture(client,"furniture-office.png");
                interact(client,BOARD);check(client.gui.screen() instanceof FurnitureScreen,"project board did not open");phase=4;ticks=0;
            }
            case 4 -> {
                if(++ticks<25)return;capture(client,"furniture-project-board.png");client.gui.screen().onClose();
                var previousSessions=OfficeClient.sessions;var previousError=OfficeClient.error;
                try {
                    OfficeClient.sessions=Arrays.stream(bindings).map(b -> new OfficeBridge.Session(b.terminal(),b.label(),b.character(),"Idle","idle")).toList();
                    OfficeClient.error=null;
                    interact(client,DESKS[0]);check(client.gui.screen() instanceof TerminalScreen,"desk did not open its terminal");client.gui.screen().onClose();
                    interact(client,DESKS[1].north(2));check(client.gui.screen() instanceof TerminalScreen,"chair did not open its agent terminal");client.gui.screen().onClose();
                } finally {OfficeClient.sessions=previousSessions;OfficeClient.error=previousError;}
                attention=true;phase=5;ticks=0;
            }
            case 5 -> {
                if(++ticks<100)return;
                run(client,owner -> {
                    for(var b:bindings) {
                        var mob=(AgentMob)owner.level().getEntity(b.mob());
                        check(!mob.seated() && mob.distanceToSqr(b.x(),b.y(),b.z())>2,"attention did not leave chair");
                    }
                    pass("desk/chair/board interactions open controls; attention makes both agents stand and approach");
                    attention=false;
                });phase=6;
            }
            case 6 -> {
                if(++ticks<160)return;
                run(client,owner -> {
                    for(var b:bindings)check(((AgentMob)owner.level().getEntity(b.mob())).seated(),"agent did not return to chair");
                    OfficeWorld.archive(owner.level().getServer(),bindings[0].terminal());
                    check(OfficeWorld.binding(bindings[0].mob()).archived(),"filing state missing");
                    check(owner.level().getEntity(bindings[0].mob())==null,"filed mob still present");
                    check(OfficeWorld.atDesk(owner.level().dimension().identifier().toString(),DESKS[0])==null,"filed desk still occupied");
                    pass("attention clears back to seated pose; filing removes mob and frees its desk");
                });phase=7;
            }
            case 7 -> {
                if(++ticks<20)return;
                run(client,owner -> {
                    var restored=OfficeWorld.assignDesk(owner.level().getServer(),owner,DESKS[0],bindings[0].terminal());
                    check(restored.mob().equals(bindings[0].mob()) && restored.terminal().equals(bindings[0].terminal()),"restoration changed identity");
                    boolean rejected=false;
                    try{OfficeWorld.assignDesk(owner.level().getServer(),owner,DESKS[0],bindings[1].terminal());}catch(IllegalStateException expected){rejected=true;}
                    check(rejected,"occupied desk accepted a second agent");
                    pass("restoration preserves mob and terminal IDs; occupied desk rejects reassignment");
                });phase=8;
            }
            case 8 -> {
                if(!(client.level.getEntity(monitor) instanceof BrowserDisplay display))return;
                var surface=BrowserEngine.surface(display);if(!surface.ready())return;
                check(display.displayScale()<.3F,"monitor did not use compact scale");
                if(pageText==null){pageText=new CompletableFuture<>();surface.browser.getCefBrowser().getText(pageText::complete);return;}
                if(!pageText.isDone() || !pageText.join().contains("Browser engine ready"))return;
                pass("compact monitor renders live Chromium and shares its persistent surface");
                capture(client,"furniture-monitor-live.png");
                run(client,owner -> {
                    OfficeWorld.archive(owner.level().getServer(),bindings[1].terminal());
                    ((BrowserDisplay)owner.level().getEntity(monitor)).url(SAVED_URL);
                });phase=9;
            }
            case 9 -> {client.disconnect(new TitleScreen(),false);phase=10;}
            case 10 -> {
                if(client.getSingleplayerServer()!=null || !(client.gui.screen() instanceof TitleScreen))return;
                client.createWorldOpenFlows().openWorld(WORLD,()->client.gui.setScreen(new TitleScreen()));phase=11;ticks=0;
            }
            case 11 -> {
                if(!backupPressed && client.gui.screen() instanceof BackupConfirmScreen screen) {
                    backupPressed=true;screen.children().stream().filter(w->w instanceof Button).map(w->(Button)w).findFirst().orElseThrow().onPress(new KeyEvent(40,13,0));return;
                }
                if(!OfficeClient.localWorld(client)||++ticks<80)return;
                run(client,owner -> {
                    var level=owner.level();var b=OfficeWorld.binding(bindings[0].mob());
                    check(b.desk().equals(DESKS[0].asLong()) && !b.archived(),"desk binding lost on reload");
                    check(((AgentMob)level.getEntity(b.mob())).seated(),"seated pose not restored");
                    check(OfficeWorld.binding(bindings[1].mob()).archived() && level.getEntity(bindings[1].mob())==null,"filed session lost on reload");
                    var board=(ProjectBoardEntity)level.getBlockEntity(BOARD);
                    check(board.title().equals("Furniture project") && board.terminals().size()==2,"project data lost on reload");
                    var display=(BrowserDisplay)level.getEntity(monitor);
                    check(display.anchor().equals(MONITOR) && display.url().equals(SAVED_URL),"monitor address or anchor lost");
                    pass("world reload preserves desks, chair pose, filed sessions, project membership and monitor address");
                    OfficeWorld.assignDesk(level.getServer(),owner,DESKS[1],bindings[1].terminal());
                    level.removeBlock(DESKS[0].north(2),false);level.removeBlock(MONITOR,false);
                });phase=12;
            }
            case 12 -> {
                if(++ticks<60)return;
                run(client,owner -> {
                    var first=(AgentMob)owner.level().getEntity(bindings[0].mob());
                    check(!first.seated() && Math.abs(first.getY()-bindings[0].y())<.01,"chair removal did not restore standing home");
                    check(((AgentMob)owner.level().getEntity(bindings[1].mob())).seated(),"filed agent could not be restored after reload");
                    check(owner.level().getEntity(monitor)==null,"broken monitor left its browser entity behind");
                    pass("restoration after reload and chair/monitor removal work; ALL PASS");
                });phase=13;
            }
            case 13 -> {phase=99;client.stop();}
        }
    }
    private static void interact(Minecraft client,BlockPos pos) {
        UseBlockCallback.EVENT.invoker().interact(client.player,client.level,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
    }
    private static void run(Minecraft client,FurnitureClient.Action action) {
        var server=client.getSingleplayerServer();var id=client.player.getUUID();pending=new CompletableFuture<>();
        server.execute(()->{try{action.run(server.getPlayerList().getPlayer(id));pending.complete(null);}catch(Exception e){pending.completeExceptionally(e);}});
    }
    private static void capture(Minecraft client,String name){Screenshot.grab(client.gameDirectory,name,client.gameRenderer.mainRenderTarget(),1,c->{});}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void pass(String message)throws Exception{Files.writeString(REPORT,"PASS "+message+"\n",StandardOpenOption.APPEND);ModdingWorkspace.LOGGER.info("FURNITURE PASS {}",message);}
}
