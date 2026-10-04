package dev.bill.minecraft.client;

import com.google.gson.JsonParser;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Disposable live verification of window geometry, terminal input and cached browser startup. */
final class WindowVerification {
    private static int phase,ticks,oldCols;
    private static String terminal,pid;
    private static long browserStart;
    private static final long DEADLINE=System.nanoTime()+180_000_000_000L;
    private static final Path REPORT=Path.of("../.runtime/window-verification.txt");
    static void register(){ClientTickEvents.END_CLIENT_TICK.register(client->{
        if(phase==99)return;
        try{
            if(!client.gameDirectory.toPath().getFileName().toString().equals("run-window-test"))throw new IllegalStateException("Wrong verification directory");
            if(System.nanoTime()>DEADLINE)throw new IllegalStateException("Timeout phase "+phase);
            switch(phase){
                case 0 -> {
                    if(!(client.gui.screen() instanceof TitleScreen))return;
                    var f=JsonParser.parseString(Files.readString(Path.of("../.runtime/window-fixture.json"))).getAsJsonObject();
                    terminal=f.get("terminal").getAsString();pid=f.get("pidline").getAsString();
                    Files.writeString(REPORT,"START window and cached-browser verification\n");phase=1;
                    client.createWorldOpenFlows().createFreshLevel("window-test-"+System.currentTimeMillis(),
                        new LevelSettings("Terminal windows",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42L,false,false),WorldPresets::createTestWorldDimensions,client.gui.screen());
                }
                case 1 -> {if(!OfficeClient.localWorld(client)||++ticks<60)return;client.gui.setScreen(new TerminalScreen(null,terminal));phase=2;ticks=0;}
                case 2 -> {
                    var screen=(TerminalScreen)client.gui.screen();if(!text(screen).contains(pid)||++ticks<30)return;
                    check(screen.windowX>0 && screen.windowY>0 && screen.windowWidth<screen.width,"terminal is not a window");
                    capture(client,"terminal-window.png");oldCols=screen.connection.frame.width();
                    int oldX=screen.windowX;
                    var down=mouse(screen.windowX+30,screen.windowY+3);screen.mouseClicked(down,false);
                    var drag=mouse(down.x()+20,down.y()+15);screen.mouseDragged(drag,20,15);screen.mouseReleased(drag);
                    check(screen.windowX!=oldX,"title drag failed");
                    down=mouse(screen.windowX+screen.windowWidth-3,screen.windowY+screen.windowHeight-3);screen.mouseClicked(down,false);
                    drag=mouse(down.x()-100,down.y()-35);screen.mouseDragged(drag,-100,-35);screen.mouseReleased(drag);
                    phase=3;ticks=0;
                }
                case 3 -> {
                    var screen=(TerminalScreen)client.gui.screen();if(screen.connection.frame.width()>=oldCols||++ticks<20)return;
                    screen.charTyped(new CharacterEvent('W'));phase=4;ticks=0;
                }
                case 4 -> {
                    var screen=(TerminalScreen)client.gui.screen();if(!text(screen).contains("INPUT=W")||++ticks<20)return;
                    capture(client,"terminal-window-resized.png");pass("window moves, resizes its live terminal, and accepts text after resize");
                    screen.mouseClicked(mouse(screen.windowX+screen.windowWidth-4,screen.windowY+3),false);
                    check(client.gui.screen()==null,"close control failed");client.gui.setScreen(new TerminalScreen(null,terminal));phase=5;ticks=0;
                }
                case 5 -> {
                    var screen=(TerminalScreen)client.gui.screen();if(!text(screen).contains(pid)||!text(screen).contains("INPUT=W"))return;
                    pass("close and reopen preserve the same terminal process and contents");screen.onClose();
                    browserStart=System.nanoTime();client.gui.setScreen(new BrowserPreviewScreen(null));phase=6;ticks=0;
                }
                case 6 -> {
                    var screen=(BrowserPreviewScreen)client.gui.screen();
                    check(screen.surface.initialization.getStage()!=net.dimaskama.mcef.api.MCEFApi.Initialization.Stage.DOWNLOADING,"cached engine attempted a download");
                    if(!screen.surface.ready()||++ticks<40)return;
                    pass("installed browser reused without download; live GPU frame in "+((System.nanoTime()-browserStart)/1_000_000_000L)+"s");
                    capture(client,"browser-cached.png");pass("ALL PASS");phase=99;
                }
            }
        }catch(Exception e){phase=99;dev.bill.minecraft.ModdingWorkspace.LOGGER.error("Window verification failed",e);try{Files.writeString(REPORT,"FAIL "+e+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}}
    });}
    private static MouseButtonEvent mouse(double x,double y){return new MouseButtonEvent(x,y,new MouseButtonInfo(1,0));}
    private static String text(TerminalScreen screen){var f=screen.connection.frame;if(f==null)return "";StringBuilder b=new StringBuilder();for(var c:f.cells())b.append(c.text());return b.toString();}
    private static void capture(Minecraft client,String name){Screenshot.grab(client.gameDirectory,name,client.gameRenderer.mainRenderTarget(),1,c->{});}
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void pass(String message)throws Exception{Files.writeString(REPORT,"PASS "+message+"\n",StandardOpenOption.APPEND);}
}
