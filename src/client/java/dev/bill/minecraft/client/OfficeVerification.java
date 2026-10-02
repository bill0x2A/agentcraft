package dev.bill.minecraft.client;

import com.google.gson.Gson;
import dev.bill.minecraft.ModdingWorkspace;
import dev.bill.minecraft.AgentMob;
import dev.bill.minecraft.AgentActivity;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.EntityHitResult;

/** Opt-in live client test. Restricted to its own game directory and fresh test world. */
final class OfficeVerification {
    record Fixture(String terminal, String label, String pidline, String agent) {}
    private static final Path CONFIG = Path.of("../.runtime/office-fixtures.json");
    private static final Path REPORT = Path.of("../.runtime/office-verification.txt");
    private static final String WORLD = "office-test-"+System.currentTimeMillis();
    private static Fixture[] fixtures;
    private static OfficeWorld.Binding[] bindings = new OfficeWorld.Binding[2];
    private static volatile int phase;
    private static int waitTicks, index;
    private static volatile UUID browserId;
    private static volatile String browserText="";
    private static Object browserIdentity;
    private static String fixtureUrl;
    private static java.util.concurrent.CompletableFuture<String> browserRects;
    private static boolean backupPressed;
    private static long deadline = System.nanoTime()+240_000_000_000L;
    private static volatile String failure;

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 99) return;
            try {
                if (!client.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run-office-test"))
                    throw new IllegalStateException("Verification is restricted to run-office-test");
                if (failure != null) throw new IllegalStateException(failure);
                if (System.nanoTime() > deadline) throw new IllegalStateException("Timed out at phase "+phase+" screen="+(client.gui.screen()==null?"none":client.gui.screen().getClass().getName()));
                tick(client);
            } catch (Exception e) {
                phase = 99;
                ModdingWorkspace.LOGGER.error("OFFICE VERIFICATION FAILED", e);
                try { Files.writeString(REPORT, "FAIL: "+e+"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception ignored) {}
            }
        });
    }

    private static void tick(Minecraft client) throws Exception {
        switch (phase) {
            case 0 -> {
                if (!(client.gui.screen() instanceof TitleScreen)) return;
                fixtureUrl=com.google.gson.JsonParser.parseString(Files.readString(Path.of("../.runtime/browser-fixture.json"))).getAsJsonObject().get("url").getAsString();
                fixtures = new Gson().fromJson(Files.readString(CONFIG), Fixture[].class);
                check(fixtures.length == 2, "two fixtures required");
                Files.writeString(REPORT, "START "+WORLD+"\n");
                phase = 1;
                client.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings("Agent Office Verification", GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false), true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L,false,false), WorldPresets::createTestWorldDimensions, client.gui.screen());
            }
            case 1 -> {
                if (!OfficeClient.localWorld(client) || ++waitTicks < 60) return;
                phase = 2;
                var server = client.getSingleplayerServer(); UUID playerId = client.player.getUUID();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(playerId);
                        var level = player.level();
                        for (int x=-8;x<=8;x++) for(int z=-8;z<=8;z++) {
                            level.setBlock(new BlockPos(x,64,z), Blocks.STONE_BRICKS.defaultBlockState(),3);
                            for(int y=65;y<70;y++) level.setBlock(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState(),3);
                        }
                        for(int i=0;i<2;i++) {
                            player.teleportTo(i==0 ? -2 : 2,65,0); player.setYRot(0);
                            Fixture f=fixtures[i];
                            bindings[i]=OfficeWorld.spawn(server,playerId,new OfficeBridge.Session(f.terminal(),f.label(),f.agent()==null?"":f.agent(),"Shell","idle"));
                            var b=bindings[i];
                            level.setBlock(BlockPos.containing(b.x(),64,b.z()),Blocks.GOLD_BLOCK.defaultBlockState(),3);
                            level.setBlock(BlockPos.containing(b.x(),65,b.z()+1),Blocks.OAK_PLANKS.defaultBlockState(),3);
                        }
                        player.teleportTo(0,65,-4); player.setYRot(0); player.setXRot(8);
                        phase=3; waitTicks=0;
                    } catch(Exception e) { failure=e.toString(); }
                });
            }
            case 2 -> { }
            case 3 -> {
                if (!entitiesReady(client) || ++waitTicks < 60 || OfficeClient.error != null) return;
                check(OfficeWorld.bindings().size()==2,"two mob bindings");
                if (Boolean.getBoolean("modding.office.verifyAgents")) {
                    check(client.level.getEntity(bindings[0].mob()) instanceof AgentMob first && !first.isClaude(),"Codex model missing");
                    check(client.level.getEntity(bindings[1].mob()) instanceof AgentMob second && second.isClaude(),"Claude model missing");
                    check(agentState(client,AgentActivity.RESTING),"initial resting state missing");
                }
                pass("two named home-anchored mobs linked to distinct terminals");
                client.gui.setScreen(new OfficeScreen(null)); phase=4; waitTicks=0;
            }
            case 4 -> {
                if (++waitTicks<20) return;
                screenshot(client,"office-picker.png");
                client.gui.setScreen(null); phase=5; waitTicks=0;
            }
            case 5 -> {
                if (++waitTicks<20) return;
                screenshot(client,"office-mobs.png");
                if (Boolean.getBoolean("modding.office.verifyAgents")) {
                    screenshot(client,"agents-resting.png");pass("AGENTS_REQUEST working");phase=30;waitTicks=0;
                } else { index=0; interact(client); phase=6; }
            }
            case 6 -> {
                if (!frame(client).contains(fixtures[index].pidline())) return;
                ((TerminalScreen)client.gui.screen()).charTyped(new CharacterEvent(index==0 ? 'A' : 'B'));
                phase=7;
            }
            case 7 -> {
                String text=frame(client);
                if (!text.contains("INPUT="+(index==0 ? "A" : "B"))) return;
                check(!text.contains(fixtures[1-index].pidline()),"wrong terminal attached");
                pass("mob "+index+" interaction opens correct live process and accepts screen text input");
                ((TerminalScreen)client.gui.screen()).onClose();
                if (++index<2) { interact(client); phase=6; }
                else {
                    phase=8; waitTicks=0;
                    var server=client.getSingleplayerServer();
                    server.execute(() -> {
                        var mob=server.overworld().getEntity(bindings[0].mob());
                        mob.snapTo(mob.getX()+3,mob.getY(),mob.getZ());
                        var old=server.overworld().getEntity(bindings[1].mob());
                        var legacy=net.minecraft.world.entity.EntityTypes.VILLAGER.create(server.overworld(),net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        legacy.setUUID(old.getUUID());legacy.snapTo(old.getX(),old.getY(),old.getZ(),old.getYRot(),0);
                        old.discard();server.overworld().addFreshEntity(legacy);
                    });
                }
            }
            case 8 -> {
                if (++waitTicks<60) return;
                var mob=client.level.getEntity(bindings[0].mob());
                var b=bindings[0];
                check(mob.distanceToSqr(b.x(),b.y(),b.z())<.05,"mob did not return home");
                pass("displaced mob returns to its fixed home");
                check(client.level.getEntity(bindings[1].mob()) instanceof AgentMob,"legacy villager was not migrated");
                pass("legacy villager migrates to custom mob preserving UUID and session binding");
                phase=9;
                client.disconnect(new TitleScreen(),false);
            }
            case 9 -> {
                if (client.getSingleplayerServer()!=null || !(client.gui.screen() instanceof TitleScreen)) return;
                phase=10; waitTicks=0;backupPressed=false;
                Path saved=client.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("data/agent-office.json");
                var legacy=com.google.gson.JsonParser.parseString(Files.readString(saved)).getAsJsonObject();
                legacy.addProperty("version",1);
                for(var entry:legacy.getAsJsonArray("bindings"))entry.getAsJsonObject().remove("character");
                Files.writeString(saved,new Gson().toJson(legacy));
                client.createWorldOpenFlows().openWorld(WORLD,()->client.gui.setScreen(new TitleScreen()));
            }
            case 10 -> {
                if (!backupPressed && client.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup) {
                    backupPressed=true;
                    backup.children().stream().filter(w -> w instanceof Button).map(w -> (Button)w).findFirst().orElseThrow().onPress(new KeyEvent(40,13,0));
                    pass("backed up disposable verification world before reload");
                    return;
                }
                if (!entitiesReady(client) || ++waitTicks<60 || OfficeClient.error!=null) return;
                for(var b:bindings) check(b.equals(OfficeWorld.binding(b.mob())),"binding changed on world reload");
                pass("version-1 world reload preserves mob UUIDs, terminal IDs, labels, homes and detected characters");
                index=0; interact(client); phase=11;
            }
            case 11 -> {
                String text=frame(client);
                if (!text.contains(fixtures[index].pidline()) || !text.contains("INPUT="+(index==0 ? "A" : "B"))) return;
                pass("mob "+index+" resumes same live process and terminal contents after world reload");
                ((TerminalScreen)client.gui.screen()).onClose();
                if (++index<2) interact(client);
                else {
                    phase=12; deadline=System.nanoTime()+300_000_000_000L;
                    pass("AWAIT_END "+fixtures[1].terminal());
                }
            }
            case 12 -> {
                if (!OfficeClient.status(fixtures[1].terminal()).equals("Unavailable")) return;
                var previous = client.gui.screen();
                check(!OfficeClient.openMob(client,bindings[1].mob()),"ended session opened");
                check(client.gui.screen()==previous,"ended session substituted a terminal");
                if (previous instanceof TerminalScreen terminal) terminal.onClose();
                pass("ended session remains linked, is unavailable, and cannot open another terminal");
                phase=13; waitTicks=0;
            }
            case 13 -> {
                if (++waitTicks<60) return;
                String name=client.level.getEntity(bindings[1].mob()).getCustomName().getString();
                check(name.contains("Unavailable"),"nameplate did not update");
                screenshot(client,"office-unavailable.png");
                pass("nameplate reflects unavailable session");
                client.gui.setScreen(new OfficeScreen(null));
                for(var widget:client.gui.screen().children()) if(widget instanceof EditBox box) box.setValue("Office UI creation");
                Button create = client.gui.screen().children().stream().filter(w -> w instanceof Button b && b.getMessage().getString().equals("Create + link"))
                    .map(w -> (Button)w).findFirst().orElseThrow();
                create.onPress(new KeyEvent(40,13,0)); phase=14;
            }
            case 14 -> {
                var binding=OfficeWorld.bindings().stream().filter(b -> b.label().equals("Office UI creation")).findFirst();
                if(binding.isEmpty() || client.gui.screen() instanceof OfficeScreen) return;
                var b=binding.get();
                if(!OfficeClient.sessions.stream().anyMatch(s -> s.terminal().equals(b.terminal()))) return;
                Files.writeString(Path.of("../.runtime/office-ui-created.json"),new Gson().toJson(Map.of("terminal",b.terminal())));
                pass("Create + link button creates a real shell and its linked mob");
                phase=15; waitTicks=0;
                var server=client.getSingleplayerServer();
                server.execute(() -> server.overworld().getEntity(b.mob()).discard());
            }
            case 15 -> {
                if(++waitTicks<60)return;
                var b=OfficeWorld.bindings().stream().filter(v -> v.label().equals("Office UI creation")).findFirst().orElseThrow();
                check(client.level.getEntity(b.mob())==null,"test mob was not removed");
                check(OfficeClient.error==null && OfficeClient.sessions.stream().anyMatch(s -> s.terminal().equals(b.terminal())),"mob removal ended the session");
                pass("removing a mob leaves its Herdr session running");
                phase=40;waitTicks=0;deadline=System.nanoTime()+300_000_000_000L;
                var server=client.getSingleplayerServer();UUID player=client.player.getUUID();
                server.execute(()-> {
                    try {
                        var owner=server.getPlayerList().getPlayer(player);owner.teleportTo(0,65,-4);owner.setYRot(0);owner.setXRot(0);
                        browserId=BrowserEngine.place(owner).getUUID();
                    }catch(Exception e){failure=e.toString();}
                });
            }
            case 40 -> {
                if(browserId==null || !(client.level.getEntity(browserId) instanceof dev.bill.minecraft.BrowserDisplay display))return;
                var surface=BrowserEngine.surface(display);
                if(!surface.ready() || ++waitTicks<100)return;
                surface.browser.getCefBrowser().getText(text->browserText=text);
                if(!browserText.contains("Browser engine ready"))return;
                screenshot(client,"browser-world.png");pass("Chromium renders a live HTML page on a placed world monitor");
                var result=UseEntityCallback.EVENT.invoker().interact(client.player,client.level,InteractionHand.MAIN_HAND,display,new EntityHitResult(display));
                check(result==InteractionResult.SUCCESS && client.gui.screen() instanceof BrowserPreviewScreen,"monitor interaction failed");
                browserIdentity=surface.browser;
                browserRects=surface.browser.getCefBrowser().getDevToolsClient().executeDevToolsMethod("Runtime.evaluate",new Gson().toJson(Map.of(
                    "expression","['button','input'].map(s=>{const r=document.querySelector(s).getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2]})", "returnByValue",true)));
                phase=41;waitTicks=0;
            }
            case 41 -> {
                if(++waitTicks<30 || !browserRects.isDone())return;
                BrowserPreviewScreen screen=(BrowserPreviewScreen)client.gui.screen();
                // Test actual SDL mouse and character input, not DOM-generated clicks.
                var rects=com.google.gson.JsonParser.parseString(browserRects.join()).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("value");
                for(var rect:rects)clickBrowser(screen,rect.getAsJsonArray().get(0).getAsInt(),rect.getAsJsonArray().get(1).getAsInt());
                for(char c:"Minecraft input works".toCharArray())screen.charTyped(new CharacterEvent(c));
                phase=42;waitTicks=0;
            }
            case 42 -> {
                var screen=(BrowserPreviewScreen)client.gui.screen();
                screen.surface.browser.getCefBrowser().getText(text->browserText=text);
                if(!browserText.contains("Clicks: 1") || !browserText.contains("Minecraft input works")) {
                    if(++waitTicks==60){screenshot(client,"browser-input-diagnostic.png");Files.writeString(Path.of("../.runtime/browser-dom.txt"),browserText);}
                    return;
                }
                if(++waitTicks<30)return;
                screenshot(client,"browser-input.png");pass("browser receives real Minecraft mouse clicks and keyboard input, verified in Chromium DOM text");
                screen.onClose();phase=43;waitTicks=0;
            }
            case 43 -> {
                if(++waitTicks<60)return;
                var display=(dev.bill.minecraft.BrowserDisplay)client.level.getEntity(browserId);
                var surface=BrowserEngine.surface(display);
                check(surface.browser==browserIdentity,"closing controls replaced browser");
                surface.browser.getCefBrowser().getText(text->browserText=text);
                check(browserText.contains("Clicks: 1") && browserText.contains("Minecraft input works"),"page lost state");
                screenshot(client,"browser-world-input.png");pass("closing controls preserves the same live browser and page state in the world");
                BrowserEngine.saveUrl(display,fixtureUrl);surface.navigate(fixtureUrl);
                phase=44;waitTicks=0;
            }
            case 44 -> {
                var display=(dev.bill.minecraft.BrowserDisplay)client.level.getEntity(browserId);
                BrowserEngine.surface(display).browser.getCefBrowser().getText(text->browserText=text);
                if(!browserText.contains("Office browser fixture") || ++waitTicks<30)return;
                pass("browser navigates to a live local HTTP app");phase=45;
                client.disconnect(new TitleScreen(),false);
            }
            case 45 -> {
                if(client.getSingleplayerServer()!=null || !(client.gui.screen() instanceof TitleScreen))return;
                phase=46;waitTicks=0;backupPressed=false;
                client.createWorldOpenFlows().openWorld(WORLD,()->client.gui.setScreen(new TitleScreen()));
            }
            case 46 -> {
                if(!backupPressed && client.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup) {
                    backupPressed=true;backup.children().stream().filter(w->w instanceof Button).map(w->(Button)w).findFirst().orElseThrow().onPress(new KeyEvent(40,13,0));return;
                }
                if(client.level==null || !(client.level.getEntity(browserId) instanceof dev.bill.minecraft.BrowserDisplay display))return;
                check(display.url().equals(fixtureUrl),"saved monitor URL lost");
                var surface=BrowserEngine.surface(display);if(!surface.ready())return;
                surface.browser.getCefBrowser().getText(text->browserText=text);
                if(!browserText.contains("Office browser fixture") || ++waitTicks<40)return;
                check(surface.browser!=browserIdentity,"world unload did not release browser");
                screenshot(client,"browser-world-reloaded.png");pass("world reload restores monitor UUID, position and saved URL with a fresh browser");
                BrowserEngine.remove(display);phase=47;waitTicks=0;
            }
            case 47 -> {
                if(++waitTicks<40)return;
                check(client.level.getEntity(browserId)==null,"monitor remove failed");
                pass("monitor removal disposes its browser; ALL PASS");phase=99;
            }
            case 30 -> {
                if(!agentState(client,AgentActivity.WORKING) || ++waitTicks<30)return;
                screenshot(client,"agents-working.png");pass("both custom mobs animate Herdr working state");
                pass("AGENTS_REQUEST blocked");phase=31;waitTicks=0;
            }
            case 31 -> {
                if(!agentState(client,AgentActivity.ATTENTION) || ++waitTicks<30)return;
                for(var b:bindings)check(client.level.getEntity(b.mob()).getCustomName().getString().contains("Needs attention"),"attention nameplate missing");
                screenshot(client,"agents-attention.png");pass("both custom mobs show Herdr attention state and explicit nameplates");
                pass("AGENTS_REQUEST idle");phase=32;waitTicks=0;
            }
            case 32 -> {
                if(!agentState(client,AgentActivity.RESTING) || ++waitTicks<30)return;
                screenshot(client,"agents-resting-again.png");pass("both custom mobs return to resting when Herdr reports idle/done");
                index=0;interact(client);phase=6;
            }
            default -> throw new IllegalStateException("Unexpected phase");
        }
    }

    private static void clickBrowser(BrowserPreviewScreen screen,int x,int y) {
        var event=new net.minecraft.client.input.MouseButtonEvent(screen.guiX(x),screen.guiY(y),new net.minecraft.client.input.MouseButtonInfo(1,0));
        screen.mouseMoved(event.x(),event.y());screen.mouseClicked(event,false);screen.mouseReleased(event);
    }

    private static boolean agentState(Minecraft client,AgentActivity activity) {
        for(var b:bindings)if(!(client.level.getEntity(b.mob()) instanceof AgentMob mob) || mob.activity()!=activity)return false;
        return true;
    }

    private static boolean entitiesReady(Minecraft client) {
        return OfficeClient.localWorld(client) && bindings[0]!=null && bindings[1]!=null
            && client.level.getEntity(bindings[0].mob())!=null && client.level.getEntity(bindings[1].mob())!=null;
    }
    private static void interact(Minecraft client) {
        var entity=client.level.getEntity(bindings[index].mob());
        InteractionResult result=UseEntityCallback.EVENT.invoker().interact(client.player,client.level,InteractionHand.MAIN_HAND,entity,new EntityHitResult(entity));
        check(result==InteractionResult.SUCCESS && client.gui.screen() instanceof TerminalScreen,"right-click callback failed");
    }
    private static String frame(Minecraft client) {
        check(client.gui.screen() instanceof TerminalScreen,"terminal unexpectedly closed");
        var frame=((TerminalScreen)client.gui.screen()).connection.frame;
        if(frame==null)return "";
        StringBuilder text=new StringBuilder();
        for(int i=0;i<frame.cells().length;i++) { text.append(frame.cells()[i].text()); if((i+1)%frame.width()==0)text.append('\n'); }
        return text.toString();
    }
    private static void screenshot(Minecraft client,String name) {
        Screenshot.grab(client.gameDirectory,name,client.gameRenderer.mainRenderTarget(),1,message->ModdingWorkspace.LOGGER.info(message.getString()));
    }
    private static void check(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
    private static void pass(String message) throws Exception {
        ModdingWorkspace.LOGGER.info("OFFICE PASS {}",message);
        Files.writeString(REPORT,"PASS "+message+"\n",StandardOpenOption.APPEND);
    }
}
